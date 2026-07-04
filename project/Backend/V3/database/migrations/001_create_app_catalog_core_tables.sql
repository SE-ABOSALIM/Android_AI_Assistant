-- Android AI Assistant backend persistence schema.
-- Apply with:
--   psql "$PSQL_DATABASE_URL" -f V3/database/migrations/001_create_app_catalog_core_tables.sql

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id TEXT NOT NULL UNIQUE,
    platform TEXT NOT NULL DEFAULT 'android',
    app_version TEXT,
    preferred_language TEXT NOT NULL DEFAULT 'TR',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS app_catalog_syncs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    session_id TEXT,
    catalog_version TEXT NOT NULL,
    catalog_hash TEXT,
    language TEXT NOT NULL DEFAULT 'TR',
    app_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT app_catalog_syncs_app_count_non_negative CHECK (app_count >= 0)
);

CREATE TABLE IF NOT EXISTS apps (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    package_name TEXT NOT NULL UNIQUE,
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS device_apps (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    app_id UUID NOT NULL REFERENCES apps(id) ON DELETE CASCADE,
    display_name TEXT NOT NULL,
    normalized_name TEXT NOT NULL,
    is_launchable BOOLEAN NOT NULL DEFAULT true,
    last_seen_catalog_version TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (device_id, app_id)
);

CREATE TABLE IF NOT EXISTS app_aliases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    app_id UUID NOT NULL REFERENCES apps(id) ON DELETE CASCADE,
    device_id UUID REFERENCES devices(id) ON DELETE CASCADE,
    language TEXT NOT NULL DEFAULT 'und',
    alias TEXT NOT NULL,
    normalized_alias TEXT NOT NULL,
    source TEXT NOT NULL DEFAULT 'catalog',
    confidence NUMERIC(5, 4) NOT NULL DEFAULT 1.0000,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT app_aliases_confidence_range CHECK (confidence >= 0 AND confidence <= 1),
    UNIQUE (app_id, device_id, language, normalized_alias, source)
);

CREATE TABLE IF NOT EXISTS command_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID REFERENCES devices(id) ON DELETE SET NULL,
    session_id TEXT,
    text TEXT NOT NULL,
    language TEXT NOT NULL,
    intent TEXT,
    parameters_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    accepted BOOLEAN NOT NULL DEFAULT false,
    confidence NUMERIC(5, 4),
    result_status TEXT,
    error_code TEXT,
    processing_time_ms NUMERIC(10, 2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT command_history_confidence_range CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1))
);

CREATE TABLE IF NOT EXISTS failed_app_open_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID REFERENCES devices(id) ON DELETE SET NULL,
    command_history_id UUID REFERENCES command_history(id) ON DELETE SET NULL,
    session_id TEXT,
    raw_text TEXT NOT NULL,
    extracted_app_name TEXT,
    normalized_app_name TEXT,
    language TEXT NOT NULL,
    candidate_count INTEGER NOT NULL DEFAULT 0,
    selected_package_name TEXT,
    status TEXT NOT NULL DEFAULT 'failed',
    context_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT failed_app_open_attempts_candidate_count_non_negative CHECK (candidate_count >= 0)
);

CREATE TABLE IF NOT EXISTS error_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID REFERENCES devices(id) ON DELETE SET NULL,
    command_history_id UUID REFERENCES command_history(id) ON DELETE SET NULL,
    failed_app_open_attempt_id UUID REFERENCES failed_app_open_attempts(id) ON DELETE SET NULL,
    error_code TEXT NOT NULL,
    message TEXT NOT NULL,
    severity TEXT NOT NULL DEFAULT 'error',
    source TEXT NOT NULL DEFAULT 'backend',
    context_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_devices_device_id ON devices(device_id);
CREATE INDEX IF NOT EXISTS idx_devices_last_seen_at ON devices(last_seen_at DESC);

CREATE INDEX IF NOT EXISTS idx_app_catalog_syncs_device_created
    ON app_catalog_syncs(device_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_app_catalog_syncs_session
    ON app_catalog_syncs(session_id);

CREATE INDEX IF NOT EXISTS idx_apps_package_name ON apps(package_name);

CREATE INDEX IF NOT EXISTS idx_device_apps_device_normalized
    ON device_apps(device_id, normalized_name);
CREATE INDEX IF NOT EXISTS idx_device_apps_device_launchable
    ON device_apps(device_id, is_launchable);

CREATE INDEX IF NOT EXISTS idx_app_aliases_device_language_normalized
    ON app_aliases(device_id, language, normalized_alias);
CREATE INDEX IF NOT EXISTS idx_app_aliases_global_language_normalized
    ON app_aliases(language, normalized_alias)
    WHERE device_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_app_aliases_app_source
    ON app_aliases(app_id, source);

CREATE INDEX IF NOT EXISTS idx_command_history_device_created
    ON command_history(device_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_command_history_session_created
    ON command_history(session_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_command_history_intent_created
    ON command_history(intent, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_command_history_parameters_gin
    ON command_history USING GIN (parameters_json);

CREATE INDEX IF NOT EXISTS idx_failed_app_open_attempts_device_created
    ON failed_app_open_attempts(device_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_failed_app_open_attempts_status_created
    ON failed_app_open_attempts(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_failed_app_open_attempts_normalized
    ON failed_app_open_attempts(normalized_app_name);
CREATE INDEX IF NOT EXISTS idx_failed_app_open_attempts_context_gin
    ON failed_app_open_attempts USING GIN (context_json);

CREATE INDEX IF NOT EXISTS idx_error_messages_device_created
    ON error_messages(device_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_error_messages_code_created
    ON error_messages(error_code, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_error_messages_source_created
    ON error_messages(source, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_error_messages_context_gin
    ON error_messages USING GIN (context_json);

COMMIT;
