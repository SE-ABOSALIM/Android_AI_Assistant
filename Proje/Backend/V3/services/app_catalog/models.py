from dataclasses import dataclass
from typing import List, Optional


@dataclass(frozen=True)
class AppCatalogEntryRecord:
    label: str
    package_name: str
    aliases: List[str]
    match_aliases: List[str]


@dataclass(frozen=True)
class AppMatch:
    label: str
    package_name: str
    score: float


@dataclass(frozen=True)
class AppMatchResolution:
    match: Optional[AppMatch]
    ambiguous_matches: List[AppMatch]
    suggested_matches: List[AppMatch]

    @property
    def is_ambiguous(self) -> bool:
        return bool(self.ambiguous_matches)
