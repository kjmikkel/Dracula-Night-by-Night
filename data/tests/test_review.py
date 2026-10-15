import json
import re
from datetime import date, time

from pipeline.characters import load_characters
from pipeline.cli import main
from pipeline.model import Block, Entry, Span
from pipeline.review import order_issues, review_html, review_rows
from tests.test_cli import make_data_dir

CHARS = load_characters("mina_harker:\n  name: Mina Harker\n  short: Mina\n")


def entry(eid, order, when, text="Text.", author="mina_harker", day=30):
    return Entry(id=eid, order=order, type="diary", author=author, date=date(1893, 9, day), time=when,
                 time_estimated=True, blocks=[Block("para", (Span(text),), 1)])


def test_order_issues_flags_a_later_entry_timed_earlier():
    entries = [entry("a", 0, time(21, 0)), entry("b", 1, time(9, 0)), entry("c", 2, time(22, 0))]
    assert order_issues(entries) == {"b"}


def test_order_issues_ignores_other_authors_and_days():
    entries = [entry("a", 0, time(21, 0)), entry("b", 1, time(9, 0), author="lucy_westenra"),
               entry("c", 2, time(8, 0), day=29)]
    assert order_issues(entries) == set()


def test_rows_are_in_story_order_with_names_and_notes():
    entries = [entry("late", 0, time(21, 0), "First para."), entry("early", 1, time(9, 0), "Second.")]
    entries[0].blocks.append(Block("para", (Span("Next para."),), 1))
    rows = review_rows(entries, CHARS, {"late": {"time": "21:00", "note": "evening cue"}})
    assert [r["id"] for r in rows] == ["early", "late"]
    late = rows[1]
    assert (late["author"], late["time"], late["estimated"], late["note"]) == (
        "Mina Harker", "21:00", True, "evening cue")
    assert late["text"] == "First para.\n\nNext para."
    assert rows[0]["note"] is None


def test_html_embeds_rows_safely():
    rows = [{"id": "x", "text": "evil </script><script>alert(1)</script>"}]
    html = review_html(rows)
    payload = re.search(r'<script id="data" type="application/json">(.*?)</script>', html, re.S).group(1)
    assert json.loads(payload) == rows
    assert "</script><script>alert" not in html


def test_cli_review_writes_page_even_with_missing_times(tmp_path, capsys):
    d = make_data_dir(tmp_path)
    assert main(["review", "--data-dir", str(d)]) == 0
    page = (d / "out" / "review.html").read_text(encoding="utf-8")
    assert "1893-05-04-jonathan_harker-01" in page
    assert "review.html" in capsys.readouterr().out


def test_rows_carry_other_override_fields_for_export():
    entries = [entry("t", 0, time(12, 0))]
    rows = review_rows(entries, CHARS, {"t": {"time": "12:00", "estimated": True, "note": "n",
                                              "type": "telegram", "author": "rufus_smith"}})
    assert rows[0]["override"] == {"type": "telegram", "author": "rufus_smith"}
