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
