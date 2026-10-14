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
