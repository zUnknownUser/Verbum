-- Account-private data. Never joined to Scripture search or AI context.
CREATE TABLE IF NOT EXISTS personal_sync_accounts (
    uid text PRIMARY KEY,
    revision bigint NOT NULL DEFAULT 0,
    deleted boolean NOT NULL DEFAULT false
);
CREATE TABLE IF NOT EXISTS personal_sync_records (
    uid text NOT NULL REFERENCES personal_sync_accounts(uid),
    id text NOT NULL,
    revision bigint NOT NULL,
    value jsonb,
    PRIMARY KEY (uid, id)
);
CREATE INDEX IF NOT EXISTS personal_sync_changes ON personal_sync_records(uid, revision);
CREATE TABLE IF NOT EXISTS personal_sync_receipts (
    uid text NOT NULL REFERENCES personal_sync_accounts(uid),
    mutation_id text NOT NULL,
    PRIMARY KEY(uid, mutation_id)
);
