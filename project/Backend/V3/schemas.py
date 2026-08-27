import json
from typing import Annotated, Any, Dict, List, Optional

from pydantic import BaseModel, Field, StringConstraints, field_validator

from V3.security.request_limits import (
    MAX_ALIAS_LENGTH,
    MAX_APP_ALIASES,
    MAX_APP_CATALOG_ENTRIES,
    MAX_APP_LABEL_LENGTH,
    MAX_APP_VERSION_LENGTH,
    MAX_CATALOG_VERSION_LENGTH,
    MAX_CUSTOM_COMMAND_NAME_LENGTH,
    MAX_CUSTOM_COMMAND_STEPS,
    MAX_IDENTIFIER_LENGTH,
    MAX_INTENT_LENGTH,
    MAX_LANGUAGE_LENGTH,
    MAX_PACKAGE_NAME_LENGTH,
    MAX_PARAMETER_KEYS,
    MAX_PARAMETERS_DEPTH,
    MAX_PARAMETERS_JSON_BYTES,
    MAX_PLATFORM_LENGTH,
    MAX_PREDICT_TEXT_LENGTH,
    MAX_STEP_WAIT_AFTER_MS,
    MAX_TEXT_ALTERNATIVES,
)


Identifier = Annotated[str, StringConstraints(max_length=MAX_IDENTIFIER_LENGTH)]
Language = Annotated[str, StringConstraints(max_length=MAX_LANGUAGE_LENGTH)]
PredictText = Annotated[str, StringConstraints(max_length=MAX_PREDICT_TEXT_LENGTH)]
CatalogVersion = Annotated[str, StringConstraints(max_length=MAX_CATALOG_VERSION_LENGTH)]
AppLabel = Annotated[str, StringConstraints(max_length=MAX_APP_LABEL_LENGTH)]
PackageName = Annotated[str, StringConstraints(max_length=MAX_PACKAGE_NAME_LENGTH)]
Alias = Annotated[str, StringConstraints(max_length=MAX_ALIAS_LENGTH)]


class PredictRequest(BaseModel):
    text: PredictText
    language: Language
    text_alternatives: List[PredictText] = Field(default_factory=list, max_length=MAX_TEXT_ALTERNATIVES)
    session_id: Optional[Identifier] = None
    device_id: Optional[Identifier] = None
    catalog_version: Optional[CatalogVersion] = None
    has_search_input: bool = False


class AppCatalogEntry(BaseModel):
    label: AppLabel
    package_name: PackageName
    aliases: List[Alias] = Field(default_factory=list, max_length=MAX_APP_ALIASES)


class AppCatalogRequest(BaseModel):
    session_id: Identifier
    device_id: Optional[Identifier] = None
    platform: Optional[Annotated[str, StringConstraints(max_length=MAX_PLATFORM_LENGTH)]] = "android"
    app_version: Optional[Annotated[str, StringConstraints(max_length=MAX_APP_VERSION_LENGTH)]] = None
    language: Optional[Language] = None
    catalog_version: Optional[CatalogVersion] = None
    apps: List[AppCatalogEntry] = Field(default_factory=list, max_length=MAX_APP_CATALOG_ENTRIES)


class InstallationRegistrationRequest(BaseModel):
    device_id: Annotated[str, StringConstraints(min_length=1, max_length=MAX_IDENTIFIER_LENGTH)]
    platform: Annotated[str, StringConstraints(min_length=1, max_length=MAX_PLATFORM_LENGTH)] = "android"
    app_version: Optional[Annotated[str, StringConstraints(max_length=MAX_APP_VERSION_LENGTH)]] = None
    language: Language = "TR"


class InstallationRegistrationResponse(BaseModel):
    credential: str
    token_type: str = "Bearer"


class AppCatalogResponse(BaseModel):
    accepted: bool
    session_id: str
    catalog_version: str
    app_count: int


class AppCatalogStatusResponse(BaseModel):
    accepted: bool
    session_id: Optional[str] = None
    available: bool
    catalog_version: Optional[str] = None
    language: Optional[str] = None
    app_count: int = 0


class AppCatalogCloseResponse(BaseModel):
    accepted: bool
    session_id: str
    removed: bool
    remaining_sessions: int


class CommandHistoryItem(BaseModel):
    id: str
    intent: Optional[str] = None
    language: str
    accepted: bool
    error_code: Optional[str] = None
    confidence: Optional[float] = None
    processing_time_ms: Optional[float] = None
    created_at: str


class CommandHistoryResponse(BaseModel):
    items: List[CommandHistoryItem] = Field(default_factory=list)
    total_count: int = 0
    successful_count: int = 0
    failed_count: int = 0
    limit: int
    offset: int
    has_more: bool = False


class CommandHistoryMutationResponse(BaseModel):
    accepted: bool
    deleted_count: int = 0


class CustomCommandStep(BaseModel):
    intent: Annotated[str, StringConstraints(max_length=MAX_INTENT_LENGTH)]
    parameters: Dict[str, Any] = Field(default_factory=dict, max_length=MAX_PARAMETER_KEYS)
    wait_after_ms: int = Field(default=0, ge=0, le=MAX_STEP_WAIT_AFTER_MS)
    stop_on_failure: bool = True

    @field_validator("parameters")
    @classmethod
    def validate_parameters(cls, value: Dict[str, Any]) -> Dict[str, Any]:
        _validate_json_depth(value)
        serialized = json.dumps(value, ensure_ascii=False, separators=(",", ":"))
        if len(serialized.encode("utf-8")) > MAX_PARAMETERS_JSON_BYTES:
            raise ValueError("parameters payload is too large")
        return value


class CustomCommandItem(BaseModel):
    id: str
    name: str
    language: str
    enabled: bool = True
    steps: List[CustomCommandStep] = Field(default_factory=list)
    created_at: str
    updated_at: str


class CustomCommandListResponse(BaseModel):
    items: List[CustomCommandItem] = Field(default_factory=list)


class CustomCommandMutationRequest(BaseModel):
    device_id: Identifier
    language: Language = "TR"
    name: Annotated[str, StringConstraints(max_length=MAX_CUSTOM_COMMAND_NAME_LENGTH)]
    steps: List[CustomCommandStep] = Field(default_factory=list, max_length=MAX_CUSTOM_COMMAND_STEPS)


class CustomCommandMutationResponse(BaseModel):
    accepted: bool
    item: Optional[CustomCommandItem] = None
    deleted_count: int = 0
    error_code: Optional[str] = None
    error_message: Optional[str] = None


class FinalResponse(BaseModel):
    intent: str
    parameters: Dict[str, Any]
    accepted: bool
    missing_slots: List[str] = Field(default_factory=list)
    error_code: Optional[str] = None
    error_message: Optional[str] = None


def _validate_json_depth(value: Any, depth: int = 0) -> None:
    if depth > MAX_PARAMETERS_DEPTH:
        raise ValueError("parameters payload is nested too deeply")
    if isinstance(value, dict):
        for key, nested_value in value.items():
            if len(str(key)) > MAX_IDENTIFIER_LENGTH:
                raise ValueError("parameter key is too long")
            _validate_json_depth(nested_value, depth + 1)
    elif isinstance(value, list):
        for nested_value in value:
            _validate_json_depth(nested_value, depth + 1)
