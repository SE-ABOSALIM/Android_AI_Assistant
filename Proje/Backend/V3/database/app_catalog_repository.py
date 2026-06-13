from typing import Dict, Iterable, List, Optional

from V3.app_catalog.arabic_aliases import _build_match_aliases, _is_arabic_language
from V3.app_catalog.indexer import _build_catalog_search_index
from V3.app_catalog.models import AppCatalogEntryRecord
from V3.app_catalog.text_normalization import _has_text, _normalize_words
from V3.database.connection import is_database_configured, open_database_connection


async def save_app_catalog_snapshot(
    *,
    session_id: str,
    catalog_version: str,
    language: Optional[str],
    entries: Iterable[AppCatalogEntryRecord],
    device_id: Optional[str] = None,
    app_version: Optional[str] = None,
    platform: Optional[str] = None,
) -> bool:
    if not is_database_configured():
        return False

    entry_list = list(entries)
    device_key = _clean_text(device_id) or _clean_text(session_id)
    if not device_key:
        return False

    normalized_language = _clean_text(language, uppercase=True) or "TR"
    normalized_platform = _clean_text(platform) or "android"

    connection = None
    try:
        connection = await open_database_connection()
        if connection is None:
            return False

        async with connection.transaction():
            database_device_id = await _upsert_device(
                connection,
                device_key=device_key,
                platform=normalized_platform,
                app_version=_clean_text(app_version),
                language=normalized_language,
            )

            for entry in entry_list:
                app_id = await _upsert_app(connection, entry.package_name, entry.label)
                await _upsert_device_app(
                    connection,
                    device_id=database_device_id,
                    app_id=app_id,
                    entry=entry,
                    catalog_version=catalog_version,
                )

            await connection.execute(
                """
                DELETE FROM device_apps
                 WHERE device_id = $1
                   AND last_seen_catalog_version IS DISTINCT FROM $2
                """,
                database_device_id,
                catalog_version,
            )

        return True
    except Exception as exc:
        print(
            f"[database] failed to persist app catalog | session_id={session_id} | error={exc}",
            flush=True,
        )
        return False
    finally:
        if connection is not None:
            await connection.close()


async def load_app_catalog_snapshot(session_id: Optional[str]) -> Optional[Dict[str, object]]:
    if not _has_text(session_id) or not is_database_configured():
        return None

    session_key = str(session_id).strip()
    connection = None
    try:
        connection = await open_database_connection()
        if connection is None:
            return None

        device = await connection.fetchrow(
            """
            SELECT
                d.id,
                d.device_id,
                COALESCE(latest_apps.catalog_version, '') AS catalog_version,
                COALESCE(d.preferred_language, 'TR') AS language
            FROM devices d
            LEFT JOIN LATERAL (
                SELECT last_seen_catalog_version AS catalog_version
                  FROM device_apps
                 WHERE device_id = d.id
                 ORDER BY updated_at DESC
                 LIMIT 1
            ) AS latest_apps ON true
            WHERE d.device_id = $1
            """,
            session_key,
        )
        if device is None:
            return None

        language = str(device["language"] or "TR").strip().upper()
        rows = await connection.fetch(
            """
            SELECT
                COALESCE(da.display_name, apps.display_name, apps.package_name) AS display_name,
                apps.package_name
            FROM device_apps da
            JOIN apps ON apps.id = da.app_id
            WHERE da.device_id = $1
            ORDER BY da.display_name, apps.package_name
            """,
            device["id"],
        )

        entries = _entry_records_from_rows(rows, language)
        if not entries:
            return None

        return {
            "catalog_version": device["catalog_version"],
            "language": language,
            "apps": entries,
            "search_index": _build_catalog_search_index(entries),
        }
    except Exception as exc:
        print(
            f"[database] failed to load app catalog | session_id={session_id} | error={exc}",
            flush=True,
        )
        return None
    finally:
        if connection is not None:
            await connection.close()


async def delete_app_catalog_snapshot(session_id: Optional[str]) -> bool:
    if not _has_text(session_id) or not is_database_configured():
        return False

    connection = None
    try:
        connection = await open_database_connection()
        if connection is None:
            return False

        result = await connection.execute(
            """
            DELETE FROM devices
             WHERE device_id = $1
            """,
            str(session_id).strip(),
        )
        return not result.endswith(" 0")
    except Exception as exc:
        print(
            f"[database] failed to delete app catalog | session_id={session_id} | error={exc}",
            flush=True,
        )
        return False
    finally:
        if connection is not None:
            await connection.close()


async def count_app_catalog_snapshots() -> int:
    if not is_database_configured():
        return 0

    connection = None
    try:
        connection = await open_database_connection()
        if connection is None:
            return 0

        return int(await connection.fetchval("SELECT COUNT(*) FROM devices"))
    except Exception as exc:
        print(f"[database] failed to count app catalogs | error={exc}", flush=True)
        return 0
    finally:
        if connection is not None:
            await connection.close()


async def _upsert_device(connection, *, device_key: str, platform: str, app_version: Optional[str], language: str):
    return await connection.fetchval(
        """
        INSERT INTO devices (
            device_id,
            platform,
            app_version,
            preferred_language,
            last_seen_at
        )
        VALUES ($1, $2, $3, $4, now())
        ON CONFLICT (device_id)
        DO UPDATE SET
            platform = EXCLUDED.platform,
            app_version = COALESCE(EXCLUDED.app_version, devices.app_version),
            preferred_language = EXCLUDED.preferred_language,
            last_seen_at = now()
        RETURNING id
        """,
        device_key,
        platform,
        app_version,
        language,
    )


async def _upsert_app(connection, package_name: str, display_name: str):
    return await connection.fetchval(
        """
        INSERT INTO apps (package_name, display_name)
        VALUES ($1, $2)
        ON CONFLICT (package_name)
        DO UPDATE SET display_name = EXCLUDED.display_name
        RETURNING id
        """,
        package_name,
        display_name,
    )


async def _upsert_device_app(connection, *, device_id, app_id, entry: AppCatalogEntryRecord, catalog_version: str) -> None:
    await connection.execute(
        """
        INSERT INTO device_apps (
            device_id,
            app_id,
            display_name,
            normalized_name,
            last_seen_catalog_version,
            updated_at
        )
        VALUES ($1, $2, $3, $4, $5, now())
        ON CONFLICT (device_id, app_id)
        DO UPDATE SET
            display_name = EXCLUDED.display_name,
            normalized_name = EXCLUDED.normalized_name,
            last_seen_catalog_version = EXCLUDED.last_seen_catalog_version,
            updated_at = now()
        """,
        device_id,
        app_id,
        entry.label,
        _normalize_words(entry.label),
        catalog_version,
    )


def _clean_text(value: Optional[str], *, uppercase: bool = False) -> Optional[str]:
    if not _has_text(value):
        return None

    text = str(value).strip()
    return text.upper() if uppercase else text


def _entry_records_from_rows(rows, language: str) -> List[AppCatalogEntryRecord]:
    entries: List[AppCatalogEntryRecord] = []
    include_arabic_phonetic_aliases = _is_arabic_language(language)

    for row in rows:
        label = str(row["display_name"]).strip()
        package_name = str(row["package_name"]).strip()
        aliases: List[str] = []
        entries.append(
            AppCatalogEntryRecord(
                label=label,
                package_name=package_name,
                aliases=aliases,
                match_aliases=_build_match_aliases(
                    label,
                    package_name,
                    aliases,
                    include_arabic_phonetic_aliases=include_arabic_phonetic_aliases,
                ),
            )
        )

    return entries
