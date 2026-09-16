-- Private operational data, separate from the read-only editorial tables.
CREATE TABLE IF NOT EXISTS usage_entitlements (
    uid text PRIMARY KEY,
    plan text NOT NULL CHECK (plan IN ('free','premium')),
    expires_at timestamptz NOT NULL,
    source text NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS usage_counters (
    subject text NOT NULL,
    bucket text NOT NULL,
    starts_at timestamptz NOT NULL,
    amount bigint NOT NULL DEFAULT 0 CHECK (amount >= 0),
    PRIMARY KEY(subject,bucket,starts_at)
);
CREATE TABLE IF NOT EXISTS usage_operations (
    id text PRIMARY KEY,
    uid text NOT NULL,
    kind text NOT NULL,
    reserved_micros bigint NOT NULL CHECK (reserved_micros >= 0),
    charged_micros bigint NOT NULL CHECK (charged_micros >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    settled boolean NOT NULL DEFAULT false,
    succeeded boolean NOT NULL DEFAULT false
);
CREATE INDEX IF NOT EXISTS usage_operations_created ON usage_operations(created_at);
CREATE TABLE IF NOT EXISTS usage_cache (
    key text PRIMARY KEY,
    payload bytea NOT NULL,
    expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS usage_cache_expiry ON usage_cache(expires_at);
CREATE TABLE IF NOT EXISTS usage_idempotency (
    key text PRIMARY KEY,
    fingerprint text NOT NULL,
    expires_at timestamptz NOT NULL
);
CREATE TABLE IF NOT EXISTS usage_voice_tickets (
    token_hash text PRIMARY KEY,
    uid text NOT NULL,
    anonymous boolean NOT NULL,
    device text NOT NULL,
    ip text NOT NULL DEFAULT '',
    expires_at timestamptz NOT NULL,
    consumed boolean NOT NULL DEFAULT false
);
