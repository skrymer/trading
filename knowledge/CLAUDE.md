# Strategy-Research Knowledge Base — schema & conventions

This is a **Karpathy LLM-Wiki** for the trading platform's strategy-research knowledge: the
hard-won, expensive-to-rederive understanding of *what works, what fails, and why* across the
backtesting funnel. Claude **owns and maintains** it incrementally; [Obsidian](#obsidian) is the
human browse layer; git versions it. Unlike RAG (retrieve-fresh, nothing accumulates), this is a
**compounding artifact** — cross-references are built once, contradictions flagged once, syntheses
evolve.

> Obsidian is the IDE · the LLM is the programmer · the wiki is the codebase.

Read [`index.md`](wiki/index.md) first on any query. Read [`purpose.md`](purpose.md) for *what we're hunting and why*.

---

## Folder structure

```
knowledge/
  CLAUDE.md      ← this file: structural governance (how the wiki is built)
  purpose.md     ← directional intent: the hunt, open questions, evolving thesis
  wiki/
    index.md     ← categorized one-line catalog — READ FIRST
    log.md       ← append-only operation log (grep-able)
    overview.md  ← evolving global summary (state of the search)
    concepts/    ← methodology + failure-mode pages (the analyst references)
    entities/    ← one page per candidate / condition / ranker / gate
    sources/     ← one dated summary per firewall run / screen / quant consult
    synthesis/   ← evolving "everything we know about X" cross-cuts
    queries/     ← good Q&A answers filed back as reusable pages
```

The **`raw/` layer is the existing repo** — `strategy_exploration/*.md` (lab-notebook dev docs),
`strategy_exploration/dossier/*.jsonl` (per-candidate execution logs, ADR 0008), and the run-result
docs. The wiki is the *generated, atomic, interlinked* layer over that raw material. **Do not copy
raw docs in here** — summarize them into `sources/` pages and link back via frontmatter `sources[]`.

---

## Page schema

Every page opens with YAML frontmatter:

```yaml
---
type: concept | entity | source | synthesis | query
title: Human Readable Title
summary: One sentence, ≤200 chars — the gist, read from frontmatter without opening the page.
status: seed | active | stable | superseded | disputed   # how settled the page is
tags: [failure-mode, methodology, candidate, ...]
sources: ["strategy_exploration/GJALLARHORN_STRATEGY_DEVELOPMENT.md", "..."]  # raw traceability
related: ["[[other-page]]", "..."]
updated: 2026-06-05
---
```

Body shape by type:

- **concept** — *Definition · How to detect · Why it kills (or matters) · Instances · Related.*
  A failure-mode or methodology page. Written **richer than its memory hook** — the memory holds the
  terse operational rule; the concept page holds the full anatomy + every candidate that hit it.
- **entity** — *Premise · Status · Funnel history · Verdicts · Why it died/lives · Failure modes hit · Reusable findings.*
  One per candidate / condition / ranker / gate. Links to its dossier and `sources/` pages.
- **source** — a dated summary of ONE run/screen/consult: *what was run · headline numbers · what it
  taught · which pages it updated.* The ingest unit.
- **synthesis** — an evolving cross-cut ("long-premise strategies in narrow-leadership tape").
- **query** — a good answer to a recurring question, filed back so it compounds.

---

## Conventions

- **`summary:`** — a ≤200-char gist on every page, so a reader/agent can judge relevance from
  frontmatter without opening the page (see [retrieval cost hierarchy](#retrieval)).
- **`status: disputed`** — use when a page's claim is contradicted by a source and not yet resolved
  (pairs with the inline contradiction flag below). The other states: `seed` (just stood up),
  `active` (evolving), `stable` (settled), `superseded` (kept for history, pointer to its replacement).
- **`[[wikilink]]`** for every cross-reference (Obsidian-native; auto-backlinks). Link liberally — a
  `[[link]]` to a page that doesn't exist yet marks a page worth writing.
- **Unique basenames across the whole vault** — Obsidian resolves `[[gjallarhorn]]` by basename
  regardless of folder, so never reuse a basename in two folders.
- **One page = one idea.** Atomic. If a page sprawls, split it and link.
- **Flag contradictions inline** rather than silently overwriting: `> ⚠ CONTRADICTION: page X says …, this run shows …` → set `status: disputed` → resolve in the next lint.
- **kebab-case filenames**; `title:` is the human form.
- Dates are absolute (`2026-06-05`), never "last week".

### Provenance markers

This wiki feeds *domain* judgment — an analyst must be able to tell a **quant-signed fact** from
**Claude's synthesis**. So mark non-source claims inline:

- **(unmarked)** — stated by a source (quant consult, firewall run, an existing memory/ADR/CONTEXT term).
- **`^[inferred]`** — Claude's synthesis / a cross-cut not stated by any single source.
- **`^[ambiguous]`** — sources disagree, or the claim is unresolved.

This is the wiki analogue of the project rule *don't present unverified hypotheses as fact*. A claim
carrying weight in a verdict (a threshold, a sign, a "this kills it") should be unmarked only if a
source backs it; otherwise mark it. Backfill of existing pages is **ongoing** — new claims are marked at
write time; older pages get marked during lint.

---

## The three operations

Each is an **invocable skill** (#108), wired to a funnel trigger so maintenance happens automatically:

- **ingest** → **`/wiki-ingest`** (triggered from `/strategy-exploration record`): write a `sources/`
  summary → update every affected `concepts/`/`entities/` page (a single source typically touches
  several) → add the entity's verdict → append one [`log.md`](wiki/log.md) line. Reads the dossier RECORD
  event, not the evaluator's output (survives #54).
- **query** → **`/wiki-query`** (on demand): read `index.md` → relevant pages (cheapest-first) →
  synthesize **with `[[citations]]`** → file a good answer back as a `queries/` page.
- **lint** → **`/wiki-lint`** (triggered from `/strategy-exploration new`; also periodic): deterministic
  checks (`scripts/wiki_lint.py` — dangling links, orphans, frontmatter/summary, index drift, status,
  provenance) + judgment checks (contradictions, synthesis gaps, missing pages). Report-only.

### `log.md` line format

```
## [2026-06-05] ingest | Gjallarhorn NULL + first screen
## [2026-06-05] lint | resolved breakout/MR contradiction
```
Recent: `grep "^## \[" wiki/log.md | tail`.

### <a name="retrieval"></a>Retrieval cost hierarchy

When reading the wiki, go cheapest-first — don't open full pages until needed:

1. `index.md` — existence + titles + the catalog.
2. the page's `summary:` frontmatter — the gist.
3. `grep` with context for a specific claim.
4. full page read — last resort.

---

## The boundary: wiki vs the other knowledge stores  ← the one discipline that matters

This wiki must **not duplicate** the four existing stores. Each owns a distinct altitude:

| Store | Owns | Altitude / form |
|---|---|---|
| **memory** (`.claude/memory/`) | how-to-work-here (feedback) + verified operational facts + user profile | terse one-fact hooks; `[[links]]` *into* this wiki |
| **this wiki** (`knowledge/`) | domain/research knowledge an *analyst* consults: failure-mode anatomies, candidate post-mortems, methodology deep-dives, syntheses | rich, interlinked, citeable |
| **ADRs** (`docs/adr/`) | irreversible architectural/methodology decisions | one decision per file, immutable record |
| **CONTEXT.md** | the domain glossary (term → definition) | canonical definitions |
| **dossier** (`strategy_exploration/dossier/`) | per-candidate append-only execution log (ADR 0008) | machine JSONL |

**Rule of thumb:**
- An *analyst* would consult it to judge a candidate → **wiki**.
- *Claude-the-operator* needs it to work correctly here → **memory**.
- It's a decision that must not silently change → **ADR**.
- It's a term's definition → **CONTEXT.md**.

**Non-duplication rule:** the failure modes already exist as terse memory hooks
(`feedback_aliased_regime_sensitivity`, …). The wiki page is the **expanded reference at a different
altitude**, not a copy — memory keeps the one-line rule and `[[links]]` here; the wiki holds the full
anatomy, detection procedure, and the list of every candidate that hit it. If you find yourself
copying a memory verbatim into the wiki, stop — link instead and add only what the memory omits.

---

## Maintenance discipline (anti-decay)

The handover docs this wiki replaces *decayed because nobody maintained them*. To avoid the same fate:

- The **funnel analyst agents** (`firewall-analyst`, `strategy-screen-analyst`,
  `condition-screen-analyst`) **consult** `index.md` + relevant `concepts/` before a verdict and emit
  a `KNOWLEDGE-UPDATE:` line when they surface a durable finding or a new failure-mode instance. They
  have Read+Bash, not Write — **they propose, the operator (main-loop Claude) commits** the page edit.
- After any funnel run that taught something durable, **ingest it** (don't let it live only in the
  dev doc).
- Run a **lint** pass periodically — orphans and stale claims are the decay signal.

---

## <a name="obsidian"></a>Obsidian

Obsidian is **optional and additive** — the wiki is fully functional via grep + `index.md` without
it. To browse: *Open folder as vault* → point at `knowledge/`. Then `[[wikilinks]]` become clickable,
frontmatter shows as filterable properties, and the **graph view + auto-backlinks** come for free.
The vault config dir (`.obsidian/`) is **gitignored** — the committed artifact is the markdown.
GitHub's web view does **not** render `[[wikilinks]]` (they show as literal text); browse in Obsidian
or via Claude.
