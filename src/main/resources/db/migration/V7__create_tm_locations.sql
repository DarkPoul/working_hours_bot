PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS tm_locations (
    tm_user_id TEXT NOT NULL,
    location_id TEXT NOT NULL,
    PRIMARY KEY (tm_user_id, location_id),
    FOREIGN KEY (tm_user_id) REFERENCES user_accounts(id),
    FOREIGN KEY (location_id) REFERENCES locations(id)
);

CREATE INDEX IF NOT EXISTS idx_tm_locations_location ON tm_locations(location_id);
CREATE INDEX IF NOT EXISTS idx_tm_locations_tm ON tm_locations(tm_user_id);
