
import time
from threading import RLock
from typing import Dict, Optional

from V3.services.app_catalog.constants import APP_CATALOG_TTL_SECONDS, MAX_APP_CATALOG_SESSIONS
from V3.services.app_catalog.text_utils import _has_text


_catalogs: Dict[str, Dict[str, object]] = {}
_catalog_lock = RLock()


def _save_catalog(session_id: str, catalog: Dict[str, object]) -> None:
    with _catalog_lock:
        _cleanup_expired_catalogs_locked()
        _catalogs[session_id] = catalog
        _prune_oldest_catalogs_locked()


def _get_catalog(session_id: Optional[str]) -> Optional[Dict[str, object]]:
    if not _has_text(session_id):
        return None

    with _catalog_lock:
        _cleanup_expired_catalogs_locked()
        catalog = _catalogs.get(str(session_id))
        if catalog:
            catalog["last_seen"] = time.monotonic()

        return catalog


def _delete_catalog(session_id: Optional[str]) -> bool:
    if not _has_text(session_id):
        return False

    with _catalog_lock:
        return _catalogs.pop(str(session_id), None) is not None


def _catalog_count() -> int:
    with _catalog_lock:
        _cleanup_expired_catalogs_locked()
        return len(_catalogs)


def _cleanup_expired_catalogs() -> None:
    with _catalog_lock:
        _cleanup_expired_catalogs_locked()


def _cleanup_expired_catalogs_locked() -> None:
    if not _catalogs:
        return

    now = time.monotonic()
    expired_session_ids = [
        session_id
        for session_id, catalog in _catalogs.items()
        if now - float(catalog.get("last_seen", catalog.get("created_at", now))) > APP_CATALOG_TTL_SECONDS
    ]

    for session_id in expired_session_ids:
        _catalogs.pop(session_id, None)


def _prune_oldest_catalogs() -> None:
    with _catalog_lock:
        _prune_oldest_catalogs_locked()


def _prune_oldest_catalogs_locked() -> None:
    overflow = len(_catalogs) - MAX_APP_CATALOG_SESSIONS
    if overflow <= 0:
        return

    oldest_session_ids = sorted(
        _catalogs,
        key=lambda session_id: float(_catalogs[session_id].get("last_seen", 0.0)),
    )

    for session_id in oldest_session_ids[:overflow]:
        _catalogs.pop(session_id, None)
