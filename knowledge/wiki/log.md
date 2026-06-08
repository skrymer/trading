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
## [2026-06-06] ingest | Firewall recalibration landed (#106, ADR 0015) — G1 30→25, G9 Sharpe-only ≥0.5, new G15 absolute Calmar ≥1.5; refreshed [[component-firewall]] (code-landed, was "pending") + [[parameter-robustness-g13]] Idunn example (29.36% CAGR now clears the 25% floor; G13 example never hinged on G1)
## [2026-06-06] ingest | Quant pre-restart review — gate basis re-confirmed on cost+idle-cash engine (KEEP, no recalibration) + 25% CAGR floor verdict REALISTIC. New [[2026-06-06-gate-basis-and-cagr-floor-feasibility]]; updated [[component-firewall]], purpose.md (binding wall = regime-survival not the return floor; idle-cash/cost open question resolved), overview.md (stale #101/#103/#105/#106 Live-threads rows corrected)
## [2026-06-06] ingest | #121 Stage 2 candidate backfill — stood up 10 entities/ pages from the strategy_exploration dev docs + dossiers: [[vz3]], [[idunn]], [[mr3]], [[dv1]] (long-pullback-MR family), [[minervini-vcp-breakout]] (shelved breakout edge), [[george]] (beta-delivery / lost-to-Random), [[btc-tyr]] (active search), [[baldr]], [[fenrir]], [[order-block-breakout-condition]] (design-time /condition-screen kills). Catalogued in index.md; overview BTC+Tyr thread repointed to [[btc-tyr]]. Source docs remain (deleted in Stage 4 after sources/ distillation). One DV1 source contradiction flagged ^[ambiguous] (validate-DV1.md vs validation-candidates.md run numbers).
## [2026-06-08] ingest | G-RANDOM baseline reproducibility fix (#130) — RandomRanker was unseeded (randomSeed fed only the 1e-10 tie-break jitter), so every "lost to Random" read was non-reproducible. New [[2026-06-08-random-baseline-reproducibility-fix]]; updated [[beta-delivery]] (reproducibility caveat on the random-ranker tell) + [[george]] (reclassification re-validation pending #135, screen-gate FAIL stands) + index. George re-run filed as #135. New MarketResidualMomentum ranker shipped in #130 to settle the factor-neutral idiosyncratic-RS premise vs the now-reproducible baseline.
