from pipeline.model import Block, Span
from pipeline.parse import parse_book, reference_text
from tests.markup import chapter, page


def roles_and_texts(blocks):
    return [(b.role, b.text) for b in blocks]


def test_front_matter_skipped():
    front = '<div class="chapter"><h2>D R A C U L A</h2><p>Preface text.</p></div>'
    blocks = parse_book(page(front, chapter(1, "<p>Hello.</p>")))
    assert roles_and_texts(blocks) == [
        ("header", "JONATHAN HARKER’S JOURNAL"),
        ("para", "Hello."),
    ]
    assert all(b.chapter == 1 for b in blocks)


def test_italic_spans_and_whitespace():
    html = page(chapter(1, '<p><i>3 May. Bistritz.</i>—Left Munich at 8:35 '
                           '<span class="smcap">P. M.</span>, on\n1st May.</p>', subtitle=None))
    [block] = parse_book(html)
    assert block.spans == (
        Span("3 May. Bistritz.", True),
        Span("—Left Munich at 8:35 P. M., on 1st May.", False),
    )


def test_roles_by_class():
    inner = """
<p class="c">(<i>Kept in shorthand.</i>)</p>
<p class="c"><i>Letter from Miss Mina Murray to Miss Lucy Westenra.</i></p>
<p class="letra"><i>Dr. Seward’s Diary.</i></p>
<p class="r">
“<i>9 May.</i><br>
</p>
<p class="nind">
“My dearest Lucy,—<br>
</p>
<p class="nind">W<small>HEN</small> we arrived at the Berkeley Hotel.</p>
<div class="poem"><div class="stanza">
<span class="i0">“Denn die Todten reiten schnell”—<br></span>
<span class="i0">(“For the dead travel fast.”)<br></span>
</div></div>
"""
    blocks = parse_book(page(chapter(1, inner, subtitle=None)))
    assert roles_and_texts(blocks) == [
        ("note", "(Kept in shorthand.)"),
        ("header", "Letter from Miss Mina Murray to Miss Lucy Westenra."),
        ("header", "Dr. Seward’s Diary."),
        ("signoff", "“9 May."),
        ("salutation", "“My dearest Lucy,—"),
        ("para", "WHEN we arrived at the Berkeley Hotel."),
        ("verse", "“Denn die Todten reiten schnell”—\n(“For the dead travel fast.”)"),
    ]


def test_a_line_of_dots_is_a_divider():
    # The book marks a break with a centred block of spaced dots (#178); its text stays, so the book still matches.
    inner = '<p>Before.</p>\n<p class="cb">\n. . . . .<br>\n. . . . .<br>\n</p>\n<p>After.</p>'
    blocks = parse_book(page(chapter(1, inner, subtitle=None)))
    assert roles_and_texts(blocks) == [("para", "Before."), ("divider", ". . . . .\n. . . . ."), ("para", "After.")]


def test_multiline_signoff_keeps_line_breaks():
    inner = '<p class="r">\n“Your friend,<br>\n“<span class="smcap">Dracula</span>.”<br>\n</p>'
    [block] = parse_book(page(chapter(1, inner, subtitle=None)))
    assert block == Block("signoff", (Span("“Your friend,\n“Dracula.”"),), 1)


def test_blank_paragraphs_skipped():
    blocks = parse_book(page(chapter(1, "<p>\u00a0</p><p> </p><p>Text.</p>", subtitle=None)))
    assert roles_and_texts(blocks) == [("para", "Text.")]


def test_stops_at_the_end():
    inner = '<p>Last words.</p>\n<p class="c">\nTHE END<br>\n</p>\n<p>Ask for our free list!</p>'
    blocks = parse_book(page(chapter(27, inner, subtitle=None)))
    assert roles_and_texts(blocks) == [("para", "Last words.")]


def test_reference_text_is_independent_of_roles():
    front = '<div class="chapter"><p>Preface.</p></div>'
    inner = '<p>Body.</p><p class="c">THE END</p><p>Advert.</p>'
    text = reference_text(page(front, chapter(1, inner)))
    assert "JONATHAN HARKER’S JOURNAL" in text
    assert "Body." in text
    assert "CHAPTER" not in text
    assert "Preface" not in text
    assert "Advert" not in text


def test_unanchored_chapter_after_the_story_is_kept():
    note = '<div class="chapter"><h2>NOTE</h2><p>Seven years ago.</p><p class="c">THE END</p></div>'
    html = page(chapter(27, "<p>Last.</p>", subtitle=None), note)
    blocks = parse_book(html)
    assert roles_and_texts(blocks) == [("para", "Last."), ("header", "NOTE"), ("para", "Seven years ago.")]
    assert blocks[-1].chapter == 27
    text = reference_text(html)
    assert "NOTE" in text and "Seven years ago." in text


PREFACE = ('<div class="chapter"><p class="nind">\nHow these papers have been placed in sequence will be made '
           'manifest in the\nreading of them.\n</p></div>')
TITLE = '<div class="chapter"><h2>D R A C U L A</h2><hr></div>'


def test_preface_before_chapter_one_is_kept():
    copyright_page = '<div class="chapter"><p class="c">Copyright, 1897.</p></div>'
    html = page(copyright_page, PREFACE, TITLE, chapter(1, "<p>Hello.</p>"))
    blocks = parse_book(html)
    assert roles_and_texts(blocks) == [
        ("para", "How these papers have been placed in sequence will be made manifest in the reading of them."),
        ("header", "JONATHAN HARKER’S JOURNAL"),
        ("para", "Hello."),
    ]
    assert blocks[0].chapter == 0
    text = reference_text(html)
    assert "How these papers" in text
    assert "Copyright" not in text and "D R A C U L A" not in text
