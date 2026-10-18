---
title: "Issue #112: the Project Gutenberg credit stays"
author: Mikkel
date: 2026-10-18
branch: fix/112-gutenberg-credit
cost: { minutes: 21, tokens: 52770 }
touches:
  - app/app/src/main/java/com/jensen/draculanightbynight/about/About.kt
  - app/app/src/main/java/com/jensen/draculanightbynight/about/AboutScreen.kt
  - app/app/src/test/java/com/jensen/draculanightbynight/about/AboutLinksTest.kt
  - docs/superpowers/specs/2026-10-14-dataset-design.md
  - ".idea/.gitignore"
  - ".idea/caches/deviceStreaming.xml"
  - ".idea/deviceManager.xml"
  - ".idea/dracula-night-by-night.iml"
  - ".idea/misc.xml"
  - ".idea/modules.xml"
---

## Decided
- Owner, 2026-10-18: the app stays free for good, so the About screen keeps crediting "Project Gutenberg eBook #345", now linked to gutenberg.org/ebooks/345, with the reasoning reworded.
- Basis: gutenberg.org/policy/permission.html says crediting Project Gutenberg as the source needs no permission, and free redistribution is fine with or without the name. Royalties apply only to commercial use of the trademark.

## Tried and ruled out
- Reading only the licence inside pg345-images.html: section 1.C says "remove all references", which seems to forbid any credit, but the permission page carves out source credits.

## Watch out
- If the app ever charges money or shows ads, revisit this: commercial use of the name brings in Project Gutenberg's trademark royalties.
- Don't call it a copy "that does not carry its name": it does carry the name, as a credit.
