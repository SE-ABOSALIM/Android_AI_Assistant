-- Simplify app catalog persistence.
-- Apply with:
--   psql "$PSQL_DATABASE_URL" -f V3/database/migrations/002_simplify_app_catalog_tables.sql

BEGIN;

ALTER TABLE apps
    ADD COLUMN IF NOT EXISTS display_name TEXT;

UPDATE apps
   SET display_name = COALESCE(NULLIF(btrim(apps.display_name), ''), latest.display_name, apps.package_name)
  FROM (
        SELECT DISTINCT ON (app_id)
               app_id,
               display_name
          FROM device_apps
         ORDER BY app_id, updated_at DESC
       ) AS latest
 WHERE apps.id = latest.app_id
   AND (apps.display_name IS NULL OR btrim(apps.display_name) = '');

UPDATE apps
   SET display_name = package_name
 WHERE display_name IS NULL
    OR btrim(display_name) = '';

ALTER TABLE apps
    ALTER COLUMN display_name SET NOT NULL;

ALTER TABLE apps
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_name = 'apps'
           AND column_name = 'first_seen_at'
    ) THEN
        UPDATE apps
           SET created_at = COALESCE(created_at, first_seen_at, now());
    ELSE
        UPDATE apps
           SET created_at = COALESCE(created_at, now());
    END IF;
END $$;

ALTER TABLE apps
    ALTER COLUMN created_at SET DEFAULT now(),
    ALTER COLUMN created_at SET NOT NULL;

ALTER TABLE apps
    DROP COLUMN IF EXISTS first_seen_at,
    DROP COLUMN IF EXISTS last_seen_at;

DROP TABLE IF EXISTS app_aliases;
DROP TABLE IF EXISTS app_catalog_syncs;

COMMIT;
