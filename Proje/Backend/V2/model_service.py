import json
import re
from typing import Any, Dict, List, Optional, Tuple

import torch
from transformers import AutoTokenizer, MT5ForConditionalGeneration

from schemas import PredictResponse


MODEL_DIR = "result_model"
DEVICE = "cuda" if torch.cuda.is_available() else "cpu"

VALID_INTENTS = {
    "OPEN_APP",
    "CALL_CONTACT",
    "ADJUST_VOLUME",
    "SCROLL_SCREEN",
    "SWIPE_GESTURE",
    "SET_TIMER",
    "TAKE_PHOTO",
    "GO_HOME",
}

VALID_LANGUAGE_CODES = {"TR", "EN", "AR"}

ALLOWED_SLOT_VALUES = {
    "SCROLL_SCREEN": {
        "direction": {"up", "down"},
    },
    "SWIPE_GESTURE": {
        "direction": {"left", "right"},
    },
    "ADJUST_VOLUME": {
        "volume_action": {"increase", "decrease", "mute", "unmute"},
    },
    "SET_TIMER": {
        "duration_unit": {"second", "seconds", "minute", "minutes", "hour", "hours"},
    },
}

REQUIRED_SLOTS = {
    "OPEN_APP": ["app_name"],
    "CALL_CONTACT": ["contact_name"],
    "ADJUST_VOLUME": ["volume_action"],
    "SCROLL_SCREEN": ["direction"],
    "SWIPE_GESTURE": ["direction"],
    "SET_TIMER": ["duration_value", "duration_unit"],
    "TAKE_PHOTO": [],
    "GO_HOME": [],
}


class ModelService:
    def __init__(self, model_dir: str = MODEL_DIR) -> None:
        self.model_dir = model_dir
        self.device = DEVICE
        self.tokenizer = AutoTokenizer.from_pretrained(model_dir, use_fast=False)
        self.model = MT5ForConditionalGeneration.from_pretrained(model_dir).to(self.device)
        self.model.eval()

    def predict_intent(self, text: str, language: str) -> PredictResponse:
        original_text = text
        text = self._normalize_input_text(text)
        language = language.strip().upper()

        input_error = self._validate_raw_input(text, language)
        if input_error is not None:
            return self._build_error_response(
                original_text=original_text,
                normalized_text=text,
                language=language,
                error_code=input_error["error_code"],
                error_message=input_error["error_message"],
            )

        source = f"[{language}] {text}"

        raw_output = self._generate(source)
        parsed = self._safe_parse_json(raw_output)

        if parsed is None:
            return self._build_error_response(
                original_text=original_text,
                normalized_text=text,
                language=language,
                error_code="PARSE_ERROR",
                error_message="Model output is not valid JSON.",
                raw_output=raw_output,
            )

        validated = self._validate_prediction(
            original_text=original_text,
            normalized_text=text,
            language=language,
            parsed=parsed,
            raw_output=raw_output,
        )
        return validated

    def _generate(self, source: str) -> str:
        inputs = self.tokenizer(
            source,
            return_tensors="pt",
            truncation=True,
            max_length=64,
        ).to(self.device)

        with torch.no_grad():
            outputs = self.model.generate(
                **inputs,
                max_length=128,
                num_beams=4,
                do_sample=False,
                early_stopping=True,
            )

        decoded = self.tokenizer.decode(outputs[0], skip_special_tokens=True).strip()
        return decoded

    def _safe_parse_json(self, raw_output: str) -> Optional[Dict[str, Any]]:
        try:
            parsed = json.loads(raw_output)
            if not isinstance(parsed, dict):
                return None
            return parsed
        except json.JSONDecodeError:
            return None

    def _normalize_input_text(self, text: str) -> str:
        text = text.strip()
        text = re.sub(r"\s+", " ", text)
        return text

    def _validate_raw_input(self, text: str, language: str) -> Optional[Dict[str, str]]:
        if language not in VALID_LANGUAGE_CODES:
            return {
                "error_code": "UNSUPPORTED_LANGUAGE",
                "error_message": "Supported languages are TR, EN, AR.",
            }

        if not text:
            return {
                "error_code": "EMPTY_INPUT",
                "error_message": "Input text is empty.",
            }

        words = text.split()

        if len(words) > 20:
            return {
                "error_code": "INPUT_TOO_LONG",
                "error_message": "Input is too long. Keep the command shorter.",
            }

        if self._is_garbage_text(text):
            return {
                "error_code": "OUT_OF_SCOPE",
                "error_message": "Input is not a valid command.",
            }

        if len(words) == 1 and words[0].lower() in {"open", "call", "scroll", "swipe", "timer", "alarm"}:
            return {
                "error_code": "AMBIGUOUS_COMMAND",
                "error_message": "Command is too vague.",
            }

        return None

    def _is_garbage_text(self, text: str) -> bool:
        stripped = text.strip()

        if not stripped:
            return True

        if re.fullmatch(r"[\W_]+", stripped):
            return True

        if re.fullmatch(r"[0-9\s]+", stripped):
            return True

        if re.fullmatch(r"(.)\1{4,}", stripped):
            return True

        if re.fullmatch(r"[a-zA-Z]{1,3}", stripped) and stripped.lower() not in {"home"}:
            return True

        low = stripped.lower()
        garbage_words = {
            "asdasd", "qweqwe", "blah", "testtest", "aaaaa", "ههههه"
        }
        if low in garbage_words:
            return True

        return False

    def _validate_prediction(
        self,
        original_text: str,
        normalized_text: str,
        language: str,
        parsed: Dict[str, Any],
        raw_output: str,
    ) -> PredictResponse:
        intent = str(parsed.get("intent", "")).strip()
        parameters = parsed.get("parameters", {})
        accepted = bool(parsed.get("accepted", False))
        missing_slots = parsed.get("missing_slots", [])

        if not isinstance(parameters, dict):
            parameters = {}

        if not isinstance(missing_slots, list):
            missing_slots = []

        if intent not in VALID_INTENTS:
            return self._build_error_response(
                original_text=original_text,
                normalized_text=normalized_text,
                language=language,
                error_code="UNSUPPORTED_COMMAND",
                error_message="Predicted intent is not supported.",
                raw_output=raw_output,
            )

        required_slots = REQUIRED_SLOTS.get(intent, [])
        actual_missing_slots = self._find_missing_required_slots(intent, parameters)

        # Model accepted=true dese bile backend son kararı verir
        if actual_missing_slots:
            return PredictResponse(
                input=original_text,
                normalized_input=normalized_text,
                language=language,
                intent=intent,
                parameters=parameters,
                accepted=False,
                missing_slots=actual_missing_slots,
                error_code="MISSING_REQUIRED_SLOTS",
                error_message="Required parameters are missing.",
                needs_confirmation=False,
                raw_output=raw_output,
            )

        slot_error = self._validate_slot_values(intent, parameters)
        if slot_error is not None:
            return PredictResponse(
                input=original_text,
                normalized_input=normalized_text,
                language=language,
                intent=intent,
                parameters=parameters,
                accepted=False,
                missing_slots=[],
                error_code="INVALID_SLOT_VALUE",
                error_message=slot_error,
                needs_confirmation=False,
                raw_output=raw_output,
            )

        # Intent-text sanity checks
        contradiction_error = self._check_intent_text_contradiction(intent, parameters, normalized_text)
        if contradiction_error is not None:
            return PredictResponse(
                input=original_text,
                normalized_input=normalized_text,
                language=language,
                intent=intent,
                parameters=parameters,
                accepted=False,
                missing_slots=[],
                error_code="LOW_CONFIDENCE",
                error_message=contradiction_error,
                needs_confirmation=False,
                raw_output=raw_output,
            )

        return PredictResponse(
            input=original_text,
            normalized_input=normalized_text,
            language=language,
            intent=intent,
            parameters=parameters,
            accepted=accepted,
            missing_slots=[],
            error_code=None,
            error_message=None,
            needs_confirmation=False,
            raw_output=raw_output,
        )

    def _find_missing_required_slots(self, intent: str, parameters: Dict[str, Any]) -> List[str]:
        missing = []
        for slot in REQUIRED_SLOTS.get(intent, []):
            value = parameters.get(slot)

            if value is None:
                missing.append(slot)
                continue

            if isinstance(value, str) and not value.strip():
                missing.append(slot)
                continue

        return missing

    def _validate_slot_values(self, intent: str, parameters: Dict[str, Any]) -> Optional[str]:
        allowed = ALLOWED_SLOT_VALUES.get(intent)
        if not allowed:
            return None

        for slot_name, allowed_values in allowed.items():
            if slot_name not in parameters:
                continue

            value = parameters[slot_name]
            if not isinstance(value, str):
                value = str(value)

            normalized = value.strip().lower()
            if normalized not in allowed_values:
                return f"Invalid value for '{slot_name}': {value}"

        if intent == "SET_TIMER":
            duration_value = parameters.get("duration_value")
            if duration_value is None:
                return None

            try:
                numeric_value = int(duration_value)
                if numeric_value <= 0:
                    return "duration_value must be a positive integer."
            except (ValueError, TypeError):
                return "duration_value must be numeric."

        return None

    def _check_intent_text_contradiction(
        self,
        intent: str,
        parameters: Dict[str, Any],
        normalized_text: str,
    ) -> Optional[str]:
        text_lower = normalized_text.lower()

        if intent == "ADJUST_VOLUME":
            action = str(parameters.get("volume_action", "")).strip().lower()

            decrease_hints = {
                "decrease", "lower", "down", "azalt", "alçalt", "kıs", "خفض", "قلل"
            }
            increase_hints = {
                "increase", "raise", "up", "artır", "yükselt", "ارفع", "زيد"
            }

            if any(h in text_lower for h in decrease_hints) and action == "increase":
                return "Text suggests decrease, but model predicted increase."

            if any(h in text_lower for h in increase_hints) and action == "decrease":
                return "Text suggests increase, but model predicted decrease."

        if intent == "SCROLL_SCREEN":
            direction = str(parameters.get("direction", "")).strip().lower()
            if "left" in text_lower or "right" in text_lower or "sol" in text_lower or "sağ" in text_lower:
                return "Text suggests horizontal movement, but intent is SCROLL_SCREEN."
            if direction not in {"up", "down"}:
                return "SCROLL_SCREEN direction must be up or down."

        if intent == "SWIPE_GESTURE":
            direction = str(parameters.get("direction", "")).strip().lower()
            if "up" in text_lower or "down" in text_lower or "aşağı" in text_lower or "yukarı" in text_lower:
                return "Text suggests vertical movement, but intent is SWIPE_GESTURE."
            if direction not in {"left", "right"}:
                return "SWIPE_GESTURE direction must be left or right."

        return None

    def _build_error_response(
        self,
        original_text: str,
        normalized_text: str,
        language: str,
        error_code: str,
        error_message: str,
        raw_output: str = "",
    ) -> PredictResponse:
        return PredictResponse(
            input=original_text,
            normalized_input=normalized_text,
            language=language,
            intent="REJECTED",
            parameters={},
            accepted=False,
            missing_slots=[],
            error_code=error_code,
            error_message=error_message,
            needs_confirmation=False,
            raw_output=raw_output,
        )