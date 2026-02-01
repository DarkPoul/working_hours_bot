PRAGMA foreign_keys = OFF;

CREATE TABLE IF NOT EXISTS substitution_request_new (
    id TEXT PRIMARY KEY,
    requester_user_id TEXT NOT NULL,
    location_id TEXT NOT NULL,
    request_date TEXT NOT NULL,
    status TEXT NOT NULL,
    urgent BOOLEAN NOT NULL DEFAULT 0,
    scope TEXT,
    replacement_user_id TEXT,
    proposed_replacement_user_id TEXT,
    tm_user_id BIGINT,
    tm_decision TEXT,
    tm_decided_at TEXT,
    resolved_by_user_id TEXT,
    reject_reason TEXT,
    created_at TEXT NOT NULL,
    resolved_at TEXT,
    version INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (requester_user_id) REFERENCES user_accounts(id),
    FOREIGN KEY (location_id) REFERENCES locations(id),
    FOREIGN KEY (replacement_user_id) REFERENCES user_accounts(id),
    FOREIGN KEY (resolved_by_user_id) REFERENCES user_accounts(id)
);

INSERT INTO substitution_request_new (
    id,
    requester_user_id,
    location_id,
    request_date,
    status,
    urgent,
    scope,
    replacement_user_id,
    proposed_replacement_user_id,
    tm_user_id,
    tm_decision,
    tm_decided_at,
    resolved_by_user_id,
    reject_reason,
    created_at,
    resolved_at,
    version
)
SELECT
    id,
    requester_user_id,
    location_id,
    request_date,
    status,
    urgent,
    scope,
    replacement_user_id,
    proposed_replacement_user_id,
    CAST(tm_user_id AS INTEGER),
    tm_decision,
    tm_decided_at,
    resolved_by_user_id,
    reject_reason,
    created_at,
    resolved_at,
    version
FROM substitution_request;

DROP INDEX IF EXISTS idx_substitution_request_requester_date;
DROP INDEX IF EXISTS idx_substitution_request_status;
DROP INDEX IF EXISTS idx_substitution_request_tm_user;
DROP INDEX IF EXISTS idx_substitution_request_request_date;
DROP INDEX IF EXISTS idx_substitution_request_status_tm;

ALTER TABLE substitution_request RENAME TO substitution_request_old;
ALTER TABLE substitution_request_new RENAME TO substitution_request;

CREATE INDEX IF NOT EXISTS idx_substitution_request_requester_date
    ON substitution_request(requester_user_id, request_date);

CREATE INDEX IF NOT EXISTS idx_substitution_request_status
    ON substitution_request(status);

CREATE INDEX IF NOT EXISTS idx_substitution_request_tm_user
    ON substitution_request(tm_user_id);

CREATE INDEX IF NOT EXISTS idx_substitution_request_request_date
    ON substitution_request(request_date);

CREATE INDEX IF NOT EXISTS idx_substitution_request_status_tm
    ON substitution_request(status, tm_user_id);

CREATE TABLE IF NOT EXISTS substitution_request_candidate_new (
    id TEXT PRIMARY KEY,
    request_id TEXT NOT NULL,
    candidate_user_id TEXT NOT NULL,
    notified_message_id BIGINT,
    notified_chat_id BIGINT,
    state TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    UNIQUE (request_id, candidate_user_id),
    FOREIGN KEY (request_id) REFERENCES substitution_request(id),
    FOREIGN KEY (candidate_user_id) REFERENCES user_accounts(id)
);

INSERT INTO substitution_request_candidate_new (
    id,
    request_id,
    candidate_user_id,
    notified_message_id,
    notified_chat_id,
    state,
    created_at,
    updated_at
)
SELECT
    id,
    request_id,
    candidate_user_id,
    notified_message_id,
    notified_chat_id,
    state,
    created_at,
    updated_at
FROM substitution_request_candidate;

DROP INDEX IF EXISTS idx_substitution_candidate_request_state;

DROP TABLE substitution_request_candidate;
ALTER TABLE substitution_request_candidate_new RENAME TO substitution_request_candidate;

CREATE INDEX IF NOT EXISTS idx_substitution_candidate_request_state
    ON substitution_request_candidate(request_id, state);

DROP TABLE substitution_request_old;

PRAGMA foreign_keys = ON;
