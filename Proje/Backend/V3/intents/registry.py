from dataclasses import dataclass, field
from typing import Any, Dict, Iterable, Optional, Tuple

from V3.config import DEFAULT_CONFIDENCE_THRESHOLD


ParameterGroup = Tuple[str, ...]


@dataclass(frozen=True)
class IntentContract:
    name: str
    threshold: float = DEFAULT_CONFIDENCE_THRESHOLD
    required_parameters: Tuple[str, ...] = field(default_factory=tuple)
    required_parameter_groups: Tuple[ParameterGroup, ...] = field(default_factory=tuple)
    optional_parameters: Tuple[str, ...] = field(default_factory=tuple)
    android_supported: bool = False
    android_required_parameters: Tuple[str, ...] = field(default_factory=tuple)
    android_required_parameter_groups: Tuple[ParameterGroup, ...] = field(default_factory=tuple)

    @property
    def parameter_names(self) -> Tuple[str, ...]:
        names = []
        seen = set()
        for name in (
            *self.required_parameters,
            *self.optional_parameters,
            *(param for group in self.required_parameter_groups for param in group),
        ):
            if name not in seen:
                names.append(name)
                seen.add(name)
        return tuple(names)


def _contract(
    name: str,
    threshold: float = DEFAULT_CONFIDENCE_THRESHOLD,
    required: Iterable[str] = (),
    one_of: Iterable[Iterable[str]] = (),
    optional: Iterable[str] = (),
    android_supported: bool = False,
    android_required: Iterable[str] = (),
    android_one_of: Optional[Iterable[Iterable[str]]] = None,
) -> IntentContract:
    required_parameters = tuple(required)
    android_required_parameters = tuple(android_required)
    if android_supported and not android_required_parameters and android_one_of is None:
        android_required_parameters = required_parameters

    return IntentContract(
        name=name,
        threshold=threshold,
        required_parameters=required_parameters,
        required_parameter_groups=tuple(tuple(group) for group in one_of),
        optional_parameters=tuple(optional),
        android_supported=android_supported,
        android_required_parameters=android_required_parameters,
        android_required_parameter_groups=(
            tuple(tuple(group) for group in android_one_of)
            if android_one_of is not None
            else tuple(tuple(group) for group in one_of)
        ),
    )


INTENT_CONTRACTS: Dict[str, IntentContract] = {
    "ADJUST_BRIGHTNESS": _contract(
        "ADJUST_BRIGHTNESS",
        threshold=0.55,
        required=("brightness",),
    ),
    "ADJUST_VOLUME": _contract(
        "ADJUST_VOLUME",
        threshold=0.55,
        one_of=(("volume_action", "volume_level"),),
        android_supported=True,
        android_one_of=(("volume_action", "volume_level"),),
    ),
    "CALL_CONTACT": _contract(
        "CALL_CONTACT",
        threshold=0.55,
        required=("contact_name",),
        android_supported=True,
    ),
    "CLEAR_TEXT": _contract(
        "CLEAR_TEXT",
        threshold=0.55
    ),
    "CLICK_ITEM": _contract(
        "CLICK_ITEM",
        threshold=0.55,
        optional=("target_text",),
    ),
    "CLOSE_APP": _contract(
        "CLOSE_APP",
        threshold=0.55
    ),
    "DOUBLE_TAP": _contract(
        "DOUBLE_TAP",
        threshold=0.55
    ),
    "GO_BACK": _contract(
        "GO_BACK",
        threshold=0.60),
    "GO_HOME": _contract(
        "GO_HOME",
        threshold=0.60,
        android_supported=True
    ),
    "HOLD_SCREEN": _contract(
        "HOLD_SCREEN",
        threshold=0.55
    ),
    "OPEN_APP": _contract(
        "OPEN_APP",
        threshold=0.45,
        required=("app_name",
                  "app_package_name"
                  ),
        optional=("app_match_score",
                  "app_match_candidates"
                  ),
        android_supported=True,
        android_required=("app_package_name",),
    ),
    "OPEN_APP_INFO": _contract(
        "OPEN_APP_INFO",
        threshold=0.55,
        required=("app_name",
                  "app_package_name"
                  ),
        optional=("app_match_score",
                  "app_match_candidates"
                  ),
    ),
    "OPEN_NOTIFICATIONS": _contract(
        "OPEN_NOTIFICATIONS",
        threshold=0.55
    ),
    "SCROLL_SCREEN": _contract(
        "SCROLL_SCREEN",
        threshold=0.55,
        required=("direction",),
        android_supported=True,
    ),
    "SEARCH_QUERY": _contract(
        "SEARCH_QUERY",
        threshold=0.55,
        required=("query",),
    ),
    "SET_ALARM": _contract(
        "SET_ALARM",
        threshold=0.55,
        optional=("period", "alarm_text"),
    ),
    "SET_BLUETOOTH": _contract(
        "SET_BLUETOOTH",
        threshold=0.55,
        required=("state",)
    ),
    "SET_FLASHLIGHT": _contract(
        "SET_FLASHLIGHT",
        threshold=0.55,
        required=("state",)
    ),
    "SET_KEYBOARD": _contract(
        "SET_KEYBOARD",
        threshold=0.55,
        required=("state",)
    ),
    "SET_LOCATION": _contract(
        "SET_LOCATION",
        threshold=0.55,
        required=("state",)
    ),
    "SET_MOBILE_DATA": _contract(
        "SET_MOBILE_DATA",
        threshold=0.55,
        required=("state",)
    ),
    "SET_MOBILE_HOTSPOT": _contract(
        "SET_MOBILE_HOTSPOT",
        threshold=0.55,
        required=("state",)
    ),
    "SET_SOUND_MODE": _contract(
        "SET_SOUND_MODE",
        threshold=0.55,
        required=("sound_mode",)
    ),
    "SET_TIMER": _contract(
        "SET_TIMER",
        threshold=0.50,
        required=("duration_value",
                  "duration_unit",
                  "duration_seconds"
                  ),
        android_supported=True,
    ),
    "SET_WIFI": _contract(
        "SET_WIFI",
        threshold=0.55,
        required=("state",)
    ),
    "SHOW_RECENTS": _contract(
        "SHOW_RECENTS",
        threshold=0.55
    ),
    "STOP_LISTENING": _contract(
        "STOP_LISTENING",
        threshold=0.60,
        android_supported=True
    ),
    "SWIPE_GESTURE": _contract(
        "SWIPE_GESTURE",
        threshold=0.55,
        required=("direction",),
        android_supported=True,
    ),
    "TAKE_PHOTO": _contract(
        "TAKE_PHOTO",
        threshold=0.60,
        optional=("camera",),
        android_supported=True,
    ),
    "TAKE_SCREENSHOT": _contract(
        "TAKE_SCREENSHOT",
        threshold=0.55
    ),
    "UNINSTALL_APP": _contract(
        "UNINSTALL_APP",
        threshold=0.55,
        required=("app_name",
                  "app_package_name"
                  ),
        optional=("app_match_score",
                  "app_match_candidates"
                  ),
    ),
    "WRITE_TEXT": _contract(
        "WRITE_TEXT",
        threshold=0.55,
        required=("text",),
    ),
    "UNKNOWN_COMMAND": _contract(
        "UNKNOWN_COMMAND",
        threshold=0.50
    ),
}


def get_intent_contract(intent: str) -> Optional[IntentContract]:
    return INTENT_CONTRACTS.get(str(intent or "").upper())


def is_known_intent(intent: str) -> bool:
    return get_intent_contract(intent) is not None


def supported_intents() -> set[str]:
    return set(INTENT_CONTRACTS.keys()) - {"UNKNOWN_COMMAND"}


def intent_thresholds() -> Dict[str, float]:
    return {intent: contract.threshold for intent, contract in INTENT_CONTRACTS.items()}


def get_threshold(intent: str) -> float:
    contract = get_intent_contract(intent)
    if contract is None:
        return DEFAULT_CONFIDENCE_THRESHOLD
    return contract.threshold


def missing_required_parameters(contract: IntentContract, parameters: Dict[str, Any]) -> list[str]:
    missing = [
        name
        for name in contract.required_parameters
        if not _has_parameter(parameters, name)
    ]

    for group in contract.required_parameter_groups:
        if not any(_has_parameter(parameters, name) for name in group):
            missing.append("|".join(group))

    return missing


def is_android_supported(contract: IntentContract, parameters: Dict[str, Any]) -> bool:
    if not contract.android_supported:
        return False

    android_contract = IntentContract(
        name=contract.name,
        required_parameters=contract.android_required_parameters,
        required_parameter_groups=contract.android_required_parameter_groups,
    )
    return not missing_required_parameters(android_contract, parameters)


def _has_parameter(parameters: Dict[str, Any], name: str) -> bool:
    value = parameters.get(name)
    if value is None:
        return False
    if isinstance(value, str):
        return value.strip() != ""
    return True
