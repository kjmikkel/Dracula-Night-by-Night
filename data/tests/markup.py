def page(*chapters: str) -> str:
    return "<html><body>" + "".join(chapters) + "</body></html>"


def chapter(n: int, inner: str, subtitle: str | None = "JONATHAN HARKER’S JOURNAL") -> str:
    small = f"<br><br>\n<small>{subtitle}</small>" if subtitle else ""
    return (
        f'<div class="chapter">\n<h2><a id="chap{n:02d}"></a>CHAPTER {n}{small}</h2>\n'
        f"{inner}\n</div><!--end chapter-->\n"
    )
