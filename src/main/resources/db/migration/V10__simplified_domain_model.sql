PRAGMA foreign_keys = ON;

ALTER TABLE user_accounts ADD COLUMN full_name TEXT;
ALTER TABLE user_accounts ADD COLUMN seller_status TEXT;
ALTER TABLE user_accounts ADD COLUMN active BOOLEAN NOT NULL DEFAULT 1;
ALTER TABLE user_accounts ADD COLUMN blocked BOOLEAN NOT NULL DEFAULT 0;
ALTER TABLE user_accounts ADD COLUMN tm_pin_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE user_accounts ADD COLUMN pending_location_id TEXT;
ALTER TABLE user_accounts ADD COLUMN state TEXT;
ALTER TABLE user_accounts ADD COLUMN state_payload TEXT;

UPDATE user_accounts
SET seller_status = CASE
    WHEN role = 'SELLER' AND status = 'APPROVED' THEN 'APPROVED'
    WHEN role = 'SELLER' AND status = 'PENDING_APPROVAL' THEN 'PENDING'
    WHEN role = 'SELLER' AND status = 'REJECTED' THEN 'REJECTED'
    WHEN role = 'SELLER' AND status = 'BLOCKED' THEN 'REJECTED'
    ELSE 'NEW'
END;

UPDATE user_accounts SET blocked = 1 WHERE status = 'BLOCKED';

CREATE INDEX IF NOT EXISTS idx_user_accounts_pending_location_id ON user_accounts(pending_location_id);
CREATE INDEX IF NOT EXISTS idx_user_accounts_role_seller_status_location ON user_accounts(role, seller_status, location_id);

CREATE TABLE IF NOT EXISTS join_requests (
    id TEXT PRIMARY KEY,
    seller_id TEXT NOT NULL,
    location_id TEXT NOT NULL,
    tm_user_id TEXT NOT NULL,
    status TEXT NOT NULL,
    created_at TEXT NOT NULL,
    resolved_at TEXT,
    FOREIGN KEY (seller_id) REFERENCES user_accounts(id),
    FOREIGN KEY (location_id) REFERENCES locations(id),
    FOREIGN KEY (tm_user_id) REFERENCES user_accounts(id)
);

CREATE INDEX IF NOT EXISTS idx_join_requests_tm_status ON join_requests(tm_user_id, status, created_at);
CREATE INDEX IF NOT EXISTS idx_join_requests_seller_status ON join_requests(seller_id, status);
CREATE UNIQUE INDEX IF NOT EXISTS uq_join_requests_seller_pending ON join_requests(seller_id) WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_schedule_days_location_date ON schedule_days(location_id, date);
