CREATE TABLE IF NOT EXISTS installation_state (
    token_hash bytea PRIMARY KEY CHECK (octet_length(token_hash) = 32),
    revision bigint NOT NULL CHECK (revision > 0),
    state jsonb NOT NULL CHECK (jsonb_typeof(state) = 'object')
);

CREATE TABLE IF NOT EXISTS account (
    id uuid PRIMARY KEY,
    email text NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS email_challenge (
    id uuid PRIMARY KEY,
    email text NOT NULL,
    digest bytea NOT NULL CHECK (octet_length(digest) = 32),
    created_at timestamptz NOT NULL DEFAULT now(),
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 5),
    consumed boolean NOT NULL DEFAULT false,
    delivered boolean NOT NULL DEFAULT false
);

CREATE TABLE IF NOT EXISTS account_session (
    token_hash bytea PRIMARY KEY CHECK (octet_length(token_hash) = 32),
    account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    expires_at timestamptz NOT NULL
);

CREATE INDEX IF NOT EXISTS account_session_expiry ON account_session (expires_at);

CREATE TABLE IF NOT EXISTS account_profile (
    account_id uuid PRIMARY KEY REFERENCES account(id) ON DELETE CASCADE,
    profile jsonb NOT NULL CHECK (jsonb_typeof(profile) = 'object')
);
