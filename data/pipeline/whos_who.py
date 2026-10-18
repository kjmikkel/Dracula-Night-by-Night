"""The who's who (#204): hand-written profiles that grow with what the reader has read.

Every name, group move and line is tied to the entry that reveals it, and the app shows it only once that entry is
read. A person shows their first name and group until a later one's entry is read, so those must never come from an
entry later than the person's earliest line.
"""
from dataclasses import dataclass
from html import escape

import yaml

from .characters import Characters
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


def whos_who_review_html(ww: WhosWho, entries: list[Entry], chars: Characters) -> str:
    """A page for checking every tie against the entry it comes from: a tie must say nothing that entry and the
    ones before it don't."""
    by_id = {e.id: e for e in entries}
    names = {r["id"]: r["name"] for r in chars.records}

    def source(entry_id: str) -> str:
        e = by_id.get(entry_id)
        if e is None:
            return f'<div class="src missing">unknown entry {escape(entry_id)}</div>'
        who = names.get(e.author, e.author or "")
        if e.recipient:
            who += " to " + names.get(e.recipient, e.recipient)
        when = e.date.isoformat() if e.date else "undated"
        text = " ".join(" ".join(b.text for b in e.blocks).split())[:200]
        return (f'<div class="src">{escape(when)} · {escape(who)} · <code>{escape(e.id)}</code>'
                f'<blockquote>{escape(text)}</blockquote></div>')

    def ties(kind: str, items: tuple[Tie, ...]) -> list[tuple[int, str]]:
        return [(by_id[t.source].order if t.source in by_id else -1,
                 f'<li><span class="kind">{kind}</span> {escape(t.value)}{source(t.source)}</li>') for t in items]

    sections = []
    for g in ww.groups:
        people = [p for p in ww.people if any(t.value == g.id for t in p.groups)]
        parts = [f"<h2>{escape(g.names[0].value if g.names else g.id)} <small>({escape(g.id)})</small></h2>",
                 "<ul>" + "".join(row for _, row in sorted(ties("group name", g.names))) + "</ul>"]
        for p in people:
            rows = ties("name", p.names) + ties("group", p.groups) + ties("line", p.lines)
            parts.append(f"<h3>{escape(p.id)}</h3><ul>" + "".join(row for _, row in sorted(rows, key=lambda r: r[0]))
                         + "</ul>")
        sections.append("<section>" + "".join(parts) + "</section>")
    return _REVIEW_TEMPLATE.replace("__BODY__", "".join(sections) or "<p>No profiles yet.</p>")


_REVIEW_TEMPLATE = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Who's Who Review</title>
<style>
:root { --bg: #f4ecdc; --panel: #fbf6ec; --ink: #2a1f1a; --muted: #75655a; --line: #dccdb4; --accent: #7a1f24; }
@media (prefers-color-scheme: dark) {
  :root:not([data-theme="light"]) {
    --bg: #141011; --panel: #1d1718; --ink: #ece2d4; --muted: #a39282; --line: #3a2e2c; --accent: #d06a6a;
  }
}
body { margin: 0 auto; max-width: 860px; padding: 16px; background: var(--bg); color: var(--ink);
  font: 15px/1.5 Georgia, "Times New Roman", serif; }
h1, h2 { color: var(--accent); } h2 small, code { color: var(--muted); font-size: 12px; }
section { background: var(--panel); border: 1px solid var(--line); border-radius: 6px; padding: 4px 16px 12px;
  margin-bottom: 16px; }
li { margin: 8px 0; } .kind { font-size: 12px; text-transform: uppercase; color: var(--muted); }
.src { font-size: 13px; color: var(--muted); } .missing { color: var(--accent); }
blockquote { margin: 2px 0 0 12px; font-style: italic; }
</style>
</head>
<body>
<h1>Who's who review</h1>
<p>Each name, group and line, in story order, beside the entry it is tied to.</p>
__BODY__
</body>
</html>
"""
