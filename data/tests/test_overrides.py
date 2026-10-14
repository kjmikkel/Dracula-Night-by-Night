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


def test_duplicate_ids_are_an_error():
    with pytest.raises(OverrideError, match="duplicate key 'a'"):
        load_overrides('a: {time: "10:00"}\na: {time: "11:00"}\n')
