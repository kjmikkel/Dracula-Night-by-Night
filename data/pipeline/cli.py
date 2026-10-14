import argparse
import json
from dataclasses import dataclass
from pathlib import Path

from .characters import Characters, load_characters
from .emit import characters_json, entries_json
from .model import Entry
from .overrides import apply_overrides, load_overrides
from .parse import parse_book, reference_text
from .source import load_source
from .split import assign_ids, split_entries
from .validate import Problem, validate

DATA_DIR = Path(__file__).resolve().parent.parent


@dataclass
class PipelineRun:
    entries: list[Entry]
    problems: list[Problem]
    chars: Characters


def run_pipeline(data_dir: Path) -> PipelineRun:
    html = load_source(data_dir / "source")
    chars = load_characters((data_dir / "characters.yaml").read_text(encoding="utf-8"))
    entries = split_entries(parse_book(html), chars)
    assign_ids(entries)
    entries = apply_overrides(entries, load_overrides((data_dir / "overrides.yaml").read_text(encoding="utf-8")))
    problems = validate(entries, chars.ids, reference_text(html), _previous_ids(data_dir))
    return PipelineRun(entries, problems, chars)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="pipeline")
    parser.add_argument("command", choices=["build", "report"])
    parser.add_argument("--strict", action="store_true", help="treat warnings as errors")
    parser.add_argument("--data-dir", type=Path, default=DATA_DIR)
    args = parser.parse_args(argv)
    run = run_pipeline(args.data_dir)
    return _build(run, args.data_dir, args.strict) if args.command == "build" else _report(run)


def _build(run: PipelineRun, data_dir: Path, strict: bool) -> int:
    for p in run.problems:
        print(f"{p.level}: {p.entry_id}: {p.message}")
    failing = [p for p in run.problems if p.level == "error" or strict]
    if failing:
        print(f"build failed: {len(failing)} problem(s)")
        return 1
    out = data_dir / "out"
    out.mkdir(exist_ok=True)
    (out / "entries.json").write_bytes(entries_json(run.entries).encode("utf-8"))
    (out / "characters.json").write_bytes(characters_json(run.chars).encode("utf-8"))
    print(f"wrote {len(run.entries)} entries")
    return 0


def _report(run: PipelineRun) -> int:
    missing = [e for e in run.entries if e.time is None]
    for e in missing:
        first = " ".join(e.text().split())[:80]
        print(f"{e.id}\t{e.date}\t{first}")
    print(f"{len(missing)} entries without a time")
    return 0


def _previous_ids(data_dir: Path) -> set[str]:
    path = data_dir / "out" / "entries.json"
    if not path.exists():
        return set()
    return {e["id"] for e in json.loads(path.read_text(encoding="utf-8"))}
