from dataclasses import dataclass


@dataclass(frozen=True)
class RuleContext:
    original: str
    normalized: str
    language: str
