PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS substitution_request (
    id TEXT PRIMARY KEY,
    requester_user_id TEXT NOT NULL,
    location_id TEXT NOT NULL,
    request_date TEXT NOT NULL,
    status TEXT NOT NULL,
    urgent BOOLEAN NOT NULL DEFAULT 0,
    scope TEXT,
    replacement_user_id TEXT,
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

CREATE INDEX IF NOT EXISTS idx_substitution_request_requester_date
    ON substitution_request(requester_user_id, request_date);

CREATE INDEX IF NOT EXISTS idx_substitution_request_status
    ON substitution_request(status);

CREATE TABLE IF NOT EXISTS substitution_request_candidate (
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

CREATE INDEX IF NOT EXISTS idx_substitution_candidate_request_state
    ON substitution_request_candidate(request_id, state);
