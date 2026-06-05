---
name: wiki-lint
description: Health-check the knowledge/ strategy-research wiki — dangling links, orphans, frontmatter/summary hygiene, index drift, invalid/disputed status, and provenance drift (deterministic via wiki_lint.py), plus the judgment checks (contradictions, synthesis gaps, missing concept pages). Report-only by default; the operator applies fixes. Run periodically and at the start of a new candidate.
argument-hint: "[knowledge-root, default knowledge/]"
---

# Wiki Lint

Keeps the `knowledge/` wiki ([#84](https://github.com/skrymer/trading/issues/84)) from decaying — the
failure mode that killed the handover docs it replaced. **Report-only**: it surfaces a punch-list; you
(or main-loop Claude) apply the fixes. This matches the wiki's "propose, operator commits" rule.

## When to run

- **At the start of a new candidate** (wired from `/strategy-exploration`) — so you consult clean
  knowledge before scoping. This is the primary trigger.
- Periodically, when the wiki has grown (after a batch of ingests / doc migrations).

## Two halves

### Deterministic — `scripts/wiki_lint.py` (run it first)

```
python3 .claude/skills/wiki-lint/scripts/wiki_lint.py knowledge
```

Emits tab-separated `kind <TAB> page <TAB> detail`. The checks:

| kind | meaning | severity |
|---|---|---|
| `dangling-link` | `[[X]]` with no page `X` (code-fenced/backtick examples excluded) | fix |
| `orphan` | content page with zero inbound `[[links]]` (control files exempt) | fix — add ≥1 inbound link |
| `index-drift` | content page not listed in `wiki/index.md` | fix — add to catalog |
| `missing-summary` | frontmatter page with no `summary:` | fix |
| `summary-too-long` | `summary:` > 200 chars | fix |
| `invalid-status` | `status:` not in {seed, active, stable, superseded, disputed} | fix |
| `disputed` | `status: disputed` — surfaced for resolution | review |
| `provenance` | page carries `^[inferred]`/`^[ambiguous]` markers; `[HUB]` = high inbound (verify first) | review |

The script is `O(pages)` — fast to hundreds of pages. Beyond a few hundred, the retrieval story shifts to
`qmd` (see `/wiki-query`); the lint script itself stays linear.

### Judgment — you do these (not scriptable)

Read `index.md` + the flagged pages, then check:

- **Contradictions** — two linked pages making conflicting claims. Add a `> ⚠ CONTRADICTION:` flag and set
  the weaker page's `status: disputed` (don't silently overwrite).
- **Synthesis gaps** — concepts that co-occur across several pages with no `synthesis/` bridge page.
- **Missing pages** — an important concept/candidate referenced repeatedly with no page of its own.
- **Resolvable provenance** — an `^[inferred]` claim a source could now confirm (promote to unmarked) or
  refute (flag). Prioritize `[HUB]` pages — errors there propagate.

## Output

Present a punch-list grouped by kind (deterministic findings + judgment findings), each with the file and
a one-line reason. Recommend fixes; **apply only what the operator approves**. Then append one line to
`wiki/log.md`:

```
## [YYYY-MM-DD] lint | N findings (orphans=A drift=B disputed=C provenance=D); fixed=E
```

## Tests

`python3 .claude/skills/wiki-lint/scripts/test_wiki_lint.py` (stdlib `unittest`).

## Not in scope (deliberately)

Auto-fix/"consolidate" mode, tag-cohesion metrics, PII/visibility scanning, `base_confidence`, typed-edge
validation — the heavier `obsidian-wiki` machinery, deferred until the wiki is much larger.
