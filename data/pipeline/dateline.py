import re
from dataclasses import dataclass
from datetime import date, time

YEAR = 1893

MONTHS = {
    name: number
    for number, name in enumerate(
        ["january", "february", "march", "april", "may", "june", "july",
         "august", "september", "october", "november", "december"],
        start=1,
    )
}
_MONTH = "(?P<month>" + "|".join(MONTHS) + ")"
_DAY = r"(?P<day>\d{1,2})(?:-\d{1,2})?(?:st|nd|rd|th)?"
_DAY_MONTH = re.compile(rf"{_DAY}\s+{_MONTH}\b", re.I)
_MONTH_DAY = re.compile(rf"{_MONTH}\s+{_DAY}\b", re.I)
_RELATIVE = re.compile(r"^(?:the\s+)?(?:same\s+day|later)\b", re.I)
_STARTS_WITH_TIME = re.compile(
    r"^(?:\d{1,2}(?::\d{2})?\s*(?:[ap]\.\s*m\.|o'clock)"
    r"|(?:midnight|noon|morning|afternoon|evening|night)\b)",
    re.I,
)
_CLOCK = re.compile(r"(?P<h>\d{1,2})(?::(?P<m>\d{2}))?\s*(?:o'clock\s*)?(?P<ap>[ap])\.\s*m\.", re.I)
_OCLOCK = re.compile(r"(?P<h>\d{1,2})(?::(?P<m>\d{2}))?\s*o'clock", re.I)
# Order matters: longer phrases first. Exact periods are not estimates.
_PERIODS = [
    ("close to midnight", time(23, 45), True),
    ("before morning", time(4, 0), True),
    ("midnight", time(23, 59), False),
    ("afternoon", time(15, 0), True),
    ("noon", time(12, 0), False),
    ("morning", time(8, 0), True),
    ("evening", time(19, 0), True),
    ("night", time(22, 0), True),
]


@dataclass(frozen=True)
class Dateline:
    date: date | None
    time: time | None
    time_estimated: bool
    place: str | None
    later: bool


def parse_dateline(text: str, current: date | None) -> Dateline | None:
    t = _clean(text)
    if not t:
        return None
    direct = _direct(t, current)
    if direct is not None:
        return direct
    head, sep, tail = t.partition(", ")
    if sep and head[:1].isupper() and not any(ch.isdigit() for ch in head):
        inner = _direct(tail, current)
        if inner is not None:
            return Dateline(inner.date, inner.time, inner.time_estimated, head, inner.later)
    return None


def extract_date(text: str) -> date | None:
    t = _clean(text)
    for pattern in (_DAY_MONTH, _MONTH_DAY):
        m = pattern.search(t)
        if m:
            return _make_date(m)
    return None


def _direct(t: str, current: date | None) -> Dateline | None:
    m = _DAY_MONTH.match(t) or _MONTH_DAY.match(t)
    if m:
        when = _make_date(m)
        if when is None:
            return None
        rest = t[m.end():]
        clock, estimated = _time_in(rest)
        return Dateline(when, clock, estimated, None if clock else _place(rest), False)
    rel = _RELATIVE.match(t)
    if rel:
        rest = t[rel.end():]
        clock, estimated = _time_in(rest)
        return Dateline(extract_date(rest) or current, clock, estimated, None, True)
    if _STARTS_WITH_TIME.match(t):
        clock, estimated = _time_in(t)
        return Dateline(current, clock, estimated, None, True)
    return None


def _make_date(m: re.Match) -> date | None:
    try:
        return date(YEAR, MONTHS[m["month"].lower()], int(m["day"]))
    except ValueError:
        return None


def _time_in(text: str) -> tuple[time | None, bool]:
    m = _CLOCK.search(text)
    if m:
        hour = int(m["h"]) % 12 + (12 if m["ap"].lower() == "p" else 0)
        return time(hour, int(m["m"] or 0)), False
    m = _OCLOCK.search(text)
    if m:
        return time(int(m["h"]), int(m["m"] or 0)), False
    low = text.lower()
    for word, clock, estimated in _PERIODS:
        if re.search(rf"\b{word}\b", low):
            return clock, estimated
    return None, False


def _place(rest: str) -> str | None:
    p = rest.strip(" .,;:—-")
    if p and p[0].isupper() and not any(ch.isdigit() for ch in p):
        return p
    return None


def _clean(text: str) -> str:
    t = text.replace("’", "'").replace("‘", "'")
    t = t.strip().strip("“”\"'").strip()
    return re.sub(r"\s+", " ", t)
