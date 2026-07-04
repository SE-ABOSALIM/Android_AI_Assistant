from typing import Any, Dict, List, Optional
from pydantic import BaseModel, Field


class PredictRequest(BaseModel):
    text: str
    language: str
    text_alternatives: List[str] = Field(default_factory=list)
    session_id: Optional[str] = None
    device_id: Optional[str] = None
    catalog_version: Optional[str] = None
    has_search_input: bool = False


class AppCatalogEntry(BaseModel):
    label: str
    package_name: str
    aliases: List[str] = Field(default_factory=list)


class AppCatalogRequest(BaseModel):
    session_id: str
    device_id: Optional[str] = None
    platform: Optional[str] = "android"
    app_version: Optional[str] = None
    language: Optional[str] = None
    catalog_version: Optional[str] = None
    apps: List[AppCatalogEntry] = Field(default_factory=list)


class AppCatalogResponse(BaseModel):
    accepted: bool
    session_id: str
    catalog_version: str
    app_count: int


class AppCatalogStatusResponse(BaseModel):
    accepted: bool
    session_id: str
    available: bool
    catalog_version: Optional[str] = None
    app_count: int = 0


class AppCatalogCloseResponse(BaseModel):
    accepted: bool
    session_id: str
    removed: bool
    remaining_sessions: int


class CommandHistoryItem(BaseModel):
    id: str
    text: str
    language: str
    intent: Optional[str] = None
    parameters: Dict[str, Any] = Field(default_factory=dict)
    accepted: bool
    result_status: Optional[str] = None
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
    intent: str
    parameters: Dict[str, Any] = Field(default_factory=dict)
    wait_after_ms: int = 0
    stop_on_failure: bool = True


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
    device_id: str
    language: str = "TR"
    name: str
    steps: List[CustomCommandStep] = Field(default_factory=list)


class CustomCommandMutationResponse(BaseModel):
    accepted: bool
    item: Optional[CustomCommandItem] = None
    deleted_count: int = 0
    error_code: Optional[str] = None
    error_message: Optional[str] = None


class FinalResponse(BaseModel):
    input: str
    normalized_input: str
    language: str
    intent: str
    parameters: Dict[str, Any]
    backend_supported: bool = False
    android_supported: bool = False
    parameter_contract: Dict[str, Any] = Field(default_factory=dict)
    accepted: bool
    missing_slots: List[str]
    error_code: Optional[str]
    error_message: Optional[str]
    needs_confirmation: bool
    confidence: float
    threshold: float
    raw_label: str
    processing_time_ms: float = 0.0
    top_predictions: List[Dict[str, Any]] = Field(default_factory=list)
