from typing import List, Tuple

from V3.services.app_catalog.models import AppCatalogEntryRecord
from V3.services.app_catalog.text_utils import (
    _fast_tokens,
    _length_penalty,
    _levenshtein_similarity,
    _ngram_overlap_ratio,
    _should_compare_edit_distance,
    _token_overlap_from_sets,
)


def _score_candidate(candidate_variants: List[Tuple[str, str]], app: AppCatalogEntryRecord) -> float:
    score = 0.0
    candidate_data = [
        (candidate_normalized, candidate_compact, _fast_tokens(candidate_normalized))
        for candidate_normalized, candidate_compact in candidate_variants
        if candidate_compact
    ]

    for candidate_normalized, candidate_compact, candidate_tokens in candidate_data:
        for alias_normalized in app.match_aliases:
            alias_compact = alias_normalized.replace(" ", "")
            if not alias_compact:
                continue
            alias_tokens = _fast_tokens(alias_normalized)

            if candidate_compact == alias_compact:
                score = max(score, 1.0)

            if candidate_normalized and candidate_normalized in alias_tokens:
                score = max(score, 0.96)

            if alias_compact.startswith(candidate_compact) or candidate_compact.startswith(alias_compact):
                score = max(score, 0.90 - _length_penalty(candidate_compact, alias_compact))

            if candidate_compact in alias_compact or alias_compact in candidate_compact:
                score = max(score, 0.84 - _length_penalty(candidate_compact, alias_compact))

            ngram_overlap = 0.0
            if candidate_compact[0] != alias_compact[0]:
                ngram_overlap = _ngram_overlap_ratio(candidate_compact, alias_compact)
                score = max(score, ngram_overlap * 0.82)

            if _should_compare_edit_distance(candidate_compact, alias_compact, ngram_overlap):
                score = max(score, _levenshtein_similarity(candidate_compact, alias_compact))
            score = max(score, _token_overlap_from_sets(candidate_tokens, alias_tokens) * 0.92)

    return score
