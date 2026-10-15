"""A self-contained HTML page for reviewing entry dates and times by eye."""
import json
from datetime import date, time

from .characters import Characters
from .model import Entry


def order_issues(entries: list[Entry]) -> set[str]:
    """Ids of entries timed before the entry they follow (same author, type and day, in book order)."""
    bad: set[str] = set()
    ordered = sorted(entries, key=lambda e: e.order)
    for a, b in zip(ordered, ordered[1:], strict=False):
        same = (a.author, a.type, a.date) == (b.author, b.type, b.date)
        if same and a.time and b.time and b.time < a.time:
            bad.add(b.id)
    return bad


def review_rows(entries: list[Entry], chars: Characters, overrides: dict[str, dict]) -> list[dict]:
    names = {r["id"]: r["name"] for r in chars.records}
    flagged = order_issues(entries)

    def key(e: Entry):
        return (e.date or date.max, e.time or time.max, e.order)

    return [
        {
            "id": e.id,
            "order": e.order,
            "date": e.date.isoformat() if e.date else None,
            "time": e.time.strftime("%H:%M") if e.time else None,
            "estimated": e.time_estimated,
            "type": e.type,
            "author": names.get(e.author, e.author),
            "recipient": names.get(e.recipient, e.recipient),
            "header": e.header,
            "note": overrides.get(e.id, {}).get("note"),
            "override": {k: v for k, v in overrides.get(e.id, {}).items() if k not in ("time", "estimated", "note")},
            "text": "\n\n".join(" ".join(b.text.split()) for b in e.blocks),
            "orderIssue": e.id in flagged,
        }
        for e in sorted(entries, key=key)
    ]


def review_html(rows: list[dict]) -> str:
    payload = json.dumps(rows, ensure_ascii=False).replace("<", "\\u003c")
    return _TEMPLATE.replace("__DATA__", payload)


_TEMPLATE = r"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Time Review</title>
<style>
:root {
  --bg: #f4ecdc; --panel: #fbf6ec; --ink: #2a1f1a; --muted: #75655a; --line: #dccdb4;
  --accent: #7a1f24; --accent-soft: #f0d9d4; --ok: #2f6b3a; --warn: #a3501b; --cue: #f3d98b;
}
@media (prefers-color-scheme: dark) {
  :root:not([data-theme="light"]) {
    --bg: #141011; --panel: #1d1718; --ink: #ece2d4; --muted: #a39282; --line: #3a2e2c;
    --accent: #d06a6a; --accent-soft: #3a1f21; --ok: #79c08a; --warn: #e3a05e; --cue: #6b5520;
  }
}
* { box-sizing: border-box; }
body { margin: 0; background: var(--bg); color: var(--ink); font: 15px/1.5 Georgia, "Times New Roman", serif; }
header { display: flex; flex-wrap: wrap; gap: 8px 16px; align-items: center; padding: 10px 16px;
  border-bottom: 1px solid var(--line); background: var(--panel); position: sticky; top: 0; z-index: 2; }
h1 { font-size: 18px; margin: 0; color: var(--accent); letter-spacing: .04em; }
header label { font-size: 13px; color: var(--muted); display: flex; gap: 4px; align-items: center; }
input, select, button, textarea { font: inherit; color: var(--ink); background: var(--bg); border: 1px solid var(--line); border-radius: 4px; padding: 3px 6px; }
button { cursor: pointer; } button:hover { border-color: var(--accent); }
#progress { margin-left: auto; font-size: 13px; color: var(--muted); }
main { display: grid; grid-template-columns: minmax(280px, 400px) 1fr; height: calc(100vh - 54px); }
#list { overflow-y: auto; border-right: 1px solid var(--line); }
.day { position: sticky; top: 0; background: var(--bg); padding: 6px 12px; font-weight: bold; color: var(--accent); border-bottom: 1px solid var(--line); }
.row { display: grid; grid-template-columns: 52px 1fr auto; gap: 6px; padding: 6px 12px; border-bottom: 1px solid var(--line); cursor: pointer; }
.row:hover { background: var(--panel); } .row.sel { background: var(--accent-soft); }
.row .t { font-variant-numeric: tabular-nums; } .row .t.est { color: var(--muted); font-style: italic; }
.row .who { font-size: 13px; } .row .first { font-size: 12px; color: var(--muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.marks { font-size: 13px; } .ok { color: var(--ok); } .fix { color: var(--warn); } .flag { color: var(--accent); }
#detail { overflow-y: auto; padding: 16px 24px 48px; }
#detail h2 { margin: 0 0 4px; font-size: 20px; }
.meta { color: var(--muted); font-size: 13px; margin-bottom: 12px; }
.note { background: var(--panel); border-left: 3px solid var(--accent); padding: 6px 10px; margin: 8px 0 12px; font-size: 14px; }
.warnbox { border-left-color: var(--warn); }
.controls { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin: 12px 0; padding: 10px; border: 1px solid var(--line); border-radius: 6px; background: var(--panel); }
.text p { margin: 0 0 .9em; max-width: 70ch; } mark { background: var(--cue); color: inherit; padding: 0 2px; border-radius: 2px; }
dialog { max-width: 720px; width: 92vw; background: var(--panel); color: var(--ink); border: 1px solid var(--line); }
dialog textarea { width: 100%; height: 50vh; font: 13px/1.4 Consolas, monospace; }
.kbd { font-size: 12px; color: var(--muted); }
@media (max-width: 760px) { main { grid-template-columns: 1fr; height: auto; } #list { max-height: 45vh; } }
</style>
</head>
<body>
<header>
  <h1>Dracula — time review</h1>
  <label>Search <input id="q" type="search" size="14"></label>
  <label>Author <select id="author"><option value="">all</option></select></label>
  <label><input id="estOnly" type="checkbox"> estimates only</label>
  <label><input id="todoOnly" type="checkbox"> unreviewed only</label>
  <label><input id="flagOnly" type="checkbox"> flagged only</label>
  <button id="export">Export corrections</button>
  <span id="progress"></span>
</header>
<main>
  <nav id="list"></nav>
  <section id="detail"><p class="meta">Select an entry. Keys: <b>j</b>/<b>k</b> next/previous, <b>a</b> approve.</p></section>
</main>
<dialog id="dlg">
  <p>Paste into <code>data/overrides.yaml</code> (replacing the matching lines), or hand it to Claude.</p>
  <textarea id="yaml" readonly></textarea>
  <p><button id="copy">Copy</button> <button id="close">Close</button></p>
</dialog>
<script id="data" type="application/json">__DATA__</script>
<script>
const rows = JSON.parse(document.getElementById("data").textContent);
const KEY = "dracula-time-review";
let marks = {};
try { marks = JSON.parse(localStorage.getItem(KEY) || "{}"); } catch (e) { marks = {}; }
const save = () => { try { localStorage.setItem(KEY, JSON.stringify(marks)); } catch (e) {} };
const CUE = /\b(\d{1,2}(?::\d{2})?\s*(?:o['’]clock|[ap]\.\s*m\.)|o['’]clock|midnight|noon|dawn|daybreak|sunrise|sunset|twilight|dusk|morning|afternoon|evening|night|to-night|breakfast|lunch|luncheon|dinner|supper|tea|early|late|later)\b/gi;
const esc = s => String(s ?? "").replace(/[&<>"]/g, c => ({"&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;"}[c]));
const fmtDay = d => d ? new Date(d + "T12:00:00").toLocaleDateString("en-GB", {weekday: "long", day: "numeric", month: "long"}) : "Undated";
let visible = [], current = null;

const authors = [...new Set(rows.map(r => r.author))].sort();
for (const a of authors) document.getElementById("author").insertAdjacentHTML("beforeend", `<option>${esc(a)}</option>`);

function markIcon(r) {
  const m = marks[r.id];
  return (r.orderIssue ? '<span class="flag" title="Out of order">⚠</span> ' : "") +
    (m?.ok ? '<span class="ok" title="Approved">✓</span>' : m?.time ? `<span class="fix" title="Corrected">✎ ${esc(m.time)}</span>` : "");
}

function renderList() {
  const q = document.getElementById("q").value.toLowerCase();
  const author = document.getElementById("author").value;
  const estOnly = document.getElementById("estOnly").checked;
  const todoOnly = document.getElementById("todoOnly").checked;
  const flagOnly = document.getElementById("flagOnly").checked;
  visible = rows.filter(r => (!author || r.author === author) && (!estOnly || r.estimated)
    && (!todoOnly || !marks[r.id]) && (!flagOnly || r.orderIssue)
    && (!q || (r.text + " " + r.id + " " + (r.note || "")).toLowerCase().includes(q)));
  let html = "", day = null;
  for (const r of visible) {
    if (r.date !== day) { day = r.date; html += `<div class="day">${esc(fmtDay(day))}</div>`; }
    html += `<div class="row${current === r.id ? " sel" : ""}" data-id="${esc(r.id)}">
      <span class="t${r.estimated ? " est" : ""}" title="${r.estimated ? "estimated" : "stated in the text"}">${esc(r.time ?? "—")}</span>
      <span><div class="who">${esc(r.author)} · ${esc(r.type)}</div><div class="first">${esc(r.text.slice(0, 90))}</div></span>
      <span class="marks">${markIcon(r)}</span></div>`;
  }
  document.getElementById("list").innerHTML = html || '<p class="meta" style="padding:12px">No entries match.</p>';
  const done = rows.filter(r => marks[r.id]).length;
  document.getElementById("progress").textContent = `${done} / ${rows.length} reviewed · ${visible.length} shown`;
}

function highlight(text) {
  return esc(text).replace(CUE, m => `<mark>${m}</mark>`);
}

function show(id) {
  current = id;
  const r = rows.find(x => x.id === id);
  const m = marks[id] || {};
  const to = r.recipient ? ` → ${esc(r.recipient)}` : "";
  document.getElementById("detail").innerHTML = `
    <h2>${esc(r.author)}${to}</h2>
    <div class="meta">${esc(fmtDay(r.date))} · <b>${esc(r.time ?? "no time")}</b> ${r.estimated ? "(estimated)" : "(stated in text)"} · ${esc(r.type)} · <code>${esc(r.id)}</code></div>
    ${r.orderIssue ? '<div class="note warnbox">⚠ Timed earlier than the entry before it by the same writer on the same day.</div>' : ""}
    <div class="note"><b>Why this time:</b> ${esc(r.note || "Time comes straight from the dateline.")}</div>
    <div class="controls">
      <button id="ok">✓ Looks right (a)</button>
      <label>Better time <input id="newTime" type="time" value="${esc(m.time || r.time || "")}"></label>
      <label>Reason <input id="newNote" size="28" value="${esc(m.note || "")}"></label>
      <button id="fix">✎ Save correction</button>
      ${marks[id] ? '<button id="clear">Undo</button>' : ""}
      <span class="kbd">${m.ok ? "Approved" : m.time ? "Corrected to " + esc(m.time) : "Not reviewed"}</span>
    </div>
    ${r.header ? `<p><i>${esc(r.header)}</i></p>` : ""}
    <div class="text">${r.text.split("\n\n").map(p => `<p>${highlight(p)}</p>`).join("")}</div>`;
  document.getElementById("ok").onclick = () => { marks[id] = {ok: true}; save(); renderList(); show(id); };
  document.getElementById("fix").onclick = () => {
    const t = document.getElementById("newTime").value;
    if (!t) return;
    marks[id] = {time: t, note: document.getElementById("newNote").value.trim()};
    save(); renderList(); show(id);
  };
  const clear = document.getElementById("clear");
  if (clear) clear.onclick = () => { delete marks[id]; save(); renderList(); show(id); };
  renderList();
  document.querySelector(`.row[data-id="${CSS.escape(id)}"]`)?.scrollIntoView({block: "nearest"});
}

function step(delta) {
  if (!visible.length) return;
  const i = visible.findIndex(r => r.id === current);
  const next = visible[Math.min(visible.length - 1, Math.max(0, i + delta))];
  show(next.id);
}

document.getElementById("list").addEventListener("click", e => {
  const row = e.target.closest(".row");
  if (row) show(row.dataset.id);
});
for (const id of ["q", "author", "estOnly", "todoOnly", "flagOnly"]) document.getElementById(id).addEventListener("input", renderList);
document.addEventListener("keydown", e => {
  if (["INPUT", "SELECT", "TEXTAREA"].includes(e.target.tagName)) return;
  if (e.key === "j") step(1);
  if (e.key === "k") step(-1);
  if (e.key === "a" && current) document.getElementById("ok").click();
});
document.getElementById("export").onclick = () => {
  const fixes = rows.filter(r => marks[r.id]?.time);
  const yq = s => '"' + String(s).replace(/\\/g, "\\\\").replace(/"/g, '\\"') + '"';
  const yv = v => typeof v === "string" ? yq(v) : String(v);
  const lines = fixes.map(r => {
    const keep = Object.entries(r.override || {}).map(([k, v]) => `${k}: ${yv(v)}, `).join("");
    return `${r.id}: {${keep}time: ${yq(marks[r.id].time)}, estimated: true, note: ${yq(marks[r.id].note || "Corrected in review")}}`;
  });
  const approved = rows.filter(r => marks[r.id]?.ok).length;
  document.getElementById("yaml").value = `# ${fixes.length} correction(s), ${approved} approved as-is\n` + lines.join("\n") + "\n";
  document.getElementById("dlg").showModal();
};
document.getElementById("copy").onclick = () => navigator.clipboard?.writeText(document.getElementById("yaml").value);
document.getElementById("close").onclick = () => document.getElementById("dlg").close();
renderList();
if (rows.length) show(visible[0]?.id ?? rows[0].id);
</script>
</body>
</html>
"""
