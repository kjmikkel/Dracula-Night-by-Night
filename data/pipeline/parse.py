import re

from bs4 import BeautifulSoup, Comment, NavigableString, Tag

from .model import Block, Span

_CHAP_ID = re.compile(r"^chap(\d+)$")
_WS = re.compile(r"[ \t\r\n\u00a0]+")
_AROUND_NL = re.compile(r" *\n *")
_END = "THE END"


def parse_book(html: str) -> list[Block]:
    soup = BeautifulSoup(html, "html.parser")
    blocks: list[Block] = []
    for div in soup.select("div.chapter"):
        anchor = div.find("a", id=_CHAP_ID)
        if anchor is None:
            continue
        chapter = int(_CHAP_ID.match(anchor["id"]).group(1))
        for el in div.find_all(["h2", "p", "div"]):
            if el.name == "h2":
                small = el.find("small")
                if small is not None:
                    blocks.append(Block("header", _spans(small), chapter))
                continue
            if el.name == "div":
                if "stanza" in el.get("class", []):
                    blocks.append(Block("verse", _spans(el), chapter))
                continue
            if el.find_parent("div", class_="stanza") or el.find_parent("p"):
                continue
            spans = _spans(el)
            text = "".join(s.text for s in spans)
            if not text.strip():
                continue
            if text.startswith(_END):
                return blocks
            blocks.append(Block(_role(el, text), spans, chapter))
    return blocks


def reference_text(html: str) -> str:
    """Book body text extracted without any role logic, for conservation checks."""
    soup = BeautifulSoup(html, "html.parser")
    parts: list[str] = []
    for div in soup.select("div.chapter"):
        if div.find("a", id=_CHAP_ID) is None:
            continue
        for h2 in div.find_all("h2"):
            small = h2.find("small")
            h2.replace_with(NavigableString(small.get_text() if small else ""))
        text = div.get_text()
        end = text.find(_END)
        if end != -1:
            parts.append(text[:end])
            break
        parts.append(text)
    return "".join(parts)


def _role(el: Tag, text: str) -> str:
    classes = set(el.get("class", []))
    if "letra" in classes:
        return "header"
    if "c" in classes:
        return "note" if text.startswith("(") else "header"
    if "r" in classes:
        return "signoff"
    if "nind" in classes and len(text) < 60 and text.rstrip("”\" ").endswith((",—", ",", ":—")):
        return "salutation"
    return "para"


def _spans(tag: Tag) -> tuple[Span, ...]:
    raw: list[Span] = []

    def walk(node: Tag, italic: bool) -> None:
        for child in node.children:
            if isinstance(child, Comment):
                continue
            if isinstance(child, NavigableString):
                raw.append(Span(_WS.sub(" ", str(child)), italic))
            elif child.name == "br":
                raw.append(Span("\n", italic))
            else:
                walk(child, italic or child.name in ("i", "em"))

    walk(tag, False)
    merged: list[Span] = []
    for s in raw:
        if merged and merged[-1].italic == s.italic:
            merged[-1] = Span(merged[-1].text + s.text, s.italic)
        else:
            merged.append(s)
    cleaned = [Span(_AROUND_NL.sub("\n", s.text), s.italic) for s in merged]
    if cleaned:
        cleaned[0] = Span(cleaned[0].text.lstrip(" \n"), cleaned[0].italic)
        cleaned[-1] = Span(cleaned[-1].text.rstrip(" \n"), cleaned[-1].italic)
    return tuple(s for s in cleaned if s.text)
