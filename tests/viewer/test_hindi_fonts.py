"""Selected Hindi fonts are applied to Unicode runs in every Office viewer."""

from pathlib import Path


FONT_SCRIPT = (
    Path(__file__).resolve().parents[2]
    / "app"
    / "src"
    / "main"
    / "assets"
    / "viewer"
    / "hindi-fonts.js"
)
PREFERRED_FONT = """
:root { --fz-hindi-default: "Mukta"; }
.fz-hindi-run {
    font-family: var(--fz-hindi-default), "Noto Sans Devanagari", sans-serif !important;
}
"""


def load_font_script(page, markup):
    page.set_content(markup)
    page.add_style_tag(content=PREFERRED_FONT)
    page.add_script_tag(path=str(FONT_SCRIPT))


def test_unicode_runs_use_the_preferred_font_and_leave_latin_text_alone(page):
    load_font_script(page, '<div id="container">English हिन्दी and नमस्ते</div>')
    page.wait_for_function("() => document.querySelectorAll('#container .fz-hindi-run').length === 2")

    runs = page.locator("#container .fz-hindi-run").all_text_contents()
    families = page.locator("#container .fz-hindi-run").evaluate_all(
        "nodes => nodes.map(node => getComputedStyle(node).fontFamily)"
    )

    assert runs == ["हिन्दी", "नमस्ते"]
    assert all(family.split(",", 1)[0].strip().strip("\\\"'") == "Mukta" for family in families)
    assert page.locator("#container").text_content() == "English हिन्दी and नमस्ते"


def test_selected_font_precedes_the_embedded_font_in_presentation_svg(page):
    load_font_script(
        page,
        '<div id="result"><svg><text id="chart-label" style="font-family: Roboto">हिन्दी</text></svg></div>',
    )
    page.wait_for_function("() => document.getElementById('chart-label').hasAttribute('data-fz-font')")

    family = page.locator("#chart-label").evaluate("node => getComputedStyle(node).fontFamily")
    priority = page.locator("#chart-label").evaluate(
        "node => node.style.getPropertyPriority('font-family')"
    )

    assert family.split(",", 1)[0].strip().strip("\\\"'") == "Mukta"
    assert priority == "important"


def test_text_added_later_to_a_spreadsheet_root_also_gets_the_font(page):
    load_font_script(page, '<div id="sheet"></div>')
    page.locator("#sheet").evaluate("node => { node.innerHTML = '<table><tr><td>नमस्ते</td></tr></table>'; }")
    page.wait_for_function("() => document.querySelector('#sheet .fz-hindi-run')")

    assert page.locator("#sheet .fz-hindi-run").text_content() == "नमस्ते"
