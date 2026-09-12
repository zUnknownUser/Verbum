package com.nexussoft.verbum.clients.fixtures

import com.nexussoft.verbum.models.BibleEntity
import com.nexussoft.verbum.models.BibleEntityType
import com.nexussoft.verbum.models.BibleRelationship
import com.nexussoft.verbum.models.BookLanguage
import com.nexussoft.verbum.models.EntityDetail
import com.nexussoft.verbum.models.EntityId
import com.nexussoft.verbum.models.PassageReference
import com.nexussoft.verbum.models.RelationshipType
import com.nexussoft.verbum.models.SourceReference
import com.nexussoft.verbum.models.formatted

// FIXTURE — hand-curated, ported from the iOS EntityFixtureData (same data, one origin).
// Labelled as such; nothing here is production content. Summaries are short and
// non-interpretive (§3.5); dates are hedged; every relationship carries a source (§33).
internal object EntityFixtureData {
    val scriptureSource = SourceReference("fixture.source.web", "World English Bible (public domain) — the passages cited", "https://worldenglish.bible")
    val editorialSource = SourceReference("fixture.source.editorial", "Verbum editorial notes (fixture; to be replaced by sourced content)", null)
    val sources = listOf(scriptureSource, editorialSource)

    fun passageNode(reference: PassageReference) =
        BibleEntity("passage.${reference.bookId}.${reference.chapter}", BibleEntityType.PASSAGE, reference.formatted(BookLanguage.ENGLISH), null)

    fun passageReference(node: BibleEntity): PassageReference? {
        if (node.type != BibleEntityType.PASSAGE) return null
        val parts = node.id.split(".")
        if (parts.size != 3 || parts[0] != "passage") return null
        return PassageReference(parts[1], parts[2].toIntOrNull() ?: return null)
    }

    val entities: List<BibleEntity> = listOf(
        BibleEntity("fixture.person.jesus", BibleEntityType.PERSON, "Jesus", "Central figure of the New Testament."),
        BibleEntity("fixture.person.paul", BibleEntityType.PERSON, "Paul", "Apostle; author of many New Testament letters."),
        BibleEntity("fixture.person.peter", BibleEntityType.PERSON, "Peter", "Disciple of Jesus; leader among the apostles."),
        BibleEntity("fixture.person.abraham", BibleEntityType.PERSON, "Abraham", "Patriarch; the covenant with God begins with him."),
        BibleEntity("fixture.person.moses", BibleEntityType.PERSON, "Moses", "Led Israel out of Egypt; received the Law."),
        BibleEntity("fixture.person.david", BibleEntityType.PERSON, "David", "King of Israel; associated with many Psalms."),
        BibleEntity("fixture.person.solomon", BibleEntityType.PERSON, "Solomon", "King of Israel; built the first temple."),
        BibleEntity("fixture.person.saul", BibleEntityType.PERSON, "Saul", "First king of Israel."),
        BibleEntity("fixture.person.mary", BibleEntityType.PERSON, "Mary", "Mother of Jesus."),
        BibleEntity("fixture.person.john", BibleEntityType.PERSON, "John", "Disciple of Jesus; traditionally linked to the Gospel of John."),
        BibleEntity("fixture.person.goliath", BibleEntityType.PERSON, "Goliath", "Philistine champion from Gath, defeated by David."),
        BibleEntity("fixture.person.samuel", BibleEntityType.PERSON, "Samuel", "Prophet and judge; anointed Saul and David."),
        BibleEntity("fixture.person.bathsheba", BibleEntityType.PERSON, "Bathsheba", "Wife of Uriah, then of David; mother of Solomon."),
        BibleEntity("fixture.place.jerusalem", BibleEntityType.PLACE, "Jerusalem", "City of David; site of the temple."),
        BibleEntity("fixture.place.bethlehem", BibleEntityType.PLACE, "Bethlehem", "Birthplace of David and of Jesus."),
        BibleEntity("fixture.place.nazareth", BibleEntityType.PLACE, "Nazareth", "Town in Galilee where Jesus grew up."),
        BibleEntity("fixture.place.galilee", BibleEntityType.PLACE, "Galilee", "Northern region; setting of much of Jesus' ministry."),
        BibleEntity("fixture.place.rome", BibleEntityType.PLACE, "Rome", "Capital of the empire; destination of Paul's letter to the Romans."),
        BibleEntity("fixture.place.corinth", BibleEntityType.PLACE, "Corinth", "Greek city; recipient of two of Paul's letters."),
        BibleEntity("fixture.place.ephesus", BibleEntityType.PLACE, "Ephesus", "City in Asia Minor; recipient of Paul's letter to the Ephesians."),
        BibleEntity("fixture.place.babylon", BibleEntityType.PLACE, "Babylon", "Empire and city; place of Judah's exile."),
        BibleEntity("fixture.place.egypt", BibleEntityType.PLACE, "Egypt", "Where Israel was enslaved before the Exodus."),
        BibleEntity("fixture.place.elah", BibleEntityType.PLACE, "Valley of Elah", "Where Israel and the Philistines faced each other in 1 Samuel 17."),
        BibleEntity("fixture.theme.faith", BibleEntityType.THEME, "Faith", "Trust in God; a recurring theme across both testaments."),
        BibleEntity("fixture.theme.grace", BibleEntityType.THEME, "Grace", "Unearned favour; central in Paul's letters."),
        BibleEntity("fixture.theme.forgiveness", BibleEntityType.THEME, "Forgiveness", "Release from wrongdoing, divine and human."),
        BibleEntity("fixture.theme.love", BibleEntityType.THEME, "Love", "Of God, and for neighbour; the greatest commandments."),
        BibleEntity("fixture.theme.anxiety", BibleEntityType.THEME, "Anxiety", "Worry and its answer; e.g. Matthew 6:25–34."),
        BibleEntity("fixture.theme.wisdom", BibleEntityType.THEME, "Wisdom", "Skill in living well; Proverbs, Job, Ecclesiastes."),
        BibleEntity("fixture.theme.justice", BibleEntityType.THEME, "Justice", "Right dealing, especially toward the vulnerable."),
        BibleEntity("fixture.theme.prayer", BibleEntityType.THEME, "Prayer", "Speaking with God; Psalms, the Lord's Prayer."),
        BibleEntity("fixture.theme.money", BibleEntityType.THEME, "Money", "Wealth, generosity and its dangers."),
        BibleEntity("fixture.theme.suffering", BibleEntityType.THEME, "Suffering", "Pain and its meaning; Job, Lamentations, the Passion."),
        BibleEntity("fixture.event.david-anointed", BibleEntityType.EVENT, "Anointing of David", "Samuel anoints David in Bethlehem while Saul is still king."),
        BibleEntity("fixture.event.david-goliath", BibleEntityType.EVENT, "David and Goliath", "David defeats the Philistine champion in the Valley of Elah."),
        BibleEntity("fixture.event.david-takes-jerusalem", BibleEntityType.EVENT, "David takes Jerusalem", "David captures the Jebusite stronghold and makes it his capital."),
        BibleEntity("fixture.event.exodus", BibleEntityType.EVENT, "The Exodus", "Israel leaves Egypt under Moses."),
        passageNode(PassageReference("Gen", 1)),
        passageNode(PassageReference("1Sam", 16)),
        passageNode(PassageReference("1Sam", 17)),
        passageNode(PassageReference("2Sam", 5)),
        passageNode(PassageReference("Ps", 23)),
        passageNode(PassageReference("Ps", 51)),
        passageNode(PassageReference("Matt", 6)),
        passageNode(PassageReference("John", 3)),
        passageNode(PassageReference("Rom", 8)),
    )

    private val byId: Map<EntityId, BibleEntity> = entities.associateBy { it.id }
    fun entity(id: EntityId): BibleEntity? = byId[id]

    private fun edge(source: String, type: RelationshipType, target: String, scripture: Boolean = true) = BibleRelationship(
        id = "fixture.edge.$source.${type.wireValue}.$target",
        sourceId = source, targetId = target, type = type,
        confidence = if (scripture) 1.0 else 0.8,
        sourceReferenceIds = listOf(if (scripture) scriptureSource.id else editorialSource.id),
    )

    val relationships: List<BibleRelationship> = listOf(
        edge("fixture.person.david", RelationshipType.APPEARS_IN, "passage.1Sam.16"),
        edge("fixture.person.david", RelationshipType.APPEARS_IN, "passage.1Sam.17"),
        edge("fixture.person.david", RelationshipType.APPEARS_IN, "passage.2Sam.5"),
        edge("fixture.person.david", RelationshipType.APPEARS_IN, "passage.Ps.23"),
        edge("fixture.person.david", RelationshipType.APPEARS_IN, "passage.Ps.51"),
        edge("fixture.person.david", RelationshipType.RELATED_TO, "fixture.person.goliath"),
        edge("fixture.person.david", RelationshipType.RELATED_TO, "fixture.person.saul"),
        edge("fixture.person.david", RelationshipType.RELATED_TO, "fixture.person.samuel"),
        edge("fixture.person.david", RelationshipType.RELATED_TO, "fixture.person.bathsheba"),
        edge("fixture.person.david", RelationshipType.RELATED_TO, "fixture.person.solomon"),
        edge("fixture.person.david", RelationshipType.PARTICIPATES_IN, "fixture.event.david-anointed"),
        edge("fixture.person.david", RelationshipType.PARTICIPATES_IN, "fixture.event.david-goliath"),
        edge("fixture.person.david", RelationshipType.PARTICIPATES_IN, "fixture.event.david-takes-jerusalem"),
        edge("fixture.person.david", RelationshipType.RELATED_TO, "fixture.place.bethlehem"),
        edge("fixture.person.david", RelationshipType.RELATED_TO, "fixture.place.jerusalem"),
        edge("fixture.person.david", RelationshipType.RELATED_TO_THEME, "fixture.theme.forgiveness", scripture = false),
        edge("fixture.person.david", RelationshipType.RELATED_TO_THEME, "fixture.theme.prayer", scripture = false),
        edge("fixture.person.goliath", RelationshipType.APPEARS_IN, "passage.1Sam.17"),
        edge("fixture.person.goliath", RelationshipType.PARTICIPATES_IN, "fixture.event.david-goliath"),
        edge("fixture.person.saul", RelationshipType.APPEARS_IN, "passage.1Sam.16"),
        edge("fixture.person.saul", RelationshipType.APPEARS_IN, "passage.1Sam.17"),
        edge("fixture.person.saul", RelationshipType.RELATED_TO, "fixture.person.samuel"),
        edge("fixture.person.samuel", RelationshipType.APPEARS_IN, "passage.1Sam.16"),
        edge("fixture.person.samuel", RelationshipType.PARTICIPATES_IN, "fixture.event.david-anointed"),
        edge("fixture.person.bathsheba", RelationshipType.RELATED_TO, "fixture.person.solomon"),
        edge("fixture.person.solomon", RelationshipType.RELATED_TO, "fixture.place.jerusalem"),
        edge("fixture.event.david-anointed", RelationshipType.OCCURS_AT, "fixture.place.bethlehem"),
        edge("fixture.event.david-anointed", RelationshipType.PRECEDES, "fixture.event.david-goliath"),
        edge("fixture.event.david-goliath", RelationshipType.OCCURS_AT, "fixture.place.elah"),
        edge("fixture.event.david-goliath", RelationshipType.PRECEDES, "fixture.event.david-takes-jerusalem"),
        edge("fixture.event.david-goliath", RelationshipType.RELATED_TO_THEME, "fixture.theme.faith", scripture = false),
        edge("fixture.event.david-takes-jerusalem", RelationshipType.OCCURS_AT, "fixture.place.jerusalem"),
        edge("fixture.event.david-takes-jerusalem", RelationshipType.APPEARS_IN, "passage.2Sam.5"),
        edge("passage.Ps.51", RelationshipType.RELATED_TO_THEME, "fixture.theme.forgiveness", scripture = false),
        edge("passage.Ps.23", RelationshipType.RELATED_TO_THEME, "fixture.theme.prayer", scripture = false),
        edge("fixture.person.jesus", RelationshipType.APPEARS_IN, "passage.John.3"),
        edge("fixture.person.jesus", RelationshipType.APPEARS_IN, "passage.Matt.6"),
        edge("fixture.person.jesus", RelationshipType.RELATED_TO, "fixture.person.mary"),
        edge("fixture.person.jesus", RelationshipType.RELATED_TO, "fixture.person.peter"),
        edge("fixture.person.jesus", RelationshipType.RELATED_TO, "fixture.person.john"),
        edge("fixture.person.jesus", RelationshipType.RELATED_TO, "fixture.place.bethlehem"),
        edge("fixture.person.jesus", RelationshipType.RELATED_TO, "fixture.place.nazareth"),
        edge("fixture.person.jesus", RelationshipType.RELATED_TO, "fixture.place.galilee"),
        edge("fixture.person.jesus", RelationshipType.RELATED_TO, "fixture.place.jerusalem"),
        edge("passage.Matt.6", RelationshipType.RELATED_TO_THEME, "fixture.theme.anxiety", scripture = false),
        edge("passage.Matt.6", RelationshipType.RELATED_TO_THEME, "fixture.theme.money", scripture = false),
        edge("passage.Matt.6", RelationshipType.RELATED_TO_THEME, "fixture.theme.prayer", scripture = false),
        edge("passage.John.3", RelationshipType.RELATED_TO_THEME, "fixture.theme.love", scripture = false),
        edge("passage.John.3", RelationshipType.RELATED_TO_THEME, "fixture.theme.faith", scripture = false),
        edge("fixture.person.paul", RelationshipType.APPEARS_IN, "passage.Rom.8"),
        edge("fixture.person.paul", RelationshipType.RELATED_TO, "fixture.place.rome"),
        edge("fixture.person.paul", RelationshipType.RELATED_TO, "fixture.place.corinth"),
        edge("fixture.person.paul", RelationshipType.RELATED_TO, "fixture.place.ephesus"),
        edge("passage.Rom.8", RelationshipType.RELATED_TO_THEME, "fixture.theme.grace", scripture = false),
        edge("passage.Rom.8", RelationshipType.RELATED_TO_THEME, "fixture.theme.suffering", scripture = false),
        edge("fixture.person.moses", RelationshipType.PARTICIPATES_IN, "fixture.event.exodus"),
        edge("fixture.person.moses", RelationshipType.RELATED_TO, "fixture.place.egypt"),
        edge("fixture.event.exodus", RelationshipType.OCCURS_AT, "fixture.place.egypt"),
        edge("fixture.person.abraham", RelationshipType.RELATED_TO_THEME, "fixture.theme.faith", scripture = false),
    )

    private fun detail(id: String, aliases: List<String> = emptyList(), dates: String? = null, role: String? = null, modern: String? = null, passages: List<PassageReference> = emptyList()) =
        EntityDetail(byId.getValue(id), aliases, dates, role, modern, passages, sources)

    val details: Map<EntityId, EntityDetail> = listOf(
        detail("fixture.person.david", dates = "c. 1010–970 BC (commonly dated; the chronology is approximate)", role = "Second king of Israel", passages = listOf(PassageReference("1Sam", 16), PassageReference("1Sam", 17), PassageReference("2Sam", 5), PassageReference("Ps", 23), PassageReference("Ps", 51))),
        detail("fixture.person.goliath", role = "Philistine champion from Gath", passages = listOf(PassageReference("1Sam", 17))),
        detail("fixture.person.saul", dates = "late 11th century BC (commonly dated)", role = "First king of Israel", passages = listOf(PassageReference("1Sam", 16), PassageReference("1Sam", 17))),
        detail("fixture.person.samuel", role = "Prophet and last of the judges", passages = listOf(PassageReference("1Sam", 16))),
        detail("fixture.person.solomon", dates = "c. 970–931 BC (commonly dated)", role = "Third king of Israel"),
        detail("fixture.person.jesus", aliases = listOf("Jesus of Nazareth", "Christ", "Messiah"), dates = "c. 4 BC – c. AD 30/33 (scholars vary)", role = "Central figure of the New Testament", passages = listOf(PassageReference("John", 3), PassageReference("Matt", 6))),
        detail("fixture.person.paul", aliases = listOf("Saul of Tarsus"), dates = "c. AD 5 – c. 64/67 (scholars vary)", role = "Apostle to the Gentiles", passages = listOf(PassageReference("Rom", 8))),
        detail("fixture.person.peter", aliases = listOf("Simon", "Cephas"), role = "Disciple and apostle"),
        detail("fixture.person.moses", role = "Prophet; leader of the Exodus"),
        detail("fixture.person.abraham", aliases = listOf("Abram"), role = "Patriarch"),
        detail("fixture.place.jerusalem", aliases = listOf("Jebus", "Salem", "Zion"), modern = "Jerusalem, Israel/Palestine", passages = listOf(PassageReference("2Sam", 5))),
        detail("fixture.place.bethlehem", aliases = listOf("Ephrath"), modern = "Bethlehem, West Bank", passages = listOf(PassageReference("1Sam", 16))),
        detail("fixture.place.elah", modern = "Wadi es-Sunt, Israel (commonly identified)", passages = listOf(PassageReference("1Sam", 17))),
        detail("fixture.place.egypt", modern = "Egypt"),
        detail("fixture.place.rome", modern = "Rome, Italy", passages = listOf(PassageReference("Rom", 8))),
        detail("fixture.event.david-goliath", dates = "during Saul's reign (commonly dated to the late 11th century BC)", passages = listOf(PassageReference("1Sam", 17))),
        detail("fixture.event.david-anointed", dates = "during Saul's reign", passages = listOf(PassageReference("1Sam", 16))),
        detail("fixture.event.david-takes-jerusalem", dates = "early in David's reign (commonly dated c. 1000 BC)", passages = listOf(PassageReference("2Sam", 5))),
        detail("fixture.event.exodus", dates = "disputed; commonly placed in the 15th or 13th century BC"),
        detail("fixture.theme.forgiveness", passages = listOf(PassageReference("Ps", 51))),
        detail("fixture.theme.anxiety", passages = listOf(PassageReference("Matt", 6, 25..34))),
        detail("fixture.theme.prayer", passages = listOf(PassageReference("Ps", 23), PassageReference("Matt", 6, 5..15))),
        detail("fixture.theme.grace", passages = listOf(PassageReference("Rom", 8))),
        detail("fixture.theme.faith", passages = listOf(PassageReference("1Sam", 17), PassageReference("John", 3))),
        detail("fixture.theme.love", passages = listOf(PassageReference("John", 3, 16..16))),
    ).associateBy { it.entity.id }
}
