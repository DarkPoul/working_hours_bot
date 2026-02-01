CREATE TABLE IF NOT EXISTS locations (
                                         id INTEGER PRIMARY KEY AUTOINCREMENT,
                                         code TEXT NOT NULL UNIQUE,
                                         name TEXT NOT NULL,
                                         is_active BOOLEAN NOT NULL DEFAULT 1,
                                         sort_order INTEGER
);

ALTER TABLE user_accounts ADD COLUMN location_id INTEGER;

ALTER TABLE registration_sessions ADD COLUMN draft_location_page INTEGER DEFAULT 0;

INSERT OR IGNORE INTO locations (code, name, is_active, sort_order) VALUES
    ('MALYSHKA_2D', 'Малишка 2Д', 1, 10),
    ('DARNYTSIA', 'Дарниця', 1, 20),
    ('POZNYAKY', 'Позняки', 1, 30),
    ('OBOLON', 'Оболонь', 1, 40),
    ('LUKIANIVKA', 'Лук''янівка', 1, 50);
