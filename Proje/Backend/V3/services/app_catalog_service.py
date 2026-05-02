import re
import time
import unicodedata
from dataclasses import dataclass
from typing import Dict, Iterable, List, Optional

APP_CATALOG_TTL_SECONDS = 2 * 60 * 60
MAX_APP_CATALOG_SESSIONS = 64
AMBIGUOUS_SCORE_MARGIN = 0.03
MAX_AMBIGUOUS_MATCHES = 5


@dataclass(frozen=True)
class AppCatalogEntryRecord:
    label: str
    package_name: str
    aliases: List[str]


@dataclass(frozen=True)
class AppMatch:
    label: str
    package_name: str
    score: float


@dataclass(frozen=True)
class AppMatchResolution:
    match: Optional[AppMatch]
    ambiguous_matches: List[AppMatch]

    @property
    def is_ambiguous(self) -> bool:
        return bool(self.ambiguous_matches)


_catalogs: Dict[str, Dict[str, object]] = {}


def save_app_catalog(session_id: str, catalog_version: Optional[str], apps: Iterable[object]) -> Dict[str, object]:
    _cleanup_expired_catalogs()

    entries: List[AppCatalogEntryRecord] = []

    for app in apps:
        label = _get_value(app, "label")
        package_name = _get_value(app, "package_name")
        aliases = _get_value(app, "aliases") or []

        if not _has_text(label) or not _has_text(package_name):
            continue

        entries.append(AppCatalogEntryRecord(
            label=str(label).strip(),
            package_name=str(package_name).strip(),
            aliases=[str(alias).strip() for alias in aliases if _has_text(alias)],
        ))

    version = catalog_version or _build_catalog_version(entries)
    now = time.monotonic()
    _catalogs[session_id] = {
        "catalog_version": version,
        "apps": entries,
        "created_at": now,
        "last_seen": now,
    }
    _prune_oldest_catalogs()

    return {
        "session_id": session_id,
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
    if not _has_text(session_id):
        return False

    return _catalogs.pop(str(session_id), None) is not None


def catalog_count() -> int:
    _cleanup_expired_catalogs()
    return len(_catalogs)


def resolve_app_match(session_id: Optional[str], candidate: str) -> AppMatchResolution:
    if not _has_text(session_id) or not _has_text(candidate):
        return AppMatchResolution(None, [])

    catalog = _get_catalog(session_id)
    if not catalog:
        return AppMatchResolution(None, [])

    candidate_normalized = _normalize_words(candidate)
    candidate_compact = candidate_normalized.replace(" ", "")
    if not candidate_compact:
        return AppMatchResolution(None, [])

    threshold = _minimum_score(candidate_compact)
    matches: List[AppMatch] = []

    for app in catalog["apps"]:
        score = _score_candidate(candidate_normalized, candidate_compact, app)
        if score < threshold:
            continue
        matches.append(AppMatch(app.label, app.package_name, score))

    if not matches:
        return AppMatchResolution(None, [])

    matches = _dedupe_matches(matches)
    matches.sort(key=lambda match: (-match.score, match.label.casefold(), match.package_name))

    best_match = matches[0]
    ambiguous_matches = [
        match
        for match in matches
        if best_match.score - match.score <= AMBIGUOUS_SCORE_MARGIN
    ]

    if len(ambiguous_matches) > 1:
        return AppMatchResolution(None, ambiguous_matches[:MAX_AMBIGUOUS_MATCHES])

    return AppMatchResolution(best_match, [])


def find_app_match(session_id: Optional[str], candidate: str) -> Optional[AppMatch]:
    return resolve_app_match(session_id, candidate).match


def _get_catalog(session_id: Optional[str]) -> Optional[Dict[str, object]]:
    _cleanup_expired_catalogs()
    if not _has_text(session_id):
        return None

    catalog = _catalogs.get(str(session_id))
    if catalog:
        catalog["last_seen"] = time.monotonic()

    return catalog


def _cleanup_expired_catalogs() -> None:
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
    overflow = len(_catalogs) - MAX_APP_CATALOG_SESSIONS
    if overflow <= 0:
        return

    oldest_session_ids = sorted(
        _catalogs,
        key=lambda session_id: float(_catalogs[session_id].get("last_seen", 0.0)),
    )

    for session_id in oldest_session_ids[:overflow]:
        _catalogs.pop(session_id, None)


def _dedupe_matches(matches: List[AppMatch]) -> List[AppMatch]:
    by_package_name: Dict[str, AppMatch] = {}

    for match in matches:
        existing = by_package_name.get(match.package_name)
        if existing is None or match.score > existing.score:
            by_package_name[match.package_name] = match

    return list(by_package_name.values())


def _score_candidate(candidate_normalized: str, candidate_compact: str, app: AppCatalogEntryRecord) -> float:
    score = 0.0

    package_compact = _normalize_words(app.package_name).replace(" ", "")
    if candidate_compact == package_compact:
        score = max(score, 1.0)

    for raw_alias in [app.label, *app.aliases]:
        alias_normalized = _normalize_words(raw_alias)
        alias_compact = alias_normalized.replace(" ", "")
        if not alias_compact:
            continue

        if candidate_compact == alias_compact:
            score = max(score, 1.0)

        if candidate_normalized and candidate_normalized in _tokens(alias_normalized):
            score = max(score, 0.96)

        if alias_compact.startswith(candidate_compact) or candidate_compact.startswith(alias_compact):
            score = max(score, 0.90 - _length_penalty(candidate_compact, alias_compact))

        score = max(score, _levenshtein_similarity(candidate_compact, alias_compact))
        score = max(score, _token_overlap(candidate_normalized, alias_normalized) * 0.92)

    return score


def _get_value(obj: object, key: str):
    if isinstance(obj, dict):
        return obj.get(key)
    return getattr(obj, key, None)


def _build_catalog_version(entries: List[AppCatalogEntryRecord]) -> str:
    parts = sorted(f"{entry.package_name}:{entry.label}" for entry in entries)
    return f"{len(entries)}-{abs(hash(tuple(parts))):x}"


def _normalize_words(value: str) -> str:
    text = str(value).casefold().replace("\u0131", "i").replace("\u0130", "i")
    text = unicodedata.normalize("NFKD", text)
    text = "".join(ch for ch in text if not unicodedata.combining(ch))
    text = re.sub(r"[^\w\s]", " ", text, flags=re.UNICODE)
    text = re.sub(r"_+", " ", text)
    return re.sub(r"\s+", " ", text).strip()


def _tokens(value: str) -> set:
    return {token for token in _normalize_words(value).split(" ") if token}


def _token_overlap(left: str, right: str) -> float:
    left_tokens = _tokens(left)
    right_tokens = _tokens(right)
    if not left_tokens or not right_tokens:
        return 0.0

    overlap = len(left_tokens.intersection(right_tokens))
    return (2.0 * overlap) / (len(left_tokens) + len(right_tokens))


def _minimum_score(candidate_compact: str) -> float:
    length = len(candidate_compact)
    if length <= 4:
        return 0.96
    if length <= 7:
        return 0.86
    return 0.74


def _length_penalty(left: str, right: str) -> float:
    return min(0.12, abs(len(left) - len(right)) * 0.01)


def _levenshtein_similarity(left: str, right: str) -> float:
    if not left or not right:
        return 0.0
    if left == right:
        return 1.0

    distance = _levenshtein_distance(left, right)
    return 1.0 - (distance / max(len(left), len(right)))


def _levenshtein_distance(left: str, right: str) -> int:
    previous = list(range(len(right) + 1))
    current = [0] * (len(right) + 1)

    for i, left_char in enumerate(left, start=1):
        current[0] = i
        for j, right_char in enumerate(right, start=1):
            cost = 0 if left_char == right_char else 1
            current[j] = min(
                current[j - 1] + 1,
                previous[j] + 1,
                previous[j - 1] + cost,
            )
        previous, current = current, previous

    return previous[len(right)]


def _has_text(value: object) -> bool:
    return value is not None and str(value).strip() != ""
