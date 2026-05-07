from typing import List, Optional, Set, Tuple

from V3.services.app_catalog.constants import (
    AMBIGUOUS_SCORE_MARGIN,
    MAX_AMBIGUOUS_MATCHES,
    MAX_SUGGESTED_MATCHES,
    SUGGESTION_MIN_SCORE,
)
from V3.services.app_catalog.indexer import _candidate_entry_indexes
from V3.services.app_catalog.models import AppCatalogEntryRecord, AppMatch, AppMatchResolution
from V3.services.app_catalog.scoring import _score_candidate
from V3.services.app_catalog.store import _get_catalog
from V3.services.app_catalog.text_utils import (
    _fast_tokens,
    _has_text,
    _minimum_score,
    _normalize_words,
    _split_compound_words,
)
from V3.services.app_catalog.text_variants import _expand_text_variants
from V3.services.app_catalog.utils import _dedupe_matches


def resolve_app_match(session_id: Optional[str], candidate: str) -> AppMatchResolution:
    if not _has_text(session_id) or not _has_text(candidate):
        return AppMatchResolution(None, [], [])

    catalog = _get_catalog(session_id)
    if not catalog:
        return AppMatchResolution(None, [], [])

    candidate_variants = _expand_text_variants(candidate)
    if not candidate_variants:
        return AppMatchResolution(None, [], [])

    threshold = min(_minimum_score(compact) for _, compact in candidate_variants)
    matches: List[AppMatch] = []
    suggested_matches: List[AppMatch] = []

    candidate_entry_indexes = _candidate_entry_indexes(catalog, candidate_variants)
    if not candidate_entry_indexes:
        return AppMatchResolution(None, [], [])

    entries = catalog["apps"]
    for app_index in candidate_entry_indexes:
        app = entries[app_index]
        score = _score_candidate(candidate_variants, app)
        app_match = AppMatch(app.label, app.package_name, score)
        if score >= threshold:
            matches.append(app_match)
        elif score >= SUGGESTION_MIN_SCORE:
            suggested_matches.append(app_match)

    suggested_matches = _top_matches(suggested_matches, MAX_SUGGESTED_MATCHES)

    if not matches:
        return AppMatchResolution(None, [], suggested_matches)

    matches = _top_matches(matches, len(matches))
    generic_label_matches = _generic_label_token_matches(entries, candidate_variants)
    if len(generic_label_matches) > 1:
        return AppMatchResolution(None, generic_label_matches[:MAX_AMBIGUOUS_MATCHES], suggested_matches)

    best_match = matches[0]
    ambiguous_matches = [
        match
        for match in matches
        if best_match.score - match.score <= AMBIGUOUS_SCORE_MARGIN
    ]

    if len(ambiguous_matches) > 1:
        return AppMatchResolution(None, ambiguous_matches[:MAX_AMBIGUOUS_MATCHES], suggested_matches)

    return AppMatchResolution(best_match, [], suggested_matches)

def suggest_app_matches(session_id: Optional[str], candidate: str) -> List[AppMatch]:
    return resolve_app_match(session_id, candidate).suggested_matches

def find_app_match(session_id: Optional[str], candidate: str) -> Optional[AppMatch]:
    return resolve_app_match(session_id, candidate).match

def _top_matches(matches: List[AppMatch], limit: int) -> List[AppMatch]:
    if not matches:
        return []

    matches = _dedupe_matches(matches)
    matches.sort(key=lambda match: (-match.score, match.label.casefold(), match.package_name))
    return matches[:limit]

def _generic_label_token_matches(
    entries: List[AppCatalogEntryRecord],
    candidate_variants: List[Tuple[str, str]],
) -> List[AppMatch]:
    for token in _single_token_candidate_values(candidate_variants):
        exact_label_matches: List[AppMatch] = []
        containing_label_matches: List[AppMatch] = []

        for app in entries:
            label_normalized = _normalize_words(_split_compound_words(app.label))
            label_compact = label_normalized.replace(" ", "")
            label_tokens = _fast_tokens(label_normalized)

            if token == label_compact:
                exact_label_matches.append(AppMatch(app.label, app.package_name, 1.0))
            elif token in label_tokens:
                containing_label_matches.append(AppMatch(app.label, app.package_name, 0.96))

        exact_label_matches = _top_matches(exact_label_matches, MAX_AMBIGUOUS_MATCHES)
        if len(exact_label_matches) > 1:
            return exact_label_matches

        if exact_label_matches:
            continue

        containing_label_matches = _top_matches(containing_label_matches, MAX_AMBIGUOUS_MATCHES)
        if len(containing_label_matches) > 1:
            return containing_label_matches

    return []

def _single_token_candidate_values(candidate_variants: List[Tuple[str, str]]) -> List[str]:
    tokens: List[str] = []
    seen: Set[str] = set()

    for candidate_normalized, candidate_compact in candidate_variants:
        if (
            candidate_normalized
            and candidate_normalized == candidate_compact
            and len(candidate_normalized) >= 3
            and candidate_normalized not in seen
        ):
            tokens.append(candidate_normalized)
            seen.add(candidate_normalized)

    return tokens
