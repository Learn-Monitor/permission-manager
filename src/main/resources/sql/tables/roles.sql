CREATE TABLE IF NOT EXISTS user_roles (
    username VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (username, role)
);
