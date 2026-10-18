from datetime import date

from pipeline.model import Block, Entry, Span
from pipeline.repeats import mark_repeats


def entry(*blocks, when=date(1893, 5, 4), place=None, type="diary", author="jonathan_harker",
          recipient=None, header=None):
    return Entry(id="x", type=type, author=author, recipient=recipient, date=when, place=place,
                 header=header, blocks=list(blocks))


def para(*spans, role="para"):
    return Block(role, tuple(Span(t, i) for t, i in spans), 1)


def marked(e, chars):
    mark_repeats([e], chars)
    return e


def test_diary_date_and_its_dash_repeat_the_header(chars):
    e = marked(entry(para(("4 May.", True), ("—I found that my landlord", False))), chars)
    assert e.blocks[0].repeats == (0, 7)
    assert e.blocks[0].text[7:] == "I found that my landlord"


def test_place_that_matches_the_header_repeats_it(chars):
    e = marked(entry(para(("3 May. Bistritz.", True), ("—Left Munich", False)),
                     when=date(1893, 5, 3), place="Bistritz"), chars)
    assert e.blocks[0].repeats == (0, 17)


def test_notes_before_the_dateline_are_skipped(chars):
    e = marked(entry(para(("(Kept in shorthand.)", False), role="note"),
                     para(("3 May. Bistritz.", True), ("—Left Munich", False)),
                     when=date(1893, 5, 3), place="Bistritz"), chars)
    assert e.blocks[0].repeats is None
    assert e.blocks[1].repeats == (0, 17)


def test_a_dateline_block_that_only_repeats_is_wholly_repeated(chars):
    e = marked(entry(para(("“", False), ("24 May.", True), role="dateline"),
                     para(("“My dearest Mina,—", False), role="salutation"),
                     when=date(1893, 5, 24), type="letter"), chars)
    assert e.blocks[0].repeats == (0, 8)
    assert e.blocks[1].repeats is None


def test_the_opening_quote_of_a_telegram_stays(chars):
    e = marked(entry(para(("“", False), ("4 September.", True), ("—Patient still better.”", False)),
                     when=date(1893, 9, 4), type="telegram"), chars)
    b = e.blocks[0]
    start, end = b.repeats
    assert b.text[:start] + b.text[end:] == "“Patient still better.”"


def test_datelines_that_add_something_are_not_repeats(chars):
    for lead in ("Midnight.", "Evening.", "10 p. m.", "Later.", "5 November, morning.",
                 "17 September. Night.", "Written 18 July", "Telegram, October 24th."):
        e = marked(entry(para((lead, True), ("—Text.", False)), when=date(1893, 11, 5)), chars)
        assert e.blocks[0].repeats is None, lead


def test_a_different_date_or_place_is_not_a_repeat(chars):
    other_day = marked(entry(para(("4 May.", True), ("—Text.", False)), when=date(1893, 5, 5)), chars)
    other_place = marked(entry(para(("5 May. The Castle.", True), ("—Text.", False)),
                               when=date(1893, 5, 5), place="Bistritz"), chars)
    assert other_day.blocks[0].repeats is None
    assert other_place.blocks[0].repeats is None


def test_only_the_opening_dateline_counts(chars):
    e = marked(entry(para(("Midnight.", True), ("—Text.", False)),
                     para(("4 May.", True), ("—More.", False))), chars)
    assert [b.repeats for b in e.blocks] == [None, None]


def test_a_heading_that_names_both_parties(chars):
    named = marked(entry(para(("Text.", False)), type="letter", author="lucy_westenra",
                         recipient="mina_harker", header="Letter, Lucy Westenra to Mina Murray."), chars)
    unnamed = marked(entry(para(("Text.", False)), type="letter", author="lucy_westenra",
                           recipient="mina_harker"), chars)
    overridden = marked(entry(para(("Text.", False)), type="letter", author="arthur_holmwood",
                              recipient="mina_harker", header="Letter, Lucy Westenra to Mina Murray."), chars)
    assert named.header_names_parties
    assert not unnamed.header_names_parties
    assert not overridden.header_names_parties


def test_a_full_stop_outside_the_italics_goes_with_the_date(chars):
    log = marked(entry(para(("22 July", True), (".—Rough weather", False)), when=date(1893, 7, 22)), chars)
    letter = marked(entry(para(("“", False), ("24 May", True), (".", False), role="dateline"),
                          when=date(1893, 5, 24), type="letter"), chars)
    assert log.blocks[0].text[slice(*log.blocks[0].repeats)] == "22 July.—"
    assert letter.blocks[0].repeats == (0, 8)
