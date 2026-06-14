-- Readable custom command step view.
-- Apply with:
--   psql "$PSQL_DATABASE_URL" -f V3/database/migrations/006_create_custom_command_step_details_view.sql
--
-- PostgreSQL tables do not have a natural row order. Use this view when
-- inspecting workflow steps so rows are grouped by command and sorted by the
-- explicit step_order saved by the backend.

BEGIN;

CREATE OR REPLACE VIEW custom_command_step_details AS
SELECT
    d.device_id,
    c.id AS custom_command_id,
    c.name AS custom_command_name,
    c.language,
    s.step_order,
    s.intent,
    s.parameters_json,
    s.wait_after_ms,
    s.stop_on_failure,
    s.created_at
FROM custom_command_steps s
JOIN custom_commands c ON c.id = s.custom_command_id
JOIN devices d ON d.id = c.device_ref_id
ORDER BY
    d.device_id,
    c.updated_at DESC,
    c.id,
    s.step_order ASC;

COMMIT;
