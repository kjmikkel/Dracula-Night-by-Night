from pathlib import Path

import pytest

DATA_DIR = Path(__file__).resolve().parent.parent


@pytest.fixture(scope="session")
def book_html() -> str:
    from pipeline.source import load_source

    return load_source(DATA_DIR / "source")


@pytest.fixture(scope="session")
def chars():
    from pipeline.characters import load_characters

    return load_characters((DATA_DIR / "characters.yaml").read_text(encoding="utf-8"))
