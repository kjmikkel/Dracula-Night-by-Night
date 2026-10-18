import json

from .characters import Characters
from .model import Block, Entry


def entry_to_dict(e: Entry) -> dict:
    d = {
        "id": e.id,
        "chapter": e.chapter,
        "order": e.order,
        "type": e.type,
        "author": e.author,
        "recipient": e.recipient,
        "date": e.date.isoformat() if e.date else None,
        "time": e.time.strftime("%H:%M") if e.time else None,
        "timeEstimated": e.time_estimated,
        "place": e.place,
        "header": e.header,
        "headerNamesParties": e.header_names_parties,
        "body": [_block_to_dict(b) for b in e.blocks],
    }
    if e.undated:
        d["undated"] = True
    return d


def _block_to_dict(b: Block) -> dict:
    d = {"role": b.role, "spans": [{"text": s.text, "italic": s.italic} for s in b.spans]}
    if b.repeats is not None:
        d["repeats"] = list(b.repeats)
    return d


def entries_json(entries: list[Entry]) -> str:
    data = [entry_to_dict(e) for e in sorted(entries, key=lambda e: e.order)]
    return _dump(data)


def characters_json(chars: Characters) -> str:
    return _dump(list(chars.records))


def _dump(data) -> str:
    return json.dumps(data, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
