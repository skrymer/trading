---
type: query
title: queries/ — how good answers compound
status: seed
tags: [meta]
updated: 2026-06-05
---

# queries/ — file good answers back here

This folder is the **compounding** mechanism of the wiki. When someone asks a recurring research
question — "what do we know about X?", "has anything beaten narrow-leadership tape?", "why was candidate Y
rejected?" — and the synthesized, `[[cited]]` answer is good and reusable, **file it back here as a page**
so the next ask reads the answer instead of re-deriving it.

## Convention

- One file per question, kebab-case (e.g. `why-did-the-breakout-fail.md`).
- Frontmatter `type: query`, plus `related:` links to the pages the answer drew on.
- Open with the question verbatim, then the answer with inline `[[citations]]`.
- If the answer reveals a missing concept/entity page, **create that page** and link it — that's the
  query operation feeding the wiki.

This folder is seeded empty on purpose; it fills as real questions get asked.
