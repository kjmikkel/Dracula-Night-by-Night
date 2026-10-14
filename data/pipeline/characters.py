import re
from dataclasses import dataclass

import yaml


@dataclass
class Characters:
    records: tuple[dict, ...]
    aliases: tuple[tuple[str, str], ...]
    diary_types: dict[str, str]

    @property
    def ids(self) -> set[str]:
        return {r["id"] for r in self.records}


def normalise_name(text: str) -> str:
    t = text.replace("’", "'").replace("‘", "'")
    t = re.sub(r"[“”\"]", "", t)
    return re.sub(r"\s+", " ", t).strip().lower()


def load_characters(yaml_text: str) -> Characters:
    data = yaml.safe_load(yaml_text) or {}
    records, aliases, diary_types = [], [], {}
    for cid, rec in data.items():
        records.append({"id": cid, "name": rec["name"], "short": rec["short"]})
        for alias in rec.get("aliases", []):
            aliases.append((normalise_name(alias), cid))
        if "diary_type" in rec:
            diary_types[cid] = rec["diary_type"]
    aliases.sort(key=lambda pair: (-len(pair[0]), pair[0]))
    return Characters(tuple(sorted(records, key=lambda r: r["id"])), tuple(aliases), diary_types)
