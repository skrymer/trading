#!/usr/bin/env python3
"""Deterministic health-check over a knowledge/ LLM-wiki tree (issue #108).

The mechanical half of `/wiki-lint`: dangling links, orphans, frontmatter/summary hygiene,
index drift, status-enum + disputed surfacing, staleness, and provenance drift. The judgment
half (contradictions, synthesis gaps, missing concept pages) stays in SKILL.md — Claude does it.

Usage: python3 wiki_lint.py <knowledge-root>   # default: cwd
Findings are dicts: {"kind": str, "page": str, "detail": str}.
"""
import re
import sys
from pathlib import Path

# Inline `code` and fenced ```code``` blocks — wikilinks inside them are convention examples, not links.
_FENCE = re.compile(r"```.*?```", re.DOTALL)
_INLINE = re.compile(r"`[^`]*`")
_LINK = re.compile(r"\[\[([^\]]+)\]\]")
_INFERRED = re.compile(r"\^\[inferred")
_AMBIGUOUS = re.compile(r"\^\[ambiguous")


def strip_code(text):
    """Remove fenced and inline code so example wikilinks aren't mistaken for real ones."""
    return _INLINE.sub("", _FENCE.sub("", text))


def link_basename(raw):
    """`folder/page#anchor|alias` -> `page` (Obsidian resolves by basename)."""
    return raw.split("|")[0].split("#")[0].strip().split("/")[-1]


def extract_links(text):
    """Set of linked page basenames in `text`, excluding code examples."""
    return {link_basename(m) for m in _LINK.findall(strip_code(text))}


# Control/navigation files are never orphans — they are entry points, not linked-to content.
CONTROL_FILES = {"index", "log", "overview", "purpose", "CLAUDE", "README"}
SUMMARY_MAX = 200
VALID_STATUS = {"seed", "active", "stable", "superseded", "disputed"}
# A page with this many inbound links is a hub — an unverified claim here propagates widely.
HUB_THRESHOLD = 3


def parse_frontmatter(text):
    """Shallow key->value of a leading `---` YAML block. {} when absent. No yaml dependency —
    we only read scalar string fields (summary, status, updated, type, title)."""
    if not text.startswith("---"):
        return {}
    end = text.find("\n---", 3)
    if end == -1:
        return {}
    fm = {}
    for line in text[3:end].splitlines():
        if ":" in line and not line.startswith(" "):
            key, _, val = line.partition(":")
            fm[key.strip()] = val.strip()
    return fm


def find_pages(root):
    """basename -> Path for every markdown page under root."""
    return {p.stem: p for p in Path(root).rglob("*.md")}


def link_graph(pages):
    """basename -> count of inbound [[links]] from other pages (excludes self-links).
    Also the centrality signal: high inbound count = a hub page (errors there propagate)."""
    inbound = {b: 0 for b in pages}
    for basename, path in pages.items():
        for link in extract_links(path.read_text()):
            if link in inbound and link != basename:
                inbound[link] += 1
    return inbound


def lint(root):
    root = Path(root)
    pages = find_pages(root)
    inbound = link_graph(pages)
    indexed = extract_links(pages["index"].read_text()) if "index" in pages else set()
    findings = []
    for basename, path in sorted(pages.items()):
        rel = str(path.relative_to(root))
        text = path.read_text()
        fm = parse_frontmatter(text)
        for link in sorted(extract_links(text)):
            if link not in pages:
                findings.append({"kind": "dangling-link", "page": rel, "detail": link})
        if basename not in CONTROL_FILES and inbound[basename] == 0:
            findings.append({"kind": "orphan", "page": rel, "detail": "no inbound [[links]]"})
        if basename not in CONTROL_FILES and basename not in indexed:
            findings.append({"kind": "index-drift", "page": rel, "detail": "not listed in index.md"})
        # Hygiene checks apply to frontmatter'd content pages (skip control/no-frontmatter files).
        if fm and basename not in CONTROL_FILES:
            if "summary" not in fm:
                findings.append({"kind": "missing-summary", "page": rel, "detail": "no summary: field"})
            elif len(fm["summary"]) > SUMMARY_MAX:
                findings.append({"kind": "summary-too-long", "page": rel,
                                 "detail": f"{len(fm['summary'])} > {SUMMARY_MAX} chars"})
            status = fm.get("status")
            if status and status not in VALID_STATUS:
                findings.append({"kind": "invalid-status", "page": rel,
                                 "detail": f"'{status}' not in {sorted(VALID_STATUS)}"})
            elif status == "disputed":
                findings.append({"kind": "disputed", "page": rel, "detail": "unresolved — resolve in lint"})
            stripped = strip_code(text)
            n_inf = len(_INFERRED.findall(stripped))
            n_amb = len(_AMBIGUOUS.findall(stripped))
            if n_inf or n_amb:
                hub = " [HUB — high inbound, verify first]" if inbound[basename] >= HUB_THRESHOLD else ""
                findings.append({"kind": "provenance", "page": rel,
                                 "detail": f"{n_inf} inferred, {n_amb} ambiguous{hub}"})
    return findings


def main(argv):
    root = argv[0] if argv else "."
    findings = lint(root)
    for f in findings:
        print(f"{f['kind']}\t{f['page']}\t{f['detail']}")
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
