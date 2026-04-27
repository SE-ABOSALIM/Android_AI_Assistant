from typing import Any, Dict, List, Optional, Literal
from pydantic import BaseModel, Field, field_validator


VALID_LANGS = {"TR", "EN", "AR"}


class PredictRequest(BaseModel):
    text: str = Field(..., min_length=1, description="User command text")
    language: str = Field(..., min_length=2, max_length=2, description="Language code: TR / EN / AR")

    @field_validator("text")
    @classmethod
    def validate_text(cls, v: str) -> str:
        v = v.strip()
        if not v:
            raise ValueError("text cannot be empty")
        return v

    @field_validator("language")
    @classmethod
    def validate_language(cls, v: str) -> str:
        v = v.strip().upper()
        if v not in VALID_LANGS:
            raise ValueError("language must be one of: TR, EN, AR")
        return v


class PredictResponse(BaseModel):
    input: str
    normalized_input: str
    language: str

    intent: str
    parameters: Dict[str, Any]
    accepted: bool
    missing_slots: List[str]

    error_code: Optional[str] = None
    error_message: Optional[str] = None
    needs_confirmation: bool = False

    raw_output: str


class HealthResponse(BaseModel):
    message: str
    model_loaded: bool
    model_path: str