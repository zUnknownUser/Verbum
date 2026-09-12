# Bible Exploration App — Product & Engineering Specification

> Status: Draft v1
> Purpose: Source of truth for product, design, architecture, implementation sequencing, and acceptance criteria.
> Audience: Product, iOS, backend, design, QA, Codex/AI coding agents.
> Primary platform: iOS
> Initial client stack: SwiftUI + TCA
> Product direction: Premium, minimal, exploration-first Bible experience.

---

# 1. Product Vision

This product is **not another Bible reader**.

The core idea is to transform the Bible from a linear reading experience into a **navigable, connected, contextual knowledge universe**.

The user should be able to move naturally between:

- passages;
- people;
- events;
- locations;
- themes;
- books;
- prophecies;
- historical context;
- original-language terms;
- interpretations;
- related passages;
- personal reflections;
- study journeys;
- collaborative study.

The central product promise is:

> **Do not just read Scripture. Understand how it connects.**

Alternative positioning:

> **The Bible, connected.**

---

# 2. Problem

Most Bible apps are optimized around:

- reading plans;
- verse of the day;
- audio;
- notes;
- highlights;
- streaks;
- prayer;
- social feed;
- basic search.

These are useful, but they largely preserve the Bible as a linear document.

The main problem this product solves is:

> People can read the Bible without understanding how its people, events, themes, books, historical moments, places, and passages connect.

Common user questions:

- Who was this person?
- When did this event happen?
- Was this before or after David?
- Where was this place?
- Why is this verse important?
- What comes before and after this verse?
- Where else is this idea discussed?
- What does this word mean in the original language?
- How do different Christian traditions interpret this passage?
- What does the Bible say about a specific topic?
- How does one story connect to another?

The app should make these questions easy to explore.

---

# 3. Product Principles

## 3.1 Scripture First

AI must never become the primary authority.

The hierarchy is:

1. Scripture
2. Source metadata
3. Historical/contextual data
4. Trusted commentary / interpretation
5. AI synthesis

AI should help users navigate sources, not replace them.

---

## 3.2 Every Important Claim Must Be Traceable

When the application explains something, the user should be able to inspect the source.

Examples:

- related Bible passages;
- historical references;
- commentary source;
- original-language lexical source;
- interpretation source.

Avoid unsupported authoritative wording.

Bad:

> God is telling you that your financial problems will end soon.

Good:

> These passages are commonly associated with anxiety about material needs.

---

## 3.3 Exploration Over Consumption

The product should reward curiosity.

The user should frequently encounter meaningful actions such as:

- Explore
- See connections
- Open context
- Follow this theme
- View timeline
- See related passages
- Compare interpretations
- View original term

Avoid infinite feeds.

---

## 3.4 Calm Premium Design

The application must feel:

- quiet;
- intentional;
- editorial;
- premium;
- modern;
- deeply focused.

Avoid:

- generic religious app visual language;
- excessive crosses;
- stock church photography;
- constant gradients;
- gamification overload;
- noisy card grids;
- excessive badges;
- social-media aesthetics.

The core visual metaphor is a **living knowledge constellation**.

---

## 3.5 No Fake Certainty

The Bible contains:

- textual complexity;
- theological disagreement;
- chronology uncertainty;
- translation differences;
- disputed interpretations.

The product must represent uncertainty honestly.

Use wording such as:

- "commonly dated to";
- "scholars disagree";
- "one interpretation is";
- "within Reformed tradition";
- "within Catholic tradition";
- "the chronology is approximate".

---

# 4. Product Pillars

The product is organized around five primary pillars.

## 4.1 Bible Graph

A navigable graph connecting biblical entities.

Entity types:

- Passage
- Verse
- Chapter
- Book
- Person
- Event
- Place
- Theme
- Prophecy
- OriginalTerm
- HistoricalPeriod
- Interpretation
- Source

Relationship examples:

- PERSON_APPEARS_IN_PASSAGE
- PERSON_PARTICIPATES_IN_EVENT
- EVENT_OCCURS_AT_PLACE
- EVENT_OCCURS_DURING_PERIOD
- PASSAGE_REFERENCES_PASSAGE
- PASSAGE_RELATES_TO_THEME
- PROPHECY_REFERENCED_BY_PASSAGE
- TERM_APPEARS_IN_PASSAGE
- BOOK_PART_OF_PERIOD
- PERSON_RELATED_TO_PERSON
- EVENT_PRECEDES_EVENT
- PASSAGE_INTERPRETED_BY
- SOURCE_SUPPORTS_CONTEXT

The graph is the conceptual heart of the application.

---

## 4.2 Timeline

A chronological visualization of biblical history.

Examples of timeline nodes:

- Abraham
- Exodus
- Judges
- United Monarchy
- David
- Solomon
- Divided Kingdom
- Assyrian conquest
- Babylonian exile
- Persian period
- Second Temple period
- Birth of Jesus
- Ministry of Jesus
- Crucifixion
- Early Church
- Pauline missions

The timeline must support approximate dates and uncertainty.

---

## 4.3 Context Engine

Every passage should be explorable through context.

Example structure:

- Speaker
- Audience
- Location
- Historical period
- Literary context
- What comes before
- What comes after
- Related passages
- Important original terms
- Key people
- Key places
- Themes
- Interpretive perspectives

This should be available from any supported passage.

---

## 4.4 Ask Scripture

Natural-language Bible exploration.

Example questions:

- "What does the Bible say about wealth?"
- "Why did Job suffer?"
- "What does Paul mean by justification?"
- "Did Jesus abolish the Law?"
- "Where does the Bible talk about anxiety?"
- "How does Romans connect to Genesis?"

The system should answer through retrieval-first generation.

The answer must include:

- short summary;
- key passages;
- related context;
- uncertainty where applicable;
- direct navigation into sources.

The product should not position the answer as divine revelation.

---

## 4.5 Journey

A private personal exploration history.

Tracks:

- passages explored;
- books visited;
- themes explored;
- people explored;
- places explored;
- questions asked;
- passages revisited;
- saved entities;
- personal notes;
- study journeys completed.

This replaces shallow streak mechanics with meaningful progress.

---

# 5. Primary User Experience

## 5.1 Home

The Home should be simple.

Suggested hierarchy:

```text
Good morning.

What do you want to understand?

[ Ask anything about Scripture ]

Continue exploring

    David
   /     \
Psalms  Solomon

Today

Understanding anxiety
Matthew 6:25-34
6 min journey
```

Primary Home sections:

1. Search / Ask
2. Continue exploring
3. Today
4. Recent exploration
5. Optional personal journey summary

Do not place all available functionality on Home.

---

# 6. Navigation Model

Primary navigation proposal:

- Home
- Explore
- Journey
- Library

Search / Ask should be globally accessible.

Potential tab structure:

```text
Home
Explore
Journey
Library
```

Optional future tab:

```text
Groups
```

Do not include Groups in MVP unless collaboration becomes a launch requirement.

---

# 7. Explore

Explore is the main discovery surface.

Entry points:

- People
- Places
- Themes
- Timeline
- Books
- Events
- Graph
- Search

Suggested screen:

```text
Explore

[ Search Scripture, people, places, themes ]

Featured paths

People
Places
Themes
Timeline
Books
Events
```

---

# 8. Bible Graph UX

## 8.1 Goal

The graph should make relationships understandable rather than merely visually impressive.

The graph must never become an unusable force-directed visualization with dozens of unlabeled nodes.

Default graph depth:

- 1 degree
- maximum ~8-12 visible nodes

User may expand nodes intentionally.

---

## 8.2 Example

User opens David.

Initial graph:

```text
             Psalms
               |
Samuel ------ David ------ Saul
               |
             Goliath
               |
           Jerusalem
               |
            Solomon
```

Node tap opens entity detail.

Long press may offer:

- Save
- Compare
- Open in timeline
- Show related passages

Expansion should be incremental.

---

## 8.3 Node Types

Visual distinction should remain subtle.

Possible shapes:

- Person: circle
- Place: diamond
- Event: rounded square
- Theme: soft abstract shape
- Passage: capsule / rectangle
- Book: book-like rectangle

Do not rely only on color for differentiation.

Accessibility is required.

---

# 9. Entity Detail

Each entity has a dedicated detail screen.

## 9.1 Person

Example: David

Fields:

- name
- aliases
- summary
- approximate dates
- role
- related people
- major events
- key passages
- related locations
- related themes
- timeline position
- graph entry

Actions:

- Explore graph
- View timeline
- Open key passages
- Save
- Add note

---

## 9.2 Place

Example: Jerusalem

Fields:

- name
- historical names
- modern geography, when meaningful
- summary
- major biblical events
- people associated
- key passages
- timeline relevance

Actions:

- Open map
- Explore graph
- View events
- Save

---

## 9.3 Event

Example: Battle of David and Goliath

Fields:

- name
- summary
- approximate date
- location
- participants
- related passages
- preceding event
- following event
- themes

---

## 9.4 Theme

Example: Forgiveness

Fields:

- overview
- representative passages
- related people
- related events
- Old Testament references
- New Testament references
- theological notes
- related themes

---

## 9.5 Passage

Passage is one of the most important entity types.

Sections:

- Bible text
- Context
- Related
- Original language
- Interpretations
- Notes

---

# 10. Passage Context

Every supported passage may expose:

## Context Summary

- Who is speaking?
- Who is being addressed?
- What is happening?
- Where?
- When?
- Why does this passage matter?

## Literary Context

- previous paragraph;
- next paragraph;
- chapter context;
- book context.

## Historical Context

- approximate period;
- political context;
- cultural context;
- major relevant historical events.

## Related Scripture

Types:

- quotation;
- allusion;
- thematic relation;
- fulfillment;
- parallel passage;
- contrast.

---

# 11. Original Language

Future-forward but important.

Supported metadata:

- original lemma;
- transliteration;
- language;
- lexical meaning;
- morphology;
- occurrence count;
- related occurrences.

Example:

```text
ἀγάπη
agápē

Language: Greek
Meaning: love, charity
Occurrences: ...
```

Avoid presenting lexical glosses as complete theological meaning.

---

# 12. Interpretation Comparison

The application should support multiple perspectives where relevant.

Possible lenses:

- Historical / Academic
- Catholic
- Eastern Orthodox
- Reformed
- Lutheran
- Wesleyan / Arminian
- Pentecostal / Charismatic

Not every passage requires every perspective.

Rules:

- Clearly label tradition.
- Never disguise interpretation as neutral fact.
- Cite commentary/source.
- Avoid adversarial framing.
- Avoid declaring a winner.

---

# 13. Ask Scripture

## 13.1 Product Contract

Ask Scripture answers questions using a retrieval-first pipeline.

The AI must not answer purely from model memory when trusted indexed sources are available.

Pipeline:

```text
User question
    ↓
Intent classification
    ↓
Query expansion
    ↓
Retrieve Scripture
    ↓
Retrieve contextual data
    ↓
Retrieve trusted commentary if needed
    ↓
Rank evidence
    ↓
Generate answer
    ↓
Attach sources
    ↓
Validate citations
```

---

## 13.2 Response Structure

Suggested format:

### Short answer

Concise synthesis.

### Key passages

List 3-8 passages.

### Context

Relevant historical/literary explanation.

### Explore further

Links into graph/entities.

### Perspectives

Only when interpretive disagreement matters.

---

## 13.3 Safety Rules

The model must never claim:

- direct revelation from God;
- prophecy about the user's future;
- guaranteed healing;
- guaranteed financial outcomes;
- divine endorsement of a personal decision.

Avoid:

> God told me to tell you...

Avoid:

> God is saying your relationship will be restored.

Prefer:

> This passage has traditionally been used to discuss...

---

# 14. Daily Experience

The app may include a short daily guided experience.

This must not become the center of the product.

Possible structure:

```text
Pause
Read
Understand
Reflect
Write
Continue
```

Example:

Topic: Anxiety

Passage:

Matthew 6:25-34

Flow:

1. 20-second breathing/pause
2. Read passage
3. Context
4. Reflection prompt
5. Optional private journal
6. Related passage

No pseudo-prophetic wording.

---

# 15. Journey

## 15.1 Goal

Journey visualizes meaningful exploration progress.

Metrics:

- passages explored;
- books visited;
- people explored;
- themes explored;
- places explored;
- questions asked;
- saved entities;
- revisit count.

---

## 15.2 Journey Map

Possible visual:

```text
        Forgiveness
            ●
           / \
          /   \
    Anxiety ●──● Faith
          \     /
           ●───●
          Purpose
```

This visualization represents the user's actual behavior.

No invented psychological profiling.

---

# 16. Library

User-owned content.

Contains:

- Saved passages
- Saved people
- Saved themes
- Saved places
- Notes
- Journals
- Study paths

Searchable.

Filter by content type.

---

# 17. Notes

Notes are private by default.

Note model:

```text
Note
- id
- userId
- entityId
- entityType
- body
- createdAt
- updatedAt
```

Future:

- collaborative notes;
- group notes;
- exported notes.

---

# 18. Collaborative Study — Post-MVP

Potential realtime experience.

Use cases:

- couple;
- family;
- small group;
- church study;
- friend.

Capabilities:

- presence;
- current passage;
- shared highlights;
- comments;
- shared notes;
- synced navigation;
- session history.

Example:

```text
Lucas is reading Romans 8:28
●
```

This feature should be introduced only after the single-user core is strong.

---

# 19. Realtime Study Architecture — Future

Possible transport:

- WebSocket
- Firestore realtime
- Supabase Realtime
- custom event service

Realtime events:

```text
session.joined
session.left
passage.opened
verse.highlighted
note.created
graph.node.opened
cursor.presence.updated
```

Presence should be ephemeral.

Do not persist high-frequency cursor events.

---

# 20. MVP Definition

MVP must prove:

> Users value contextual exploration enough to choose this over a standard Bible reader.

## MVP Features

### Required

- Onboarding
- Home
- Bible text reading
- Search
- Entity model
- Person detail
- Place detail
- Theme detail
- Passage detail
- Context Engine
- Related passages
- Bible Graph v1
- Timeline v1
- Ask Scripture v1
- Saved items
- Notes
- Journey basics
- Analytics
- Error handling
- Offline cache for recently read content

### Not Required for MVP

- Groups
- realtime collaboration
- audio Bible
- social feed
- church management
- public profiles
- public comments
- subscriptions with many tiers
- advanced original-language tools
- full theological tradition comparison
- multiplayer graph
- complex gamification

---

# 21. MVP User Stories

## 21.1 Explore a Person

**As a user,**
I want to open a biblical person,
so that I can understand who they were and how they connect to Scripture.

Acceptance criteria:

- person page shows summary;
- major passages;
- related people;
- major events;
- timeline position;
- graph entry;
- save action works.

---

## 21.2 Explore a Passage

**As a user,**
I want to open context for a passage,
so that I understand it beyond the isolated verse.

Acceptance criteria:

- passage text visible;
- previous/next context available;
- key people visible when available;
- key places visible when available;
- related passages visible;
- context summary visible;
- context sources available.

---

## 21.3 Ask a Question

**As a user,**
I want to ask a Bible question,
so that I can discover relevant passages and context quickly.

Acceptance criteria:

- system retrieves passages;
- answer references retrieved passages;
- source links open;
- unsupported certainty avoided;
- failure gracefully falls back to search results.

---

## 21.4 Use the Graph

**As a user,**
I want to see relationships visually,
so that I understand how biblical entities connect.

Acceptance criteria:

- graph starts with selected entity;
- graph depth defaults to one;
- nodes are tappable;
- node count remains manageable;
- expansion is explicit;
- accessibility labels exist.

---

## 21.5 Use Timeline

**As a user,**
I want to see biblical events chronologically,
so that I understand when things happened relative to one another.

Acceptance criteria:

- major periods represented;
- approximate dates supported;
- uncertainty represented;
- tapping event opens detail;
- horizontal/vertical scrolling remains smooth.

---

# 22. Data Model

## 22.1 BibleEntity

```swift
enum BibleEntityType: String, Codable {
    case person
    case place
    case event
    case theme
    case passage
    case book
    case prophecy
    case originalTerm
    case historicalPeriod
}
```

```swift
struct BibleEntity: Identifiable, Codable, Equatable {
    let id: String
    let type: BibleEntityType
    let name: String
    let summary: String?
}
```

---

## 22.2 Relationship

```swift
enum RelationshipType: String, Codable {
    case appearsIn
    case participatesIn
    case occursAt
    case occursDuring
    case references
    case relatedToTheme
    case relatedTo
    case precedes
    case follows
    case fulfills
    case quotes
}
```

```swift
struct BibleRelationship: Identifiable, Codable, Equatable {
    let id: String
    let sourceId: String
    let targetId: String
    let type: RelationshipType
    let confidence: Double?
    let sourceReferenceIds: [String]
}
```

---

## 22.3 Passage

```swift
struct BiblePassage: Identifiable, Codable, Equatable {
    let id: String
    let translationId: String
    let bookId: String
    let chapter: Int
    let verseStart: Int
    let verseEnd: Int
    let text: String
}
```

---

## 22.4 PassageContext

```swift
struct PassageContext: Codable, Equatable {
    let passageId: String
    let summary: String
    let speakerEntityIds: [String]
    let audienceEntityIds: [String]
    let placeEntityIds: [String]
    let eventEntityIds: [String]
    let themeEntityIds: [String]
    let relatedPassageIds: [String]
    let sourceReferenceIds: [String]
}
```

---

## 22.5 TimelineEvent

```swift
struct TimelineEvent: Identifiable, Codable, Equatable {
    let id: String
    let title: String
    let startYear: Int?
    let endYear: Int?
    let datePrecision: TimelineDatePrecision
    let summary: String?
    let entityIds: [String]
}
```

```swift
enum TimelineDatePrecision: String, Codable {
    case exact
    case approximate
    case debated
    case unknown
}
```

---

# 23. Backend Architecture

Recommended initial architecture:

```text
iOS App
   |
API Gateway
   |
Application API
   |
---------------------------------
| Bible Content Service          |
| Graph Service                  |
| Search Service                 |
| AI / RAG Service               |
| User Data Service              |
---------------------------------
   |
---------------------------------
| Postgres                       |
| Vector Store                   |
| Cache                          |
| Object Storage                 |
---------------------------------
```

Keep the first version modular, but avoid premature microservices.

Preferred starting point:

- one backend application;
- domain modules;
- one relational database;
- one vector index;
- background ingestion pipeline.

Split services later only when scale justifies it.

---

# 24. Database

Recommended:

- PostgreSQL

Potential extensions:

- pgvector for embeddings;
- PostGIS for geographical data;
- recursive queries for graph exploration.

Do not start with Neo4j unless graph query requirements prove PostgreSQL insufficient.

Reason:

The graph is important to the product, but initial graph depth is shallow and relational data is easier to maintain in one operational system.

---

# 25. Suggested Core Tables

```text
books
passages
entities
entity_aliases
relationships
themes
timeline_events
timeline_event_entities
sources
source_references
commentaries
original_terms
passage_terms
users
saved_items
notes
journey_events
search_queries
ai_queries
```

---

# 26. Graph Storage

Initial model:

```sql
entities
--------
id
type
name
summary

relationships
-------------
id
source_entity_id
target_entity_id
relationship_type
confidence
metadata
```

Indexes:

```sql
(source_entity_id)
(target_entity_id)
(source_entity_id, relationship_type)
(target_entity_id, relationship_type)
```

---

# 27. Search

Search should support:

- exact Bible references;
- free-text passage search;
- people;
- places;
- events;
- themes;
- semantic search.

Examples:

```text
John 3:16
David
Jerusalem
forgiveness
fear
money
why did Job suffer
```

Search result groups:

```text
Passages
People
Places
Themes
Events
```

---

# 28. Search Architecture

Recommended hybrid retrieval:

```text
Lexical search
+
Semantic vector search
+
Entity lookup
+
Reference parser
```

Ranking should favor direct Bible-reference matches.

---

# 29. Ask Scripture — RAG Architecture

Recommended pipeline:

```text
Question
   |
Intent detection
   |
Bible reference extraction
   |
Entity extraction
   |
Hybrid retrieval
   |
Evidence reranking
   |
Prompt assembly
   |
LLM synthesis
   |
Citation validator
   |
Response
```

Never stream final text before enough evidence is retrieved to anchor the answer.

---

# 30. AI Response Data Contract

```json
{
  "answer": "...",
  "summary": "...",
  "passageReferences": [],
  "entityReferences": [],
  "sourceReferences": [],
  "confidence": "medium",
  "interpretiveVariance": true
}
```

The client should render references from structured fields.

Do not rely on markdown-parsed citations as the only source mechanism.

---

# 31. AI Guardrails

The AI must:

- cite relevant passages;
- distinguish Scripture from interpretation;
- indicate disagreement;
- avoid fabricated references;
- refuse to invent verses;
- avoid prophecy-style claims;
- avoid pretending to speak for God;
- avoid replacing professional medical/legal/financial help when a user asks high-stakes questions.

The system should include citation validation.

If the generated verse reference does not exist, reject or regenerate.

---

# 32. Content Pipeline

The content pipeline should be separate from app runtime.

Pipeline stages:

```text
Source import
↓
Normalization
↓
Entity extraction
↓
Relationship extraction
↓
Human/editorial review
↓
Embedding generation
↓
Indexing
↓
Publish
```

Do not allow AI-generated graph edges to automatically become production truth without review.

---

# 33. Source Provenance

Every non-trivial contextual claim should be traceable.

SourceReference:

```text
id
sourceId
citation
url
page
section
license
```

This is especially important for:

- commentary;
- chronology;
- archaeology;
- historical context;
- lexical definitions.

---

# 34. Content Licensing

Before production launch, validate licenses for:

- Bible translations;
- commentary;
- maps;
- original-language lexicons;
- historical datasets.

Do not assume Bible text is public domain.

Translation licensing must be treated as a blocking legal requirement.

---

# 35. iOS Architecture

Recommended:

- SwiftUI
- The Composable Architecture
- async/await
- Observation where appropriate
- URLSession
- SwiftData or SQLite-based local cache

Architecture principles:

- feature modules;
- explicit dependencies;
- reducer-driven state;
- isolated side effects;
- testable clients;
- no API access directly from views.

---

# 36. iOS Module Proposal

```text
App
Core
DesignSystem
Networking
Persistence
Analytics

Features/
    Onboarding
    Home
    Explore
    Search
    Scripture
    EntityDetail
    BibleGraph
    Timeline
    AskScripture
    Journey
    Library
    Notes
```

---

# 37. Feature Dependency Rule

A feature should depend on protocols/clients, not concrete implementations.

Example:

```swift
@Dependency(\.bibleClient) var bibleClient
@Dependency(\.graphClient) var graphClient
@Dependency(\.searchClient) var searchClient
@Dependency(\.askScriptureClient) var askScriptureClient
```

---

# 38. Client Interfaces

```swift
struct BibleClient {
    var passage: @Sendable (PassageReference) async throws -> BiblePassage
    var chapter: @Sendable (BookID, Int) async throws -> [BiblePassage]
}
```

```swift
struct GraphClient {
    var entity: @Sendable (EntityID) async throws -> BibleEntity
    var neighbors: @Sendable (EntityID, Int) async throws -> GraphSnapshot
}
```

```swift
struct SearchClient {
    var search: @Sendable (String) async throws -> SearchResponse
}
```

```swift
struct AskScriptureClient {
    var ask: @Sendable (String) async throws -> ScriptureAnswer
}
```

---

# 39. Local Persistence

Cache:

- recently opened passages;
- recent entities;
- saved items;
- notes;
- journey events waiting for sync;
- basic search history.

Offline behavior:

- saved passages readable;
- cached passage context available;
- notes editable;
- pending sync queued;
- Ask Scripture unavailable unless a local model is later added.

---

# 40. Design System

## Visual Direction

- near-black and warm-light themes;
- large editorial typography;
- extremely restrained accent color;
- subtle depth;
- soft glass only where appropriate;
- graph lines should feel elegant, not technical;
- smooth spatial transitions.

---

# 41. Motion

Motion should communicate navigation through knowledge.

Examples:

- graph node expansion;
- focus transition into entity;
- timeline movement;
- graph-to-passage transition.

Requirements:

- 60/120 FPS where possible;
- Reduce Motion support;
- no unnecessary particle effects;
- no constant ambient animation distracting reading.

---

# 42. Typography

The Bible text should prioritize readability.

Consider:

- serif typeface for Scripture;
- sans-serif for interface;
- strong line-height;
- user-adjustable text size;
- Dynamic Type.

Do not use tiny metadata typography that breaks accessibility.

---

# 43. Accessibility

Required:

- Dynamic Type;
- VoiceOver;
- Reduce Motion;
- sufficient contrast;
- graph accessibility fallback;
- non-color-only meaning;
- scalable hit targets.

Graph must expose an alternative list representation.

---

# 44. Graph Performance

Never render the entire graph.

Use viewport-driven / depth-limited snapshots.

Example API:

```text
GET /graph/entities/{id}?depth=1&limit=12
```

Response:

```json
{
  "root": {},
  "nodes": [],
  "edges": []
}
```

---

# 45. API Proposal

## Passage

```http
GET /v1/passages/{reference}
```

## Context

```http
GET /v1/passages/{reference}/context
```

## Entity

```http
GET /v1/entities/{id}
```

## Graph

```http
GET /v1/entities/{id}/graph
```

## Timeline

```http
GET /v1/timeline
```

## Search

```http
GET /v1/search?q=
```

## Ask

```http
POST /v1/ask
```

## Journey

```http
GET /v1/me/journey
```

## Save

```http
POST /v1/me/saved-items
```

---

# 46. Analytics

Track product behavior, not private spiritual conclusions.

Events:

```text
home_opened
search_submitted
search_result_opened
passage_opened
context_opened
entity_opened
graph_opened
graph_node_expanded
timeline_opened
timeline_event_opened
ask_submitted
ask_source_opened
item_saved
note_created
journey_opened
```

Never log note bodies or journal content in analytics.

---

# 47. Privacy

Treat the following as sensitive:

- prayer-related questions;
- journal content;
- spiritual doubts;
- personal notes;
- relationship questions;
- personal confession-like content.

Rules:

- private by default;
- no ads based on spiritual questions;
- encrypt transport;
- encrypt sensitive stored user content where practical;
- minimize raw AI query retention;
- allow deletion/export.

---

# 48. Authentication

MVP can support:

- Sign in with Apple
- Email

Anonymous exploration should be considered.

Preferred onboarding strategy:

Allow the user to experience the product before forcing account creation.

Require account only for:

- saving;
- notes;
- journey sync;
- paid subscription.

---

# 49. Monetization

Do not start by locking core Scripture reading.

Potential premium features:

- advanced Ask Scripture;
- deeper graph exploration;
- advanced timelines;
- original-language tools;
- premium study paths;
- cross-device sync;
- advanced journey analytics;
- collaboration;
- premium commentary packs.

Possible structure:

```text
Free
- Read
- Basic search
- Basic graph
- Basic context
- Limited Ask Scripture

Premium
- Unlimited Ask Scripture
- Advanced graph
- Advanced original language
- Deep context
- Study paths
- Journey insights
- Future collaboration
```

Avoid manipulative religious paywall copy.

Never imply spiritual growth requires payment.

---

# 50. Onboarding

Goal: teach the mental model.

Possible screens:

## Screen 1

**The Bible is more connected than it looks.**

Explore people, places, events, passages, and ideas.

## Screen 2

**Follow the connections.**

Move naturally between Scripture, history, themes, and context.

## Screen 3

**Ask. Then verify.**

Answers guide you back to Scripture and sources.

CTA:

**Start exploring**

---

# 51. Empty States

Avoid generic copy.

Example Graph empty state:

> No verified connections are available yet for this entity.

Example Ask failure:

> I could not build a reliable answer from the available sources. Here are the closest passages I found.

Trust is more important than always producing an answer.

---

# 52. Error Handling

The application must distinguish:

- network unavailable;
- content unavailable;
- malformed API response;
- AI unavailable;
- source retrieval failure;
- authentication error;
- sync conflict.

Never show raw backend errors.

---

# 53. Testing Strategy

## Unit Tests

Required for:

- reducers;
- reference parsing;
- search ranking helpers;
- graph state;
- timeline sorting;
- saved items;
- Ask response mapping;
- citation validation.

## Integration Tests

Required for:

- API clients;
- database queries;
- RAG retrieval;
- graph neighborhood query;
- timeline endpoints.

## Snapshot/UI Tests

Recommended for:

- Home
- Passage
- Entity detail
- Graph
- Timeline
- Ask answer

---

# 54. Observability

Backend:

- structured logs;
- tracing;
- AI latency;
- retrieval latency;
- vector search latency;
- cache hit rate;
- failed citation validation;
- failed reference parsing.

iOS:

- crash reporting;
- non-fatal error telemetry;
- launch performance;
- screen load latency.

---

# 55. Performance Budgets

Target:

- Home first meaningful content: < 1.5s warm network
- Passage load: < 700ms p95 API
- Graph load: < 1s p95
- Search: < 800ms p95
- Ask Scripture first structured response: optimize aggressively, but correctness over speed

Use skeletons sparingly.

---

# 56. Security

Required:

- rate limiting;
- API authentication;
- short-lived access tokens;
- protected AI endpoints;
- server-side subscription validation;
- input sanitization;
- abuse monitoring;
- no API secrets in app bundle.

---

# 57. Content Administration

A lightweight editorial/admin tool will eventually be necessary.

Capabilities:

- edit entity;
- merge duplicate entity;
- approve relationship;
- reject relationship;
- edit timeline event;
- edit context summary;
- attach sources;
- mark uncertain;
- preview graph.

Do not attempt to maintain a large Bible knowledge graph exclusively through SQL scripts.

---

# 58. Editorial Confidence

Relationships and contextual claims should support confidence.

Example:

```text
verified
editorial
inferred
debated
```

Only verified/editorial relationships should appear by default in MVP.

Inferred relationships may remain internal until reviewed.

---

# 59. Implementation Phases

## Phase 0 — Foundation

- project bootstrap;
- SwiftUI app shell;
- TCA;
- design system;
- networking;
- persistence;
- analytics abstraction;
- backend skeleton;
- database;
- CI;
- environments.

---

## Phase 1 — Scripture Core

- books;
- chapters;
- passages;
- reference parser;
- reader;
- translation model;
- Bible search;
- caching.

Definition of done:

A user can search a reference and read Scripture reliably.

---

## Phase 2 — Entities

- entity model;
- person;
- place;
- event;
- theme;
- entity search;
- entity detail.

Definition of done:

A user can move from a passage to relevant entities.

---

## Phase 3 — Context Engine

- passage context endpoint;
- context UI;
- related passages;
- people;
- places;
- themes;
- literary context;
- source provenance.

Definition of done:

Context meaningfully improves passage understanding.

---

## Phase 4 — Bible Graph

- relationship table;
- graph API;
- graph reducer;
- graph renderer;
- expansion;
- accessibility fallback.

Definition of done:

A user can visually explore one-hop relationships without confusion.

---

## Phase 5 — Timeline

- historical periods;
- events;
- chronology confidence;
- timeline UI;
- entity integration.

Definition of done:

Users can understand when key events happened relative to each other.

---

## Phase 6 — Ask Scripture

- semantic index;
- hybrid retrieval;
- RAG;
- citation validation;
- answer UI;
- source navigation.

Definition of done:

Answers consistently guide users into Scripture and supported context.

---

## Phase 7 — Personal Layer

- auth;
- saved items;
- notes;
- journey events;
- journey UI;
- sync.

---

## Phase 8 — Monetization

- RevenueCat or equivalent;
- premium entitlement;
- usage limits;
- paywall;
- server validation.

---

## Phase 9 — Collaboration

Post-MVP.

- group model;
- sessions;
- realtime;
- presence;
- shared notes;
- shared highlights.

---

# 60. Initial Codex Execution Order

Codex should execute in this order unless explicitly instructed otherwise.

## Task 1

Create project architecture.

Expected:

```text
App
Core
DesignSystem
Features
Clients
Models
```

No feature implementation yet.

---

## Task 2

Create foundational models.

Implement:

- BibleBook
- BiblePassage
- PassageReference
- BibleEntity
- BibleEntityType
- BibleRelationship
- RelationshipType

Tests required.

---

## Task 3

Implement passage reference parser.

Supported inputs:

```text
John 3:16
John 3
John 3:16-18
Jn 3:16
Romans 8:28
1 Samuel 17
```

Parser must be deterministic and well tested.

---

## Task 4

Create BibleClient.

Use mocked/local data first.

Do not connect a production Bible provider before contracts are stable.

---

## Task 5

Create ScriptureFeature.

Requirements:

- book/chapter navigation;
- passage reading;
- loading;
- error;
- Dynamic Type;
- selection of verse.

---

## Task 6

Create SearchFeature.

Initially search:

- references;
- book names;
- mock entities.

---

## Task 7

Create EntityDetailFeature.

Use mock data.

Support:

- person;
- place;
- event;
- theme.

---

## Task 8

Create ContextFeature.

Render:

- summary;
- people;
- places;
- themes;
- related passages;
- sources.

---

## Task 9

Create GraphFeature.

Use deterministic mock graph.

Focus on:

- interaction;
- state architecture;
- performance;
- accessibility.

Do not build force-layout complexity prematurely.

---

## Task 10

Create TimelineFeature.

Use static curated data initially.

---

## Task 11

Replace mocks with backend API.

Only after client contracts and UI states are stable.

---

## Task 12

Implement Ask Scripture.

Only after Scripture, entities, context, and search are working.

Do not begin with AI.

AI is not the foundation of the product.

---

# 61. Coding Standards

Codex must follow:

- small focused types;
- no giant reducers;
- no networking inside views;
- no global singleton state;
- explicit dependencies;
- test every important reducer transition;
- avoid unnecessary abstractions;
- avoid speculative generic frameworks;
- prefer domain language;
- maintain compileability after every step;
- do not introduce a new dependency without justification.

---

# 62. TCA Rules

For every feature:

```text
Feature
Feature.State
Feature.Action
Feature.Body
Feature.View
```

Separate child features when:

- they own meaningful state;
- they perform effects;
- they have independent navigation;
- they are reusable.

Do not create child reducers for purely visual components.

---

# 63. Navigation

Prefer state-driven navigation.

Do not navigate from views using hidden imperative global coordinators.

App navigation should be represented by feature state.

---

# 64. Design Tokens

Create:

- spacing;
- typography;
- radius;
- surfaces;
- foreground;
- background;
- accent;
- shadow/elevation;
- animation durations.

Avoid magic values in feature views.

---

# 65. Product Non-Goals

This app is not initially:

- a church management platform;
- a Christian social network;
- a sermon hosting platform;
- a prayer social feed;
- a theology debate forum;
- a generic devotional app;
- a Christian TikTok;
- a Bible game;
- a replacement for pastors, clergy, scholars, or community.

---

# 66. Success Metrics

North-star candidate:

> Weekly users who perform at least one meaningful exploration beyond reading a verse.

Meaningful exploration:

- open context;
- open related passage;
- open entity;
- expand graph;
- navigate timeline;
- inspect source;
- ask a question and open a reference.

Secondary metrics:

- exploration depth;
- entities per session;
- context open rate;
- source open rate;
- weekly retention;
- save rate;
- revisit rate;
- Ask Scripture follow-through.

Avoid optimizing purely for session duration.

The goal is understanding, not addiction.

---

# 67. Launch Scope

A strong first launch may intentionally cover only a curated subset deeply.

Example:

- New Testament
- major Old Testament figures
- core locations
- major themes
- major timeline events

Depth and trust are preferable to shallow completeness.

---

# 68. Editorial Strategy

Do not attempt to fully map the entire Bible automatically before launch.

Start with a curated graph.

Suggested first entity coverage:

People:

- Jesus
- Paul
- Peter
- Abraham
- Moses
- David
- Solomon
- Saul
- Mary
- John

Places:

- Jerusalem
- Bethlehem
- Nazareth
- Galilee
- Rome
- Corinth
- Ephesus
- Babylon
- Egypt

Themes:

- Faith
- Grace
- Forgiveness
- Love
- Anxiety
- Wisdom
- Justice
- Prayer
- Money
- Suffering

---

# 69. Example Core Experience

User searches:

```text
David
```

App opens:

```text
David

King of Israel

[ Explore connections ]

Key passages
1 Samuel 16
1 Samuel 17
2 Samuel 5
Psalm 51

Related
Samuel
Saul
Goliath
Bathsheba
Solomon
Jerusalem
Psalms
```

User taps:

```text
Goliath
```

Graph animates focus.

User opens:

```text
1 Samuel 17
```

Then:

```text
Context
```

App shows:

- event summary;
- geography;
- participants;
- literary context;
- timeline;
- themes;
- related passages.

This interaction represents the core product loop.

---

# 70. Core Product Loop

```text
Curiosity
↓
Search / Ask
↓
Passage or Entity
↓
Context
↓
Connection
↓
Another Passage or Entity
↓
Save / Note / Continue
↓
Journey grows
```

This is the loop the product should optimize.

---

# 71. Future Opportunities

Post-MVP possibilities:

- spatial Bible maps;
- reconstructed journeys;
- Paul's missionary routes;
- Exodus route comparisons;
- family trees;
- genealogy explorer;
- prophecy relationship explorer;
- manuscript comparison;
- translation comparison;
- audio synchronized to graph;
- Apple Vision Pro exploration;
- teacher mode;
- church group study;
- collaborative realtime sessions;
- public curated study journeys;
- scholar commentary marketplace.

---

# 72. Feature Priorities

## P0

- Scripture reader
- Reference parser
- Search
- Entity model
- Passage context
- Graph v1
- Timeline v1
- Source provenance

## P1

- Ask Scripture
- Saved items
- Notes
- Journey
- Auth
- Sync
- Monetization

## P2

- Original-language deep dive
- Interpretation comparison
- Advanced maps
- Study journeys
- Realtime collaboration

## P3

- Social/community features
- public profiles
- creator ecosystem
- church administration

---

# 73. Hard Product Rules

1. Do not build AI before reliable retrieval.
2. Do not build the full graph before validating small curated graphs.
3. Do not force authentication before value is demonstrated.
4. Do not gamify reading with guilt.
5. Do not present theology as universal fact when traditions disagree.
6. Do not claim divine revelation.
7. Do not ship uncited contextual claims.
8. Do not overload Home.
9. Do not use a social feed as the core retention mechanism.
10. Do not sacrifice reading quality for visual novelty.

---

# 74. Definition of Product Quality

The application should make the user think:

> "I finally understand how this connects."

not:

> "This app has many Bible features."

That distinction should guide every product decision.

---

# 75. First Milestone

The first meaningful internal milestone is:

> Search "David" → open David → explore graph → open Goliath → open 1 Samuel 17 → open Context → open related passage.

If this interaction feels excellent, the product foundation is correct.

Build this golden path before expanding breadth.

---

# 76. Golden Path Acceptance Criteria

The golden path is considered complete when:

- navigation is deterministic;
- all screens handle loading/error;
- graph remains smooth;
- source data is traceable;
- passage transition is clear;
- back navigation preserves state;
- Dynamic Type works;
- VoiceOver works;
- Reduce Motion works;
- deep links can represent opened entity/passage;
- no mock identifiers leak into UI.

---

# 77. Suggested Initial Repository Docs

Create:

```text
/docs
    PRODUCT.md
    ARCHITECTURE.md
    DATA_MODEL.md
    AI_RAG.md
    DESIGN_SYSTEM.md
    CONTENT_GUIDELINES.md
    ROADMAP.md
```

This document may initially serve as `/docs/PRODUCT.md`.

Later, split technical sections into dedicated documents.

---

# 78. Codex Instructions

When Codex reads this specification:

1. Treat this file as product direction, not permission to implement everything at once.
2. Follow the implementation phases.
3. Prefer the smallest production-quality increment.
4. Keep the project compiling after each task.
5. Add tests with every domain change.
6. Never introduce fake production content to hide missing backend behavior.
7. Use fixtures explicitly labeled as fixtures during early development.
8. Do not redesign product flows without documenting the reason.
9. When uncertain, preserve architectural simplicity.
10. Optimize for correctness, traceability, testability, and maintainability.

---

# 79. Immediate Next Step

Start with:

**Phase 0 + Phase 1**

Specifically:

1. Bootstrap iOS architecture.
2. Create the design system foundation.
3. Create core Bible domain models.
4. Implement PassageReference parser.
5. Implement mocked BibleClient.
6. Implement ScriptureFeature.
7. Implement SearchFeature.
8. Add tests.
9. Build the initial navigation shell.

Do not implement Ask Scripture yet.

The goal is to establish a stable Scripture and navigation foundation before introducing graph intelligence or AI.

---

# 80. Final Product Statement

This product should feel less like a digital book and more like a **living atlas of Scripture**.

The Bible remains the center.

The software exists to reveal:

- relationships;
- chronology;
- geography;
- language;
- historical context;
- interpretive context;
- the user's own path through Scripture.

The unique product advantage is not one isolated feature.

It is the combination of:

> **Scripture + Graph + Context + Timeline + Search + AI Navigation + Personal Journey**

working together as one coherent system.

---

End of specification.
