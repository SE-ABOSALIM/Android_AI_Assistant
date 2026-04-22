from typing import Any, Dict, List
from pydantic import BaseModel, Field

class PredictRequest(BaseModel):
    text: str = Field(..., min_length=1)
    language: str = Field(..., min_length=2, max_length=2)

class PredictResponse(BaseModel):
    input: str
    intent: str
    parameters: Dict[str, Any]
    accepted: bool
    missing_slots: List[str]
    raw_output: str