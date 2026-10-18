import json
from datetime import date

from pipeline.characters import load_characters
from pipeline.model import Block, Entry, Span
from pipeline.whos_who import (
    Group,
    Person,
    Tie,
    WhosWho,
    load_whos_who,
    whos_who_json,
    whos_who_problems,
    whos_who_review_html,
)

ENTRIES = [Entry(id=eid, order=n, type="diary", author="mina_harker", date=date(1893, 9, 30))
           for n, eid in enumerate(["a", "b", "c"])]

VALID = """
groups:
  lucy:
    names:
      - {from: a, text: Lucy and her friends}
      - {from: b, text: Lucy and her suitors}
  band:
    names:
      - {from: c, text: The band}
people:
  arthur_holmwood:
    group:
      - {from: a, id: lucy}
      - {from: c, id: band}
    names:
      - {from: a, text: Arthur Holmwood}
    lines:
      - {from: c, text: "One of the band."}
      - {from: a, text: "A friend of Lucy's."}
"""


def person(group="a", name="a", lines=(("a", "A line."),), group_id="lucy"):
    lines_yaml = "".join(f"      - {{from: {f}, text: \"{t}\"}}\n" for f, t in lines)
    return (
        "groups:\n  lucy:\n    names:\n      - {from: a, text: Lucy}\n"
        "people:\n  arthur_holmwood:\n"
        f"    group:\n      - {{from: {group}, id: {group_id}}}\n"
        f"    names:\n      - {{from: {name}, text: Arthur}}\n"
        + ("    lines:\n" + lines_yaml if lines else "    lines: []\n")
    )


def problems(text):
    return whos_who_problems(load_whos_who(text), ENTRIES)


def test_loads_groups_in_file_order_and_people():
    ww = load_whos_who(VALID)
    assert [g.id for g in ww.groups] == ["lucy", "band"]
    assert ww.groups[0] == Group("lucy", (Tie("a", "Lucy and her friends"), Tie("b", "Lucy and her suitors")))
    arthur = ww.people[0]
    assert arthur == Person(
        "arthur_holmwood",
        (Tie("a", "lucy"), Tie("c", "band")),
        (Tie("a", "Arthur Holmwood"),),
        (Tie("c", "One of the band."), Tie("a", "A friend of Lucy's.")),
    )


def test_empty_text_gives_empty_who_s_who():
    assert load_whos_who("") == WhosWho((), ())


def test_valid_file_has_no_problems():
    assert problems(VALID) == []


def test_unknown_entry_is_an_error():
    found = problems(person(lines=(("zzz", "A line."),)))
    assert len(found) == 1
    assert found[0].level == "error"
    assert found[0].entry_id == "arthur_holmwood"
    assert "zzz" in found[0].message


def test_unknown_group_is_an_error():
    found = problems(person(group_id="nowhere"))
    assert [p.entry_id for p in found] == ["arthur_holmwood"]
    assert "nowhere" in found[0].message


def test_person_without_lines_is_an_error():
    assert [p.entry_id for p in problems(person(lines=()))] == ["arthur_holmwood"]


def test_first_name_after_earliest_line_is_an_error():
    found = problems(person(name="b"))
    assert len(found) == 1
    assert "name" in found[0].message


def test_first_group_after_earliest_line_is_an_error():
    found = problems(person(group="b"))
    assert len(found) == 1
    assert "group" in found[0].message


def test_group_named_after_a_member_joins_is_an_error():
    text = person().replace("{from: a, text: Lucy}", "{from: c, text: Lucy}").replace(
        "{from: a, id: lucy}", "{from: a, id: lucy}\n      - {from: b, id: lucy}")
    found = problems(text)
    assert found and all(p.entry_id == "lucy" for p in found)


def test_empty_text_is_an_error():
    assert len(problems(person(lines=(("a", ""),)))) == 1


def test_json_sorts_ties_by_story_order_and_people_by_first_line():
    # Mina comes first in the file, but Arthur's first line is earlier in the story.
    mina = (
        "  mina_harker:\n    group:\n      - {from: b, id: lucy}\n"
        "    names:\n      - {from: b, text: Mina}\n    lines:\n      - {from: b, text: \"Lucy's friend.\"}\n"
    )
    text = VALID.replace("people:\n", "people:\n" + mina)
    data = json.loads(whos_who_json(load_whos_who(text), list(reversed(ENTRIES))))
    assert [g["id"] for g in data["groups"]] == ["lucy", "band"]
    assert [p["id"] for p in data["people"]] == ["arthur_holmwood", "mina_harker"]
    arthur = data["people"][0]
    assert [line["from"] for line in arthur["lines"]] == ["a", "c"]
    assert arthur["groups"] == [{"from": "a", "id": "lucy"}, {"from": "c", "id": "band"}]
    assert arthur["names"] == [{"from": "a", "text": "Arthur Holmwood"}]


def test_review_page_lists_each_line_with_its_entry():
    chars = load_characters("mina_harker:\n  name: Mina Harker\n  short: Mina\n")
    entries = [Entry(id="a", order=0, type="diary", author="mina_harker", date=date(1893, 9, 30),
                     blocks=[Block("para", (Span("Lucy came to stay."),), 1)])]
    text = ("groups:\n  lucy:\n    names:\n      - {from: a, text: Lucy}\n"
            "people:\n  lucy_westenra:\n    group:\n      - {from: a, id: lucy}\n"
            "    names:\n      - {from: a, text: Lucy}\n"
            "    lines:\n      - {from: a, text: \"Mina's <b>closest</b> friend.\"}\n")
    page = whos_who_review_html(load_whos_who(text), entries, chars)
    assert "closest" in page
    assert "<b>closest</b>" not in page
    assert "1893-09-30" in page
    assert "Mina Harker" in page
    assert "Lucy came to stay." in page


def owners(text):
    return [(p.level, p.entry_id) for p in problems(text)]


def test_tie_without_from_is_an_error_naming_the_person():
    found = problems(person().replace("{from: a, text: Arthur}", "{text: Arthur}"))
    assert [p.entry_id for p in found] == ["arthur_holmwood"]
    assert "from" in found[0].message


def test_line_without_text_is_an_error_naming_the_person():
    found = problems(person().replace('{from: a, text: "A line."}', "{from: a}"))
    assert [p.entry_id for p in found] == ["arthur_holmwood"]
    assert "text" in found[0].message


def test_group_tie_without_id_is_an_error_naming_the_person():
    found = problems(person().replace("{from: a, id: lucy}", "{from: a}"))
    assert [p.entry_id for p in found] == ["arthur_holmwood"]
    assert "id" in found[0].message


def test_group_name_without_from_is_an_error_naming_the_group():
    found = problems(person().replace("{from: a, text: Lucy}", "{text: Lucy}"))
    assert [p.entry_id for p in found] == ["lucy"]


def test_list_written_without_dashes_is_an_error_naming_the_person():
    text = person().replace('    lines:\n      - {from: a, text: "A line."}\n',
                            '    lines:\n      from: a\n      text: "A line."\n')
    found = problems(text)
    assert [p.entry_id for p in found] == ["arthur_holmwood"]
    assert "list" in found[0].message


def test_group_names_written_without_dashes_is_an_error_naming_the_group():
    text = person().replace("    names:\n      - {from: a, text: Lucy}\n",
                            "    names:\n      from: a\n      text: Lucy\n")
    assert [p.entry_id for p in problems(text)] == ["lucy"]


def test_unknown_top_level_key_is_an_error():
    found = problems(person().replace("people:", "peoples:"))
    assert ("error", "peoples") in [(p.level, p.entry_id) for p in found]


def test_unknown_key_in_a_person_is_an_error_naming_the_person():
    found = problems(person().replace("    lines:", "    line:"))
    assert ("error", "arthur_holmwood") in [(p.level, p.entry_id) for p in found]
    assert any("unknown key line" in p.message for p in found)


def test_person_that_is_not_a_mapping_is_an_error_naming_the_person():
    found = problems("groups: {}\npeople:\n  renfield: A patient.\n")
    assert [p.entry_id for p in found] == ["renfield"]


def test_duplicate_person_is_an_error_naming_the_person():
    arthur = person().split("people:\n")[1]
    found = problems(person() + arthur)
    assert [p.entry_id for p in found] == ["arthur_holmwood"]
    assert "twice" in found[0].message


def test_duplicate_group_is_an_error_naming_the_group():
    text = person().replace("people:", "  lucy:\n    names:\n      - {from: a, text: Lucy}\npeople:")
    found = problems(text)
    assert [p.entry_id for p in found] == ["lucy"]
    assert "twice" in found[0].message


def test_yaml_that_does_not_parse_is_an_error():
    found = problems("people:\n  arthur: [unclosed\n")
    assert len(found) == 1
    assert found[0].level == "error"


def test_first_name_and_group_are_the_earliest_in_story_order_not_in_the_file():
    # The file lists the later name and group first; the earliest by story order is from "a", so all is well.
    text = (
        "groups:\n  lucy:\n    names:\n      - {from: c, text: Lucy's suitors}\n      - {from: a, text: Lucy}\n"
        "people:\n  arthur_holmwood:\n"
        "    group:\n      - {from: c, id: lucy}\n      - {from: a, id: lucy}\n"
        "    names:\n      - {from: c, text: Lord Godalming}\n      - {from: a, text: Arthur}\n"
        "    lines:\n      - {from: a, text: \"A line.\"}\n"
    )
    assert problems(text) == []


def test_late_first_name_is_caught_even_when_listed_after_a_later_one():
    text = person(name="c").replace("{from: c, text: Arthur}", "{from: c, text: Arthur}\n      - {from: b, text: Art}")
    found = problems(text)
    assert len(found) == 1
    assert "b" in found[0].message
