from typing import Any, Dict, List, Optional
from pydantic import BaseModel, Field


class PredictRequest(BaseModel):
    text: str
    language: str


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
