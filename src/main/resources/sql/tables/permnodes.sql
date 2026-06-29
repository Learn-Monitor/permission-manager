CREATE TABLE IF NOT EXISTS permnodes (
    permission VARCHAR(255) NOT NULL,
    username VARCHAR(255) NOT NULL,
    PRIMARY KEY (permission, username),
    FOREIGN KEY (permission) REFERENCES permissions(name) ON DELETE CASCADE,
    active BOOLEAN NOT NULL DEFAULT TRUE
)