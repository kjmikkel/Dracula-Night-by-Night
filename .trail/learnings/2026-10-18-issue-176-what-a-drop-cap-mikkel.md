---
title: "Issue #176: what a drop cap would land on"
author: Mikkel
date: 2026-10-18
branch: claude/nifty-mcnulty-097177
cost: { minutes: 2, tokens: 62626 }
---

## Decided
Nothing yet; design questions are with the owner (2026-10-18). Any rule must look at the block after withoutRepeat(), since the #177 skip setting changes which character opens the paragraph.

## Tried and ruled out
Surveying the raw first `para` is misleading: with skip off (default) ~115 diary entries open with an italic dateline inside the para ("3 May. Bistritz.—Left Munich"), so the cap would be an italic digit. With skip on: ~124 start with a plain letter, ~72 still open with a non-repeat italic dateline ("Later.", "24 June, before morning."), 31 open with a curly quote, 7 chapter openings keep the print edition's caps ("WHEN I found", "ONE of the").

## Watch out
Compose has no drop cap. A true one means splitting the text into several nodes (TalkBack would read "W… hen") unless the block gets one merged reading. A raised initial (bigger first letter via SpanStyle) stays one Text. The reader has no SelectionContainer, so there is no text selection to preserve today. Block repeats in JSON are [from, to) (StoryJson maps to `from until to`).
