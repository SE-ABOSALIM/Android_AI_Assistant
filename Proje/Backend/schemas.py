from pydantic import BaseModel, Field

class PredictRequest(BaseModel):
    text: str = Field(..., min_length=1)
    language: str = Field(..., min_length=2, max_length=2)

class PredictResponse(BaseModel):
    input: str
    predicted_label: str
    confidence: float
    accepted: bool
    temperature: float