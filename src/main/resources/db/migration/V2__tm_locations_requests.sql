PRAGMA foreign_keys = ON;

ALTER TABLE locations ADD COLUMN tm_user_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_locations_tm_user_id ON locations(tm_user_id);

CREATE TABLE IF NOT EXISTS tm_sessions (
                                           id TEXT PRIMARY KEY,
                                           telegram_user_id BIGINT NOT NULL UNIQUE,
                                           state TEXT NOT NULL,
                                           selected_request_id TEXT,
                                           selected_location_id TEXT,
                                           draft_location_name TEXT,
                                           created_at TEXT NOT NULL,
                                           updated_at TEXT NOT NULL
);
