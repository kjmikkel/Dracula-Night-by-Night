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
    problems: tuple[Problem, ...] = ()  # what was wrong with the file's shape, found while loading it


FILE = "whos_who.yaml"
_KEYS = {FILE: {"groups", "people"}, "group": {"names"}, "person": {"group", "names", "lines"}}


def _duplicate_keys(node) -> list[str]:
    if not isinstance(node, yaml.MappingNode):
        return []
    seen: set[str] = set()
    twice: list[str] = []
    for key, _ in node.value:
        if key.value in seen and key.value not in twice:
            twice.append(key.value)
        seen.add(key.value)
    return twice


def _child(node, key: str):
    if isinstance(node, yaml.MappingNode):
        for k, v in node.value:
            if k.value == key:
                return v
    return None


def load_whos_who(yaml_text: str) -> WhosWho:
    """Loads the file, turning anything malformed into a named problem rather than an exception or a silent gap."""
    problems: list[Problem] = []

    def error(owner: str, message: str) -> None:
        problems.append(Problem("error", owner, message))

    try:
        root = yaml.compose(yaml_text, Loader=yaml.SafeLoader)
        data = yaml.safe_load(yaml_text)
    except yaml.YAMLError as e:
        return WhosWho((), (), (Problem("error", FILE, "does not parse: " + " ".join(str(e).split())),))
    if data is None:
        return WhosWho((), ())
    if not isinstance(data, dict):
        return WhosWho((), (), (Problem("error", FILE, "should be a mapping with groups and people"),))

    def unknown_keys(owner: str, kind: str, value: dict) -> None:
        for key in value:
            if key in _KEYS[kind]:
                continue
            if kind == FILE:
                error(str(key), "unknown top-level key: expected groups or people")
            else:
                error(owner, f"unknown key {key}")

    def ties(owner: str, what: str, items, key: str) -> tuple[Tie, ...]:
        if items is None:
            return ()
        if isinstance(items, dict):
            error(owner, f"{what}s should be a list: each one starts with a dash")
            items = [items]
        if not isinstance(items, list):
            error(owner, f"{what}s should be a list")
            return ()
        found = []
        for item in items:
            if not isinstance(item, dict):
                error(owner, f"{what} {item!r} should look like {{from: <entry id>, {key}: ...}}")
                continue
            found.append(Tie(str(item.get("from") or ""), str(item.get(key) or "")))
        return tuple(found)

    def entries_of(kind: str) -> dict:
        value = data.get(kind)
        for twice in _duplicate_keys(_child(root, kind)):
            error(twice, f"{kind[:-1] if kind == 'groups' else 'person'} {twice} appears twice")
        if value is None:
            return {}
        if not isinstance(value, dict):
            error(FILE, f"{kind} should be a mapping from ids")
            return {}
        return value

    unknown_keys(FILE, FILE, data)
    groups = []
    for gid, g in entries_of("groups").items():
        gid = str(gid)
        if not isinstance(g, dict):
            error(gid, "group should be a mapping with names")
            continue
        unknown_keys(gid, "group", g)
        groups.append(Group(gid, ties(gid, "group name", g.get("names"), "text")))
    people = []
    for pid, p in entries_of("people").items():
        pid = str(pid)
        if not isinstance(p, dict):
            error(pid, "person should be a mapping with group, names and lines")
            continue
        unknown_keys(pid, "person", p)
        people.append(Person(pid, ties(pid, "group", p.get("group"), "id"), ties(pid, "name", p.get("names"), "text"),
                             ties(pid, "line", p.get("lines"), "text")))
    return WhosWho(tuple(groups), tuple(people), tuple(problems))


def whos_who_problems(ww: WhosWho, entries: list[Entry]) -> list[Problem]:
    order = {e.id: e.order for e in entries}
    group_ids = {g.id for g in ww.groups}
    problems: list[Problem] = list(ww.problems)

    def error(owner: str, message: str) -> None:
        problems.append(Problem("error", owner, message))

    def check_ties(owner: str, what: str, ties: tuple[Tie, ...], key: str = "text") -> None:
        for t in ties:
            if not t.source:
                error(owner, f"{what} {t.value!r} has no from")
            elif t.source not in order:
                error(owner, f"{what} tied to unknown entry {t.source}")
            if not t.value.strip():
                error(owner, f"{what} from {t.source} has no {key}")

    def first(ties: tuple[Tie, ...]) -> Tie | None:
        """The tie the reader meets first: the earliest in story order, wherever it sits in the file."""
        return min((t for t in ties if t.source in order), key=lambda t: order[t.source], default=None)

    for g in ww.groups:
        check_ties(g.id, "group name", g.names)
        if not g.names:
            error(g.id, "group has no name")
    first_group_name = {g.id: order[t.source] for g in ww.groups if (t := first(g.names))}

    for p in ww.people:
        check_ties(p.id, "line", p.lines)
        check_ties(p.id, "name", p.names)
        check_ties(p.id, "group", p.groups, "id")
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
        first_name, first_group = first(p.names), first(p.groups)
        if first_name and order[first_name.source] > earliest:
            error(p.id, f"first name comes from {first_name.source}, after the earliest line")
        if first_group and order[first_group.source] > earliest:
            error(p.id, f"first group comes from {first_group.source}, after the earliest line")
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
        named = sorted(g.names, key=lambda t: by_id[t.source].order if t.source in by_id else -1)
        parts = [f"<h2>{escape(named[0].value if named else g.id)} <small>({escape(g.id)})</small></h2>",
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
