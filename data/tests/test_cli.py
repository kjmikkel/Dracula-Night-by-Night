import hashlib

from pipeline.cli import main
from tests.markup import chapter, page

BOOK = page(chapter(1, "<p><i>3 May. Bistritz.</i>—Left Munich at 8:35 P. M.</p>"
                       "<p><i>4 May.</i>—I found.</p>"))
CHARS = "jonathan_harker:\n  name: Jonathan Harker\n  short: Jonathan\n  aliases: [jonathan harker]\n"
OVERRIDES = (
    '1893-05-03-jonathan_harker-01:\n  time: "20:35"\n'
    '1893-05-04-jonathan_harker-01:\n  time: "21:00"\n  estimated: true\n'
)


def make_data_dir(tmp_path, overrides="{}\n"):
    src = tmp_path / "source"
    src.mkdir()
    raw = BOOK.encode("utf-8")
    (src / "pg345-images.html").write_bytes(raw)
    (src / "CHECKSUM").write_text(hashlib.sha256(raw).hexdigest() + "  pg345-images.html\n")
    (tmp_path / "characters.yaml").write_text(CHARS, encoding="utf-8")
    (tmp_path / "overrides.yaml").write_text(overrides, encoding="utf-8")
    return tmp_path


def test_build_fails_without_times_and_writes_nothing(tmp_path, capsys):
    d = make_data_dir(tmp_path)
    assert main(["build", "--data-dir", str(d)]) == 1
    assert "1893-05-04-jonathan_harker-01: missing time" in capsys.readouterr().out
    assert not (d / "out" / "entries.json").exists()


def test_report_lists_entries_without_time(tmp_path, capsys):
    d = make_data_dir(tmp_path)
    assert main(["report", "--data-dir", str(d)]) == 0
    out = capsys.readouterr().out
    assert "1893-05-04-jonathan_harker-01" in out
    assert "2 entries without a time" in out


def test_build_succeeds_and_is_deterministic(tmp_path):
    d = make_data_dir(tmp_path, overrides=OVERRIDES)
    assert main(["build", "--data-dir", str(d)]) == 0
    first = (d / "out" / "entries.json").read_bytes()
    assert main(["build", "--data-dir", str(d)]) == 0
    assert (d / "out" / "entries.json").read_bytes() == first
    assert b'"time": "20:35"' in first
    assert (d / "out" / "characters.json").exists()
