from typing import Dict, Iterable, List, Set, Tuple

from V3.services.app_catalog.constants import MAX_INDEXED_CANDIDATE_APPS, MIN_NGRAM_MATCH_RATIO
from V3.services.app_catalog.models import AppCatalogEntryRecord
from V3.services.app_catalog.text_utils import _fast_tokens, _ngrams, _normalize_words


def _build_catalog_search_index(entries: List[AppCatalogEntryRecord]) -> Dict[str, Dict[str, Set[int]]]:
    exact_index: Dict[str, Set[int]] = {}
    compact_index: Dict[str, Set[int]] = {}
    token_index: Dict[str, Set[int]] = {}
    ngram_index: Dict[str, Set[int]] = {}

    for app_index, app in enumerate(entries):
        for alias in app.match_aliases:
            normalized = _normalize_words(alias)
            compact = normalized.replace(" ", "")
            if not compact:
                continue

            _add_index_value(exact_index, normalized, app_index)
            _add_index_value(compact_index, compact, app_index)

            for token in _fast_tokens(normalized):
                if len(token) >= 2:
                    _add_index_value(token_index, token, app_index)

            for ngram in _ngrams(compact):
                _add_index_value(ngram_index, ngram, app_index)

    return {
        "exact": exact_index,
        "compact": compact_index,
        "token": token_index,
        "ngram": ngram_index,
    }

def _candidate_entry_indexes(
    catalog: Dict[str, object],
    candidate_variants: List[Tuple[str, str]],
) -> List[int]:
    search_index = catalog.get("search_index")
    if not isinstance(search_index, dict):
        return []

    exact_index = search_index.get("exact", {})
    compact_index = search_index.get("compact", {})
    token_index = search_index.get("token", {})
    ngram_index = search_index.get("ngram", {})
    candidate_scores: Dict[int, float] = {}

    for candidate_normalized, candidate_compact in candidate_variants:
        if not candidate_compact:
            continue

        _add_candidate_scores(candidate_scores, exact_index.get(candidate_normalized, set()), 6.0)
        _add_candidate_scores(candidate_scores, compact_index.get(candidate_compact, set()), 5.5)

        for token in _fast_tokens(candidate_normalized):
            if len(token) >= 2:
                _add_candidate_scores(candidate_scores, token_index.get(token, set()), 2.0)

        ngrams = _ngrams(candidate_compact)
        if ngrams:
            ngram_hits: Dict[int, int] = {}
            for ngram in ngrams:
                for app_index in ngram_index.get(ngram, set()):
                    ngram_hits[app_index] = ngram_hits.get(app_index, 0) + 1

            min_hits = max(1, int(len(ngrams) * MIN_NGRAM_MATCH_RATIO))
            for app_index, hit_count in ngram_hits.items():
                if hit_count >= min_hits:
                    _add_candidate_scores(candidate_scores, [app_index], hit_count / len(ngrams))

    return [
        app_index
        for app_index, _ in sorted(candidate_scores.items(), key=lambda item: (-item[1], item[0]))
    ][:MAX_INDEXED_CANDIDATE_APPS]

def _add_index_value(index: Dict[str, Set[int]], key: str, app_index: int) -> None:
    if key:
        index.setdefault(key, set()).add(app_index)

def _add_candidate_scores(candidate_scores: Dict[int, float], app_indexes: Iterable[int], score: float) -> None:
    for app_index in app_indexes:
        candidate_scores[app_index] = candidate_scores.get(app_index, 0.0) + score
