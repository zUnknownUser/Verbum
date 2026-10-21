-- Withdraw demonstration attribution and unsupported legacy historical fields.
-- Stable entity IDs, biblical passages, STEP records and publication receipts remain.
-- Exact seed values + the old attribution guard against erasing later editorial work.
-- No substitute source or editorial approval is fabricated.
WITH legacy(entity_id, field, english, portuguese) AS (VALUES
    ('fixture.person.jesus', 'approximateDates', 'c. 4 BC – c. AD 30/33 (scholars vary)', 'c. 4 a.C. – c. 30/33 d.C. (os estudiosos variam)'),
    ('fixture.person.paul', 'approximateDates', 'c. AD 5 – c. 64/67 (scholars vary)', 'c. 5 d.C. – c. 64/67 d.C. (os estudiosos variam)'),
    ('fixture.person.david', 'approximateDates', 'c. 1010–970 BC (commonly dated; the chronology is approximate)', 'c. 1010–970 a.C. (datado comumente; a cronologia é aproximada)'),
    ('fixture.person.solomon', 'approximateDates', 'c. 970–931 BC (commonly dated)', 'c. 970–931 a.C. (datado comumente)'),
    ('fixture.person.saul', 'approximateDates', 'late 11th century BC (commonly dated)', 'final do século XI a.C. (comumente datado)'),
    ('fixture.place.jerusalem', 'modernGeography', 'Jerusalem, Israel/Palestine', 'Jerusalém, Israel/Palestina'),
    ('fixture.place.bethlehem', 'modernGeography', 'Bethlehem, West Bank', 'Belém, Cisjordânia'),
    ('fixture.place.rome', 'modernGeography', 'Rome, Italy', 'Roma, Itália'),
    ('fixture.place.egypt', 'modernGeography', 'Egypt', 'Egito'),
    ('fixture.place.elah', 'modernGeography', 'Wadi es-Sunt, Israel (commonly identified)', 'Wadi es-Sunt, Israel (comumente identificado)')
), targets AS MATERIALIZED (
    SELECT l.* FROM legacy l JOIN entity_details d ON d.entity_id=l.entity_id
    WHERE CASE l.field WHEN 'approximateDates' THEN d.approximate_dates
                      ELSE d.modern_geography END = l.english
      AND EXISTS (SELECT 1 FROM entity_detail_sources s WHERE s.entity_id=d.entity_id
                  AND s.source_id='fixture.source.editorial')
      AND NOT EXISTS (SELECT 1 FROM entity_detail_sources s WHERE s.entity_id=d.entity_id
                      AND s.source_id NOT IN ('fixture.source.editorial','fixture.source.web'))
), cleared AS (
    UPDATE entity_details d SET
      approximate_dates=CASE WHEN EXISTS (SELECT 1 FROM targets t WHERE t.entity_id=d.entity_id AND t.field='approximateDates') THEN NULL ELSE d.approximate_dates END,
      modern_geography=CASE WHEN EXISTS (SELECT 1 FROM targets t WHERE t.entity_id=d.entity_id AND t.field='modernGeography') THEN NULL ELSE d.modern_geography END
    WHERE d.entity_id IN (SELECT entity_id FROM targets) RETURNING d.entity_id
)
UPDATE entity_localizations l SET fields=jsonb_set(l.fields, ARRAY[t.field], 'null'::jsonb)
FROM targets t WHERE l.entity_id=t.entity_id AND l.source_id='verbum.localization.pt-BR'
  AND l.language='pt-BR' AND l.fields->>t.field=t.portuguese;

-- An edge with only demonstration evidence cannot remain a sourced connection.
DELETE FROM relationships r
WHERE EXISTS (SELECT 1 FROM relationship_sources s WHERE s.relationship_id=r.id AND s.source_id='fixture.source.editorial')
  AND NOT EXISTS (SELECT 1 FROM relationship_sources s WHERE s.relationship_id=r.id AND s.source_id<>'fixture.source.editorial');

-- Unreplaced legacy timeline summaries/dates have no historical evidence.
-- Current bilingual narrative entries have real sources and are unaffected.
WITH targets AS MATERIALIZED (
 SELECT t.id FROM timeline_events t WHERE EXISTS (SELECT 1 FROM timeline_event_sources s WHERE s.event_id=t.id AND s.source_id='fixture.source.editorial')
  AND NOT EXISTS (SELECT 1 FROM timeline_event_sources s WHERE s.event_id=t.id AND s.source_id<>'fixture.source.editorial')
), cleared AS (
 UPDATE timeline_events SET start_year=NULL,end_year=NULL,date_precision='unknown',summary=NULL
 WHERE id IN (SELECT id FROM targets) RETURNING id
)
UPDATE timeline_localizations SET fields=jsonb_set(fields, '{summary}', 'null'::jsonb)
WHERE event_id IN (SELECT id FROM targets) AND source_id='verbum.localization.pt-BR';

DELETE FROM relationship_sources WHERE source_id='fixture.source.editorial';
DELETE FROM entity_detail_sources WHERE source_id='fixture.source.editorial';
DELETE FROM entity_sources WHERE source_id='fixture.source.editorial';
DELETE FROM timeline_event_sources WHERE source_id='fixture.source.editorial';
-- Retain the source row and old review records for traceability, without live associations.

-- Localize the existing authorship labels, without inventing scholarly provenance.
-- Preserve any explicit source translation already supplied by an editor.
WITH labels(reference_id,original,language,citation,input_hash) AS (VALUES
    ('editorial.themes.2026-10', 'Verbum — seleção temática e resumos editoriais / thematic selection and editorial summaries (2026-10).', 'en', 'Verbum — thematic selection and editorial summaries (2026-10).', '8b4a1ff156fb15624aa0a57557901987bfabf4fd87f268aed181ffad67b3f5f8'),
    ('editorial.themes.2026-10', 'Verbum — seleção temática e resumos editoriais / thematic selection and editorial summaries (2026-10).', 'pt-BR', 'Verbum — seleção temática e resumos editoriais (2026-10).', '8b4a1ff156fb15624aa0a57557901987bfabf4fd87f268aed181ffad67b3f5f8'),
    ('editorial.events.2026-10', 'Verbum — estudos de acontecimentos bíblicos / biblical event studies. Original bilingual editorial notes with chapter references.', 'en', 'Verbum — original bilingual biblical event studies with chapter references.', '3eba7b46699a17f64f9c569ab4b1999033f1c087d9705f0ea00eb7b3e422fd16'),
    ('editorial.events.2026-10', 'Verbum — estudos de acontecimentos bíblicos / biblical event studies. Original bilingual editorial notes with chapter references.', 'pt-BR', 'Verbum — estudos editoriais bilíngues de acontecimentos bíblicos, com referências aos capítulos.', '3eba7b46699a17f64f9c569ab4b1999033f1c087d9705f0ea00eb7b3e422fd16'),
    ('editorial.timeline.2026-10', 'Verbum — original bilingual study notes; biblical narrative order, not an absolute chronology.', 'en', 'Verbum — original bilingual study notes; biblical narrative order, not an absolute chronology.', '22488536430d149d68624250d0db7830bc51ec7464bef097ee0a26801a04ae54'),
    ('editorial.timeline.2026-10', 'Verbum — original bilingual study notes; biblical narrative order, not an absolute chronology.', 'pt-BR', 'Verbum — notas de estudo editoriais bilíngues; ordem da narrativa bíblica, sem afirmar uma cronologia absoluta.', '22488536430d149d68624250d0db7830bc51ec7464bef097ee0a26801a04ae54')
)
INSERT INTO source_localizations(reference_id,language,source_id,fields,input_hash)
SELECT l.reference_id,l.language,l.reference_id,jsonb_build_object('citation',l.citation),l.input_hash
FROM labels l JOIN sources s ON s.id=l.reference_id AND s.citation=l.original
WHERE NOT EXISTS (SELECT 1 FROM source_localizations x WHERE x.reference_id=l.reference_id AND x.language=l.language)
ON CONFLICT DO NOTHING;
