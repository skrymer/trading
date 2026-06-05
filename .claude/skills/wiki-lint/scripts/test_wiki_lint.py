#!/usr/bin/env python3
"""Tests for wiki_lint.py — the deterministic health-check over a knowledge/ wiki tree.

Run: python3 -m pytest test_wiki_lint.py  (or `python3 test_wiki_lint.py`)
"""
import tempfile
import unittest
from pathlib import Path

import wiki_lint


def page(frontmatter_summary=True, status="active", body="", title="T"):
    """Minimal wiki page with frontmatter. summary omitted when frontmatter_summary=False."""
    fm = ["---", "type: concept", f"title: {title}"]
    if frontmatter_summary:
        fm.append("summary: a gist")
    fm += [f"status: {status}", "updated: 2026-06-05", "---", ""]
    return "\n".join(fm) + body


class WikiLintTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        (self.root / "wiki" / "concepts").mkdir(parents=True)

    def tearDown(self):
        self.tmp.cleanup()

    def write(self, rel, text):
        p = self.root / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(text)
        return p

    def test_dangling_link_reported_but_code_examples_excluded(self):
        # Given a page linking one real page, one ghost, and a backtick example
        self.write("wiki/concepts/real-page.md", page(body="# Real"))
        self.write(
            "wiki/concepts/source.md",
            page(body="Links to [[real-page]] and [[ghost]]. Convention: a `[[example]]` is illustrative."),
        )

        # When linted
        findings = wiki_lint.lint(self.root)

        # Then only the genuine dangling link is reported — the backtick example is not
        dangling = {f["detail"] for f in findings if f["kind"] == "dangling-link"}
        self.assertIn("ghost", dangling)
        self.assertNotIn("real-page", dangling)
        self.assertNotIn("example", dangling)

    def test_orphan_pages_have_no_inbound_links_excluding_control_files(self):
        # Given a hub (linked from index) that links a concept, an unlinked orphan, and the index
        self.write("wiki/concepts/linked.md", page(body="# Linked"))
        self.write("wiki/concepts/orphan.md", page(body="# Orphan — nobody links here"))
        self.write("wiki/concepts/hub.md", page(body="See [[linked]]."))
        self.write("wiki/index.md", page(title="Index", body="catalog: [[hub]]"))  # control file links hub

        # When linted
        findings = wiki_lint.lint(self.root)

        # Then only the genuinely unreferenced content page is an orphan
        orphans = {f["page"] for f in findings if f["kind"] == "orphan"}
        self.assertTrue(any(o.endswith("orphan.md") for o in orphans), orphans)
        self.assertFalse(any(o.endswith("linked.md") for o in orphans), orphans)
        self.assertFalse(any(o.endswith("index.md") for o in orphans), orphans)
        self.assertFalse(any(o.endswith("hub.md") for o in orphans), orphans)

    def test_missing_summary_flagged_on_content_pages(self):
        # Given a linked page without a summary: field (linked so it isn't also an orphan)
        self.write("wiki/concepts/no-summary.md", page(frontmatter_summary=False, body="# X"))
        self.write("wiki/index.md", page(title="Index", body="[[no-summary]]"))

        # When linted
        findings = wiki_lint.lint(self.root)

        # Then the missing summary is flagged
        missing = {f["page"] for f in findings if f["kind"] == "missing-summary"}
        self.assertTrue(any(p.endswith("no-summary.md") for p in missing), missing)

    def test_summary_over_200_chars_flagged(self):
        # Given a page whose summary exceeds the 200-char budget
        long_fm = "\n".join(["---", "type: concept", "title: T", "summary: " + ("x" * 201),
                             "status: active", "updated: 2026-06-05", "---", "# X"])
        self.write("wiki/concepts/verbose.md", long_fm)
        self.write("wiki/index.md", page(title="Index", body="[[verbose]]"))

        # When linted, the over-budget summary is flagged
        findings = wiki_lint.lint(self.root)
        over = {f["page"] for f in findings if f["kind"] == "summary-too-long"}
        self.assertTrue(any(p.endswith("verbose.md") for p in over), over)

    def test_invalid_status_flagged_and_disputed_surfaced(self):
        # Given one page with an out-of-enum status and one genuinely disputed page
        self.write("wiki/concepts/bad-status.md", page(status="wip", body="# X"))
        self.write("wiki/concepts/contested.md", page(status="disputed", body="# Y"))
        self.write("wiki/index.md", page(title="Index", body="[[bad-status]] [[contested]]"))

        # When linted
        findings = wiki_lint.lint(self.root)

        # Then the bad status is an error and the disputed page is surfaced for resolution
        bad = {f["page"] for f in findings if f["kind"] == "invalid-status"}
        disputed = {f["page"] for f in findings if f["kind"] == "disputed"}
        self.assertTrue(any(p.endswith("bad-status.md") for p in bad), bad)
        self.assertTrue(any(p.endswith("contested.md") for p in disputed), disputed)
        self.assertFalse(any(p.endswith("contested.md") for p in bad), "disputed is a valid status")

    def test_index_drift_flags_content_page_not_in_index(self):
        # Given a page linked from a concept (so not an orphan) but absent from index.md
        self.write("wiki/concepts/in-catalog.md", page(body="# In catalog"))
        self.write("wiki/concepts/missing-page.md", page(body="# Missing from index"))
        self.write("wiki/concepts/hub.md", page(body="[[in-catalog]] and [[missing-page]]"))
        self.write("wiki/index.md", page(title="Index", body="[[in-catalog]] [[hub]]"))

        # When linted
        findings = wiki_lint.lint(self.root)

        # Then the linked-but-uncataloged page is index-drift; the cataloged one is not
        drift = {f["page"] for f in findings if f["kind"] == "index-drift"}
        self.assertTrue(any(p.endswith("missing-page.md") for p in drift), drift)
        self.assertFalse(any(p.endswith("in-catalog.md") for p in drift), drift)

    def test_provenance_markers_surfaced_for_review(self):
        # Given a page with two inferred claims and one ambiguous, and a clean page with none
        marked = page(body="A claim.^[inferred] Another.^[inferred — why] Contested.^[ambiguous]")
        self.write("wiki/concepts/synthesised.md", marked)
        self.write("wiki/concepts/sourced.md", page(body="# All from sources, no markers"))
        self.write("wiki/index.md", page(title="Index", body="[[synthesised]] [[sourced]]"))

        # When linted
        findings = wiki_lint.lint(self.root)

        # Then the marked page is surfaced (counts reported); the clean page is not
        prov = {f["page"]: f["detail"] for f in findings if f["kind"] == "provenance"}
        marked_hit = next((d for p, d in prov.items() if p.endswith("synthesised.md")), None)
        self.assertIsNotNone(marked_hit, prov)
        self.assertIn("2 inferred", marked_hit)
        self.assertIn("1 ambiguous", marked_hit)
        self.assertFalse(any(p.endswith("sourced.md") for p in prov), prov)


if __name__ == "__main__":
    unittest.main()
