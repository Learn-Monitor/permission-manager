INSERT INTO permissions (name, description)
VALUES (?, ?)
ON CONFLICT (name) DO
UPDATE SET description = EXCLUDED.description;