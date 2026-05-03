from typing import Any, Dict, List, Optional
from pydantic import BaseModel, Field


class PredictRequest(BaseModel):
    text: str
    language: str
    text_alternatives: List[str] = Field(default_factory=list)
    session_id: Optional[str] = None
    catalog_version: Optional[str] = None


class AppCatalogEntry(BaseModel):
    label: str
    package_name: str
    aliases: List[str] = Field(default_factory=list)


class AppCatalogRequest(BaseModel):
    session_id: str
    language: Optional[str] = None
    catalog_version: Optional[str] = None
    apps: List[AppCatalogEntry] = Field(default_factory=list)


class AppCatalogResponse(BaseModel):
    accepted: bool
    session_id: str
    catalog_version: str
    app_count: int


class AppCatalogCloseResponse(BaseModel):
    accepted: bool
    session_id: str
    removed: bool
    remaining_sessions: int


class FinalResponse(BaseModel):
    input: str
    normalized_input: str
    language: str
    intent: str
    parameters: Dict[str, Any]
    accepted: bool
    missing_slots: List[str]
    error_code: Optional[str]
    error_message: Optional[str]
    needs_confirmation: bool
    confidence: float
    threshold: float
    raw_label: str
    top_predictions: List[Dict[str, Any]] = Field(default_factory=list)
