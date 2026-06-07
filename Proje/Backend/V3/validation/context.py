from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional


@dataclass
class ValidationContext:
    original_text: str
    language: str
    intent: str
    parameters: Dict[str, Any]
    confidence: float
    raw_label: str
    top_predictions: List[Dict[str, Any]]
    text_alternatives: Optional[List[str]]
    session_id: Optional[str]
    catalog_version: Optional[str]
    has_search_input: bool = False
    missing_slots: List[str] = field(default_factory=list)
    error_code: Optional[str] = None
    error_message: Optional[str] = None

    def reject(self, error_code: str, error_message: str) -> None:
        self.error_code = error_code
        self.error_message = error_message
