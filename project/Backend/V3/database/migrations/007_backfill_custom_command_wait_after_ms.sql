-- Backfill WAIT step delays from parameters_json.duration_ms.
-- Apply with:
--   psql "$PSQL_DATABASE_URL" -f V3/database/migrations/007_backfill_custom_command_wait_after_ms.sql

BEGIN;

WITH parsed AS (
    SELECT
        id,
        NULLIF(
            regexp_replace(
                translate(
                    parameters_json ->> 'duration_ms',
                    U&'\0660\0661\0662\0663\0664\0665\0666\0667\0668\0669\06F0\06F1\06F2\06F3\06F4\06F5\06F6\06F7\06F8\06F9',
                    '01234567890123456789'
                ),
                '[^0-9]',
                '',
                'g'
            ),
            ''
        ) AS duration_digits
    FROM custom_command_steps
    WHERE intent = 'WAIT'
      AND wait_after_ms = 0
      AND parameters_json ? 'duration_ms'
)
UPDATE custom_command_steps steps
   SET wait_after_ms = parsed.duration_digits::integer
  FROM parsed
 WHERE steps.id = parsed.id
   AND parsed.duration_digits IS NOT NULL;

COMMIT;
