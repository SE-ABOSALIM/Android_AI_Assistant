import asyncio
from typing import Dict, Optional

from V3.app_catalog.text_normalization import _has_text


def _save_catalog(session_id: str, catalog: Dict[str, object]) -> None:
    # Catalog persistence is handled by V3.database.app_catalog_repository.
    # This function remains only to keep older imports stable.
    return None


def _get_catalog(session_id: Optional[str]) -> Optional[Dict[str, object]]:
    if not _has_text(session_id):
        return None

    session_key = str(session_id).strip()
    return _run_database(lambda: _load_catalog_snapshot(session_key))


def _delete_catalog(session_id: Optional[str]) -> bool:
    if not _has_text(session_id):
        return False

    session_key = str(session_id).strip()
    return bool(_run_database(lambda: _delete_catalog_snapshot(session_key)))


def _catalog_count() -> int:
    from V3.database.app_catalog_repository import count_app_catalog_snapshots

    return int(_run_database(count_app_catalog_snapshots) or 0)


def _cleanup_expired_catalogs() -> None:
    return None


def _prune_oldest_catalogs() -> None:
    return None


def _run_database(operation):
    try:
        asyncio.get_running_loop()
    except RuntimeError:
        return asyncio.run(operation())

    print(
        "[database] app catalog database access was requested from an active event loop",
        flush=True,
    )
    return None


async def _load_catalog_snapshot(session_id: str) -> Optional[Dict[str, object]]:
    from V3.cache.app_catalog_cache import (
        get_cached_app_catalog_snapshot,
        set_cached_app_catalog_snapshot,
    )
    from V3.database.app_catalog_repository import load_app_catalog_snapshot

    cached_catalog = await get_cached_app_catalog_snapshot(session_id)
    if cached_catalog:
        return cached_catalog

    catalog = await load_app_catalog_snapshot(session_id)
    if catalog:
        await set_cached_app_catalog_snapshot(session_id, catalog)

    return catalog


async def _delete_catalog_snapshot(session_id: str) -> bool:
    from V3.cache.app_catalog_cache import delete_cached_app_catalog_snapshot
    from V3.database.app_catalog_repository import delete_app_catalog_snapshot

    await delete_cached_app_catalog_snapshot(session_id)
    return await delete_app_catalog_snapshot(session_id)
