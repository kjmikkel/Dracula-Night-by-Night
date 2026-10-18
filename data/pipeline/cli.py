import argparse
import json
from dataclasses import dataclass
from pathlib import Path

from .characters import Characters, load_characters
from .emit import characters_json, entries_json
from .model import Entry
from .overrides import apply_overrides, load_overrides
from .parse import parse_book, reference_text
from .repeats import mark_repeats
from .review import review_html, review_rows
from .source import load_source
from .split import assign_ids, split_entries
from .validate import Problem, fingerprint_of_json, unmatched_header_problems, validate
from .whos_who import WhosWho, load_whos_who, whos_who_json, whos_who_problems

DATA_DIR = Path(__file__).resolve().parent.parent


@dataclass
class PipelineRun:
    entries: list[Entry]
    problems: list[Problem]
    chars: Characters
    whos_who: WhosWho


def run_pipeline(data_dir: Path) -> PipelineRun:
    html = load_source(data_dir / "source")
    chars = load_characters((data_dir / "characters.yaml").read_text(encoding="utf-8"))
    unmatched: list[str] = []
    entries = split_entries(parse_book(html), chars, unmatched)
    assign_ids(entries)
    entries = apply_overrides(entries, load_overrides((data_dir / "overrides.yaml").read_text(encoding="utf-8")))
    mark_repeats(entries, chars)
    problems = validate(entries, chars.ids, reference_text(html), _previous_fingerprints(data_dir))
    problems += unmatched_header_problems(unmatched)
    yaml_path = data_dir / "whos_who.yaml"
    whos_who = load_whos_who(yaml_path.read_text(encoding="utf-8") if yaml_path.exists() else "")
    problems += whos_who_problems(whos_who, entries)
    return PipelineRun(entries, problems, chars, whos_who)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="pipeline")
    parser.add_argument("command", choices=["build", "report", "review"])
    parser.add_argument("--strict", action="store_true", help="treat warnings as errors")
    parser.add_argument("--data-dir", type=Path, default=DATA_DIR)
    args = parser.parse_args(argv)
    run = run_pipeline(args.data_dir)
    if args.command == "build":
        return _build(run, args.data_dir, args.strict)
    if args.command == "review":
        return _review(run, args.data_dir)
    return _report(run)


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
    (out / "whos_who.json").write_bytes(whos_who_json(run.whos_who, run.entries).encode("utf-8"))
    print(f"wrote {len(run.entries)} entries")
    return 0


def _report(run: PipelineRun) -> int:
    missing = [e for e in run.entries if e.time is None]
    for e in missing:
        first = " ".join(e.text().split())[:80]
        print(f"{e.id}\t{e.date}\t{first}")
    print(f"{len(missing)} entries without a time")
    return 0


def _review(run: PipelineRun, data_dir: Path) -> int:
    overrides = load_overrides((data_dir / "overrides.yaml").read_text(encoding="utf-8"))
    out = data_dir / "out"
    out.mkdir(exist_ok=True)
    page = out / "review.html"
    page.write_bytes(review_html(review_rows(run.entries, run.chars, overrides)).encode("utf-8"))
    print(f"wrote {page.resolve()} - open it in a browser")
    return 0


def _previous_fingerprints(data_dir: Path) -> dict[str, str]:
    path = data_dir / "out" / "entries.json"
    if not path.exists():
        return {}
    return {e["id"]: fingerprint_of_json(e) for e in json.loads(path.read_text(encoding="utf-8"))}
