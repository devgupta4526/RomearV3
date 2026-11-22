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

CREATE TABLE IF NOT EXISTS device_account (
    token_hash bytea PRIMARY KEY CHECK (octet_length(token_hash) = 32),
    account_id uuid NOT NULL UNIQUE REFERENCES account(id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS device_transfer (
    account_id uuid PRIMARY KEY REFERENCES account(id) ON DELETE CASCADE,
    code_hash bytea NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    redeemed_by bytea
);

CREATE TABLE IF NOT EXISTS account_profile (
    account_id uuid PRIMARY KEY REFERENCES account(id) ON DELETE CASCADE,
    profile jsonb NOT NULL CHECK (jsonb_typeof(profile) = 'object')
);

ALTER TABLE account_profile ADD COLUMN IF NOT EXISTS discoverable boolean NOT NULL DEFAULT false;
CREATE UNIQUE INDEX IF NOT EXISTS account_profile_public_username
    ON account_profile (lower(profile->>'username')) WHERE discoverable;

CREATE TABLE IF NOT EXISTS personal_plan (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    item_id text NOT NULL,
    day date NOT NULL,
    time_label text NOT NULL,
    guests integer NOT NULL CHECK (guests BETWEEN 1 AND 8),
    cancelled boolean NOT NULL DEFAULT false
);
CREATE INDEX IF NOT EXISTS personal_plan_account_day ON personal_plan (account_id, day, id);
CREATE UNIQUE INDEX IF NOT EXISTS personal_plan_active_slot
    ON personal_plan (account_id, item_id, day, time_label) WHERE NOT cancelled;

CREATE TABLE IF NOT EXISTS account_follow (
    account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    peer_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    PRIMARY KEY (account_id, peer_id),
    CHECK (account_id <> peer_id)
);

CREATE TABLE IF NOT EXISTS direct_message (
    sender_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    recipient_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    id uuid NOT NULL,
    body text NOT NULL CHECK (char_length(body) BETWEEN 1 AND 2000),
    sent_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (sender_id, id),
    CHECK (sender_id <> recipient_id)
);
CREATE INDEX IF NOT EXISTS dm_sender_recipient_recent ON direct_message (sender_id, recipient_id, sent_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS dm_recipient_sender_recent ON direct_message (recipient_id, sender_id, sent_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS dm_sender_rate ON direct_message (sender_id, sent_at);

CREATE TABLE IF NOT EXISTS activity_pool (
    id uuid PRIMARY KEY,
    owner_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    city text NOT NULL,
    title text NOT NULL,
    location text NOT NULL,
    description text NOT NULL,
    day date NOT NULL,
    time_label text NOT NULL,
    capacity integer NOT NULL CHECK (capacity BETWEEN 2 AND 12)
);
CREATE INDEX IF NOT EXISTS activity_pool_city_day ON activity_pool (city, day, id);
CREATE INDEX IF NOT EXISTS activity_pool_owner ON activity_pool (owner_id);

CREATE TABLE IF NOT EXISTS pool_request (
    pool_id uuid NOT NULL REFERENCES activity_pool(id) ON DELETE CASCADE,
    account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    status text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'declined')),
    PRIMARY KEY (pool_id, account_id)
);
CREATE INDEX IF NOT EXISTS pool_request_account ON pool_request (account_id);

CREATE TABLE IF NOT EXISTS club_member (
    club_id text NOT NULL,
    account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    PRIMARY KEY (club_id, account_id)
);

CREATE TABLE IF NOT EXISTS club_message (
    club_id text NOT NULL,
    sender_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    id uuid NOT NULL,
    body text NOT NULL CHECK (char_length(body) BETWEEN 1 AND 2000),
    sent_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (sender_id, id)
);
CREATE INDEX IF NOT EXISTS club_message_recent ON club_message (club_id, sent_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS club_message_sender_rate ON club_message (sender_id, sent_at);

CREATE TABLE IF NOT EXISTS community_post (
    id uuid PRIMARY KEY,
    owner_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    body text NOT NULL CHECK (char_length(body) BETWEEN 1 AND 2000),
    category text NOT NULL,
    photo bytea CHECK (octet_length(photo) <= 262144),
    sample text,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX IF NOT EXISTS community_post_recent ON community_post (created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS community_post_owner ON community_post (owner_id);

CREATE TABLE IF NOT EXISTS post_comment (
    post_id uuid NOT NULL REFERENCES community_post(id) ON DELETE CASCADE,
    account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    id uuid NOT NULL,
    body text NOT NULL CHECK (char_length(body) BETWEEN 1 AND 1000),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (post_id, account_id, id)
);
CREATE INDEX IF NOT EXISTS post_comment_recent ON post_comment (post_id, created_at DESC, id DESC);

CREATE TABLE IF NOT EXISTS post_reaction (
    post_id uuid NOT NULL REFERENCES community_post(id) ON DELETE CASCADE,
    account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    kind text NOT NULL CHECK (kind IN ('like', 'save')),
    PRIMARY KEY (post_id, account_id, kind)
);
CREATE INDEX IF NOT EXISTS post_comment_account ON post_comment (account_id);
CREATE INDEX IF NOT EXISTS post_reaction_account ON post_reaction (account_id, kind);

CREATE TABLE IF NOT EXISTS account_library (
    account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    kind text NOT NULL CHECK (kind IN ('stamps', 'events', 'offers')),
    id text NOT NULL,
    payload jsonb NOT NULL CHECK (jsonb_typeof(payload) = 'object'),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, kind, id)
);
