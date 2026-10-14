from pathlib import Path

import pytest

DATA_DIR = Path(__file__).resolve().parent.parent


@pytest.fixture(scope="session")
def book_html() -> str:
    from pipeline.source import load_source

    return load_source(DATA_DIR / "source")
