INSERT INTO user_roles (username, role, active)
VALUES (?, ?, ?)
ON CONFLICT (username, role) DO
UPDATE SET active = EXCLUDED.active;
