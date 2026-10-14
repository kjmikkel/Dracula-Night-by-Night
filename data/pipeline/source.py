import hashlib
from pathlib import Path

SOURCE_FILE = "pg345-images.html"


class ChecksumError(Exception):
    pass


def load_source(source_dir: Path) -> str:
    data = (source_dir / SOURCE_FILE).read_bytes()
    expected = (source_dir / "CHECKSUM").read_text(encoding="utf-8").split()[0]
    actual = hashlib.sha256(data).hexdigest()
    if actual != expected:
        raise ChecksumError(f"{SOURCE_FILE}: expected sha256 {expected}, got {actual}")
    return data.decode("utf-8")
