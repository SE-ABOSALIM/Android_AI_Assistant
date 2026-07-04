from typing import Any, Dict, List, Optional

from V3.extraction.app import extract_app_name_for_intent
from V3.app_catalog.matcher import resolve_app_match
from V3.app_catalog.service import has_app_catalog, is_catalog_version_current
from V3.validation.context import ValidationContext


def enrich_app_command(context: ValidationContext) -> None:
    catalog_key = _catalog_lookup_key(context)
    app_names = _extract_app_name_candidates(
        context.original_text,
        context.language,
        context.text_alternatives,
        context.intent,
        context.parameters,
    )
    if not app_names:
        context.missing_slots.append("app_name")
        return

    context.parameters["app_name"] = app_names[0]

    if not has_app_catalog(catalog_key):
        context.reject(
            "APP_CATALOG_MISSING",
            "Installed app catalog is missing for this device.",
        )
        return

    if not is_catalog_version_current(catalog_key, context.catalog_version):
        context.reject(
            "APP_CATALOG_STALE",
            "Installed app catalog version is stale for this device.",
        )
        return

    app_resolution = _resolve_best_app_match(catalog_key, app_names)
    app_name = app_resolution["app_name"]
    if app_resolution["ambiguous_matches"]:
        context.parameters["app_name"] = app_name
        context.parameters["app_match_candidates"] = _serialize_app_matches(app_resolution["ambiguous_matches"])
        context.reject(
            "APP_MATCH_AMBIGUOUS",
            "Multiple installed apps match the requested app name.",
        )
    elif app_resolution["match"]:
        match = app_resolution["match"]
        context.parameters["app_name"] = match.label
        context.parameters["app_package_name"] = match.package_name
        context.parameters["app_match_score"] = round(match.score, 4)
    else:
        context.parameters["app_name"] = app_name
        context.parameters["app_match_candidates"] = _serialize_app_matches(app_resolution["suggested_matches"])
        if app_resolution["suggested_matches"]:
            error_message = "The requested app did not match confidently. Choose one of the closest installed apps."
        else:
            error_message = "The requested app does not match an installed app."
        context.reject("APP_NOT_IN_CATALOG", error_message)


def _catalog_lookup_key(context: ValidationContext) -> Optional[str]:
    if context.device_id and str(context.device_id).strip():
        return str(context.device_id).strip()
    return context.session_id


def _extract_app_name_candidates(
    original_text: str,
    language: str,
    text_alternatives: Optional[List[str]],
    intent: str,
    parameters: Optional[Dict[str, Any]] = None,
) -> List[str]:
    candidates: List[str] = []
    seen = set()

    existing_app_name = (parameters or {}).get("app_name")
    if existing_app_name and str(existing_app_name).casefold() not in seen:
        candidates.append(str(existing_app_name))
        seen.add(str(existing_app_name).casefold())

    for text in [original_text, *(text_alternatives or [])]:
        app_name = extract_app_name_for_intent(text, language, intent)
        if app_name and app_name.casefold() not in seen:
            candidates.append(app_name)
            seen.add(app_name.casefold())

    return candidates


def _resolve_best_app_match(session_id: Optional[str], app_names: List[str]) -> Dict[str, Any]:
    best_matches = []
    ambiguous_matches = []
    suggested_matches = []

    for app_name in app_names:
        resolution = resolve_app_match(session_id, app_name)
        if resolution.match:
            best_matches.append((app_name, resolution.match))
        if resolution.ambiguous_matches:
            ambiguous_matches.extend((app_name, match) for match in resolution.ambiguous_matches)
        if resolution.suggested_matches:
            suggested_matches.extend((app_name, match) for match in resolution.suggested_matches)

    if best_matches:
        best_matches.sort(key=lambda item: (-item[1].score, item[1].label.casefold(), item[1].package_name))
        best_score = best_matches[0][1].score
        tied_matches = [
            match
            for _, match in best_matches
            if best_score - match.score <= 0.03
        ]
        deduped_ties = _dedupe_app_matches(tied_matches)
        if len(deduped_ties) > 1:
            return {
                "app_name": best_matches[0][0],
                "match": None,
                "ambiguous_matches": deduped_ties[:5],
                "suggested_matches": [],
            }
        return {
            "app_name": best_matches[0][0],
            "match": best_matches[0][1],
            "ambiguous_matches": [],
            "suggested_matches": [],
        }

    if ambiguous_matches:
        ambiguous_matches.sort(key=lambda item: (-item[1].score, item[1].label.casefold(), item[1].package_name))
        return {
            "app_name": ambiguous_matches[0][0],
            "match": None,
            "ambiguous_matches": _dedupe_app_matches([match for _, match in ambiguous_matches])[:5],
            "suggested_matches": [],
        }

    suggested_matches.sort(key=lambda item: (-item[1].score, item[1].label.casefold(), item[1].package_name))
    return {
        "app_name": app_names[0],
        "match": None,
        "ambiguous_matches": [],
        "suggested_matches": _dedupe_app_matches([match for _, match in suggested_matches])[:5],
    }


def _dedupe_app_matches(matches: List[Any]) -> List[Any]:
    by_package_name = {}
    for match in matches:
        existing = by_package_name.get(match.package_name)
        if existing is None or match.score > existing.score:
            by_package_name[match.package_name] = match
    return list(by_package_name.values())


def _serialize_app_matches(matches: List[Any]) -> List[Dict[str, Any]]:
    return [
        {
            "label": match.label,
            "package_name": match.package_name,
            "score": round(match.score, 4),
        }
        for match in matches
    ]
