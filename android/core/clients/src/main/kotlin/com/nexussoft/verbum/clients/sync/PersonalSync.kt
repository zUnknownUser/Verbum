package com.nexussoft.verbum.clients.sync

import com.nexussoft.verbum.clients.PreferencesClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable data class PersonalValue(
    val book: String? = null, val chapter: Int? = null, val verse: Int? = null,
    val highlight: String? = null, val style: String? = null, val note: String? = null,
    val bookmarked: Boolean? = null, val time: Long? = null, val day: String? = null,
)
@Serializable data class PersonalRecord(val id: String, val revision: Long, val value: PersonalValue? = null)
@Serializable data class PersonalChange(val id: String, val baseRevision: Long = 0, val mutationId: String = UUID.randomUUID().toString(), val base: PersonalValue? = null, val value: PersonalValue? = null)
@Serializable data class PersonalSyncRequest(val cursor: Long, val changes: List<PersonalChange>)
@Serializable data class PersonalSyncResponse(val cursor: Long, val more: Boolean, val records: List<PersonalRecord>, val accepted: List<PersonalRecord>)

/** Durable outbox; immutable account binding and conditional application prevent cross-account or in-flight overwrites. */
class PersonalSyncClient(
    private val preferences: PreferencesClient,
    private val read: suspend () -> Map<String, PersonalValue>,
    private val apply: suspend (PersonalRecord, PersonalValue?) -> Unit,
    private val exchange: suspend (PersonalSyncRequest) -> PersonalSyncResponse,
    private val active: () -> Boolean,
) {
    @Serializable private data class Ledger(val cursor: Long = 0, val records: Map<String, PersonalRecord> = emptyMap(), val pending: List<PersonalChange> = emptyList())
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    suspend fun sync(): Boolean {
        if (!mutex.tryLock()) return false
        try {
            checkActive()
            var ledger = preferences.string("syncLedger")?.let { json.decodeFromString<Ledger>(it) } ?: Ledger()
            repeat(10) {
                checkActive()
                var snapshot = read()
                val missingHistory = ledger.records.values.filter {
                    !it.id.startsWith("annotation:") && it.value != null && snapshot[it.id] == null
                }
                if (missingHistory.isNotEmpty()) {
                    missingHistory.forEach { apply(it, null) }
                    snapshot = read()
                }
                if (ledger.pending.isEmpty()) {
                    val changes = (snapshot.keys + ledger.records.keys).sorted().mapNotNull { id ->
                        val base = ledger.records[id]?.value
                        if (snapshot[id] == base) null else PersonalChange(id, baseRevision = ledger.records[id]?.revision ?: 0, base = base, value = snapshot[id])
                    }
                    ledger = ledger.copy(pending = changes)
                    if (changes.isNotEmpty()) save(ledger)
                }
                val batch = ledger.pending.take(100)
                val result = exchange(PersonalSyncRequest(ledger.cursor, batch))
                checkActive()
                val sent = batch.associateBy { it.id }
                val records = ledger.records.toMutableMap()
                for (record in (result.records + result.accepted).sortedBy { it.revision }) {
                    if ((records[record.id]?.revision ?: -1) >= record.revision) continue
                    if (result.accepted.any { it.id == record.id && it.revision > record.revision }) continue
                    val expected = if (sent.containsKey(record.id)) sent[record.id]?.value else records[record.id]?.value
                    apply(record, expected)
                    records[record.id] = record
                }
                val changed = batch.isNotEmpty() || result.cursor != ledger.cursor || result.records.isNotEmpty()
                ledger = Ledger(result.cursor, records, ledger.pending.filter { sent[it.id]?.mutationId != it.mutationId })
                if (changed) save(ledger)
                if (!result.more && ledger.pending.isEmpty()) {
                    val current = read()
                    return (current.keys + records.keys).all { current[it] == records[it]?.value }
                }
            }
            return false
        } finally { mutex.unlock() }
    }
    private fun checkActive() { if (!active()) throw CancellationException("Account changed") }
    private fun save(ledger: Ledger) { checkActive(); preferences.setString("syncLedger", json.encodeToString(ledger)) }
}
