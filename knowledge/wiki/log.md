# Operation log

Append-only, chronological. Format: `## [YYYY-MM-DD] op | summary` where op ∈ {ingest, query, lint}.
Recent: `grep "^## \[" log.md | tail`.

## [2026-06-05] ingest | Skeleton stood up (issue #84)
Seeded the wiki: schema (`CLAUDE.md`) + `purpose.md` + control files (`index`, `log`, `overview`).
8 concept pages (the-funnel, component-firewall, parameter-robustness-g13, participate-and-lose,
thinning-not-selecting, aliased-regime-sensitivity, lottery-vs-signature, crisis-timer-cadence-ceiling),
1 entity ([[gjallarhorn]]), 1 source ([[2026-06-04-gjallarhorn-null]]), 1 synthesis
([[long-premise-in-narrow-leadership]]), queries/ seeded empty. Source material: the failure-mode memory
files + `BACKTESTING_FUNNEL.md` + `GJALLARHORN_STRATEGY_DEVELOPMENT.md`. Boundary vs memory/ADR/CONTEXT/
dossier defined in `CLAUDE.md`. Remaining `strategy_exploration/` docs migrate lazily as touched.

## [2026-06-05] ingest | Adopted 3 ideas from the obsidian-wiki skill
Folded three low-cost ideas from `Ar9av/obsidian-wiki`'s llm-wiki SKILL into the schema: `summary:`
frontmatter (≤200 char, backfilled across all 14 frontmatter pages) + the retrieval cost hierarchy;
`status: disputed` for unresolved contradictions; and **provenance markers** (`^[inferred]`/`^[ambiguous]`)
to separate quant-signed facts from Claude synthesis — demonstrated on [[participate-and-lose]] and
[[long-premise-in-narrow-leadership]], full backfill ongoing during lint. Skipped the manifest/confidence-
formula/tier/env-config/companion-skill machinery (over-engineering at 16 pages). Operations-as-`/wiki-*`-
skills filed as the #84 follow-up.
## [2026-06-05] ingest | component-firewall G16 — added the SPY buy-and-hold Calmar baseline gate (#102, ADR 0013) to the roster; beta-delivery page deferred until a real instance
## [2026-06-05] ingest | beta-delivery (seed) — failure-mode page for the G16 SPY-baseline detector; anatomy + detector settled, instances empty until first rejection
