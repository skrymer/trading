---
name: wiki-query
description: Answer a strategy-research question against the knowledge/ wiki — read cheapest-first (index → frontmatter → grep → full page), synthesize with [[citations]], annotate disputed/stale pages, and file a good answer back as a queries/ page so it compounds. Use to ask "what do we know about X?", "why was candidate Y rejected?", "has anything beaten narrow-leadership tape?".
argument-hint: "<question>"
---

# Wiki Query

Answers from the **compiled** wiki — pre-synthesized, cross-referenced pages — rather than re-deriving
from raw dev docs each time. The compounding half: a good answer gets **filed back** as a `queries/` page,
so the next ask reads it instead of re-deriving.

## Retrieval — cheapest-first (don't open full pages until needed)

1. **Index pass** — read `knowledge/wiki/index.md`; build a candidate set from titles + categories.
2. **Frontmatter pass** — `grep` the `summary:` / `tags:` of candidates to rank relevance without opening
   bodies.
3. **Section pass** — `grep -A 10 -B 2 "<term>"` on the top candidates for the specific claim.
4. **Full read** — only the top ~3 (prefer hub pages — high inbound link count), then follow **one hop**
   of `[[wikilinks]]` and the `related:` frontmatter.

(At a few hundred pages this stays fast. Past that, a `qmd` semantic pass slots in before step 2 —
deferred until the wiki is that large; the hierarchy is already shaped for it.)

## Synthesize

Compose the answer from wiki content with inline `[[page-name]]` citations. **Annotate trust** from each
cited page's `status:`:

- `disputed` → "(DISPUTED — see the contradiction flag)"
- `superseded` → "(SUPERSEDED by [[replacement]])"
- a stale `updated:` date → "(last updated <date> — may be stale)"

Never present an `^[inferred]` claim as a sourced fact — carry the marker's caveat into the answer.

## Answer structure

```
**From the wiki:**
<synthesized answer with [[citations]]>

**Pages consulted:** [[page-a]], [[page-b]]
**Gaps:** <what the wiki doesn't cover — candidate next ingest/research>
```

## File good answers back

If the answer is durable and reusable (not a one-off), offer to write it as `knowledge/wiki/queries/<question>.md`
(`type: query`, `related:` the pages it drew on, the question verbatim + the cited answer). If answering
revealed a missing concept/entity page, **create it** — that's the query operation feeding the wiki. Append
`wiki/log.md`: `## [YYYY-MM-DD] query | <question> — <result, pages consulted>`.

## Not in scope (deliberately)

Typed-edge graph BFS, `qmd` semantic retrieval, visibility/PII filtering — deferred machinery from the
`obsidian-wiki` reference; the cost-hierarchy above is the scalable core.
