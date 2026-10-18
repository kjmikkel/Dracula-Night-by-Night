"""The who's who (#204): hand-written profiles that grow with what the reader has read.

Every name, group move and line is tied to the entry that reveals it, and the app shows it only once that entry is
read. A person shows their first name and group until a later one's entry is read, so those must never come from an
entry later than the person's earliest line.
"""
from dataclasses import dataclass

import yaml

from .emit import dump_json
from .model import Entry
from .validate import Problem


@dataclass(frozen=True)
class Tie:
    """A piece of text (or a group id) shown once the entry [source] is read."""

    source: str
    value: str


@dataclass(frozen=True)
class Group:
    id: str
    names: tuple[Tie, ...]


@dataclass(frozen=True)
class Person:
    id: str
    groups: tuple[Tie, ...]
    names: tuple[Tie, ...]
    lines: tuple[Tie, ...]


@dataclass(frozen=True)
class WhosWho:
    groups: tuple[Group, ...]
    people: tuple[Person, ...]


def _ties(items, key: str) -> tuple[Tie, ...]:
    return tuple(Tie(str(i["from"]), str(i.get(key) or "")) for i in items or [])


def load_whos_who(yaml_text: str) -> WhosWho:
    data = yaml.safe_load(yaml_text) or {}
    groups = tuple(Group(gid, _ties(g.get("names"), "text")) for gid, g in (data.get("groups") or {}).items())
    people = tuple(
        Person(pid, _ties(p.get("group"), "id"), _ties(p.get("names"), "text"), _ties(p.get("lines"), "text"))
        for pid, p in (data.get("people") or {}).items()
    )
    return WhosWho(groups, people)


def whos_who_problems(ww: WhosWho, entries: list[Entry]) -> list[Problem]:
    order = {e.id: e.order for e in entries}
    group_ids = {g.id for g in ww.groups}
    problems: list[Problem] = []

    def error(owner: str, message: str) -> None:
        problems.append(Problem("error", owner, message))

    def check_ties(owner: str, what: str, ties: tuple[Tie, ...]) -> None:
        for t in ties:
            if t.source not in order:
                error(owner, f"{what} tied to unknown entry {t.source}")
            if not t.value.strip():
                error(owner, f"{what} from {t.source} is empty")

    for g in ww.groups:
        check_ties(g.id, "group name", g.names)
        if not g.names:
            error(g.id, "group has no name")
    first_group_name = {g.id: order.get(g.names[0].source) for g in ww.groups if g.names}

    for p in ww.people:
        check_ties(p.id, "line", p.lines)
        check_ties(p.id, "name", p.names)
        check_ties(p.id, "group", p.groups)
        for t in p.groups:
            if t.value and t.value not in group_ids:
                error(p.id, f"unknown group {t.value}")
        if not p.lines:
            error(p.id, "person has no lines")
            continue
        if not p.names or not p.groups:
            error(p.id, "person needs a name and a group")
            continue
        earliest = min((order[t.source] for t in p.lines if t.source in order), default=None)
        if earliest is None:
            continue
        if order.get(p.names[0].source, earliest) > earliest:
            error(p.id, f"first name comes from {p.names[0].source}, after the earliest line")
        if order.get(p.groups[0].source, earliest) > earliest:
            error(p.id, f"first group comes from {p.groups[0].source}, after the earliest line")
        for t in p.groups:
            named = first_group_name.get(t.value)
            joins = order.get(t.source)
            if named is not None and joins is not None and named > joins:
                error(t.value, f"named only after {p.id} joins it at {t.source}")
    return problems


def whos_who_json(ww: WhosWho, entries: list[Entry]) -> str:
    order = {e.id: e.order for e in entries}

    def sort(ties: tuple[Tie, ...]) -> list[Tie]:
        return sorted(ties, key=lambda t: order.get(t.source, -1))

    def texts(ties: tuple[Tie, ...]) -> list[dict]:
        return [{"from": t.source, "text": t.value} for t in sort(ties)]

    def first_line(p: Person) -> tuple[int, str]:
        return (min((order.get(t.source, -1) for t in p.lines), default=-1), p.id)

    return dump_json({
        "groups": [{"id": g.id, "names": texts(g.names)} for g in ww.groups],
        "people": [
            {
                "id": p.id,
                "groups": [{"from": t.source, "id": t.value} for t in sort(p.groups)],
                "names": texts(p.names),
                "lines": texts(p.lines),
            }
            for p in sorted(ww.people, key=first_line)
        ],
    })
