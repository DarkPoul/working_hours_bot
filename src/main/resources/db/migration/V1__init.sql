CREATE TABLE IF NOT EXISTS user_accounts (
                                             id TEXT PRIMARY KEY,
                                             telegram_user_id BIGINT NOT NULL UNIQUE,
                                             telegram_chat_id BIGINT NOT NULL,
                                             last_name TEXT NOT NULL,
                                             role TEXT NOT NULL,
                                             location TEXT,
                                             status TEXT NOT NULL,
                                             created_at TEXT NOT NULL,
                                             updated_at TEXT NOT NULL,
                                             approved_by_telegram_user_id BIGINT,
                                             approved_at TEXT
);

CREATE TABLE IF NOT EXISTS registration_sessions (
                                                     id TEXT PRIMARY KEY,
                                                     telegram_user_id BIGINT NOT NULL UNIQUE,
                                                     state TEXT NOT NULL,
                                                     draft_last_name TEXT,
                                                     draft_role TEXT,
                                                     created_at TEXT NOT NULL,
                                                     updated_at TEXT NOT NULL
);