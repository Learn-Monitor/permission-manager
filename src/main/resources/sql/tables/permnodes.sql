CREATE TABLE IF NOT EXISTS permnodes (
    permission VARCHAR(255) NOT NULL,
    username VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (permission, username),
    FOREIGN KEY (permission) REFERENCES permissions(name) ON DELETE CASCADE
);