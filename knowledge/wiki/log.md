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
## [2026-06-07] ingest | #121 Stage 3+4 consolidation — distilled the run-result docs into 4 new sources/ pages ([[2026-05-28-mean-reversion-firewall-runs]], [[2026-05-27-v4-block-a-sweep]], [[2026-05-27-strategy-screen-sweep]], [[2026-06-05-funnel-deepresearch-findings]]); captured the abandoned program in [[regime-conditional-portfolio]] (ADR 0010 repointed) + the sizing rationale in [[position-sizing-and-risk]]. Folded durable methodology into [[the-funnel]] (config-vs-premise + data-span), [[parameter-robustness-g13]] (two-tier + joint-fragility), [[minervini-vcp-breakout]] (pivot-not-entry-close stop). CORRECTNESS FIX: [[gjallarhorn]] composite A/B was stale ("pending") — now records the NO-GO verdict + 17-event crisis map. Repointed all sources[]/prose/CONTEXT/README refs; deleted the 29 strategy_exploration/*.md dev docs + diagnostics/ (dossier/ kept). Wiki is now the single source of truth.
## [2026-06-07] lint | clean after #121 consolidation — no dangling links / orphans / index-drift; informational ^[inferred]/^[ambiguous] provenance flags only.
## [2026-06-07] ingest | Quant pre-resume correctness consult ([[2026-06-07-funnel-correctness-consult]]) — 3 corrections before resuming exploration. (1) **G13 is ADVISORY, not binding** (code `g13_aggregate.py: binding=False`); fixed [[parameter-robustness-g13]] (removed the binding-firewall-gate wording + the ^[ambiguous] note, added the definitive advisory status + the two calibration sweeps that gate the bind; two-tier scoping marked not-yet-wired) + [[component-firewall]] (frontmatter + G13 bullet: G10/G11/G14 binding, G13 advisory). (2) **RS-momentum-rotation downgraded** from deprecated to untested-hypothesis — only the [[george]] 52wk-anchoring ranker is earned-dead; factor-neutral idiosyncratic-RS un-ruled-out; updated [[purpose]], [[overview]], george scope note. (3) **RSP #99 done** — BTC+Tyr data block lifted but permanent Block-A 2003-span caveat (dot-com gap) remains; updated [[btc-tyr]], overview, purpose. Follow-ups tracked as issues (G1 idiosyncratic-RS screen, D2 p95 calibration, PBO, joint-G13).
