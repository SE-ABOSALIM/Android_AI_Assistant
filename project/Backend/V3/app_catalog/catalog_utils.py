from typing import Dict, List

from V3.app_catalog.models import AppCatalogEntryRecord, AppMatch


def _dedupe_matches(matches: List[AppMatch]) -> List[AppMatch]:
    by_package_name: Dict[str, AppMatch] = {}

    for match in matches:
        existing = by_package_name.get(match.package_name)
        if existing is None or match.score > existing.score:
            by_package_name[match.package_name] = match

    return list(by_package_name.values())

def _get_value(obj: object, key: str):
    if isinstance(obj, dict):
        return obj.get(key)
    return getattr(obj, key, None)

def _build_catalog_version(entries: List[AppCatalogEntryRecord]) -> str:
    parts = sorted(f"{entry.package_name}:{entry.label}" for entry in entries)
    return f"{len(entries)}-{abs(hash(tuple(parts))):x}"
