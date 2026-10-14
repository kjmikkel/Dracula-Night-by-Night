from collections import Counter
from dataclasses import dataclass
from datetime import date

from .model import TYPES, Entry

SEASON_START = date(1893, 5, 3)
SEASON_END = date(1893, 11, 6)
MISSING_TIME = "missing time"


@dataclass(frozen=True)
class Problem:
    level: str
    entry_id: str
    message: str


def validate(entries: list[Entry], character_ids: set[str], reference: str,
             previous_ids: set[str]) -> list[Problem]:
    problems: list[Problem] = []

    def error(eid: str, message: str) -> None:
        problems.append(Problem("error", eid, message))

    for e in entries:
        if e.type not in TYPES:
            error(e.id, f"invalid type '{e.type}'")
        if e.author is None:
            error(e.id, "missing author")
        elif e.author not in character_ids:
            error(e.id, f"unknown author '{e.author}'")
        if e.recipient is not None and e.recipient not in character_ids:
            error(e.id, f"unknown recipient '{e.recipient}'")
        if e.date is None:
            error(e.id, "missing date")
        elif not SEASON_START <= e.date <= SEASON_END:
            error(e.id, f"date {e.date.isoformat()} outside season")
        if e.time is None:
            error(e.id, MISSING_TIME)

    for eid, count in Counter(e.id for e in entries).items():
        if count > 1:
            error(eid, "duplicate id")
    if [e.order for e in entries] != list(range(len(entries))):
        error("*", "order is not contiguous from 0")

    ours = _normalise("".join(e.text() for e in entries))
    book = _normalise(reference)
    if ours != book:
        at = next((i for i, (a, b) in enumerate(zip(ours, book)) if a != b), min(len(ours), len(book)))
        error("*", f"text differs from book at character {at}: "
                   f"entries '...{ours[max(0, at - 30):at + 30]}...' vs book '...{book[max(0, at - 30):at + 30]}...'")

    current = {e.id for e in entries}
    for eid in sorted(previous_ids - current):
        problems.append(Problem("warning", eid, "id from previous build no longer exists"))
    return problems


def _normalise(text: str) -> str:
    return "".join(ch for ch in text.lower() if ch.isalnum())
