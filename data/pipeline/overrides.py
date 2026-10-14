import copy
from datetime import date, time

import yaml

from .model import Entry

ALLOWED = {"time", "estimated", "date", "type", "author", "recipient", "place",
           "split_after", "merge_with_next", "note"}


class OverrideError(Exception):
    pass


def load_overrides(yaml_text: str) -> dict[str, dict]:
    data = yaml.safe_load(yaml_text) or {}
    if not isinstance(data, dict) or not all(isinstance(v, dict) for v in data.values()):
        raise OverrideError("overrides.yaml must map entry ids to mappings")
    return data


def apply_overrides(entries: list[Entry], overrides: dict[str, dict]) -> list[Entry]:
    for eid, fields in overrides.items():
        for key in fields:
            if key not in ALLOWED:
                raise OverrideError(f"{eid}: unknown key '{key}'")

    original = {e.id for e in entries}
    out: list[Entry] = []
    skip = False
    for idx, e in enumerate(entries):
        if skip:
            skip = False
            continue
        e = copy.deepcopy(e)
        o = overrides.get(e.id, {})
        if o.get("merge_with_next"):
            if idx + 1 >= len(entries):
                raise OverrideError(f"{e.id}: merge_with_next on the last entry")
            e.blocks += copy.deepcopy(entries[idx + 1].blocks)
            skip = True
        out.append(e)
        if "split_after" in o:
            k = o["split_after"]
            if not 0 <= k < len(e.blocks) - 1:
                raise OverrideError(f"{e.id}: split_after {k} out of range")
            tail = copy.deepcopy(e)
            tail.id, tail.header, tail.time, tail.time_estimated = f"{e.id}-b", None, None, False
            tail.blocks, e.blocks = e.blocks[k + 1:], e.blocks[:k + 1]
            out.append(tail)

    by_id = {e.id: e for e in out}
    for eid, o in overrides.items():
        if eid not in by_id and eid not in original:
            raise OverrideError(f"unknown entry id: {eid}")
        if eid not in by_id:
            continue
        e = by_id[eid]
        if "time" in o:
            if not isinstance(o["time"], str):
                raise OverrideError(f'{eid}: time must be a quoted "HH:MM" string')
            e.time = time.fromisoformat(o["time"])
        if "estimated" in o:
            e.time_estimated = bool(o["estimated"])
        if "date" in o:
            e.date = date.fromisoformat(str(o["date"]))
        for key in ("type", "author", "recipient", "place"):
            if key in o:
                setattr(e, key, o[key])

    for n, e in enumerate(out):
        e.order = n
    return out
