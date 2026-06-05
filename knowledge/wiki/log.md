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
