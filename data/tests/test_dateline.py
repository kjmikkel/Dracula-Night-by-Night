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
