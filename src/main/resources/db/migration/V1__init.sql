-- V1__init_schema.sql
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS locations (
                                         id TEXT PRIMARY KEY,
                                         code TEXT NOT NULL UNIQUE,
                                         name TEXT NOT NULL,
                                         is_active BOOLEAN NOT NULL DEFAULT 1,
                                         sort_order INTEGER
);

CREATE TABLE IF NOT EXISTS user_accounts (
                                             id TEXT PRIMARY KEY,
                                             telegram_user_id BIGINT NOT NULL UNIQUE,
                                             telegram_chat_id BIGINT NOT NULL,
                                             last_name TEXT NOT NULL,
                                             role TEXT NOT NULL,
                                             location_id TEXT,
                                             status TEXT NOT NULL,
                                             created_at TEXT NOT NULL,
                                             updated_at TEXT NOT NULL,
                                             approved_by_telegram_user_id BIGINT,
                                             approved_at TEXT,
                                             FOREIGN KEY (location_id) REFERENCES locations(id)
);

CREATE INDEX IF NOT EXISTS idx_user_accounts_location_id ON user_accounts(location_id);

CREATE TABLE IF NOT EXISTS registration_sessions (
                                                     id TEXT PRIMARY KEY,
                                                     telegram_user_id BIGINT NOT NULL UNIQUE,
                                                     state TEXT NOT NULL,
                                                     draft_last_name TEXT,
                                                     draft_role TEXT,
                                                     draft_location_page INTEGER DEFAULT 0,
                                                     created_at TEXT NOT NULL,
                                                     updated_at TEXT NOT NULL
);