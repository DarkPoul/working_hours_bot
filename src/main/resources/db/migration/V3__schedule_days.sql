-- V3__schedule_days.sql
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS schedule_days (
    id TEXT PRIMARY KEY,
    telegram_user_id BIGINT NOT NULL,
    location_id TEXT NOT NULL,
    date TEXT NOT NULL,
    status TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    UNIQUE (telegram_user_id, location_id, date),
    FOREIGN KEY (location_id) REFERENCES locations(id)
);

CREATE INDEX IF NOT EXISTS idx_schedule_days_user_location_date
    ON schedule_days(telegram_user_id, location_id, date);
