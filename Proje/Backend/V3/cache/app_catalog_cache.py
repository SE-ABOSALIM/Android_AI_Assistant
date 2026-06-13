import json
from typing import Dict, List, Optional

from V3.app_catalog.arabic_aliases import _build_match_aliases, _is_arabic_language
from V3.app_catalog.constants import APP_CATALOG_TTL_SECONDS
from V3.app_catalog.indexer import _build_catalog_search_index
from V3.app_catalog.models import AppCatalogEntryRecord
from V3.app_catalog.text_normalization import _has_text
from V3.cache.connection import close_redis_client, open_redis_client


_CACHE_ERROR_LOGGED = False


async def get_cached_app_catalog_snapshot(session_id: Optional[str]) -> Optional[Dict[str, object]]:
    if not _has_text(session_id):
        return None

    client = None
    try:
        client = await open_redis_client()
        if client is None:
            return None

        raw_payload = await client.get(_catalog_key(str(session_id).strip()))
        if not raw_payload:
            return None

        return _catalog_from_payload(json.loads(raw_payload))
    except Exception as exc:
        _log_cache_error_once(f"failed to read app catalog cache | session_id={session_id} | error={exc}")
        return None
    finally:
        await close_redis_client(client)


async def set_cached_app_catalog_snapshot(
    session_id: Optional[str],
    catalog: Dict[str, object],
    ttl_seconds: int = APP_CATALOG_TTL_SECONDS,
) -> bool:
    if not _has_text(session_id) or not catalog:
        return False

    client = None
    try:
        client = await open_redis_client()
        if client is None:
            return False

        await client.set(
            _catalog_key(str(session_id).strip()),
            json.dumps(_payload_from_catalog(catalog), ensure_ascii=False, separators=(",", ":")),
            ex=ttl_seconds,
        )
        return True
    except Exception as exc:
        _log_cache_error_once(f"failed to write app catalog cache | session_id={session_id} | error={exc}")
        return False
    finally:
        await close_redis_client(client)


async def delete_cached_app_catalog_snapshot(session_id: Optional[str]) -> bool:
    if not _has_text(session_id):
        return False

    client = None
    try:
        client = await open_redis_client()
        if client is None:
            return False

        deleted_count = await client.delete(_catalog_key(str(session_id).strip()))
        return bool(deleted_count)
    except Exception as exc:
        _log_cache_error_once(f"failed to delete app catalog cache | session_id={session_id} | error={exc}")
        return False
    finally:
        await close_redis_client(client)


def _catalog_key(session_id: str) -> str:
    return f"app_catalog:{session_id}"


def _payload_from_catalog(catalog: Dict[str, object]) -> Dict[str, object]:
    entries = catalog.get("apps") or []
    return {
        "catalog_version": catalog.get("catalog_version") or "",
        "language": catalog.get("language"),
        "apps": [
            {
                "label": entry.label,
                "package_name": entry.package_name,
                "aliases": entry.aliases,
                "match_aliases": entry.match_aliases,
            }
            for entry in entries
            if isinstance(entry, AppCatalogEntryRecord)
        ],
    }


def _catalog_from_payload(payload: Dict[str, object]) -> Optional[Dict[str, object]]:
    language = str(payload.get("language") or "TR").strip().upper()
    entries = _entries_from_payload(payload.get("apps"), language)
    if not entries:
        return None

    return {
        "catalog_version": payload.get("catalog_version") or "",
        "language": language,
        "apps": entries,
        "search_index": _build_catalog_search_index(entries),
    }


def _entries_from_payload(raw_entries, language: str) -> List[AppCatalogEntryRecord]:
    if not isinstance(raw_entries, list):
        return []

    include_arabic_phonetic_aliases = _is_arabic_language(language)
    entries: List[AppCatalogEntryRecord] = []

    for raw_entry in raw_entries:
        if not isinstance(raw_entry, dict):
            continue

        label = str(raw_entry.get("label") or "").strip()
        package_name = str(raw_entry.get("package_name") or "").strip()
        if not label or not package_name:
            continue

        aliases = _string_list(raw_entry.get("aliases"))
        match_aliases = _string_list(raw_entry.get("match_aliases"))
        if not match_aliases:
            match_aliases = _build_match_aliases(
                label,
                package_name,
                aliases,
                include_arabic_phonetic_aliases=include_arabic_phonetic_aliases,
            )

        entries.append(AppCatalogEntryRecord(
            label=label,
            package_name=package_name,
            aliases=aliases,
            match_aliases=match_aliases,
        ))

    return entries


def _string_list(value) -> List[str]:
    if not isinstance(value, list):
        return []

    return [str(item).strip() for item in value if _has_text(item)]


def _log_cache_error_once(message: str) -> None:
    global _CACHE_ERROR_LOGGED
    if _CACHE_ERROR_LOGGED:
        return

    _CACHE_ERROR_LOGGED = True
    print(f"[cache] {message}", flush=True)
