# Handover — Regime-conditional framework: next two tracks (#1 defender, #2 regime read-out)

Created 2026-06-03. Paste as the first message of a clean session, or read from disk.
**Obey every CLAUDE.md instruction + saved memories (they override defaults).**
**Never fire a backtest/screen without showing the POST and getting an explicit "go". Never push / open-or-merge a PR / deploy to PRD without explicit per-action permission.**

## Read this first (authoritative, in order)
1. `COMPONENT_FIREWALL_PLAN.md` — the **Component Firewall** methodology + the **FROZEN gate table (§4b)** + the window classifier (§5) + Track-1 result (§10). The frozen gates are quant-signed; **do NOT re-tune them.**
2. `TRADE_ANATOMY_ANALYSIS.md` — the trade-level death analysis + **portable design principles** (§6, §7c) for the next candidate. Read §7c (ablation) before designing any breakout-family entry.
3. `TRACK2_BREADTH_GATE_PLAN.md` §8 — why the breakout family is DEPRECATED (3 strikes; no selector fixes it).
4. `REGIME_CONDITIONAL_BATTLE_PLAN.md` — the broader risk-on/risk-off program this all sits inside.

## Where the program stands (2026-06-03)
The **regime-conditional portfolio** thesis: build **regime specialists** (each real in its native regime, flat otherwise) + a portfolio/transition layer that deploys the right one. Status: **0 of N components passing the Component Firewall.** Multiple strikes across two premise families — the Minervini **breakout** family (Track-1/2/2b all REJECTED — participate-and-lose in narrow-leadership chop) and the earlier **leveraged-long-ETF** attempts (both rejected, premise capped). **Both died in the same regime — narrow-leadership / low-breadth trending tape — which is exactly the regime the framework most needs to survive.**

**Merged to main (PR #85, reusable building blocks — the strategy is dead, the code is not):** the Minervini trend-template conditions, the **VCP-base conditions** `NarrowingRange`/`VolumeDryUp` (G14-verified), and the **market-relative-strength percentile pipeline** (ADR 0009, Midgaard recompute + Udgaard ingest). The **breakout edge is SHELVED** as a known-real risk-on building block (Block B earned it: 0 neg windows, 20.8% in-mkt CAGR) — deploy it only once a validated regime-transition layer exists (= Track #2).

## Shared assets + discipline that carry forward (apply to BOTH tracks)
- **The Component Firewall is built** — frozen gate table (`COMPONENT_FIREWALL_PLAN.md §4b`), window classifier (§5), the anti-snooping calibration rule (§7: every data-gated threshold derived from an **external anchor** and frozen **before** the runs are read). The WF-firing pattern: per-layer walk-forward (Block A 2000-14, B 2014-21.5 COVID-inclusive, 25y, C 2021-25; 36/12/12) + single backtests for per-trade data. Gen/eval/fire script *patterns* are in `/tmp` (regenerable; see `diagnostics/`).
- **Failure-mode library (check every candidate against these):** participate-and-lose in narrow leadership ([[mean-reversion-pullback-known-weakness]], now confirmed for breakouts too), ARS ([[aliased-regime-sensitivity]]), lottery-vs-signature ([[lottery-screen-diagnostic]]), thinning-not-selecting (a scalar/market gate can't solve a per-name selection problem — `TRACK2_BREADTH_GATE_PLAN.md §8`), the capital-aware ablation metric-confound ([[ablation-metric-confound-capital-aware]]), IS-fitting/data-snooping discipline.
- **The hard discipline (non-negotiable):** never fit a regime/entry condition to the specific good/bad years you observed (that's IS-fitting on one realization). External-anchor calibration, pre-register thresholds, validate OOS, re-screen every carried hypothesis from a fresh `/condition-screen`. A REJECTED config is dead — never iterate it.
- **Portable design principles from the breakout ablation** (`TRADE_ANATOMY_ANALYSIS.md §7c`, all hypotheses to re-screen, never inherited): a breakout-event trigger + a SPY-regime crash filter are load-bearing trade-quality; **ADX-as-a-gate is contraindicated for tail-edge premises** (it cuts the tail that IS the edge); a VCP-style base should be the *primary* thinner. Monsters enter far-from-quiet (a ranker input).
- **The funnel for any new component:** `/condition-screen` (design-time) → `/strategy-screen` (10y 2005-15 triage) → **Component Firewall** (frozen gates; C-PARTICIPATE **re-derived per archetype**) → `/monte-carlo`. Track via `/strategy-exploration` dossier.

---

## TRACK #1 — Defensive / crisis-specialist component  ← quant's recommended FIRST move

**Why first (quant 2026-06-03):** every strike so far was a long *participant*; there is **no defender**. The framework structurally **cannot portfolio-validate any component** (incl. the shelved breakout) until **≥2 components exist** — the `C-CASHOVERLAP` + portfolio-blend gates are deferred until then. A defender also attacks a *different* regime than the two we keep dying in. Higher-leverage than another participant.

**What it is:** a component whose **native edge IS the regime the participants die in** — narrow-leadership chop / crisis — i.e. it is *supposed* to be flat-or-active precisely when breadth collapses. Candidate premise classes (quant, rough priority):
1. **Cross-sectional leadership-concentration / RS-dispersion** — long the *narrowing* leadership (the few names actually working when breadth collapses), flat otherwise. Uses narrow tape as the alpha source instead of dying in it. Adjacent to the RS-gate (ADR 0009) + the single-name S&P pivot in the battle plan.
2. **A crisis/defensive specialist** (the explicit cash-overlap partner) — different archetype; **C-PARTICIPATE must be RE-DERIVED, NOT inherit the breakout's 40%** (a crisis specialist legitimately participates < 40%; the breakout's floor was archetype-specific — see `COMPONENT_FIREWALL_PLAN.md §4b` C-PARTICIPATE note).
3. **Mean-reversion that is SHORT-the-extension / volatility-harvesting in chop** — explicitly the *non-deprecated* cousin of the dead long-the-pullback class. NOT long-the-pullback ([[mean-reversion-pullback-known-weakness]] forbids that).

**Immediate next step:** pick a premise class with the quant, then **scope it as a doc first** (premise, the entry/exit/ranker components, which existing conditions apply, the archetype-specific C-PARTICIPATE re-derivation) → quant review → `/condition-screen` the novel conditions → assemble → `/strategy-screen` → Component Firewall. Do NOT reuse the breakout's gate thresholds blindly.

**Caveat:** a defender's success signature is the *inverse* of a participant's — judge it on contribution in 2008/2011/2015-16/2021-23 (where the participants bled) and on cash-complementarity, not standalone CAGR. The exact component-acceptance criterion vs the 25% portfolio target is a portfolio-construction question to settle with the quant.

---

## TRACK #2 — Portfolio-layer market-regime read-out  (GitHub issue #83)

**Why:** the thing every entry-gate experiment (Track-1/2/2b) proved **cannot live inside a strategy** — deciding *which specialist runs when* belongs at the portfolio/transition layer. It's what deploys the shelved breakout edge and any defender. Fully scoped in **issue #83** (read it first).

**What it is (single-user app):** a **read-out the operator consults** (not an auto-switcher) — a small set of regime labels + the underlying signals, so the user picks which specialist(s) to deploy.

**Candidate inputs (all computed; market breadth verified full 2000-2025):** SPY-vs-200EMA (`spyTrendUp`), market breadth level (`breadthEma10Above50`) + momentum (`marketUptrend`), **breadth-vs-index divergence** (the narrow-leadership tell — likely a new derived signal), sector-breadth dispersion (`sectorBreadthGreaterThanMarket`). Mechanics in the breadth conditions; data via `GET /api/breadth/market-daily` (field `quoteDate`/`breadthPercent`/`ema10`) + `/api/breadth/sector-daily/{XLK..}` (field `bullPercentage`).

**⚠️ The hard discipline (front and center in #83):** the classifier must be **market-defined, pre-registered, and OOS-validated — NEVER fit to a strategy's good/bad years** (labelling 2011/21-23 "bad" *because* the breakout lost there = IS-fitting/ARS). Regime is a property of the *market*, derived independently of any strategy P&L. Breadth trust floor = 2000-01-01 ([[breadth-trust-floor-2000]]).

**Dual use:** the same principled labeller should serve (1) the live read-out and (2) **backtest regime-attribution** — it would make the Component Firewall's §5 window classifier principled + shared across components. Build once, use both.

**Immediate next step:** it's a build (Kotlin regime-classifier service reading breadth/SPY + a Vue card on `mission-control`/`breadth`, per `asgaard/claude.md`). Start by drafting the **taxonomy + signal thresholds as text for quant review** (pre-registration), then implement TDD. No backtest needed to start; OOS-validate the labeller on held-out history before trusting it.

---

## Sequencing
Complementary (a defender is a *component*; the read-out is the *deployment layer*). **Quant lean: defender (#1) first** — the framework needs the cash-overlap partner before *any* component (incl. the shelved breakout) can be portfolio-validated. #2 can proceed in parallel as a build (it's not gated on a backtest). The deferred LLM-Wiki knowledge base is **issue #84** (not urgent; don't lock in the old `.claude/memory/` symlink approach — PR #71 closed for that reason).

## Operational / infra
- Backtests/screens run against **PRD: `http://localhost:9080/udgaard`, header `X-API-Key: changeme`** (memory `feedback_prd_backtest`); helper `.claude/scripts/udgaard-post.sh` (env `API_KEY`). udgaard **1.0.81** deployed. **One run at a time** (engine OOMs concurrent). Always show the POST + get "go" first.
- **udgaard heap 18g + `mem_limit` 20g** (committed). **Stop dev containers** (`trading-postgres`, `midgaard-app`, etc.) before heavy PRD runs — they compete for RAM. Only the PRD stack (`trading-prd-*`) should run during firewall work.
- **Full-universe `/condition-screen` OOMs** a high-firing gate → use the 300-sym sanity universe (faithful for precomputed indicators). **Full-universe `/strategy-screen` / walk-forward is fine** (~7 GiB). **Dropping a strong selectivity filter from a full-universe single backtest can OOM** (candidate-set explosion) — that's a "primary thinner" signal, not a bug ([[ablation-metric-confound-capital-aware]]).
- The build needs the **dev Postgres on `localhost:5432`** (flyway/jooq) — start it for local gradle, stop it before heavy PRD runs. Tests: run with the dev pg up; `/pre-commit` is the gate (CI backend jobs fail by design — no Docker, memory `feedback_ci_no_docker`).
- **/tmp is wiped on reboot** — regenerate request JSONs / runners from the gen-script patterns (`diagnostics/` has the persisted analysis scripts).

## Branch / state
PR #85 merged to `main` (conditions + RS pipeline + research record). This handover is on branch `docs/regime-framework-handover` (commit + PR when the user says so). Open PRs: none. Issues: **#83** (regime read-out = Track #2), **#84** (LLM Wiki knowledge base, deferred). Closed this session: #71 (agents already extracted; memory → #84), #35 (target doc deleted).

## Reference
- Docs: `COMPONENT_FIREWALL_PLAN.md`, `TRADE_ANATOMY_ANALYSIS.md`, `TRACK2_BREADTH_GATE_PLAN.md`, `REGIME_CONDITIONAL_BATTLE_PLAN.md`, `MINERVINI_VCP_STRATEGY_DEVELOPMENT.md`
- ADRs: 0009 (RS percentile), 0007 (condition-screen leakage), 0006 (per-month OOS buckets), 0005 (WF aggregation)
- Skills: `/condition-screen`, `/strategy-screen`, `/validate-candidate` (adapt to the Component gates — do NOT use its v4 run-pipeline.sh as-is for a regime component), `/create-condition`, `/monte-carlo`, `/strategy-exploration`
- Memories: `project_regime_conditional_portfolio_framework`, `project_minervini_vcp_breakout_rejected`, `project_phase1_leveraged_long_etf_attempts`, the failure-mode feedback memories above, `feedback_get_expert_review_before_persisting`, `feedback_min_cagr_tradable`
- Issues: #83 (Track #2), #84 (LLM Wiki)
