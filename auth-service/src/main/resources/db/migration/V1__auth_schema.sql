CREATE TABLE users (
    id             BIGSERIAL PRIMARY KEY,
    -- Stored lower-cased. Email comparison is case-insensitive in practice, and
    -- a UNIQUE index on the raw value would happily accept Ana@x.com alongside
    -- ana@x.com as two accounts for one person.
    email          VARCHAR(255) NOT NULL,
    password_hash  VARCHAR(72)  NOT NULL,
    display_name   VARCHAR(120) NOT NULL,
    created_at     TIMESTAMP    NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email)
);
