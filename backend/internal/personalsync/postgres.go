package personalsync

import (
	"context"
	"encoding/json"
	"errors"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"reflect"
)

type Store struct{ pool *pgxpool.Pool }

func Open(ctx context.Context, url string) (*Store, error) {
	p, err := pgxpool.New(ctx, url)
	if err != nil {
		return nil, err
	}
	var ok bool
	err = p.QueryRow(ctx, "SELECT EXISTS(SELECT 1 FROM personal_sync_accounts LIMIT 1)").Scan(&ok)
	if err != nil {
		p.Close()
		return nil, err
	}
	return &Store{p}, nil
}
func (s *Store) Close() { s.pool.Close() }

func (s *Store) Exchange(ctx context.Context, uid string, request Request) (Response, error) {
	result := Response{Cursor: request.Cursor, Records: []Record{}, Accepted: []Record{}}
	tx, err := s.pool.Begin(ctx)
	if err != nil {
		return result, err
	}
	defer tx.Rollback(ctx)
	if _, err = tx.Exec(ctx, "INSERT INTO personal_sync_accounts(uid) VALUES($1) ON CONFLICT DO NOTHING", uid); err != nil {
		return result, err
	}
	var revision int64
	var deleted bool
	if err = tx.QueryRow(ctx, "SELECT revision,deleted FROM personal_sync_accounts WHERE uid=$1 FOR UPDATE", uid).Scan(&revision, &deleted); err != nil {
		return result, err
	}
	if deleted {
		return result, ErrDeleted
	}
	if request.Cursor > revision {
		return result, ErrInvalid
	}
	for _, change := range request.Changes {
		var raw []byte
		var version int64
		err = tx.QueryRow(ctx, "SELECT revision,value FROM personal_sync_records WHERE uid=$1 AND id=$2", uid, change.ID).Scan(&version, &raw)
		exists := err == nil
		if err != nil && !errors.Is(err, pgx.ErrNoRows) {
			return result, err
		}
		var old *Value
		if exists {
			if err = json.Unmarshal(raw, &old); err != nil {
				return result, err
			}
		}
		receipt, err := tx.Exec(ctx, "INSERT INTO personal_sync_receipts(uid,mutation_id) VALUES($1,$2) ON CONFLICT DO NOTHING", uid, change.MutationID)
		if err != nil {
			return result, err
		}
		merged := old
		if receipt.RowsAffected() > 0 {
			if exists && old == nil && change.BaseRevision != version {
				merged = nil
			} else {
				merged = Merge(change.ID, change.Base, change.Value, old, exists)
			}
		}
		if merged != nil && len(merged.Note) > 32768 {
			return result, ErrCapacity
		}
		if !exists || !reflect.DeepEqual(old, merged) {
			revision++
			version = revision
			_, err = tx.Exec(ctx, `INSERT INTO personal_sync_records(uid,id,revision,value) VALUES($1,$2,$3,$4::jsonb)
                ON CONFLICT(uid,id) DO UPDATE SET revision=excluded.revision,value=excluded.value`, uid, change.ID, version, encode(merged))
			if err != nil {
				return result, err
			}
		}
		result.Accepted = append(result.Accepted, Record{change.ID, version, merged})
	}
	if len(request.Changes) > 0 {
		var count, size int64
		if err = tx.QueryRow(ctx, "SELECT count(*),COALESCE(sum(octet_length(value::text)),0) FROM personal_sync_records WHERE uid=$1", uid).Scan(&count, &size); err != nil {
			return result, err
		}
		var receipts int64
		if err = tx.QueryRow(ctx, "SELECT count(*) FROM personal_sync_receipts WHERE uid=$1", uid).Scan(&receipts); err != nil {
			return result, err
		}
		if receipts > 100000 || count > 50000 || size > 32*1024*1024 {
			return result, ErrCapacity
		}
		if _, err = tx.Exec(ctx, "UPDATE personal_sync_accounts SET revision=$2 WHERE uid=$1", uid, revision); err != nil {
			return result, err
		}
	}
	rows, err := tx.Query(ctx, "SELECT id,revision,value FROM personal_sync_records WHERE uid=$1 AND revision>$2 ORDER BY revision LIMIT $3", uid, request.Cursor, PageSize+1)
	if err != nil {
		return result, err
	}
	for rows.Next() {
		var record Record
		var raw []byte
		if err = rows.Scan(&record.ID, &record.Revision, &raw); err != nil {
			rows.Close()
			return result, err
		}
		if len(result.Records) == PageSize {
			result.More = true
			break
		}
		if err = json.Unmarshal(raw, &record.Value); err != nil {
			rows.Close()
			return result, err
		}
		result.Records = append(result.Records, record)
		result.Cursor = record.Revision
	}
	rows.Close()
	if err = rows.Err(); err != nil {
		return result, err
	}
	if !result.More {
		result.Cursor = revision
	}
	return result, tx.Commit(ctx)
}

// A persistent tombstone rejects in-flight writes, including on other API replicas.
// Repeated deletion is safe, even if Firebase account deletion needs to be retried.
func (s *Store) Delete(ctx context.Context, uid string) error {
	tx, err := s.pool.Begin(ctx)
	if err != nil {
		return err
	}
	defer tx.Rollback(ctx)
	_, err = tx.Exec(ctx, `INSERT INTO personal_sync_accounts(uid,deleted) VALUES($1,true)
        ON CONFLICT(uid) DO UPDATE SET deleted=true`, uid)
	if err != nil {
		return err
	}
	for _, table := range []string{"personal_sync_records", "personal_sync_receipts"} {
		if _, err = tx.Exec(ctx, "DELETE FROM "+table+" WHERE uid=$1", uid); err != nil {
			return err
		}
	}
	return tx.Commit(ctx)
}
