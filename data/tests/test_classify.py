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
        ("DR. SEWARD’S PHONOGRAPH DIARY, SPOKEN BY VAN HELSING",
         HeaderInfo("memo", "van_helsing", None, None)),
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


def test_closing_note_header(chars):
    assert classify_header("NOTE", chars) == HeaderInfo("memo", None, None, None)
