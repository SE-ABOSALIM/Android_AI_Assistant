-- Clarify device foreign-key column names.
-- Apply with:
--   psql "$PSQL_DATABASE_URL" -f V3/database/migrations/005_rename_device_fk_columns.sql

BEGIN;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_name = 'device_apps'
           AND column_name = 'device_id'
    ) AND NOT EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_name = 'device_apps'
           AND column_name = 'device_ref_id'
    ) THEN
        ALTER TABLE device_apps RENAME COLUMN device_id TO device_ref_id;
    END IF;

    IF EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_name = 'command_history'
           AND column_name = 'device_id'
    ) AND NOT EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_name = 'command_history'
           AND column_name = 'device_ref_id'
    ) THEN
        ALTER TABLE command_history RENAME COLUMN device_id TO device_ref_id;
    END IF;

    IF EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_name = 'failed_app_open_attempts'
           AND column_name = 'device_id'
    ) AND NOT EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_name = 'failed_app_open_attempts'
           AND column_name = 'device_ref_id'
    ) THEN
        ALTER TABLE failed_app_open_attempts RENAME COLUMN device_id TO device_ref_id;
    END IF;

    IF EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_name = 'error_messages'
           AND column_name = 'device_id'
    ) AND NOT EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_name = 'error_messages'
           AND column_name = 'device_ref_id'
    ) THEN
        ALTER TABLE error_messages RENAME COLUMN device_id TO device_ref_id;
    END IF;

    IF EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_name = 'custom_commands'
           AND column_name = 'device_id'
    ) AND NOT EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_name = 'custom_commands'
           AND column_name = 'device_ref_id'
    ) THEN
        ALTER TABLE custom_commands RENAME COLUMN device_id TO device_ref_id;
    END IF;
END $$;

DROP INDEX IF EXISTS idx_device_apps_device_normalized;
CREATE INDEX IF NOT EXISTS idx_device_apps_device_ref_normalized
    ON device_apps(device_ref_id, normalized_name);

DROP INDEX IF EXISTS idx_command_history_device_created;
CREATE INDEX IF NOT EXISTS idx_command_history_device_ref_created
    ON command_history(device_ref_id, created_at DESC);

DROP INDEX IF EXISTS idx_failed_app_open_attempts_device_created;
CREATE INDEX IF NOT EXISTS idx_failed_app_open_attempts_device_ref_created
    ON failed_app_open_attempts(device_ref_id, created_at DESC);

DROP INDEX IF EXISTS idx_error_messages_device_created;
CREATE INDEX IF NOT EXISTS idx_error_messages_device_ref_created
    ON error_messages(device_ref_id, created_at DESC);

DROP INDEX IF EXISTS idx_custom_commands_device_language_name;
CREATE INDEX IF NOT EXISTS idx_custom_commands_device_ref_language_name
    ON custom_commands(device_ref_id, language, normalized_name);

DROP INDEX IF EXISTS idx_custom_commands_device_updated;
CREATE INDEX IF NOT EXISTS idx_custom_commands_device_ref_updated
    ON custom_commands(device_ref_id, updated_at DESC);

COMMIT;
