import re
import unicodedata
from dataclasses import dataclass
from typing import Dict, Iterable, List, Optional


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


_catalogs: Dict[str, Dict[str, object]] = {}


def save_app_catalog(session_id: str, catalog_version: Optional[str], apps: Iterable[object]) -> Dict[str, object]:
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
    _catalogs[session_id] = {
        "catalog_version": version,
        "apps": entries,
    }

    return {
        "session_id": session_id,
        "catalog_version": version,
        "app_count": len(entries),
    }


def has_app_catalog(session_id: Optional[str]) -> bool:
    return _has_text(session_id) and session_id in _catalogs


def is_catalog_version_current(session_id: Optional[str], catalog_version: Optional[str]) -> bool:
    if not _has_text(catalog_version):
        return True

    if not _has_text(session_id):
        return False

    catalog = _catalogs.get(session_id)
    if not catalog:
        return False

    return catalog.get("catalog_version") == catalog_version


def find_app_match(session_id: Optional[str], candidate: str) -> Optional[AppMatch]:
    if not _has_text(session_id) or not _has_text(candidate):
        return None

    catalog = _catalogs.get(session_id)
    if not catalog:
        return None

    candidate_normalized = _normalize_words(candidate)
    candidate_compact = candidate_normalized.replace(" ", "")
    if not candidate_compact:
        return None

    best_match: Optional[AppMatch] = None
    threshold = _minimum_score(candidate_compact)

    for app in catalog["apps"]:
        score = _score_candidate(candidate_normalized, candidate_compact, app)
        if score < threshold:
            continue
        if best_match is None or score > best_match.score:
            best_match = AppMatch(app.label, app.package_name, score)

    return best_match


def _score_candidate(candidate_normalized: str, candidate_compact: str, app: AppCatalogEntryRecord) -> float:
    score = 0.0

    for raw_alias in [app.label, app.package_name, *app.aliases]:
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
