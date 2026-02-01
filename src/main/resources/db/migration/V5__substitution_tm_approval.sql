PRAGMA foreign_keys = ON;

ALTER TABLE substitution_request
    ADD COLUMN proposed_replacement_user_id TEXT;

ALTER TABLE substitution_request
    ADD COLUMN tm_user_id TEXT;

ALTER TABLE substitution_request
    ADD COLUMN tm_decision TEXT;

ALTER TABLE substitution_request
    ADD COLUMN tm_decided_at TEXT;

CREATE INDEX IF NOT EXISTS idx_substitution_request_tm_user
    ON substitution_request(tm_user_id);

CREATE INDEX IF NOT EXISTS idx_substitution_request_request_date
    ON substitution_request(request_date);

CREATE INDEX IF NOT EXISTS idx_substitution_request_status_tm
    ON substitution_request(status, tm_user_id);
