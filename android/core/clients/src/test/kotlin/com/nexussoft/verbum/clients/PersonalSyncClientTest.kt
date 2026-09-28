package com.nexussoft.verbum.clients

import com.nexussoft.verbum.clients.sync.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class PersonalSyncClientTest {
    private val id="annotation:Job.38.4"
    private val note=PersonalValue(book="Job",chapter=38,verse=4,note="First")
    @Test fun outboxSurvivesFailureAndPreservesEditsMadeDuringRequest()=runTest {
        val prefs=InMemoryPreferencesClient()
        val local=mutableMapOf(id to note)
        var firstMutation=""
        val failing=PersonalSyncClient(prefs,{local.toMap()},{_,_->},{ request ->
            firstMutation=request.changes.single().mutationId
            error("offline")
        },{true})
        assertFailsWith<IllegalStateException> { failing.sync() }
        assertNotNull(prefs.string("syncLedger"))
        val newer=note.copy(note="Edited while uploading")
        var count=0
        val restarted=PersonalSyncClient(prefs,{local.toMap()},{record,expected->
            if(local[record.id]==expected) { if(record.value==null) local.remove(record.id) else local[record.id]=record.value }
        },{request->
            count++
            val change=request.changes.single()
            if(count==1){assertEquals(firstMutation,change.mutationId);local[id]=newer}
            PersonalSyncResponse(count.toLong(),false,emptyList(),listOf(PersonalRecord(id,count.toLong(),change.value)))
        },{true})
        assertFalse(restarted.sync())
        assertEquals(newer,local[id])
        assertTrue(restarted.sync())
        assertEquals(newer,local[id])
    }
    @Test fun incomingDeletionIsAppliedAndNotResurrected()=runTest {
        val prefs=InMemoryPreferencesClient();val local=mutableMapOf(id to note);var calls=0
        val client=PersonalSyncClient(prefs,{local.toMap()},{record,expected->
            if(local[record.id]==expected){if(record.value==null)local.remove(record.id) else local[record.id]=record.value}
        },{request->
            calls++
            when(calls){
                1->PersonalSyncResponse(1,false,emptyList(),listOf(PersonalRecord(id,1,note)))
                2->PersonalSyncResponse(2,false,listOf(PersonalRecord(id,2,null)),emptyList())
                else->{assertTrue(request.changes.isEmpty());PersonalSyncResponse(2,false,emptyList(),emptyList())}
            }
        },{true})
        assertTrue(client.sync());assertTrue(client.sync());assertTrue(local.isEmpty());assertTrue(client.sync())
    }
    @Test fun switchingAccountsRejectsInFlightResponse()=runTest {
        val prefs=InMemoryPreferencesClient();var active=true;var applied=false
        val client=PersonalSyncClient(prefs,{emptyMap()},{_,_->applied=true},{active=false;PersonalSyncResponse(1,false,listOf(PersonalRecord(id,1,note)),emptyList())},{active})
        assertFailsWith<CancellationException>{client.sync()};assertFalse(applied)
    }
    @Test fun storageMergesHistoryAndAnnotationsAcrossPlatformWireFormat()=runTest {
        val prefs=InMemoryPreferencesClient();val storage=PersonalDataStorage(prefs)
        storage.apply(PersonalRecord(id,1,note),null)
        storage.apply(PersonalRecord("visit:Job.38",2,PersonalValue(book="Job",chapter=38,time=1000)),null)
        storage.apply(PersonalRecord("day:2026-09-28",3,PersonalValue(day="2026-09-28")),null)
        storage.apply(PersonalRecord("position:last",4,PersonalValue(book="Job",chapter=38,time=1000)),null)
        val snapshot=storage.snapshot()
        assertEquals(note,snapshot[id]);assertEquals(4,snapshot.size)
        assertEquals("Job 38",prefs.string("lastRead"))
        storage.apply(PersonalRecord(id,5,null),note)
        assertNull(storage.snapshot()[id])
    }
    @Test fun missingHistoryIsRestoredWithoutSendingDeletion() = runTest {
        val prefs = InMemoryPreferencesClient()
        val local = mutableMapOf<String, PersonalValue>()
        val day = "day:2026-09-28"
        val value = PersonalValue(day = "2026-09-28")
        val client = PersonalSyncClient(prefs, { local.toMap() }, { record, _ ->
            record.value?.let { local[record.id] = it }
        }, { request ->
            assertTrue(request.changes.isEmpty())
            PersonalSyncResponse(1, false,
                if (request.cursor == 0L) listOf(PersonalRecord(day, 1, value)) else emptyList(), emptyList())
        }, { true })
        assertTrue(client.sync())
        local.clear()
        assertTrue(client.sync())
        assertEquals(value, local[day])
    }
}
