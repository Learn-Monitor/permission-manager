INSERT INTO permnodes (permission, username, active)
VALUES (?, ?, ?)
ON CONFLICT (permission, username) DO
UPDATE SET active = EXCLUDED.active;