import hashlib

import pytest

from pipeline.source import ChecksumError, load_source


def _write(tmp_path, content: bytes, checksum: str):
    (tmp_path / "pg345-images.html").write_bytes(content)
    (tmp_path / "CHECKSUM").write_text(f"{checksum}  pg345-images.html\n")


def test_returns_text_when_checksum_matches(tmp_path):
    content = "<p>Dracula ’</p>".encode()
    _write(tmp_path, content, hashlib.sha256(content).hexdigest())
    assert load_source(tmp_path) == "<p>Dracula ’</p>"


def test_raises_when_checksum_differs(tmp_path):
    _write(tmp_path, b"<p>changed</p>", "0" * 64)
    with pytest.raises(ChecksumError, match="pg345-images.html"):
        load_source(tmp_path)


def test_pinned_book_loads(book_html):
    assert "Bistritz" in book_html
