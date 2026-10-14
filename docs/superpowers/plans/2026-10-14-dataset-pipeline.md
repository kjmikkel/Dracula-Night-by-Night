# Story Dataset Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Python pipeline in `data/` that turns Project Gutenberg's HTML *Dracula* into a validated, deterministic `entries.json` of dated, timed, typed entries.

**Architecture:** Small single-purpose modules chained by `pipeline.cli.run_pipeline`: `source` (checksum) → `parse` (HTML → role-tagged blocks) → `split` (blocks → entries, using `dateline` and `classify`) → ids → `overrides` (hand-curated YAML) → `validate` (incl. text conservation) → `emit` (JSON). Every stage is a pure function over plain dataclasses from `model`, so each is unit-tested with small fixtures; the full book is tested in Tasks 9–10.

**Tech Stack:** Python 3.12, beautifulsoup4 4.12.3 (`html.parser`), PyYAML 6.0.2, pytest 8.3.3, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-10-14-dataset-design.md`

## Global Constraints

- Python `>=3.12`; dependencies pinned exactly: `beautifulsoup4==4.12.3`, `PyYAML==6.0.2`, `pytest==8.3.3`.
- Story year is **1893**. Valid entry dates: `1893-05-03` .. `1893-11-06` inclusive.
- Entry `type` ∈ `diary, phonograph, letter, telegram, newspaper, memo, log`.
- Block `role` ∈ `note, para, dateline, salutation, signoff, verse` (`header` exists only between `parse` and `split`).
- Entry id format: `<ISO date or "undated">-<author id or "unknown">-<2-digit sequence>`, e.g. `1893-05-03-jonathan_harker-01`; ids come from *parsed* date/author, before overrides. Split-off entries get suffix `-b`.
- JSON output: UTF-8, `ensure_ascii=False`, `indent=2`, `sort_keys=True`, trailing newline, LF line endings. Field names exactly: `id, chapter, order, type, author, recipient, date, time, timeEstimated, place, header, body` (body items: `role, spans`; spans: `text, italic`).
- Source file `data/source/pg345-images.html` is never modified; its SHA-256 lives in `data/source/CHECKSUM`.
- License: Apache-2.0. Repository: `kjmikkel/Dracula-Night-by-Night` (public); local clone folder `dracula-night-by-night/`.
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`; PR bodies end with `🤖 Generated with [Claude Code](https://claude.com/claude-code)`.

## Workflow (applies to every task 1–10)

Each task is one GitHub issue (issue number = task number, created in Task 0) and one pull request, built red-green:

```bash
git switch main && git pull --ff-only
git switch -c task-<N>-<slug>
# ... steps: failing test → run (RED) → minimal code → run (GREEN) → commit ...
git push -u origin HEAD
gh pr create --title "Task <N>: <name>" --body "Closes #<N>

<one-paragraph summary>

🤖 Generated with [Claude Code](https://claude.com/claude-code)"
gh pr merge --auto --squash --delete-branch
```

Then bind the PR in the desktop app (`ccd_pr` `bind_pr`) and turn **Auto-fix** on. Do not start the next task until the PR has merged (tasks build on each other). Do not poll CI; wait for the merge notification.

All commands below run from the `data/` directory with the virtualenv active (`python -m venv .venv`, then `.venv\Scripts\activate` on Windows / `source .venv/bin/activate` elsewhere, then `pip install -e ".[dev]"`).

## Review Focus

1. **Italic emphasis mistaken for a dateline** ("Firstly.", "Ground of inquiry.", "Whitby", "Written 18 July, …") must not start a new entry → negative cases in Task 3 and `test_italic_emphasis_does_not_split` in Task 5.
2. **Datelines wrapped in quotation marks** (telegrams: `“<i>4 September.</i>—…`, letters: `“<i>9 May.</i>`) must still be recognized → Task 3 quote case, Task 5 telegram test.
3. **A letter's dateline right after its header** must date that letter, not create an empty entry → `test_letter_dateline_dates_the_letter` in Task 5.
4. **Publisher adverts after "THE END" and the Gutenberg header/footer** must never reach an entry, while the closing NOTE (an un-anchored chapter after chapter XXVII) must → `test_stops_at_the_end`, `test_front_matter_skipped` (Task 2), `test_unanchored_chapter_after_the_story_is_kept`, `test_epilogue_is_last` (Task 9).
5. **Typos in `overrides.yaml`** (unknown key such as `tme:`, unknown id) must fail loudly, never be ignored → Task 6 error tests.

---

### Task 0: Bootstrap repository settings and issues (done directly on `main`, no PR)

The GitHub repo `kjmikkel/Dracula-Night-by-Night` already exists (public, with `README.md`, the Apache-2.0 `LICENSE` and a Java `.gitignore` from GitHub's templates); the local clone is the `dracula-night-by-night/` folder. The specs and this plan are untracked in `docs/`.

**Files:**
- Create: `.gitattributes`
- Modify: `.gitignore` (append Python entries)
- Commit (currently untracked): `docs/superpowers/specs/*.md`, `docs/superpowers/plans/*.md`

- [ ] **Step 1: Repo hygiene files**

`.gitattributes`:
```
* text=auto eol=lf
data/source/** binary
```

Append to `.gitignore`:
```
# Python
__pycache__/
*.egg-info/
.venv/
.pytest_cache/
```

- [ ] **Step 2: Commit to `main` and push** (the last direct push; everything after goes through PRs)

```bash
git add .gitattributes .gitignore docs
git commit -m "Add design specs, dataset plan and repo hygiene files

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
git push origin main
```

- [ ] **Step 3: Enable auto-merge and protect `main` so auto-merge waits for CI**

```bash
gh repo edit kjmikkel/Dracula-Night-by-Night --enable-auto-merge --enable-squash-merge --delete-branch-on-merge
gh api -X PUT repos/kjmikkel/Dracula-Night-by-Night/branches/main/protection --input - <<'EOF'
{"required_status_checks": {"strict": false, "contexts": ["data-tests"]},
 "enforce_admins": false, "required_pull_request_reviews": null, "restrictions": null}
EOF
gh api repos/kjmikkel/Dracula-Night-by-Night --jq .allow_auto_merge
```
Expected: the protection response contains `"contexts": ["data-tests"]`; the last command prints `true`.

- [ ] **Step 4: Create one issue per task, in order** (so issue #N = Task N)

```bash
gh issue create --title "Task 1: Python project, pinned source, CI" --body "See docs/superpowers/plans/2026-10-14-dataset-pipeline.md, Task 1."
gh issue create --title "Task 2: Parse HTML into role-tagged blocks" --body "See plan, Task 2."
gh issue create --title "Task 3: Dateline parser" --body "See plan, Task 3."
gh issue create --title "Task 4: Characters and header classification" --body "See plan, Task 4."
gh issue create --title "Task 5: Split blocks into entries and assign ids" --body "See plan, Task 5."
gh issue create --title "Task 6: Overrides" --body "See plan, Task 6."
gh issue create --title "Task 7: Validation and text conservation" --body "See plan, Task 7."
gh issue create --title "Task 8: JSON emit and CLI" --body "See plan, Task 8."
gh issue create --title "Task 9: Real-book parse fidelity" --body "See plan, Task 9."
gh issue create --title "Task 10: Editorial times and committed dataset" --body "See plan, Task 10."
gh issue list --limit 20
```
Expected: issues #1–#10 with matching titles.

---

### Task 1: Python project, pinned source, CI

**Files:**
- Create: `data/pyproject.toml`, `data/pipeline/__init__.py`, `data/pipeline/source.py`
- Create: `data/source/pg345-images.html` (copied), `data/source/CHECKSUM`
- Create: `data/tests/__init__.py`, `data/tests/conftest.py`, `data/tests/test_source.py`
- Create: `.github/workflows/data.yml`

**Interfaces:**
- Produces: `pipeline.source.load_source(source_dir: Path) -> str`, `pipeline.source.ChecksumError`; test fixture `book_html` (str) and constant `tests.conftest.DATA_DIR: Path`.

- [ ] **Step 1: Project file and package skeleton**

`data/pyproject.toml`:
```toml
[build-system]
requires = ["setuptools>=68"]
build-backend = "setuptools.build_meta"

[project]
name = "dracula-pipeline"
version = "0.1.0"
requires-python = ">=3.12"
dependencies = ["beautifulsoup4==4.12.3", "PyYAML==6.0.2"]

[project.optional-dependencies]
dev = ["pytest==8.3.3"]

[tool.setuptools]
packages = ["pipeline"]

[tool.pytest.ini_options]
testpaths = ["tests"]
pythonpath = ["."]
```

`data/pipeline/__init__.py`:
```python
"""Builds the Dracula Night by Night story dataset."""
```

`data/tests/__init__.py`: empty file.

- [ ] **Step 2: Pin the source text**

```bash
mkdir -p source
cp "C:/Users/kjmik/Documents/Dracula/Dracula/pg345-h/pg345-images.html" source/
python -c "import hashlib, pathlib; d = pathlib.Path('source'); d.joinpath('CHECKSUM').write_text(hashlib.sha256(d.joinpath('pg345-images.html').read_bytes()).hexdigest() + '  pg345-images.html\n')"
cat source/CHECKSUM
```
Expected: one line `<64 hex chars>  pg345-images.html`.

- [ ] **Step 3: Write the failing tests**

`data/tests/conftest.py`:
```python
from pathlib import Path

import pytest

DATA_DIR = Path(__file__).resolve().parent.parent


@pytest.fixture(scope="session")
def book_html() -> str:
    from pipeline.source import load_source

    return load_source(DATA_DIR / "source")
```

`data/tests/test_source.py`:
```python
import hashlib

import pytest

from pipeline.source import ChecksumError, load_source


def _write(tmp_path, content: bytes, checksum: str):
    (tmp_path / "pg345-images.html").write_bytes(content)
    (tmp_path / "CHECKSUM").write_text(f"{checksum}  pg345-images.html\n")


def test_returns_text_when_checksum_matches(tmp_path):
    content = "<p>Dracula ’</p>".encode("utf-8")
    _write(tmp_path, content, hashlib.sha256(content).hexdigest())
    assert load_source(tmp_path) == "<p>Dracula ’</p>"


def test_raises_when_checksum_differs(tmp_path):
    _write(tmp_path, b"<p>changed</p>", "0" * 64)
    with pytest.raises(ChecksumError, match="pg345-images.html"):
        load_source(tmp_path)


def test_pinned_book_loads(book_html):
    assert "Bistritz" in book_html
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `pytest tests/test_source.py -v`
Expected: FAIL / ERROR with `ModuleNotFoundError: No module named 'pipeline.source'`.

- [ ] **Step 5: Implement `source.py`**

`data/pipeline/source.py`:
```python
import hashlib
from pathlib import Path

SOURCE_FILE = "pg345-images.html"


class ChecksumError(Exception):
    pass


def load_source(source_dir: Path) -> str:
    data = (source_dir / SOURCE_FILE).read_bytes()
    expected = (source_dir / "CHECKSUM").read_text(encoding="utf-8").split()[0]
    actual = hashlib.sha256(data).hexdigest()
    if actual != expected:
        raise ChecksumError(f"{SOURCE_FILE}: expected sha256 {expected}, got {actual}")
    return data.decode("utf-8")
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `pytest tests/test_source.py -v`
Expected: 3 passed.

- [ ] **Step 7: Add CI**

`.github/workflows/data.yml`:
```yaml
name: data

on:
  pull_request:
  push:
    branches: [main]

jobs:
  data-tests:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: data
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with:
          python-version: "3.12"
      - run: pip install -e ".[dev]"
      - run: pytest -q
```

- [ ] **Step 8: Commit, push, PR (see Workflow)**

```bash
git add -A
git commit -m "Add pipeline project, pinned Gutenberg source and CI

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Parse HTML into role-tagged blocks

**Files:**
- Create: `data/pipeline/model.py`, `data/pipeline/parse.py`
- Create: `data/tests/markup.py`, `data/tests/test_parse.py`

**Interfaces:**
- Produces:
  - `pipeline.model.Span(text: str, italic: bool = False)` (frozen)
  - `pipeline.model.Block(role: str, spans: tuple[Span, ...], chapter: int = 0)` (frozen) with property `text -> str`
  - `pipeline.model.Entry` (mutable dataclass: `id: str = ""`, `chapter: int = 0`, `order: int = 0`, `type: str | None`, `author: str | None`, `recipient: str | None`, `date: date | None`, `time: time | None`, `time_estimated: bool = False`, `place: str | None`, `header: str | None`, `blocks: list[Block]`), with method `text() -> str`
  - `pipeline.model.TYPES`, `pipeline.model.ROLES`
  - `pipeline.parse.parse_book(html: str) -> list[Block]`
  - `pipeline.parse.reference_text(html: str) -> str`

- [ ] **Step 1: Write the model** (pure data, exercised by the parse tests)

`data/pipeline/model.py`:
```python
from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date, time

TYPES = ("diary", "phonograph", "letter", "telegram", "newspaper", "memo", "log")
ROLES = ("note", "para", "dateline", "salutation", "signoff", "verse")


@dataclass(frozen=True)
class Span:
    text: str
    italic: bool = False


@dataclass(frozen=True)
class Block:
    role: str
    spans: tuple[Span, ...]
    chapter: int = 0

    @property
    def text(self) -> str:
        return "".join(s.text for s in self.spans)


@dataclass
class Entry:
    id: str = ""
    chapter: int = 0
    order: int = 0
    type: str | None = None
    author: str | None = None
    recipient: str | None = None
    date: date | None = None
    time: time | None = None
    time_estimated: bool = False
    place: str | None = None
    header: str | None = None
    blocks: list[Block] = field(default_factory=list)

    def text(self) -> str:
        return (self.header or "") + "".join(b.text for b in self.blocks)
```

- [ ] **Step 2: Write the HTML test helpers and failing tests**

`data/tests/markup.py`:
```python
def page(*chapters: str) -> str:
    return "<html><body>" + "".join(chapters) + "</body></html>"


def chapter(n: int, inner: str, subtitle: str | None = "JONATHAN HARKER’S JOURNAL") -> str:
    small = f"<br><br>\n<small>{subtitle}</small>" if subtitle else ""
    return (
        f'<div class="chapter">\n<h2><a id="chap{n:02d}"></a>CHAPTER {n}{small}</h2>\n'
        f"{inner}\n</div><!--end chapter-->\n"
    )
```

`data/tests/test_parse.py`:
```python
from pipeline.model import Block, Span
from pipeline.parse import parse_book, reference_text
from tests.markup import chapter, page


def roles_and_texts(blocks):
    return [(b.role, b.text) for b in blocks]


def test_front_matter_skipped():
    front = '<div class="chapter"><h2>D R A C U L A</h2><p>Preface text.</p></div>'
    blocks = parse_book(page(front, chapter(1, "<p>Hello.</p>")))
    assert roles_and_texts(blocks) == [
        ("header", "JONATHAN HARKER’S JOURNAL"),
        ("para", "Hello."),
    ]
    assert all(b.chapter == 1 for b in blocks)


def test_italic_spans_and_whitespace():
    html = page(chapter(1, '<p><i>3 May. Bistritz.</i>—Left Munich at 8:35 '
                           '<span class="smcap">P. M.</span>, on\n1st May.</p>', subtitle=None))
    [block] = parse_book(html)
    assert block.spans == (
        Span("3 May. Bistritz.", True),
        Span("—Left Munich at 8:35 P. M., on 1st May.", False),
    )


def test_roles_by_class():
    inner = """
<p class="c">(<i>Kept in shorthand.</i>)</p>
<p class="c"><i>Letter from Miss Mina Murray to Miss Lucy Westenra.</i></p>
<p class="letra"><i>Dr. Seward’s Diary.</i></p>
<p class="r">
“<i>9 May.</i><br>
</p>
<p class="nind">
“My dearest Lucy,—<br>
</p>
<p class="nind">W<small>HEN</small> we arrived at the Berkeley Hotel.</p>
<div class="poem"><div class="stanza">
<span class="i0">“Denn die Todten reiten schnell”—<br></span>
<span class="i0">(“For the dead travel fast.”)<br></span>
</div></div>
"""
    blocks = parse_book(page(chapter(1, inner, subtitle=None)))
    assert roles_and_texts(blocks) == [
        ("note", "(Kept in shorthand.)"),
        ("header", "Letter from Miss Mina Murray to Miss Lucy Westenra."),
        ("header", "Dr. Seward’s Diary."),
        ("signoff", "“9 May."),
        ("salutation", "“My dearest Lucy,—"),
        ("para", "WHEN we arrived at the Berkeley Hotel."),
        ("verse", "“Denn die Todten reiten schnell”—\n(“For the dead travel fast.”)"),
    ]


def test_multiline_signoff_keeps_line_breaks():
    inner = '<p class="r">\n“Your friend,<br>\n“<span class="smcap">Dracula</span>.”<br>\n</p>'
    [block] = parse_book(page(chapter(1, inner, subtitle=None)))
    assert block == Block("signoff", (Span("“Your friend,\n“Dracula.”"),), 1)


def test_blank_paragraphs_skipped():
    blocks = parse_book(page(chapter(1, "<p>\u00a0</p><p> </p><p>Text.</p>", subtitle=None)))
    assert roles_and_texts(blocks) == [("para", "Text.")]


def test_stops_at_the_end():
    inner = '<p>Last words.</p>\n<p class="c">\nTHE END<br>\n</p>\n<p>Ask for our free list!</p>'
    blocks = parse_book(page(chapter(27, inner, subtitle=None)))
    assert roles_and_texts(blocks) == [("para", "Last words.")]


def test_reference_text_is_independent_of_roles():
    front = '<div class="chapter"><p>Preface.</p></div>'
    inner = '<p>Body.</p><p class="c">THE END</p><p>Advert.</p>'
    text = reference_text(page(front, chapter(1, inner)))
    assert "JONATHAN HARKER’S JOURNAL" in text
    assert "Body." in text
    assert "CHAPTER" not in text
    assert "Preface" not in text
    assert "Advert" not in text
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `pytest tests/test_parse.py -v`
Expected: ERROR `ModuleNotFoundError: No module named 'pipeline.parse'`.

- [ ] **Step 4: Implement `parse.py`**

`data/pipeline/parse.py`:
```python
import re

from bs4 import BeautifulSoup, Comment, NavigableString, Tag

from .model import Block, Span

_CHAP_ID = re.compile(r"^chap(\d+)$")
_WS = re.compile(r"[ \t\r\n\u00a0]+")
_AROUND_NL = re.compile(r" *\n *")
_END = "THE END"


def parse_book(html: str) -> list[Block]:
    soup = BeautifulSoup(html, "html.parser")
    blocks: list[Block] = []
    for div in soup.select("div.chapter"):
        anchor = div.find("a", id=_CHAP_ID)
        if anchor is None:
            continue
        chapter = int(_CHAP_ID.match(anchor["id"]).group(1))
        for el in div.find_all(["h2", "p", "div"]):
            if el.name == "h2":
                small = el.find("small")
                if small is not None:
                    blocks.append(Block("header", _spans(small), chapter))
                continue
            if el.name == "div":
                if "stanza" in el.get("class", []):
                    blocks.append(Block("verse", _spans(el), chapter))
                continue
            if el.find_parent("div", class_="stanza") or el.find_parent("p"):
                continue
            spans = _spans(el)
            text = "".join(s.text for s in spans)
            if not text.strip():
                continue
            if text.startswith(_END):
                return blocks
            blocks.append(Block(_role(el, text), spans, chapter))
    return blocks


def reference_text(html: str) -> str:
    """Book body text extracted without any role logic, for conservation checks."""
    soup = BeautifulSoup(html, "html.parser")
    parts: list[str] = []
    for div in soup.select("div.chapter"):
        if div.find("a", id=_CHAP_ID) is None:
            continue
        for h2 in div.find_all("h2"):
            small = h2.find("small")
            h2.replace_with(NavigableString(small.get_text() if small else ""))
        text = div.get_text()
        end = text.find(_END)
        if end != -1:
            parts.append(text[:end])
            break
        parts.append(text)
    return "".join(parts)


def _role(el: Tag, text: str) -> str:
    classes = set(el.get("class", []))
    if "letra" in classes:
        return "header"
    if "c" in classes:
        return "note" if text.startswith("(") else "header"
    if "r" in classes:
        return "signoff"
    if "nind" in classes and len(text) < 60 and text.rstrip("”\" ").endswith((",—", ",", ":—")):
        return "salutation"
    return "para"


def _spans(tag: Tag) -> tuple[Span, ...]:
    raw: list[Span] = []

    def walk(node: Tag, italic: bool) -> None:
        for child in node.children:
            if isinstance(child, Comment):
                continue
            if isinstance(child, NavigableString):
                raw.append(Span(_WS.sub(" ", str(child)), italic))
            elif child.name == "br":
                raw.append(Span("\n", italic))
            else:
                walk(child, italic or child.name in ("i", "em"))

    walk(tag, False)
    merged: list[Span] = []
    for s in raw:
        if merged and merged[-1].italic == s.italic:
            merged[-1] = Span(merged[-1].text + s.text, s.italic)
        else:
            merged.append(s)
    cleaned = [Span(_AROUND_NL.sub("\n", s.text), s.italic) for s in merged]
    if cleaned:
        cleaned[0] = Span(cleaned[0].text.lstrip(" \n"), cleaned[0].italic)
        cleaned[-1] = Span(cleaned[-1].text.rstrip(" \n"), cleaned[-1].italic)
    return tuple(s for s in cleaned if s.text)
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `pytest tests/test_parse.py -v`
Expected: 7 passed.

- [ ] **Step 6: Commit, push, PR**

```bash
git add -A
git commit -m "Parse Gutenberg HTML into role-tagged blocks

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Dateline parser

**Files:**
- Create: `data/pipeline/dateline.py`, `data/tests/test_dateline.py`

**Interfaces:**
- Produces:
  - `pipeline.dateline.YEAR = 1893`
  - `pipeline.dateline.Dateline(date: date | None, time: time | None, time_estimated: bool, place: str | None, later: bool)` (frozen)
  - `pipeline.dateline.parse_dateline(text: str, current: date | None) -> Dateline | None`
  - `pipeline.dateline.extract_date(text: str) -> date | None` (finds a date anywhere in text)

- [ ] **Step 1: Write the failing tests**

`data/tests/test_dateline.py`:
```python
from datetime import date, time

import pytest

from pipeline.dateline import Dateline, extract_date, parse_dateline


def d(month, day):
    return date(1893, month, day)


@pytest.mark.parametrize(
    "text, current, expected",
    [
        ("3 May. Bistritz.", None, Dateline(d(5, 3), None, False, "Bistritz", False)),
        ("5 May. The Castle.", d(5, 4), Dateline(d(5, 5), None, False, "The Castle", False)),
        ("15 October, Varna.", None, Dateline(d(10, 15), None, False, "Varna", False)),
        ("Later.", d(5, 15), Dateline(d(5, 15), None, False, None, True)),
        ("Later: the Morning of 16 May.", d(5, 15), Dateline(d(5, 16), time(8, 0), True, None, True)),
        ("24 June, before morning.", None, Dateline(d(6, 24), time(4, 0), True, None, False)),
        ("Same day, later.", d(6, 25), Dateline(d(6, 25), None, False, None, True)),
        ("The same day.", d(8, 1), Dateline(d(8, 1), None, False, None, True)),
        ("Midnight.", d(5, 8), Dateline(d(5, 8), time(23, 59), False, None, True)),
        ("10 p. m.", d(7, 19), Dateline(d(7, 19), time(22, 0), False, None, True)),
        ("11 a. m.", d(7, 20), Dateline(d(7, 20), time(11, 0), False, None, True)),
        ("Same day, 11 o’clock p. m.", d(8, 10), Dateline(d(8, 10), time(23, 0), False, None, True)),
        ("11 August, 3 a. m.", None, Dateline(d(8, 11), time(3, 0), False, None, False)),
        ("Same day, noon.", d(8, 11), Dateline(d(8, 11), time(12, 0), False, None, True)),
        ("5 November, afternoon.", None, Dateline(d(11, 5), time(15, 0), True, None, False)),
        ("Hillingham, 24 August.", None, Dateline(d(8, 24), None, False, "Hillingham", False)),
        ("Piccadilly, 12:30 o’clock.", d(10, 3), Dateline(d(10, 3), time(12, 30), False, "Piccadilly", True)),
        ("October 30. Night.", None, Dateline(d(10, 30), time(22, 0), True, None, False)),
        ("3-4 October, close to midnight.", None, Dateline(d(10, 3), time(23, 45), True, None, False)),
        ("2 October 10 p. m.", None, Dateline(d(10, 2), time(22, 0), False, None, False)),
        ("“4 September.", None, Dateline(d(9, 4), None, False, None, False)),
        ("29 September, in train to London.", None, Dateline(d(9, 29), None, False, None, False)),
        ("Later, midnight.", d(10, 5), Dateline(d(10, 5), time(23, 59), False, None, True)),
        ("11 October, Evening.", None, Dateline(d(10, 11), time(19, 0), True, None, False)),
    ],
)
def test_parses_datelines(text, current, expected):
    assert parse_dateline(text, current) == expected


@pytest.mark.parametrize(
    "text",
    [
        "Whitby",
        "Firstly.",
        "Ground of inquiry.",
        "Varna to Whitby.",
        "Written 18 July, things so strange happening, that I shall keep accurate note",
        "Letter from Miss Mina Murray to Miss Lucy Westenra",
        "We are, dear Sirs, Faithfully yours",
        "Dr. Seward’s Diary.",
        "",
    ],
)
def test_rejects_non_datelines(text):
    assert parse_dateline(text, d(7, 1)) is None


def test_invalid_day_is_not_a_date():
    assert parse_dateline("31 June.", None) is None


@pytest.mark.parametrize(
    "text, expected",
    [
        ("“The Pall Mall Gazette,” 18 September.", d(9, 18)),
        ("Telegram, October 24th.", d(10, 24)),
        ("CUTTING FROM “THE DAILYGRAPH,” 8 AUGUST", d(8, 8)),
        ("Dr. Seward’s Diary.", None),
    ],
)
def test_extract_date(text, expected):
    assert extract_date(text) == expected
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `pytest tests/test_dateline.py -v`
Expected: ERROR `ModuleNotFoundError: No module named 'pipeline.dateline'`.

- [ ] **Step 3: Implement `dateline.py`**

`data/pipeline/dateline.py`:
```python
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
```

The place-prefix branch only runs when the whole text is not itself a dateline, and requires a capitalised, digit-free head ("Hillingham", "Piccadilly"), so "Same day, later." and "Written 18 July, …" never take it.

- [ ] **Step 4: Run tests to verify they pass**

Run: `pytest tests/test_dateline.py -v`
Expected: all passed (24 + 9 + 1 + 4 = 38).

- [ ] **Step 5: Commit, push, PR**

```bash
git add -A
git commit -m "Add dateline parser for dates, times, places and Later markers

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Characters and header classification

**Files:**
- Create: `data/characters.yaml`, `data/pipeline/characters.py`, `data/pipeline/classify.py`
- Create: `data/tests/test_classify.py`

**Interfaces:**
- Consumes: `pipeline.dateline.extract_date`
- Produces:
  - `pipeline.characters.Characters` (dataclass: `records: tuple[dict, ...]`, `aliases: tuple[tuple[str, str], ...]` longest-first, `diary_types: dict[str, str]`; property `ids -> set[str]`)
  - `pipeline.characters.load_characters(yaml_text: str) -> Characters`
  - `pipeline.characters.normalise_name(text: str) -> str`
  - `pipeline.classify.HeaderInfo(type: str | None, author: str | None, recipient: str | None, date: date | None)` (frozen)
  - `pipeline.classify.classify_header(text: str, chars: Characters) -> HeaderInfo | None`
  - test fixture `chars` (real `characters.yaml`), added to `tests/conftest.py`

- [ ] **Step 1: Write `characters.yaml`**

`data/characters.yaml`:
```yaml
# id: display name, short name, aliases used in headers (matched case-insensitively as prefixes)
jonathan_harker:
  name: Jonathan Harker
  short: Jonathan
  aliases: [jonathan harker]
mina_harker:
  name: Mina Harker
  short: Mina
  aliases: [mina harker, mina murray, miss mina murray, miss wilhelmina murray, mrs. harker]
lucy_westenra:
  name: Lucy Westenra
  short: Lucy
  aliases: [lucy westenra, miss lucy westenra]
john_seward:
  name: Dr. John Seward
  short: Seward
  diary_type: phonograph
  aliases: [dr. seward, john seward, seward]
arthur_holmwood:
  name: Arthur Holmwood
  short: Arthur
  aliases: [arthur holmwood, hon. arthur holmwood, lord godalming]
quincey_morris:
  name: Quincey P. Morris
  short: Quincey
  aliases: [quincey p. morris, quincey morris]
van_helsing:
  name: Abraham Van Helsing
  short: Van Helsing
  aliases: [van helsing, abraham van helsing, dr. van helsing]
sister_agatha:
  name: Sister Agatha
  short: Sister Agatha
  aliases: [sister agatha]
patrick_hennessey:
  name: Dr. Patrick Hennessey
  short: Hennessey
  aliases: [patrick hennessey]
billington_and_son:
  name: Samuel F. Billington & Son
  short: Billington
  aliases: [samuel f. billington, messrs. billington]
carter_paterson:
  name: Carter, Paterson & Co.
  short: Carter, Paterson
  aliases: ["messrs. carter, paterson"]
mitchell_sons_candy:
  name: Mitchell, Sons & Candy
  short: Mitchell
  aliases: ["mitchell, sons and candy"]
rufus_smith:
  name: Rufus Smith
  short: Rufus Smith
  aliases: [rufus smith]
demeter_captain:
  name: Captain of the Demeter
  short: Demeter
  aliases: [log of the demeter]
dailygraph:
  name: The Dailygraph
  short: Dailygraph
  aliases: [dailygraph]
pall_mall_gazette:
  name: The Pall Mall Gazette
  short: Pall Mall Gazette
  aliases: [pall mall gazette]
westminster_gazette:
  name: The Westminster Gazette
  short: Westminster Gazette
  aliases: [westminster gazette]
```

- [ ] **Step 2: Add the `chars` fixture and write failing tests**

Append to `data/tests/conftest.py`:
```python
@pytest.fixture(scope="session")
def chars():
    from pipeline.characters import load_characters

    return load_characters((DATA_DIR / "characters.yaml").read_text(encoding="utf-8"))
```

`data/tests/test_classify.py`:
```python
from datetime import date

import pytest

from pipeline.characters import load_characters
from pipeline.classify import HeaderInfo, classify_header


def test_aliases_are_longest_first(chars):
    lengths = [len(alias) for alias, _ in chars.aliases]
    assert lengths == sorted(lengths, reverse=True)
    assert "john_seward" in chars.ids
    assert chars.diary_types["john_seward"] == "phonograph"


def test_load_characters_records_sorted():
    c = load_characters("b:\n  name: B\n  short: B\na:\n  name: A\n  short: A\n  aliases: [Ay]\n")
    assert [r["id"] for r in c.records] == ["a", "b"]
    assert c.aliases == (("ay", "a"),)


@pytest.mark.parametrize(
    "header, expected",
    [
        ("JONATHAN HARKER’S JOURNAL", HeaderInfo("diary", "jonathan_harker", None, None)),
        ("Dr. Seward’s Diary—continued.", HeaderInfo("phonograph", "john_seward", None, None)),
        ("Lucy Westenra’s Diary", HeaderInfo("diary", "lucy_westenra", None, None)),
        ("Mina Harker’s Memorandum.", HeaderInfo("memo", "mina_harker", None, None)),
        ("Dr. Van Helsing’s Memorandum.", HeaderInfo("memo", "van_helsing", None, None)),
        ("Memorandum by Abraham Van Helsing.", HeaderInfo("memo", "van_helsing", None, None)),
        ("Memorandum left by Lucy Westenra.", HeaderInfo("memo", "lucy_westenra", None, None)),
        ("Note left by Van Helsing in his portmanteau, Berkeley Hotel directed to John Seward, M. D.",
         HeaderInfo("memo", "van_helsing", None, None)),
        ("Letter from Miss Mina Murray to Miss Lucy Westenra.",
         HeaderInfo("letter", "mina_harker", "lucy_westenra", None)),
        ("Letter, Lucy Westenra to Mina Murray .", HeaderInfo("letter", "lucy_westenra", "mina_harker", None)),
        ("Letter (by hand), Van Helsing to Mrs. Harker.", HeaderInfo("letter", "van_helsing", "mina_harker", None)),
        ("Letter, Mina Harker to Lucy Westenra. (Unopened by her.)",
         HeaderInfo("letter", "mina_harker", "lucy_westenra", None)),
        ("Letter, Samuel F. Billington & Son, Solicitors, Whitby, to Messrs. Carter, Paterson & Co., London.",
         HeaderInfo("letter", "billington_and_son", "carter_paterson", None)),
        ("Report from Patrick Hennessey, M. D., M. R. C. S. L. K. Q. C. P. I., etc., etc., to John Seward, M. D.",
         HeaderInfo("letter", "patrick_hennessey", "john_seward", None)),
        ("Telegram, Seward, London, to Van Helsing, Amsterdam.",
         HeaderInfo("telegram", "john_seward", "van_helsing", None)),
        ("Telegram from Arthur Holmwood to Quincey P. Morris.",
         HeaderInfo("telegram", "arthur_holmwood", "quincey_morris", None)),
        ("Telegram, October 24th.\nRufus Smith, Lloyd’s, London, to Lord Godalming, care of H. B. M. Vice-Consul, Varna.",
         HeaderInfo("telegram", None, "arthur_holmwood", date(1893, 10, 24))),
        ("LOG OF THE “DEMETER.”\nVarna to Whitby.", HeaderInfo("log", "demeter_captain", None, None)),
        ("CUTTING FROM “THE DAILYGRAPH,” 8 AUGUST",
         HeaderInfo("newspaper", "dailygraph", None, date(1893, 8, 8))),
        ("“The Pall Mall Gazette,” 18 September.\nTHE ESCAPED WOLF.",
         HeaderInfo("newspaper", "pall_mall_gazette", None, date(1893, 9, 18))),
    ],
)
def test_classifies_real_headers(chars, header, expected):
    assert classify_header(header, chars) == expected


@pytest.mark.parametrize("header", ["“FINIS.”", "DRACULA.", "THE ESCAPED WOLF."])
def test_unclassifiable_headers_return_none(chars, header):
    assert classify_header(header, chars) is None
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `pytest tests/test_classify.py -v`
Expected: ERROR `ModuleNotFoundError: No module named 'pipeline.characters'`.

- [ ] **Step 4: Implement `characters.py` and `classify.py`**

`data/pipeline/characters.py`:
```python
import re
from dataclasses import dataclass

import yaml


@dataclass
class Characters:
    records: tuple[dict, ...]
    aliases: tuple[tuple[str, str], ...]
    diary_types: dict[str, str]

    @property
    def ids(self) -> set[str]:
        return {r["id"] for r in self.records}


def normalise_name(text: str) -> str:
    t = text.replace("’", "'").replace("‘", "'")
    t = re.sub(r"[“”\"]", "", t)
    return re.sub(r"\s+", " ", t).strip().lower()


def load_characters(yaml_text: str) -> Characters:
    data = yaml.safe_load(yaml_text) or {}
    records, aliases, diary_types = [], [], {}
    for cid, rec in data.items():
        records.append({"id": cid, "name": rec["name"], "short": rec["short"]})
        for alias in rec.get("aliases", []):
            aliases.append((normalise_name(alias), cid))
        if "diary_type" in rec:
            diary_types[cid] = rec["diary_type"]
    aliases.sort(key=lambda pair: (-len(pair[0]), pair[0]))
    return Characters(tuple(sorted(records, key=lambda r: r["id"])), tuple(aliases), diary_types)
```

`data/pipeline/classify.py`:
```python
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


@dataclass(frozen=True)
class HeaderInfo:
    type: str | None
    author: str | None
    recipient: str | None
    date: date | None


def classify_header(text: str, chars: Characters) -> HeaderInfo | None:
    t = normalise_name(text.replace("\n", " "))
    when = extract_date(text)
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
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `pytest tests/test_classify.py -v`
Expected: all passed (2 + 20 + 3 = 25).

- [ ] **Step 6: Commit, push, PR**

```bash
git add -A
git commit -m "Classify document headers into type, author and recipient

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Split blocks into entries and assign ids

**Files:**
- Create: `data/pipeline/split.py`, `data/tests/test_split.py`

**Interfaces:**
- Consumes: `Block`, `Span`, `Entry` (Task 2); `parse_dateline` (Task 3); `classify_header`, `HeaderInfo`, `Characters` (Task 4)
- Produces:
  - `pipeline.split.split_entries(blocks: list[Block], chars: Characters) -> list[Entry]` (ids empty, `order` set 0..n-1)
  - `pipeline.split.assign_ids(entries: list[Entry]) -> None` (mutates `id`)

- [ ] **Step 1: Write the failing tests**

`data/tests/test_split.py`:
```python
from datetime import date, time

from pipeline.model import Block, Entry, Span
from pipeline.split import assign_ids, split_entries


def H(text):
    return Block("header", (Span(text),), 1)


def N(text):
    return Block("note", (Span(text),), 1)


def P(text, lead=None, role="para"):
    spans = ((Span(lead, True),) if lead else ()) + ((Span(text),) if text else ())
    return Block(role, spans, 1)


def test_diary_days_become_entries(chars):
    entries = split_entries(
        [
            H("JONATHAN HARKER’S JOURNAL"),
            N("(Kept in shorthand.)"),
            P("—Left Munich.", "3 May. Bistritz."),
            P("More text."),
            P("—I found.", "4 May."),
        ],
        chars,
    )
    assert len(entries) == 2
    first, second = entries
    assert (first.type, first.author, first.date, first.place) == (
        "diary", "jonathan_harker", date(1893, 5, 3), "Bistritz")
    assert first.header == "JONATHAN HARKER’S JOURNAL"
    assert [b.role for b in first.blocks] == ["note", "para", "para"]
    assert (second.header, second.author, second.date) == (None, "jonathan_harker", date(1893, 5, 4))
    assert [e.order for e in entries] == [0, 1]


def test_later_and_midnight_inherit_date(chars):
    entries = split_entries(
        [H("Jonathan Harker’s Journal"), P("—a.", "8 May."), P("—b.", "Later."), P("—c.", "Midnight.")],
        chars,
    )
    assert [(e.date, e.time) for e in entries] == [
        (date(1893, 5, 8), None),
        (date(1893, 5, 8), None),
        (date(1893, 5, 8), time(23, 59)),
    ]


def test_letter_dateline_dates_the_letter(chars):
    entries = split_entries(
        [
            H("Letter from Miss Mina Murray to Miss Lucy Westenra."),
            Block("signoff", (Span("“"), Span("9 May.", True)), 1),
            P("“My dearest Lucy,—", role="salutation"),
            P("Forgive my long delay."),
        ],
        chars,
    )
    [letter] = entries
    assert (letter.type, letter.author, letter.recipient, letter.date) == (
        "letter", "mina_harker", "lucy_westenra", date(1893, 5, 9))
    assert [b.role for b in letter.blocks] == ["dateline", "salutation", "para"]


def test_telegram_with_quoted_dateline(chars):
    entries = split_entries(
        [
            H("Telegram, Seward, London, to Van Helsing, Amsterdam."),
            Block("para", (Span("“"), Span("4 September.", True), Span("—Patient still better to-day.”")), 1),
        ],
        chars,
    )
    [telegram] = entries
    assert (telegram.type, telegram.author, telegram.recipient, telegram.date) == (
        "telegram", "john_seward", "van_helsing", date(1893, 9, 4))


def test_unclassifiable_header_becomes_note(chars):
    entries = split_entries([H("Jonathan Harker’s Journal"), P("—a.", "8 May."), H("“FINIS.”")], chars)
    [entry] = entries
    assert (entry.blocks[-1].role, entry.blocks[-1].text) == ("note", "“FINIS.”")


def test_consecutive_headers_merge_and_header_date_applies(chars):
    entries = split_entries(
        [
            H("“The Pall Mall Gazette,” 18 September."),
            H("THE ESCAPED WOLF."),
            P("After many inquiries."),
        ],
        chars,
    )
    [paper] = entries
    assert paper.header == "“The Pall Mall Gazette,” 18 September.\nTHE ESCAPED WOLF."
    assert (paper.type, paper.author, paper.date) == ("newspaper", "pall_mall_gazette", date(1893, 9, 18))


def test_italic_emphasis_does_not_split(chars):
    entries = split_entries(
        [H("Mina Harker’s Memorandum."), P("—x.", "30 October."), P("—the river.", "Firstly.")],
        chars,
    )
    assert len(entries) == 1


def test_assign_ids_sequences_per_date_and_author():
    entries = [
        Entry(author="mina_harker", date=date(1893, 9, 30)),
        Entry(author="mina_harker", date=date(1893, 9, 30)),
        Entry(author=None, date=None),
    ]
    assign_ids(entries)
    assert [e.id for e in entries] == [
        "1893-09-30-mina_harker-01",
        "1893-09-30-mina_harker-02",
        "undated-unknown-01",
    ]
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `pytest tests/test_split.py -v`
Expected: ERROR `ModuleNotFoundError: No module named 'pipeline.split'`.

- [ ] **Step 3: Implement `split.py`**

`data/pipeline/split.py`:
```python
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `pytest tests/test_split.py -v`
Expected: 8 passed.

- [ ] **Step 5: Commit, push, PR**

```bash
git add -A
git commit -m "Split blocks into dated entries and assign stable ids

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Overrides

**Files:**
- Create: `data/overrides.yaml`, `data/pipeline/overrides.py`, `data/tests/test_overrides.py`

**Interfaces:**
- Consumes: `Entry`, `Block`, `Span`
- Produces:
  - `pipeline.overrides.OverrideError(Exception)`
  - `pipeline.overrides.load_overrides(yaml_text: str) -> dict[str, dict]`
  - `pipeline.overrides.apply_overrides(entries: list[Entry], overrides: dict[str, dict]) -> list[Entry]` (returns new list; renumbers `order`)

- [ ] **Step 1: Create the empty overrides file**

`data/overrides.yaml`:
```yaml
# Hand-curated editorial decisions, keyed by entry id.
# Keys: time ("HH:MM"), estimated (bool), date ("1893-MM-DD"), type, author,
#       recipient, place, split_after (block index), merge_with_next (bool), note (free text).
{}
```

- [ ] **Step 2: Write the failing tests**

`data/tests/test_overrides.py`:
```python
from datetime import date, time

import pytest

from pipeline.model import Block, Entry, Span
from pipeline.overrides import OverrideError, apply_overrides, load_overrides


def entry(eid, *texts):
    return Entry(id=eid, author="mina_harker", date=date(1893, 9, 30),
                 blocks=[Block("para", (Span(t),), 1) for t in texts])


def test_sets_fields():
    [e] = apply_overrides([entry("a", "x")], {"a": {"time": "21:30", "estimated": True,
                                                   "place": "Whitby", "date": "1893-10-01",
                                                   "note": "evening writing"}})
    assert (e.time, e.time_estimated, e.place, e.date) == (time(21, 30), True, "Whitby", date(1893, 10, 1))


def test_unknown_id_is_an_error():
    with pytest.raises(OverrideError, match="unknown entry id: gone"):
        apply_overrides([entry("a", "x")], {"gone": {"time": "10:00"}})


def test_unknown_key_is_an_error():
    with pytest.raises(OverrideError, match="unknown key 'tme'"):
        apply_overrides([entry("a", "x")], {"a": {"tme": "10:00"}})


def test_unquoted_time_is_an_error():
    # YAML reads an unquoted 21:30 as the base-60 integer 1290
    with pytest.raises(OverrideError, match="quoted"):
        apply_overrides([entry("a", "x")], load_overrides("a:\n  time: 21:30\n"))


def test_split_after_creates_b_entry():
    out = apply_overrides([entry("a", "x", "y", "z")], {"a": {"split_after": 0, "time": "08:00"},
                                                        "a-b": {"time": "14:00"}})
    assert [e.id for e in out] == ["a", "a-b"]
    assert [b.text for b in out[1].blocks] == ["y", "z"]
    assert (out[0].time, out[1].time) == (time(8, 0), time(14, 0))
    assert out[1].header is None
    assert [e.order for e in out] == [0, 1]


def test_split_index_out_of_range_is_an_error():
    with pytest.raises(OverrideError, match="split_after"):
        apply_overrides([entry("a", "x")], {"a": {"split_after": 0}})


def test_merge_with_next():
    out = apply_overrides([entry("a", "x"), entry("b", "y"), entry("c", "z")], {"a": {"merge_with_next": True}})
    assert [e.id for e in out] == ["a", "c"]
    assert [b.text for b in out[0].blocks] == ["x", "y"]


def test_load_overrides_rejects_non_mapping():
    assert load_overrides("{}") == {}
    with pytest.raises(OverrideError):
        load_overrides("- just a list")
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `pytest tests/test_overrides.py -v`
Expected: ERROR `ModuleNotFoundError: No module named 'pipeline.overrides'`.

- [ ] **Step 4: Implement `overrides.py`**

`data/pipeline/overrides.py`:
```python
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
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `pytest tests/test_overrides.py -v`
Expected: 8 passed.

- [ ] **Step 6: Commit, push, PR**

```bash
git add -A
git commit -m "Apply hand-curated overrides with loud failure on stale ids and typos

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Validation and text conservation

**Files:**
- Create: `data/pipeline/validate.py`, `data/tests/test_validate.py`

**Interfaces:**
- Consumes: `Entry`, `TYPES`
- Produces:
  - `pipeline.validate.Problem(level: str, entry_id: str, message: str)` (frozen; level `"error"` or `"warning"`)
  - `pipeline.validate.MISSING_TIME = "missing time"`
  - `pipeline.validate.validate(entries: list[Entry], character_ids: set[str], reference: str, previous_ids: set[str]) -> list[Problem]`

- [ ] **Step 1: Write the failing tests**

`data/tests/test_validate.py`:
```python
from datetime import date, time

from pipeline.model import Block, Entry, Span
from pipeline.validate import MISSING_TIME, Problem, validate

IDS = {"mina_harker", "lucy_westenra"}


def good(eid="a", text="Hello there.", order=0):
    return Entry(id=eid, order=order, type="diary", author="mina_harker", date=date(1893, 9, 30),
                 time=time(21, 0), blocks=[Block("para", (Span(text),), 1)])


def messages(problems):
    return {(p.level, p.entry_id, p.message) for p in problems}


def test_valid_entries_have_no_problems():
    assert validate([good()], IDS, "Hello there.", set()) == []


def test_field_errors():
    e = good()
    e.type, e.author, e.recipient, e.date, e.time = "poem", None, "count", date(1893, 11, 7), None
    assert messages(validate([e], IDS, "Hello there.", set())) == {
        ("error", "a", "invalid type 'poem'"),
        ("error", "a", "missing author"),
        ("error", "a", "unknown recipient 'count'"),
        ("error", "a", "date 1893-11-07 outside season"),
        ("error", "a", MISSING_TIME),
    }


def test_missing_date_and_unknown_author():
    e = good()
    e.date, e.author = None, "renfield"
    assert messages(validate([e], IDS, "Hello there.", set())) == {
        ("error", "a", "missing date"),
        ("error", "a", "unknown author 'renfield'"),
    }


def test_duplicate_ids_and_order_gaps():
    problems = validate([good("a", "x", 0), good("a", "y", 2)], IDS, "xy", set())
    assert ("error", "a", "duplicate id") in messages(problems)
    assert ("error", "*", "order is not contiguous from 0") in messages(problems)


def test_text_conservation_ignores_punctuation_and_case():
    assert validate([good(text="“Hello,” there!")], IDS, "hello there", set()) == []


def test_text_conservation_reports_first_difference():
    [problem] = validate([good(text="Hello there.")], IDS, "Hello where.", set())
    assert problem.level == "error" and problem.entry_id == "*"
    assert problem.message.startswith("text differs from book at character 5")


def test_vanished_previous_ids_are_warnings():
    assert validate([good()], IDS, "Hello there.", {"a", "old"}) == [
        Problem("warning", "old", "id from previous build no longer exists"),
    ]
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `pytest tests/test_validate.py -v`
Expected: ERROR `ModuleNotFoundError: No module named 'pipeline.validate'`.

- [ ] **Step 3: Implement `validate.py`**

`data/pipeline/validate.py`:
```python
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `pytest tests/test_validate.py -v`
Expected: 7 passed.

- [ ] **Step 5: Commit, push, PR**

```bash
git add -A
git commit -m "Validate entries, conserve book text and track vanished ids

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: JSON emit and CLI

**Files:**
- Create: `data/pipeline/emit.py`, `data/pipeline/cli.py`, `data/pipeline/__main__.py`
- Create: `data/tests/test_emit.py`, `data/tests/test_cli.py`

**Interfaces:**
- Consumes: everything above.
- Produces:
  - `pipeline.emit.entry_to_dict(e: Entry) -> dict`, `pipeline.emit.entries_json(entries: list[Entry]) -> str`, `pipeline.emit.characters_json(chars: Characters) -> str`
  - `pipeline.cli.PipelineRun(entries: list[Entry], problems: list[Problem], chars: Characters)`
  - `pipeline.cli.run_pipeline(data_dir: Path) -> PipelineRun`
  - `pipeline.cli.main(argv: list[str] | None = None) -> int` with subcommands `build [--strict]` and `report`; `--data-dir` defaults to the `data/` directory.

- [ ] **Step 1: Write the failing tests**

`data/tests/test_emit.py`:
```python
import json
from datetime import date, time

from pipeline.characters import load_characters
from pipeline.emit import characters_json, entries_json, entry_to_dict
from pipeline.model import Block, Entry, Span


def sample(order=0, eid="1893-05-03-jonathan_harker-01"):
    return Entry(id=eid, chapter=1, order=order, type="diary", author="jonathan_harker",
                 date=date(1893, 5, 3), time=time(20, 35), time_estimated=False, place="Bistritz",
                 header="JONATHAN HARKER’S JOURNAL",
                 blocks=[Block("para", (Span("3 May. Bistritz.", True), Span("—Left Munich")), 1)])


def test_entry_to_dict_matches_schema():
    assert entry_to_dict(sample()) == {
        "id": "1893-05-03-jonathan_harker-01", "chapter": 1, "order": 0, "type": "diary",
        "author": "jonathan_harker", "recipient": None, "date": "1893-05-03", "time": "20:35",
        "timeEstimated": False, "place": "Bistritz", "header": "JONATHAN HARKER’S JOURNAL",
        "body": [{"role": "para", "spans": [{"text": "3 May. Bistritz.", "italic": True},
                                            {"text": "—Left Munich", "italic": False}]}],
    }


def test_entries_json_is_sorted_by_order_and_stable():
    text = entries_json([sample(1, "b"), sample(0, "a")])
    assert [e["id"] for e in json.loads(text)] == ["a", "b"]
    assert text.endswith("\n") and "’" in text
    assert text == entries_json([sample(0, "a"), sample(1, "b")])


def test_characters_json():
    chars = load_characters("lucy:\n  name: Lucy Westenra\n  short: Lucy\n  aliases: [lucy]\n")
    assert json.loads(characters_json(chars)) == [{"id": "lucy", "name": "Lucy Westenra", "short": "Lucy"}]
```

`data/tests/test_cli.py`:
```python
import hashlib

from pipeline.cli import main
from tests.markup import chapter, page

BOOK = page(chapter(1, "<p><i>3 May. Bistritz.</i>—Left Munich at 8:35 P. M.</p>"
                       "<p><i>4 May.</i>—I found.</p>"))
CHARS = "jonathan_harker:\n  name: Jonathan Harker\n  short: Jonathan\n  aliases: [jonathan harker]\n"
OVERRIDES = (
    '1893-05-03-jonathan_harker-01:\n  time: "20:35"\n'
    '1893-05-04-jonathan_harker-01:\n  time: "21:00"\n  estimated: true\n'
)


def make_data_dir(tmp_path, overrides="{}\n"):
    src = tmp_path / "source"
    src.mkdir()
    raw = BOOK.encode("utf-8")
    (src / "pg345-images.html").write_bytes(raw)
    (src / "CHECKSUM").write_text(hashlib.sha256(raw).hexdigest() + "  pg345-images.html\n")
    (tmp_path / "characters.yaml").write_text(CHARS, encoding="utf-8")
    (tmp_path / "overrides.yaml").write_text(overrides, encoding="utf-8")
    return tmp_path


def test_build_fails_without_times_and_writes_nothing(tmp_path, capsys):
    d = make_data_dir(tmp_path)
    assert main(["build", "--data-dir", str(d)]) == 1
    assert "1893-05-04-jonathan_harker-01: missing time" in capsys.readouterr().out
    assert not (d / "out" / "entries.json").exists()


def test_report_lists_entries_without_time(tmp_path, capsys):
    d = make_data_dir(tmp_path)
    assert main(["report", "--data-dir", str(d)]) == 0
    out = capsys.readouterr().out
    assert "1893-05-04-jonathan_harker-01" in out
    assert "2 entries without a time" in out


def test_build_succeeds_and_is_deterministic(tmp_path):
    d = make_data_dir(tmp_path, overrides=OVERRIDES)
    assert main(["build", "--data-dir", str(d)]) == 0
    first = (d / "out" / "entries.json").read_bytes()
    assert main(["build", "--data-dir", str(d)]) == 0
    assert (d / "out" / "entries.json").read_bytes() == first
    assert b'"time": "20:35"' in first
    assert (d / "out" / "characters.json").exists()
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `pytest tests/test_emit.py tests/test_cli.py -v`
Expected: ERROR `ModuleNotFoundError: No module named 'pipeline.emit'`.

- [ ] **Step 3: Implement `emit.py`, `cli.py`, `__main__.py`**

`data/pipeline/emit.py`:
```python
import json

from .characters import Characters
from .model import Entry


def entry_to_dict(e: Entry) -> dict:
    return {
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
        "body": [
            {"role": b.role, "spans": [{"text": s.text, "italic": s.italic} for s in b.spans]}
            for b in e.blocks
        ],
    }


def entries_json(entries: list[Entry]) -> str:
    data = [entry_to_dict(e) for e in sorted(entries, key=lambda e: e.order)]
    return _dump(data)


def characters_json(chars: Characters) -> str:
    return _dump(list(chars.records))


def _dump(data) -> str:
    return json.dumps(data, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
```

`data/pipeline/cli.py`:
```python
import argparse
import json
from dataclasses import dataclass
from pathlib import Path

from .characters import Characters, load_characters
from .emit import characters_json, entries_json
from .model import Entry
from .overrides import apply_overrides, load_overrides
from .parse import parse_book, reference_text
from .source import load_source
from .split import assign_ids, split_entries
from .validate import Problem, validate

DATA_DIR = Path(__file__).resolve().parent.parent


@dataclass
class PipelineRun:
    entries: list[Entry]
    problems: list[Problem]
    chars: Characters


def run_pipeline(data_dir: Path) -> PipelineRun:
    html = load_source(data_dir / "source")
    chars = load_characters((data_dir / "characters.yaml").read_text(encoding="utf-8"))
    entries = split_entries(parse_book(html), chars)
    assign_ids(entries)
    entries = apply_overrides(entries, load_overrides((data_dir / "overrides.yaml").read_text(encoding="utf-8")))
    problems = validate(entries, chars.ids, reference_text(html), _previous_ids(data_dir))
    return PipelineRun(entries, problems, chars)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="pipeline")
    parser.add_argument("command", choices=["build", "report"])
    parser.add_argument("--strict", action="store_true", help="treat warnings as errors")
    parser.add_argument("--data-dir", type=Path, default=DATA_DIR)
    args = parser.parse_args(argv)
    run = run_pipeline(args.data_dir)
    return _build(run, args.data_dir, args.strict) if args.command == "build" else _report(run)


def _build(run: PipelineRun, data_dir: Path, strict: bool) -> int:
    for p in run.problems:
        print(f"{p.level}: {p.entry_id}: {p.message}")
    failing = [p for p in run.problems if p.level == "error" or strict]
    if failing:
        print(f"build failed: {len(failing)} problem(s)")
        return 1
    out = data_dir / "out"
    out.mkdir(exist_ok=True)
    (out / "entries.json").write_bytes(entries_json(run.entries).encode("utf-8"))
    (out / "characters.json").write_bytes(characters_json(run.chars).encode("utf-8"))
    print(f"wrote {len(run.entries)} entries")
    return 0


def _report(run: PipelineRun) -> int:
    missing = [e for e in run.entries if e.time is None]
    for e in missing:
        first = " ".join(e.text().split())[:80]
        print(f"{e.id}\t{e.date}\t{first}")
    print(f"{len(missing)} entries without a time")
    return 0


def _previous_ids(data_dir: Path) -> set[str]:
    path = data_dir / "out" / "entries.json"
    if not path.exists():
        return set()
    return {e["id"] for e in json.loads(path.read_text(encoding="utf-8"))}
```

Note: `_build` prints each problem as `"{level}: {id}: {message}"`; the CLI test asserts on the substring `"1893-05-04-jonathan_harker-01: missing time"`, which this format contains.

`data/pipeline/__main__.py`:
```python
import sys

from .cli import main

sys.exit(main())
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `pytest -v`
Expected: all tests pass (Tasks 1–8).

- [ ] **Step 5: Commit, push, PR**

```bash
git add -A
git commit -m "Emit deterministic JSON and add build/report CLI

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Real-book parse fidelity

Goal: the full book produces **no errors other than `missing time`**, and three defects that the fixture tests cannot see are fixed. (These were found by running Tasks 1–8 against the real book while writing this plan.)

1. **The closing NOTE is dropped.** "Seven years ago…" sits in a `div.chapter` with no `chapNN` anchor, so `parse_book` *and* `reference_text` skip it, and conservation cannot notice.
2. **Header runs over-merge.** `<p class="c"><i>Rufus Smith, Lloyd's, London, to Lord Godalming…</i></p>` directly followed by `<p class="letra"><i>Dr. Seward's Diary.</i></p>` merges into one header that `_OWNED` reads as *Rufus Smith's diary*, so Seward's diary until the next header is attributed to `rufus_smith`. Likewise `“FINIS.”` is glued onto the next *Westminster Gazette* header.
3. **The Demeter log's first days are not split.** "On 6 July we finished…", "On 11 July…" are plain (non-italic) paragraphs.

**Files:**
- Create: `data/tests/test_real_book.py`
- Modify: `data/pipeline/parse.py` (`parse_book`, `reference_text`, new `_story_divs`), `data/pipeline/classify.py` (`classify_header`), `data/pipeline/split.py` (`split_entries`, new `_header_groups`, `_log_day`)
- Modify: `data/tests/test_parse.py`, `data/tests/test_classify.py`, `data/tests/test_split.py`, `data/overrides.yaml`

**Interfaces:**
- Consumes: `pipeline.cli.run_pipeline`, `pipeline.validate.MISSING_TIME`, `tests.conftest.DATA_DIR`; `Dateline`, `extract_date` from Task 3.
- Produces: no new public names; `classify_header("NOTE", chars)` now returns `HeaderInfo("memo", None, None, None)`.

- [ ] **Step 1: Write the failing real-book tests**

`data/tests/test_real_book.py`:
```python
from datetime import date

import pytest

from pipeline.cli import run_pipeline
from pipeline.validate import MISSING_TIME
from tests.conftest import DATA_DIR

DIARISTS = {"jonathan_harker", "mina_harker", "lucy_westenra", "john_seward"}


@pytest.fixture(scope="module")
def run():
    return run_pipeline(DATA_DIR)


def test_no_structural_errors(run):
    structural = [p for p in run.problems if p.level == "error" and p.message != MISSING_TIME]
    assert structural == []


def test_first_entry_is_harker_in_bistritz(run):
    first = run.entries[0]
    assert (first.id, first.type, first.place) == ("1893-05-03-jonathan_harker-01", "diary", "Bistritz")


def test_story_spans_the_season(run):
    dates = [e.date for e in run.entries]
    assert min(dates) == date(1893, 5, 3)
    assert max(dates) == date(1893, 11, 6)


def test_plausible_entry_count(run):
    assert 200 <= len(run.entries) <= 450


def test_epilogue_is_last(run):
    last = run.entries[-1]
    assert (last.header, last.author) == ("NOTE", "jonathan_harker")
    assert "Seven years ago" in last.text()


def test_diaries_belong_to_diarists(run):
    wrong = [(e.id, e.author) for e in run.entries if e.type in ("diary", "phonograph") and e.author not in DIARISTS]
    assert wrong == []


def test_demeter_log_is_split_by_day(run):
    log_dates = [e.date for e in run.entries if e.type == "log"]
    assert date(1893, 7, 6) in log_dates and date(1893, 8, 4) in log_dates
    assert len(log_dates) >= 15


def test_finis_is_not_part_of_a_header(run):
    assert [e.id for e in run.entries if e.header and "FINIS" in e.header] == []
```

- [ ] **Step 2: Run to verify they fail**

Run: `pytest tests/test_real_book.py -v`
Expected: FAIL — `test_epilogue_is_last` (last entry is Mina's 6 November journal), `test_diaries_belong_to_diarists` (`rufus_smith` entries), `test_demeter_log_is_split_by_day` (10 log entries), `test_finis_is_not_part_of_a_header`. The other four pass already.

- [ ] **Step 3: Write the failing unit tests for the three fixes**

Append to `data/tests/test_parse.py`:
```python
def test_unanchored_chapter_after_the_story_is_kept():
    note = '<div class="chapter"><h2>NOTE</h2><p>Seven years ago.</p><p class="c">THE END</p></div>'
    html = page(chapter(27, "<p>Last.</p>", subtitle=None), note)
    blocks = parse_book(html)
    assert roles_and_texts(blocks) == [("para", "Last."), ("header", "NOTE"), ("para", "Seven years ago.")]
    assert blocks[-1].chapter == 27
    text = reference_text(html)
    assert "NOTE" in text and "Seven years ago." in text
```

Append to `data/tests/test_classify.py`:
```python
def test_closing_note_header(chars):
    assert classify_header("NOTE", chars) == HeaderInfo("memo", None, None, None)
```

Append to `data/tests/test_split.py`:
```python
def test_unclassifiable_header_before_a_real_one_stays_with_previous_entry(chars):
    entries = split_entries(
        [
            H("Jonathan Harker’s Journal"),
            P("—a.", "24 October."),
            H("Rufus Smith, Lloyd’s, London, to Lord Godalming."),
            H("Dr. Seward’s Diary."),
            P("—b.", "25 October."),
        ],
        chars,
    )
    assert [(e.author, e.header) for e in entries] == [
        ("jonathan_harker", "Jonathan Harker’s Journal"),
        ("john_seward", "Dr. Seward’s Diary."),
    ]
    assert (entries[0].blocks[-1].role, entries[0].blocks[-1].text) == (
        "note", "Rufus Smith, Lloyd’s, London, to Lord Godalming.")


def test_log_days_split_on_plain_paragraphs(chars):
    entries = split_entries(
        [
            H("LOG OF THE “DEMETER.”"),
            P("", "Written 18 July, things so strange happening."),
            P("On 6 July we finished taking in cargo."),
            P("On 11 July at dawn entered Bosphorus."),
        ],
        chars,
    )
    assert [(e.type, e.date) for e in entries] == [
        ("log", None),
        ("log", date(1893, 7, 6)),
        ("log", date(1893, 7, 11)),
    ]
```

- [ ] **Step 4: Run to verify they fail**

Run: `pytest tests/test_parse.py tests/test_classify.py tests/test_split.py -v`
Expected: the four new tests FAIL; all earlier tests still pass.

- [ ] **Step 5: Fix `parse.py` — keep chapter divs after CHAPTER I even without an anchor**

In `data/pipeline/parse.py`, replace the loop header of `parse_book` and the h2 branch:
```python
    blocks: list[Block] = []
    chapter = 0
    for div, anchor in _story_divs(soup):
        if anchor is not None:
            chapter = int(_CHAP_ID.match(anchor["id"]).group(1))
        for el in div.find_all(["h2", "p", "div"]):
            if el.name == "h2":
                small = el.find("small")
                if small is not None:
                    blocks.append(Block("header", _spans(small), chapter))
                elif anchor is None:
                    blocks.append(Block("header", _spans(el), chapter))
                continue
```
(the rest of the loop body is unchanged). Replace the loop in `reference_text`:
```python
    parts: list[str] = []
    for div, anchor in _story_divs(soup):
        if anchor is not None:
            for h2 in div.find_all("h2"):
                small = h2.find("small")
                h2.replace_with(NavigableString(small.get_text() if small else ""))
        text = div.get_text()
        end = text.find(_END)
        if end != -1:
            parts.append(text[:end])
            break
        parts.append(text)
    return "".join(parts)
```
Add above `_role`:
```python
def _story_divs(soup: BeautifulSoup) -> list[tuple[Tag, Tag | None]]:
    """Chapter divs from CHAPTER I onwards, with their chapNN anchor (None for e.g. the closing NOTE)."""
    out: list[tuple[Tag, Tag | None]] = []
    for div in soup.select("div.chapter"):
        anchor = div.find("a", id=_CHAP_ID)
        if anchor is not None or out:
            out.append((div, anchor))
    return out
```

- [ ] **Step 6: Fix `classify.py` — recognise the closing NOTE**

In `classify_header`, directly after `when = extract_date(text)`:
```python
    if t in ("note", "note."):
        return HeaderInfo("memo", None, None, when)
```

- [ ] **Step 7: Fix `split.py` — group header runs and split log days**

Replace `data/pipeline/split.py` with:
```python
import re
from collections import Counter
from datetime import date

from .characters import Characters
from .classify import HeaderInfo, classify_header
from .dateline import Dateline, extract_date, parse_dateline
from .model import Block, Entry, Span

_QUOTES = set("“‘\"' ")
_NO_HEADER = HeaderInfo(None, None, None, None)
_LOG_DAY = re.compile(r"^On \d{1,2} [A-Z][a-z]+")


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
            while j < len(blocks) and blocks[j].role == "header":
                j += 1
            for text, info in _header_groups(blocks[i:j], chars):
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
        if dl is None and ctx.type == "log" and role == "para":
            dl = _log_day(b)
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


def _header_groups(run: list[Block], chars: Characters) -> list[tuple[str, HeaderInfo | None]]:
    """Each header that classifies on its own starts a group; unclassifiable lines join the group before them."""
    groups: list[list[str]] = []
    for b in run:
        if not groups or classify_header(b.text, chars) is not None:
            groups.append([b.text])
        else:
            groups[-1].append(b.text)
    return [("\n".join(g), classify_header("\n".join(g), chars)) for g in groups]


def _log_day(b: Block) -> Dateline | None:
    """Ship's log paragraphs such as "On 6 July we finished taking in cargo" start a new day."""
    m = _LOG_DAY.match(b.text)
    when = extract_date(m.group(0)) if m else None
    return Dateline(when, None, False, None, False) if when else None


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
```

- [ ] **Step 8: Run the unit tests**

Run: `pytest tests/test_parse.py tests/test_classify.py tests/test_split.py -v`
Expected: all pass.

- [ ] **Step 9: Add the editorial overrides the book needs**

Replace the `{}` line in `data/overrides.yaml` with:
```yaml
1893-08-09-demeter_captain-01:
  date: "1893-07-18"
  note: "Captain's preface is headed 'Written 18 July'; it inherited the cutting's date"
1893-10-24-jonathan_harker-02:
  type: telegram
  author: rufus_smith
  recipient: arthur_holmwood
  note: "Lloyd's telegram quoted in Harker's journal"
1893-11-06-unknown-01:
  author: jonathan_harker
  note: "Closing NOTE signed by Jonathan Harker, written seven years later; unlocks last"
```

- [ ] **Step 10: Run the whole suite**

Run: `pytest -v`
Expected: 114 passed, including all 8 in `tests/test_real_book.py`. `python -m pipeline build` now reports only `missing time` errors (≈187).

If any further real-book defect turns up while reviewing `python -m pipeline report`, handle it the same way: a failing unit test reproducing the markup, then the fix, or an override with a `note:` when it is a one-off.

- [ ] **Step 11: Commit, push, PR**

```bash
git add -A
git commit -m "Keep the closing NOTE, stop header over-merging, split Demeter log days

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: Editorial times and committed dataset

Goal: every entry has a time, the dataset is committed, and CI proves it is fresh.

**Files:**
- Modify: `data/overrides.yaml`, `data/tests/test_real_book.py`
- Create: `data/out/entries.json`, `data/out/characters.json` (generated)

- [ ] **Step 1: Write the failing completeness and freshness tests**

Append to `data/tests/test_real_book.py`:
```python
from pipeline.emit import characters_json, entries_json


def test_no_problems_at_all(run):
    assert run.problems == []


def test_committed_output_is_fresh(run):
    out = DATA_DIR / "out"
    assert (out / "entries.json").read_text(encoding="utf-8") == entries_json(run.entries)
    assert (out / "characters.json").read_text(encoding="utf-8") == characters_json(run.chars)
```

- [ ] **Step 2: Run to verify they fail**

Run: `pytest tests/test_real_book.py -v`
Expected: FAIL — `missing time` problems, and `FileNotFoundError` for `out/entries.json`.

- [ ] **Step 3: Editorial pass on times**

Run `python -m pipeline report > ../time-report.txt` (do not commit it). For every listed entry add an override:
```yaml
1893-05-04-jonathan_harker-01:
  time: "21:00"
  estimated: true
  note: "Written in the evening after the day's journey"
```
Rules for estimates, applied in this priority:
1. A time stated in the entry's own text ("at 8:35 P. M.", "it is now 3 o'clock") → that time, `estimated: false`.
2. Implied by context ("before breakfast", "after dinner", "as I write the sun is setting") → best estimate, `estimated: true`, `note` quoting the cue in ≤ 10 words.
3. Letters and telegrams without cues → letters `10:00`, telegrams `12:00`, `estimated: true`.
4. Diaries without cues → `21:00` (written at day's end), `estimated: true`.
5. Same-day entries must stay in story order: a `Later.` entry's time must be after the previous entry's time.

- [ ] **Step 4: Build the dataset**

Run: `python -m pipeline build --strict`
Expected: `wrote <n> entries`, exit 0.

- [ ] **Step 5: Run the full suite**

Run: `pytest -v`
Expected: 116 passed, including `test_no_problems_at_all` and `test_committed_output_is_fresh`.

- [ ] **Step 6: Commit, push, PR**

```bash
git add data/overrides.yaml data/out data/tests/test_real_book.py
git commit -m "Add editorial times and commit generated dataset

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
This PR is mostly editorial judgement; leave auto-merge **off** and ask the maintainer to review the estimates before merging (see execution handoff).
