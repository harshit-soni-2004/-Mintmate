ALTER TABLE users
    ADD COLUMN google_sub VARCHAR(255) NULL,
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    MODIFY COLUMN password_hash VARCHAR(255) NULL;

UPDATE users SET email_verified = TRUE;

ALTER TABLE users
    ADD CONSTRAINT uk_users_google_sub UNIQUE (google_sub);
