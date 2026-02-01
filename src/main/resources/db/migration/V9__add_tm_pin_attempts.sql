ALTER TABLE registration_sessions
ADD COLUMN tm_pin_attempts INTEGER NOT NULL DEFAULT 0;
