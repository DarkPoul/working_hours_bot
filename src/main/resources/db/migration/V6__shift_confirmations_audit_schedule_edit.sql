PRAGMA foreign_keys = ON;

ALTER TABLE locations
    ADD COLUMN schedule_edit_enabled BOOLEAN NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS shift_confirmations (
    id TEXT PRIMARY KEY,
    telegram_user_id BIGINT NOT NULL,
    location_id TEXT NOT NULL,
    shift_date TEXT NOT NULL,
    status TEXT NOT NULL,
    asked_at TEXT NOT NULL,
    responded_at TEXT,
    auto_rejected BOOLEAN NOT NULL DEFAULT 0,
    UNIQUE (telegram_user_id, location_id, shift_date)
);

CREATE INDEX IF NOT EXISTS idx_shift_confirmations_user_date
    ON shift_confirmations(telegram_user_id, shift_date);

CREATE TABLE IF NOT EXISTS audit_events (
    id TEXT PRIMARY KEY,
    created_at TEXT NOT NULL,
    event_type TEXT NOT NULL,
    actor_user_id TEXT,
    target_user_id TEXT,
    location_id TEXT,
    payload TEXT,
    chat_id BIGINT,
    message_context TEXT
);

CREATE INDEX IF NOT EXISTS idx_audit_events_created_at
    ON audit_events(created_at);
