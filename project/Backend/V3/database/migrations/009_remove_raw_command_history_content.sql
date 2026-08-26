-- Remove transcript-like content from command history and keep operational metadata only.
-- Dropping these columns permanently removes existing raw command text and parameters.
-- Apply with:
--   psql "$PSQL_DATABASE_URL" -f V3/database/migrations/009_remove_raw_command_history_content.sql

BEGIN;

DROP INDEX IF EXISTS idx_command_history_session_created;
DROP INDEX IF EXISTS idx_command_history_parameters_gin;

ALTER TABLE command_history
    DROP COLUMN IF EXISTS text,
    DROP COLUMN IF EXISTS parameters_json,
    DROP COLUMN IF EXISTS session_id,
    DROP COLUMN IF EXISTS result_status;

ALTER TABLE command_history
    DROP CONSTRAINT IF EXISTS command_history_intent_format,
    DROP CONSTRAINT IF EXISTS command_history_language_format,
    DROP CONSTRAINT IF EXISTS command_history_error_code_format,
    DROP CONSTRAINT IF EXISTS command_history_processing_time_non_negative;

UPDATE command_history
   SET intent = NULL
 WHERE intent IS NOT NULL
   AND intent !~ '^[A-Z][A-Z0-9_]{0,63}$';

UPDATE command_history
   SET language = 'UND'
 WHERE language NOT IN ('AR', 'EN', 'TR', 'UND');

UPDATE command_history
   SET error_code = NULL
 WHERE error_code IS NOT NULL
   AND error_code NOT IN (
       'APP_CATALOG_MISSING',
       'APP_CATALOG_STALE',
       'APP_MATCH_AMBIGUOUS',
       'APP_NOT_IN_CATALOG',
       'BARE_ALARM_TIME',
       'CUSTOM_COMMAND_NOT_FOUND',
       'LOW_CONFIDENCE',
       'MISSING_ALARM_SIGNAL',
       'MISSING_REQUIRED_SLOT',
       'MODEL_STOP_LISTENING_DISABLED',
       'STOP_LISTENING_TOO_SHORT',
       'UNKNOWN_COMMAND',
       'UNSUPPORTED_INTENT',
       'UNSUPPORTED_STOPWATCH',
       'WEAK_STOP_LISTENING_COMMAND'
   );

UPDATE command_history
   SET processing_time_ms = NULL
 WHERE processing_time_ms < 0;

ALTER TABLE command_history
    ADD CONSTRAINT command_history_intent_format
        CHECK (intent IS NULL OR intent ~ '^[A-Z][A-Z0-9_]{0,63}$'),
    ADD CONSTRAINT command_history_language_format
        CHECK (language IN ('AR', 'EN', 'TR', 'UND')),
    ADD CONSTRAINT command_history_error_code_format
        CHECK (
            error_code IS NULL
            OR error_code IN (
                'APP_CATALOG_MISSING',
                'APP_CATALOG_STALE',
                'APP_MATCH_AMBIGUOUS',
                'APP_NOT_IN_CATALOG',
                'BARE_ALARM_TIME',
                'CUSTOM_COMMAND_NOT_FOUND',
                'LOW_CONFIDENCE',
                'MISSING_ALARM_SIGNAL',
                'MISSING_REQUIRED_SLOT',
                'MODEL_STOP_LISTENING_DISABLED',
                'STOP_LISTENING_TOO_SHORT',
                'UNKNOWN_COMMAND',
                'UNSUPPORTED_INTENT',
                'UNSUPPORTED_STOPWATCH',
                'WEAK_STOP_LISTENING_COMMAND'
            )
        ),
    ADD CONSTRAINT command_history_processing_time_non_negative
        CHECK (processing_time_ms IS NULL OR processing_time_ms >= 0);

COMMIT;
