-- Executadas em transação REPEATABLE READ READ ONLY; apenas tabelas de catálogo.

-- totals
SELECT type,count(*) n FROM entities GROUP BY type ORDER BY type;

-- chapters
WITH linked AS (
 SELECT DISTINCT p.book_id,p.chapter,e.id,e.type FROM entity_passage_associations p JOIN entities e ON e.id=p.entity_id
 UNION SELECT split_part(p.id,'.',2),split_part(p.id,'.',3)::int,e.id,e.type
 FROM entities p JOIN relationships r ON r.source_entity_id=p.id OR r.target_entity_id=p.id
 JOIN entities e ON e.id=CASE WHEN r.source_entity_id=p.id THEN r.target_entity_id ELSE r.source_entity_id END
 WHERE p.id ~ '^passage\.[^.]+\.[0-9]+$'
 ) SELECT book_id,chapter,count(DISTINCT id) entities,
 count(DISTINCT id) FILTER (WHERE type='person') people,
 count(DISTINCT id) FILTER (WHERE type='place') places,
 count(DISTINCT id) FILTER (WHERE type='theme') themes,
 count(DISTINCT id) FILTER (WHERE type='event') events,
 count(DISTINCT id) FILTER (WHERE type='originalTerm') terms
 FROM linked GROUP BY book_id,chapter ORDER BY book_id,chapter;

-- guides
SELECT book_id,chapter,ARRAY(SELECT jsonb_object_keys(presentations)) languages FROM chapter_studies ORDER BY book_id,chapter;

-- profiles
SELECT entity_id,ARRAY(SELECT jsonb_object_keys(presentations)) languages FROM entity_profiles ORDER BY entity_id;

-- themes
SELECT entity_id,ARRAY(SELECT jsonb_object_keys(presentations)) languages FROM theme_studies ORDER BY entity_id;

-- entities
SELECT e.id,e.type,e.name,lang.code,
 COALESCE(ep.presentations->lang.code->>'name',l.name,CASE WHEN lang.code='en' OR e.type='originalTerm' THEN e.name ELSE 'Nome em tradução' END) display_name,
 length(COALESCE(ep.presentations->lang.code->>'summary',l.description,CASE WHEN lang.code='en' THEN e.summary END,'')) summary_chars,
 CASE WHEN trim(COALESCE(ep.presentations->lang.code->>'summary',l.description,CASE WHEN lang.code='en' THEN e.summary END,''))='' THEN 0 ELSE cardinality(regexp_split_to_array(trim(COALESCE(ep.presentations->lang.code->>'summary',l.description,CASE WHEN lang.code='en' THEN e.summary END,'')), '\s+')) END summary_words,
 EXISTS(SELECT 1 FROM entity_passage_associations a WHERE a.entity_id=e.id) has_passages,
 EXISTS(SELECT 1 FROM entity_source_records s WHERE s.entity_id=e.id) has_source_record,
 EXISTS(SELECT 1 FROM entity_sources s WHERE s.entity_id=e.id) has_source
 FROM entities e CROSS JOIN (VALUES('en'),('pt-BR')) lang(code)
 LEFT JOIN entity_profiles ep ON ep.entity_id=e.id
 LEFT JOIN LATERAL (SELECT * FROM entity_localizations l WHERE l.entity_id=e.id AND l.language=lang.code ORDER BY (l.source_id LIKE 'step.%'),l.source_id LIMIT 1) l ON true
 WHERE e.type<>'passage' ORDER BY e.type,e.id,lang.code;

-- timeline
SELECT count(*) n FROM timeline_events;

-- sequences
SELECT era_id,count(*) n FROM event_sequence_items GROUP BY era_id;

-- scripture
SELECT book_id,count(*) verses,count(DISTINCT chapter) chapters FROM scripture_verses GROUP BY book_id ORDER BY book_id;

-- source_quality
SELECT 'edges_without_sources' metric,count(*) n FROM relationships r WHERE NOT EXISTS(SELECT 1 FROM relationship_sources s WHERE s.relationship_id=r.id)
 UNION ALL SELECT 'demo_sources',count(*) FROM entity_sources WHERE source_id='fixture.source.editorial';