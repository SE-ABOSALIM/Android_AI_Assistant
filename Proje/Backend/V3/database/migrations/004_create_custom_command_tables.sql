-- Custom command workflow schema.
-- Apply with:
--   psql "$PSQL_DATABASE_URL" -f V3/database/migrations/004_create_custom_command_tables.sql

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS custom_commands (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    normalized_name TEXT NOT NULL,
    language TEXT NOT NULL DEFAULT 'TR',
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT custom_commands_name_not_blank CHECK (btrim(name) <> ''),
    UNIQUE (device_id, language, normalized_name)
);

CREATE TABLE IF NOT EXISTS custom_command_steps (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    custom_command_id UUID NOT NULL REFERENCES custom_commands(id) ON DELETE CASCADE,
    step_order INTEGER NOT NULL,
    intent TEXT NOT NULL,
    parameters_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    wait_after_ms INTEGER NOT NULL DEFAULT 0,
    stop_on_failure BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT custom_command_steps_order_positive CHECK (step_order > 0),
    CONSTRAINT custom_command_steps_wait_non_negative CHECK (wait_after_ms >= 0),
    CONSTRAINT custom_command_steps_intent_not_blank CHECK (btrim(intent) <> ''),
    UNIQUE (custom_command_id, step_order)
);

CREATE INDEX IF NOT EXISTS idx_custom_commands_device_language_name
    ON custom_commands(device_id, language, normalized_name);

CREATE INDEX IF NOT EXISTS idx_custom_commands_device_updated
    ON custom_commands(device_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_custom_command_steps_command_order
    ON custom_command_steps(custom_command_id, step_order);

CREATE INDEX IF NOT EXISTS idx_custom_command_steps_parameters_gin
    ON custom_command_steps USING GIN (parameters_json);

COMMIT;
