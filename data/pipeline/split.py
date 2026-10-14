from collections import Counter
from datetime import date

from .characters import Characters
from .classify import HeaderInfo, classify_header
from .dateline import parse_dateline
from .model import Block, Entry, Span

_QUOTES = set("“‘\"' ")
_NO_HEADER = HeaderInfo(None, None, None, None)


def split_entries(blocks: list[Block], chars: Characters) -> list[Entry]:
    entries: list[Entry] = []
    cur: Entry | None = None
    ctx = _NO_HEADER
    current: date | None = None
    i = 0
    while i < len(blocks):
        b = blocks[i]
        if b.role == "header":
            j = i
            texts = []
            while j < len(blocks) and blocks[j].role == "header":
                texts.append(blocks[j].text)
                j += 1
            text = "\n".join(texts)
            info = classify_header(text, chars)
            if info is None and cur is not None:
                cur.blocks.append(Block("note", (Span(text),), b.chapter))
            else:
                ctx = info or _NO_HEADER
                current = ctx.date or current
                cur = _new(entries, b.chapter, ctx, current, header=text)
            i = j
            continue

        if cur is None:
            cur = _new(entries, b.chapter, ctx, current, header=None)
        role = b.role
        lead = _leading_italic(b) if role in ("para", "signoff") else None
        dl = parse_dateline(lead, current) if lead else None
        if dl is not None:
            if role == "signoff":
                role = "dateline"
            if _has_body(cur):
                cur = _new(entries, b.chapter, ctx, dl.date or current, header=None)
            if dl.date is not None:
                cur.date = dl.date
                current = dl.date
            if dl.time is not None and cur.time is None:
                cur.time, cur.time_estimated = dl.time, dl.time_estimated
            if dl.place is not None and cur.place is None:
                cur.place = dl.place
        cur.blocks.append(Block(role, b.spans, b.chapter))
        i += 1

    for n, e in enumerate(entries):
        e.order = n
    return entries


def assign_ids(entries: list[Entry]) -> None:
    seen: Counter[str] = Counter()
    for e in entries:
        stem = f"{e.date.isoformat() if e.date else 'undated'}-{e.author or 'unknown'}"
        seen[stem] += 1
        e.id = f"{stem}-{seen[stem]:02d}"


def _new(entries: list[Entry], chapter: int, ctx: HeaderInfo, when: date | None, header: str | None) -> Entry:
    e = Entry(chapter=chapter, type=ctx.type, author=ctx.author, recipient=ctx.recipient,
              date=when, header=header)
    entries.append(e)
    return e


def _has_body(e: Entry) -> bool:
    return any(b.role != "note" for b in e.blocks)


def _leading_italic(b: Block) -> str | None:
    for s in b.spans:
        if not s.italic and all(ch in _QUOTES for ch in s.text):
            continue
        return s.text if s.italic else None
    return None
