import time
from typing import Dict, Iterable, List, Optional

from V3.app_catalog.indexer import _build_catalog_search_index
from V3.app_catalog.models import AppCatalogEntryRecord
from V3.app_catalog.text_normalization import _has_text
from V3.app_catalog.store import (
    _catalog_count,
    _delete_catalog,
    _get_catalog,
    _save_catalog
)
from V3.app_catalog.catalog_utils import (
    _build_catalog_version,
    _get_value
)
from V3.app_catalog.arabic_aliases import (
    _build_match_aliases,
    _is_arabic_language
)


def save_app_catalog(
    session_id: str,
    catalog_version: Optional[str],
    apps: Iterable[object],
    language: Optional[str] = None,
) -> Dict[str, object]:
    entries: List[AppCatalogEntryRecord] = []
    include_arabic_phonetic_aliases = _is_arabic_language(language)

    for app in apps:
        label = _get_value(app, "label")
        package_name = _get_value(app, "package_name")
        aliases = _get_value(app, "aliases") or []

        if not _has_text(label) or not _has_text(package_name):
            continue

        label_text = str(label).strip()
        package_text = str(package_name).strip()
        alias_texts = [str(alias).strip() for alias in aliases if _has_text(alias)]

        entries.append(AppCatalogEntryRecord(
            label=label_text,
            package_name=package_text,
            aliases=alias_texts,
            match_aliases=_build_match_aliases(
                label_text,
                package_text,
                alias_texts,
                include_arabic_phonetic_aliases=include_arabic_phonetic_aliases,
            ),
        ))

    version = catalog_version or _build_catalog_version(entries)
    now = time.monotonic()
    session_key = str(session_id)
    catalog = {
        "catalog_version": version,
        "language": str(language).strip().upper() if _has_text(language) else None,
        "apps": entries,
        "search_index": _build_catalog_search_index(entries),
        "created_at": now,
        "last_seen": now,
    }

    _save_catalog(session_key, catalog)

    return {
        "session_id": session_key,
        "catalog_version": version,
        "app_count": len(entries),
    }


def has_app_catalog(session_id: Optional[str]) -> bool:
    return _get_catalog(session_id) is not None


def is_catalog_version_current(session_id: Optional[str], catalog_version: Optional[str]) -> bool:
    if not _has_text(catalog_version):
        return True

    if not _has_text(session_id):
        return False

    catalog = _get_catalog(session_id)
    if not catalog:
        return False

    return catalog.get("catalog_version") == catalog_version


def delete_app_catalog(session_id: Optional[str]) -> bool:
    return _delete_catalog(session_id)


def catalog_count() -> int:
    return _catalog_count()
