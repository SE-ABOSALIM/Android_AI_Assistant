-- Rotatable bearer credential hashes for stable Android device ownership rows.
-- ANDROID_ID recovery is continuity-oriented, not proof of device possession.
-- TODO: Require Play Integrity/app attestation for strong public-production recovery.
-- Apply with:
--   psql "$PSQL_DATABASE_URL" -f V3/database/migrations/008_create_device_auth_credentials.sql

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS device_auth_credentials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_ref_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at TIMESTAMPTZ,
    CONSTRAINT device_auth_credentials_token_hash_format
        CHECK (token_hash ~ '^[0-9a-f]{64}$')
);

ALTER TABLE device_auth_credentials
    DROP CONSTRAINT IF EXISTS device_auth_credentials_device_ref_id_key;

CREATE UNIQUE INDEX IF NOT EXISTS idx_device_auth_credentials_one_active_per_device
    ON device_auth_credentials(device_ref_id)
    WHERE revoked_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_device_auth_credentials_active_token_hash
    ON device_auth_credentials(token_hash)
    WHERE revoked_at IS NULL;

COMMIT;
