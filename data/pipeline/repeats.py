"""Marks what the text says that the entry's header already shows (#177), so the app can choose to skip it.

Nothing is removed: the text stays as printed, and a block only gains the range that repeats the header.
"""
import re
from dataclasses import replace

from .characters import Characters
from .classify import classify_header
from .dateline import _DAY_MONTH, _MONTH_DAY, _clean, _make_date
from .model import Block, Entry

_QUOTES = set("“‘\"' ")
# What joins a dateline to the text after it: "4 May.—I found", or ".—" when the full stop isn't italic.
_JOIN = re.compile(r"^[.,]?\s*[—–-]*\s*")


def mark_repeats(entries: list[Entry], chars: Characters) -> None:
    for e in entries:
        e.header_names_parties = _names_parties(e, chars)
        i = next((i for i, b in enumerate(e.blocks) if b.role != "note"), None)
        if i is None:
            continue
        span = _repeated_dateline(e, e.blocks[i])
        if span is not None:
            e.blocks[i] = replace(e.blocks[i], repeats=span)


def _names_parties(e: Entry, chars: Characters) -> bool:
    info = classify_header(e.header, chars) if e.header else None
    return (info is not None and e.author is not None and e.recipient is not None
            and (info.author, info.recipient) == (e.author, e.recipient))


def _repeated_dateline(e: Entry, b: Block) -> tuple[int, int] | None:
    """The range of an opening dateline that says only the header's date (and place), with what joins it on."""
    start = 0
    for s in b.spans:
        if not s.italic and all(ch in _QUOTES for ch in s.text):
            start += len(s.text)
            continue
        if not s.italic or not _only_date_and_place(s.text, e):
            return None
        end = start + len(s.text)
        end += _JOIN.match(b.text[end:]).end()
        rest = b.text[:start] + b.text[end:]
        return (0, len(b.text)) if all(ch in _QUOTES for ch in rest) else (start, end)
    return None


def _only_date_and_place(text: str, e: Entry) -> bool:
    t = _clean(text)
    m = _DAY_MONTH.match(t) or _MONTH_DAY.match(t)
    if m is None or _make_date(m) != e.date:
        return False
    rest = t[m.end():].strip(" .,;:—-")
    return rest == "" or rest == e.place
