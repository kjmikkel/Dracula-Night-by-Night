import re
from dataclasses import dataclass
from datetime import date

from .characters import Characters, normalise_name
from .dateline import extract_date

_PARTIES = re.compile(r"^(?P<kind>telegram|letter|report)\b(?: \(by hand\))?(?:,| from)? (?P<a>.+?) to (?P<b>.+)$")
_OWNED = re.compile(r"^(?P<a>.+?)'s (?P<kind>diary|journal|memorandum)\b")
_NOTE_LEFT = re.compile(r"^note left by (?P<a>.+?) in\b")
_MEMO_BY = re.compile(r"^memorandum (?:left )?by (?P<a>.+)$")
_NEWSPAPER = re.compile(r"gazette|dailygraph|cutting from")
_SPOKEN = re.compile(r"\bspoken by (?P<a>.+)$")


@dataclass(frozen=True)
class HeaderInfo:
    type: str | None
    author: str | None
    recipient: str | None
    date: date | None


def classify_header(text: str, chars: Characters) -> HeaderInfo | None:
    t = normalise_name(text.replace("\n", " "))
    when = extract_date(text)
    if t in ("note", "note."):
        return HeaderInfo("memo", None, None, when)
    if t.startswith("log of the"):
        return HeaderInfo("log", _resolve(t, chars), None, when)
    if _NEWSPAPER.search(t):
        return HeaderInfo("newspaper", _find(t, chars), None, when)
    m = _PARTIES.match(t)
    if m:
        kind = "telegram" if m["kind"] == "telegram" else "letter"
        return HeaderInfo(kind, _resolve(m["a"], chars), _resolve(m["b"], chars), when)
    if t.startswith("telegram"):
        return HeaderInfo("telegram", None, None, when)
    m = _NOTE_LEFT.match(t) or _MEMO_BY.match(t)
    if m:
        return HeaderInfo("memo", _resolve(m["a"], chars), None, when)
    m = _SPOKEN.search(t)
    if m:
        return HeaderInfo("memo", _resolve(m["a"], chars), None, when)
    m = _OWNED.match(t)
    if m:
        author = _resolve(m["a"], chars)
        if m["kind"] == "memorandum":
            return HeaderInfo("memo", author, None, when)
        return HeaderInfo(chars.diary_types.get(author, "diary"), author, None, when)
    return None


def _resolve(fragment: str, chars: Characters) -> str | None:
    f = normalise_name(fragment)
    for alias, cid in chars.aliases:
        if f.startswith(alias):
            return cid
    return None


def _find(text: str, chars: Characters) -> str | None:
    for alias, cid in chars.aliases:
        if alias in text:
            return cid
    return None
