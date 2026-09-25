ALTER TABLE users ADD COLUMN password_reset_hash VARCHAR(64);
ALTER TABLE users ADD COLUMN password_reset_expires_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN password_reset_requested_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN credential_version BIGINT NOT NULL DEFAULT 0;
CREATE UNIQUE INDEX users_password_reset_hash_idx ON users(password_reset_hash) WHERE password_reset_hash IS NOT NULL;
