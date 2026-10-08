package postgres

import (
	"context"
	"verbum/backend/internal/domain"
	"verbum/backend/internal/store"
)

// NFD removes combining marks without requiring the unaccent extension or a migration.
// One statement gives rows, index letters and next-page metadata from the same snapshot.
func (s *Store) EntityPage(ctx context.Context, kind domain.EntityType, request store.EntityPageRequest) (store.EntityPage, error) {
	return readJSON[store.EntityPage](ctx, s, `WITH named AS (
 SELECT `+localizedEntityJSON("$2")+` AS value,e.id,
 lower(regexp_replace(normalize(btrim((`+localizedEntityJSON("$2")+`)->>'name'), NFD), U&'[\0300-\036f]', '', 'g')) COLLATE "C" AS name
 FROM entities e WHERE e.type=$1 AND e.type<>'passage'
 ), searched AS (
 SELECT *,CASE WHEN left(name,1) BETWEEN 'a' AND 'z' THEN upper(left(name,1)) ELSE '#' END AS letter
 FROM named WHERE strpos(name,$3)>0
 ), selected AS (
 SELECT * FROM searched WHERE $4='' OR letter=$4 ORDER BY letter,name,id LIMIT $5+1 OFFSET $6
 ), page AS (SELECT * FROM selected ORDER BY letter,name,id LIMIT $5)
 SELECT jsonb_build_object(
 'entities',COALESCE((SELECT jsonb_agg(value ORDER BY letter,name,id) FROM page),'[]'::jsonb),
 'letters',COALESCE((SELECT jsonb_agg(letter ORDER BY letter) FROM (SELECT DISTINCT letter FROM searched) l),'[]'::jsonb),
 'nextOffset',CASE WHEN (SELECT count(*) FROM selected)>$5 THEN $6+$5 ELSE NULL END
 )`, kind, store.Language(ctx), store.CatalogName(request.Query), request.Letter, request.Limit, request.Offset)
}
