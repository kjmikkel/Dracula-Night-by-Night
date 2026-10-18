---
title: "Who's who cast and YAML problem reporting (#204 PR 2)"
author: Mikkel
date: 2026-10-18
branch: feat/204-whos-who-cast
touches:
  - data/out/whos_who.json
  - data/whos_who.yaml
---

## Decided
- `load_whos_who` never raises: shape errors go into `WhosWho.problems` (named by person/group id), and `whos_who_problems` starts from them. Duplicate ids are found with `yaml.compose` (safe_load silently keeps the last duplicate key).
- A list written without dashes is reported, then read as a one-item list, so one mistake gives one problem, not a cascade ("person has no lines").
- First name/group for validation = earliest by entry `order`, not file order.
- Cast groups: jonathan_mina, castle, lucy, asylum, whitby, band. The band is named at the promise in Lucy's tomb (1893-09-29-john_seward-03); Jonathan and Mina join at the 30 Sep meeting (1893-09-30-mina_harker-03). People who are also voices keep their characters.yaml id (billington_and_son, demeter_captain, ...).

## Tried and ruled out
- Tying Lucy to the "bloofer lady" at the Westminster Gazette entries: the link isn't revealed until Van Helsing says so (1893-09-26-john_seward-01).
- Starting Billington at 1893-05-12-jonathan_harker-01: his group (whitby) is only named later, which fails validation.

## Watch out
- `order` is book order, not date order: Seward's June/July entries come after Mina's 1 August.
- Letter headers carry facts the body doesn't, such as Sister Agatha's hospital in Buda-Pesth. Check the `header` field before deciding a line overreaches.
- The Gradle project is under app/ (run `cd app && ./gradlew ...`); a new worktree needs app/local.properties copied in.
- `trail check` flags keyword false positives on the who's who docs and docstrings.
