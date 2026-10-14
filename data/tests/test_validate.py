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
    assert validate([good()], IDS, "Hello there.", {}) == []


def test_field_errors():
    e = good()
    e.type, e.author, e.recipient, e.date, e.time = "poem", None, "count", date(1893, 11, 7), None
    assert messages(validate([e], IDS, "Hello there.", {})) == {
        ("error", "a", "invalid type 'poem'"),
        ("error", "a", "missing author"),
        ("error", "a", "unknown recipient 'count'"),
        ("error", "a", "date 1893-11-07 outside season"),
        ("error", "a", MISSING_TIME),
    }


def test_missing_date_and_unknown_author():
    e = good()
    e.date, e.author = None, "renfield"
    assert messages(validate([e], IDS, "Hello there.", {})) == {
        ("error", "a", "missing date"),
        ("error", "a", "unknown author 'renfield'"),
    }


def test_duplicate_ids_and_order_gaps():
    problems = validate([good("a", "x", 0), good("a", "y", 2)], IDS, "xy", {})
    assert ("error", "a", "duplicate id") in messages(problems)
    assert ("error", "*", "order is not contiguous from 0") in messages(problems)


def test_text_conservation_ignores_punctuation_and_case():
    assert validate([good(text="“Hello,” there!")], IDS, "hello there", {}) == []


def test_text_conservation_reports_first_difference():
    [problem] = validate([good(text="Hello there.")], IDS, "Hello where.", {})
    assert problem.level == "error" and problem.entry_id == "*"
    assert problem.message.startswith("text differs from book at character 5")


def test_vanished_previous_ids_are_warnings():
    assert validate([good()], IDS, "Hello there.", {"a": "hellothere", "old": "x"}) == [
        Problem("warning", "old", "id from previous build no longer exists"),
    ]


def test_unmatched_headers_warn_unless_allowed():
    from pipeline.validate import unmatched_header_problems

    assert unmatched_header_problems(["“FINIS.”", "Mystery header"]) == [
        Problem("warning", "*", "unmatched header 'Mystery header'"),
    ]


def test_id_pointing_at_different_text_warns():
    assert validate([good()], IDS, "Hello there.", {"a": "goodbye"}) == [
        Problem("warning", "a", "id now points at different text (was 'goodbye...')"),
    ]


def test_fingerprint_is_first_40_normalised_characters():
    from pipeline.validate import fingerprint

    assert fingerprint(good(text="“Hello,” there! " + "x" * 50)) == ("hellothere" + "x" * 30)
