from datetime import date

import pytest

from pipeline.cli import run_pipeline
from pipeline.emit import characters_json, entries_json
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
    # Book order: the preface (order 0), then Harker's first entry.
    first = run.entries[1]
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


def test_no_problems_at_all(run):
    assert run.problems == []


def test_committed_output_is_fresh(run):
    out = DATA_DIR / "out"
    assert (out / "entries.json").read_text(encoding="utf-8") == entries_json(run.entries)
    assert (out / "characters.json").read_text(encoding="utf-8") == characters_json(run.chars)


def _starting(run, text):
    return [e for e in run.entries if " ".join("".join(b.text for b in e.blocks).split()).startswith(text)]


def test_van_helsing_phonograph_message_is_his(run):
    [message] = _starting(run, "THIS to Jonathan Harker")
    assert (message.author, message.type) == ("van_helsing", "memo")


def test_lloyds_telegrams_are_telegrams_from_rufus_smith(run):
    lloyds = [e for e in run.entries if "Rufus Smith" in e.text() and "Czarina Catherine reported" in e.text()]
    assert len(lloyds) == 2
    assert {(e.type, e.author, e.recipient) for e in lloyds} == {("telegram", "rufus_smith", "arthur_holmwood")}


def test_van_helsing_replies_after_mina_invites_him(run):
    by_id = {e.id: e for e in run.entries}
    invite, reply = by_id["1893-09-25-mina_harker-01"], by_id["1893-09-25-van_helsing-01"]
    assert reply.time > invite.time


def test_lucys_wednesday_letter_is_on_a_wednesday(run):
    [letter] = [e for e in run.entries if e.type == "letter" and "Wednesday" in e.text()[:120]]
    assert letter.date.strftime("%A") == "Wednesday"


def test_no_entry_is_placed_in_a_document_kind(run):
    assert [e.id for e in run.entries if e.place in ("Telegram", "Letter")] == []


def test_preface_comes_first(run):
    first = min(run.entries, key=lambda e: (e.date, e.time, e.order))
    assert first.text().startswith("How these papers have been placed in sequence")
    assert (first.author, first.type, first.date, first.order) == ("bram_stoker", "memo", date(1893, 5, 3), 0)
