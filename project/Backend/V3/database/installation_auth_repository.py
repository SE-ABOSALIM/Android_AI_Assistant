import hashlib
import secrets
from dataclasses import dataclass
from typing import Optional

from V3.database.connection import is_database_configured, open_database_connection


@dataclass(frozen=True)
class InstallationRegistrationResult:
    status: str
    credential: Optional[str] = None


async def register_installation(
    *,
    device_id: str,
    platform: str,
    app_version: Optional[str],
    language: str,
) -> InstallationRegistrationResult:
    if not is_database_configured():
        return InstallationRegistrationResult(status="unavailable")

    connection = None
    try:
        connection = await open_database_connection()
        if connection is None:
            return InstallationRegistrationResult(status="unavailable")

        async with connection.transaction():
            device_ref_id = await connection.fetchval(
                """
                INSERT INTO devices (
                    device_id,
                    platform,
                    app_version,
                    preferred_language,
                    last_seen_at
                )
                VALUES ($1, $2, $3, $4, now())
                ON CONFLICT (device_id) DO NOTHING
                RETURNING id
                """,
                device_id,
                platform,
                app_version,
                language,
            )
            registration_status = "created"
            if device_ref_id is None:
                device_ref_id = await connection.fetchval(
                    """
                    SELECT id
                    FROM devices
                    WHERE device_id = $1
                    FOR UPDATE
                    """,
                    device_id,
                )
                if device_ref_id is None:
                    raise RuntimeError("Stable device row was not available after conflict")
                registration_status = "recovered"
                await connection.execute(
                    """
                    UPDATE devices
                    SET platform = $2,
                        app_version = COALESCE($3, app_version),
                        preferred_language = $4,
                        last_seen_at = now()
                    WHERE id = $1
                    """,
                    device_ref_id,
                    platform,
                    app_version,
                    language,
                )

            raw_credential = secrets.token_urlsafe(32)
            # ANDROID_ID selects the stable ownership row but is not secret or proof.
            # TODO: Bind registration/recovery to Play Integrity or equivalent app
            # attestation before treating this as strong public-production device auth.
            await connection.execute(
                """
                UPDATE device_auth_credentials
                SET revoked_at = now()
                WHERE device_ref_id = $1
                  AND revoked_at IS NULL
                """,
                device_ref_id,
            )
            await connection.execute(
                """
                INSERT INTO device_auth_credentials (
                    device_ref_id,
                    token_hash
                )
                VALUES ($1, $2)
                """,
                device_ref_id,
                hash_bearer_credential(raw_credential),
            )

        return InstallationRegistrationResult(
            status=registration_status,
            credential=raw_credential,
        )
    except Exception as exc:
        print(f"[auth] installation registration failed | error={type(exc).__name__}", flush=True)
        return InstallationRegistrationResult(status="unavailable")
    finally:
        if connection is not None:
            await connection.close()


async def find_installation_by_credential(raw_credential: str):
    if not raw_credential or not is_database_configured():
        return None

    connection = None
    try:
        connection = await open_database_connection()
        if connection is None:
            return None

        return await connection.fetchrow(
            """
            SELECT
                d.id::text AS device_ref_id,
                d.device_id
            FROM device_auth_credentials credentials
            JOIN devices d ON d.id = credentials.device_ref_id
            WHERE credentials.token_hash = $1
              AND credentials.revoked_at IS NULL
            """,
            hash_bearer_credential(raw_credential),
        )
    except Exception as exc:
        print(f"[auth] credential verification failed | error={type(exc).__name__}", flush=True)
        return None
    finally:
        if connection is not None:
            await connection.close()


def hash_bearer_credential(raw_credential: str) -> str:
    return hashlib.sha256(raw_credential.encode("utf-8")).hexdigest()
