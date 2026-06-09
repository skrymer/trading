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
## [2026-06-08] ingest | #135 George Random-baseline re-validation RESULT — re-ran George's class-deprecation against the now-seeded baseline as a 17-draw distribution (seeds 1..17, George seed 42, current 4,997-name universe) per the pre-registered rule. New [[2026-06-08-george-random-revalidation-prereg]] (rule + RESULT). Verdict: **deprecation HOLDS — affirmatively re-confirmed** (E=BETA edge +0.96% at floor ∧ C=BETA CAGR +1.75% below the entire Random 6.6–12.6% cloud ∧ d=BETA K=0/7); stronger than the original single unseeded draw. Updated [[george]] (status → re-validation COMPLETE, seeded 17-draw table + persisted george.request.json per ADR 0017), [[beta-delivery]] (caveat → re-run and HELD), [[2026-06-04-gjallarhorn-null]] (RNG footnote closed), index. George remains non-tradable (independent /strategy-screen FAIL stands).
## [2026-06-08] ingest | #130 MRM screen REJECTED — single-factor SPY-beta-residual momentum is anti-selective [[beta-delivery]]. Lost to the now-seeded Random baseline on edge (+2.80 vs +6.21) AND CAGR (8.95 vs 23.05); stronger signature than [[george]] (which only matched on edge). Stage 1 not run (foregone negative). The 23% Random CAGR diagnosed as structural long-beta (cash-dodge 2008 + equal-weight small-cap 2009/2010 rebound; SPY 9.86% over same support), NOT survivorship (universe carries 1500+ delisted names) — corrected after a bad probe. New [[mrm]] + [[2026-06-08-mrm-screen-reject]]; updated [[beta-delivery]] (2nd instance), purpose.md (#4 class stays untested, single-factor recipe crossed off), [[2026-06-08-random-baseline-reproducibility-fix]] (open question resolved), index. Multi-factor-neutral recipe filed as #137.
## [2026-06-08] ingest | BTC+Tyr DEAD — breadth-thrust gate REJECTED at /condition-screen
Screened the genuinely-fresh component (breadth-thrust GATE) in isolation per the 2026-06-08 quant spec
(inline-script dip-then-surge, sweeps window 10±2 / low 30±5 / high 55±5, 300-sym sanity universe). Step-0
cadence PASSED (199 distinct firing dates/21yr) but three binding failures killed it: SPY-regime sign-flip
at all 3 horizons (5d down −1.62%), no 10/20d edge (hit-rate lift negative), and the "thrust" degenerating
into a level gate (all lift in high=50, firing un-holdable; 62% of firings in 2009-14 = lottery). KILLS
[[btc-tyr]] (the order-block trigger was a deprecated-family cousin already weak solo) — NOT a firewall
death (no config_hash burned, G13 brake idle). New [[2026-06-08-btc-breadth-thrust-screen-reject]] +
[[thrust-degenerates-to-level]] concept (seed); updated [[btc-tyr]] (→ superseded/dead), [[lottery-vs-signature]]
+ [[thinning-not-selecting]] (BTC instance), overview.md + purpose.md (funnel now EMPTY; #137 the one filed
thread), index. Authored the gate via /create-condition (efficient O(1) getMarketBreadth point-lookups,
strictly anti-lookahead). Dossier: strategy_exploration/dossier/condition-breadththrust.jsonl.
## [2026-06-08] ingest | PEAD chosen as the new active direction (post-BTC+Tyr) + feasibility GREEN
After [[btc-tyr]] died and emptied the funnel, routed an open-ended next-premise consult to quant-analyst
(emphasising the engine is ours/extensible — indicators are buildable, only raw-data span is fixed). Top
pick: **[[pead]] — Post-Earnings Announcement Drift**, an event-conditioned per-name long (enter on a
confirmed positive earnings-day price gap, hold the multi-week underreaction drift) — the regime-orthogonal
"5th class" the deep-research flagged as durable; off the axis that killed all four deprecated families.
Verified the data kill-switch GREEN: EODHD earnings depth to 1993 (AAPL demo probe), PRD earnings table
245k rows / 3,712 symbols dense 2000-2019 (1899 sentinel report_dates to filter). New [[pead]] entity
(active/scoping); updated purpose.md + overview.md (PEAD = new active direction, #137 demoted) + index.
Next step: author EarningsGapCondition via /create-condition, then /condition-screen (kill on 20d
SPY-regime sign-flip / thrust-degenerates-to-level / one-tape lottery; decisive test = drift survives
AFTER next-session entry). #137 stays a lower-priority class-closer.

## [2026-06-09] ingest | pead REJECT (price-gap proxy) — beta-delivery materialised, class alive; sector-quote engine change filed #143
## [2026-06-09] ingest | PEAD market-neutral gap residual REJECTED (condition-screen, both arms) — KILL trigger, regime sign-flip persisted after neutralisation; price-proxy class condemned -> EPS-gated residual next
