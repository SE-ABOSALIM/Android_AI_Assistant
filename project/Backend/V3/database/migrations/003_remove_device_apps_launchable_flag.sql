-- Remove obsolete launchable flag from device app catalog rows.
-- Apply with:
--   psql "$PSQL_DATABASE_URL" -f V3/database/migrations/003_remove_device_apps_launchable_flag.sql

BEGIN;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = 'public'
           AND table_name = 'device_apps'
           AND column_name = 'is_launchable'
    ) THEN
        DELETE FROM device_apps
         WHERE is_launchable = false;
    END IF;
END $$;

DROP INDEX IF EXISTS idx_device_apps_device_launchable;

ALTER TABLE device_apps
    DROP COLUMN IF EXISTS is_launchable;

COMMIT;
