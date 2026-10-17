from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date, time

TYPES = ("diary", "phonograph", "letter", "telegram", "newspaper", "memo", "log")
ROLES = ("note", "para", "dateline", "salutation", "signoff", "verse", "divider")


@dataclass(frozen=True)
class Span:
    text: str
    italic: bool = False


@dataclass(frozen=True)
class Block:
    role: str
    spans: tuple[Span, ...]
    chapter: int = 0

    @property
    def text(self) -> str:
        return "".join(s.text for s in self.spans)


@dataclass
class Entry:
    id: str = ""
    chapter: int = 0
    order: int = 0
    type: str | None = None
    author: str | None = None
    recipient: str | None = None
    date: date | None = None
    time: time | None = None
    time_estimated: bool = False
    place: str | None = None
    header: str | None = None
    blocks: list[Block] = field(default_factory=list)

    def text(self) -> str:
        return (self.header or "") + "".join(b.text for b in self.blocks)
