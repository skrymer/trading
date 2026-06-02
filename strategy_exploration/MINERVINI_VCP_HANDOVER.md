# Handover — Minervini VCP strategy (regime component)

Created 2026-06-03. Paste as the first message of a clean session, or read from disk.
**Obey every CLAUDE.md instruction + saved memories (they override defaults).**
**Never fire a backtest/screen without showing the POST and getting an explicit "go".**

## Read this first
The full, current record is **`strategy_exploration/MINERVINI_VCP_STRATEGY_DEVELOPMENT.md`** — fidelity map, every screen result, the Component Firewall methodology, the G13 two-tier scoping, and all caveats. This handover only orients you to the immediate next action; the doc is authoritative.

## One-line state
A Minervini VCP **breakout** strategy is **fully screened** (Stage-1 exits + Stage-2 rankers done). Two near-tie exit variants advance, both × the locked ranker `SectorStrengthMomentum`. It is a **regime-conditional COMPONENT** (flat in bear/chop; portfolio target 25% CAGR, NOT standalone 30%).

**UPDATE 2026-06-03:** VCP-A/VCP-B **promoted** (NarrowingRange/VolumeDryUp, G14-PASS, deployed udgaard 1.0.81); Component Firewall **fired → REJECTED.** EX-ATR20×SSM failed 6 binding gates (C1a in-mkt CAGR 9.6–20.8% ≪ 30%; 25y C2/C3/C5; C7 on A+25y). Failure = **participate-and-lose in narrow-leadership chop** (`spyTrendUp` too coarse — only stands aside in crisis, bleeds 2015–16/2021–23). EX-VCPOLD NO-GO. **NEXT = Track 2: NEW breadth-gated candidate re-screened from Stage 1, keep breakout premise + promoted conditions, replace ONLY `spyTrendUp`; NOT a patch on the rejected config.** Full result `COMPONENT_FIREWALL_PLAN.md` §10. This config is dead — do not re-touch.

## The candidate (regenerate request JSONs from this — /tmp is wiped on reboot)
- **Entry (AND):** `spyTrendUp` ∧ `movingAverageStack`(SMA 50/150/200, requirePriceAboveFast) ∧ `movingAverageRising`(SMA200, 30) ∧ `percentFrom52WeekHigh`(25) ∧ `percentFrom52WeekLow`(30) ∧ `relativeStrengthPercentile`(70) ∧ **VCP-A** (inline script, progressive contraction) ∧ **VCP-B** (inline script, volume dry-up) ∧ `priceNearDonchianHigh`(1.5) ∧ `volumeAboveAverage`(1.3, 20). *(VCP-A/VCP-B exact vetted Kotlin is in the doc §3.)*
- **Exit — two variants advancing (OR):** **EX-ATR20** = `stopLoss`(2.0 ATR) OR `priceBelowEma`(50) [best DD]; **EX-VCPOLD** = `emaCross`(10,20) OR `stopLoss`(2.5 ATR) OR `stagnation`(3%,15d) [best edge/win%, the old VcpExitStrategy].
- **Ranker:** `SectorStrengthMomentum` (LOCKED). **Sizer:** `AtrRisk`(riskPercentage 1.25, nAtr 2.0), maxPositions 10, startingCapital 100000, leverageRatio 1.0.
- **WF request shape:** `{"type":"custom","conditions":[…],"operator":"AND"|"OR"}` for entry/exit; full universe = `assetTypes:["STOCK"]` + no `stockSymbols`; `inSampleMonths 36, outOfSampleMonths 12, stepMonths 12, entryDelayDays 1, randomSeed 42, riskFreeRatePct 0.0`. Script condition = `{"type":"script","parameters":{"script":"…"}}`.
- Screen numbers (10y 2005-2015, full universe): EX-VCPOLD×SSM Sharpe 1.25 / CAGR 17.5 / DD 21.4 / win 42.9; EX-ATR20×SSM Sharpe 1.245 / CAGR 17.0 / DD 17.9 / win 33.8.

## The plan (user-approved 2026-06-02): TWO tracks
- **Track 1 — validate the current two candidates** via the Component Firewall, accepting the bounded **W4/2011** loss as a *disclosed portfolio coverage gap*.
- **Track 2 — breadth-gated variant as a NEW candidate** (re-screen from Stage 1): add broad-breadth confirmation to the market gate (`marketUptrend`/breadth-EMA/sector-breadth alongside `spyTrendUp`) — Minervini's "M" done properly. NOT a post-hoc W4 patch. Queued.

## IMMEDIATE NEXT STEP (Track 1) — DONE through pre-registration; next is FIRING
~~Promote VCP-A/VCP-B + G14~~ **DONE 2026-06-03** (see One-line state update). The Component Firewall framework is frozen + quant-signed in `COMPONENT_FIREWALL_PLAN.md`. **The actual next action: fire the EX-ATR20×SSM Component Firewall runs** (§3 run plan): 4 walk-forwards (Block A 2000-14, B 2014-21.5, 25y, C 2021-25; 36/12/12) + per-block single backtests (`/api/backtest`→`/{id}/trades`) for days-in-market (WF has no per-trade export). Then the §9 calibrate-after checks against the frozen bars (plug `f` into the C1a formula; check realized cash DDs ≤3% ceiling; ARS ±1-step on the classifier). Then re-run EX-VCPOLD×SSM through the frozen gates. Needs explicit "go" + stop dev containers first. G14 config generator at `/tmp/gen-g14-configs.py` (regenerate the candidate request JSONs from it; /tmp wiped on reboot).

## Track-1 caveats the new session MUST know
- **Do NOT use the v4 `/validate-candidate` `run-pipeline.sh` as-is** — its G1 (30% CAGR) + G6/G7 (regime-positive mandates) + G8 (30 trades/window) **false-REJECT a regime component by design.** Instead run the raw blocks (A 2000-14, B 2014-21 incl COVID, 25y, C 2021-25) and apply the **Component Firewall gates** (C1a in-market CAGR≥30%, C6-STAND-ASIDE vs C6-IN-MARKET, C2/C3/C5/C9/C11/C12/C14, C8 with N_min=5) — full table in the doc. Make it a reusable `/validate-component` skill, but **calibrate the 3 data-gated thresholds on this candidate + get quant sign-off BEFORE persisting as code** (memory `feedback_get_expert_review_before_persisting`).
- **Days-in-market** (for C1a) needs a trade-level export — not in the WF aggregate. Settle before committing C1a's 30%.
- **W4/2011 = bounded, disclosed gap** (spyTrendUp too coarse for narrow-breadth chop; DD 15.5–17.4% < 20%, only negative participating window). Accepted for Track 1; don't try to rescue it (IS-fitting) — that's what Track 2 addresses structurally.

## Operational / infra
- Backtests/screens run against **PRD: `http://localhost:9080/udgaard`, header `X-API-Key: changeme`** (memory `feedback_prd_backtest`); helper `.claude/scripts/udgaard-post.sh` (env `API_KEY`). One run at a time (OOMs on concurrent).
- **udgaard heap = 18g, `mem_limit` 20g** (committed). A 20g heap crashed the host; this is the fix. **Stop the dev containers** (`midgaard-app`, `midgaard-postgres`, `trading-dev-adminer-1`, `trading-postgres`) before heavy PRD runs — they compete for RAM.
- **Full-universe `/condition-screen` OOMs** (high-firing gate) → use the 300-sym `condition-screen/sanity-universe/sanity-universe-v1.json` (faithful for precomputed indicators). **Full-universe `/strategy-screen` / walk-forward is fine** (different memory profile, peaks ~7 GiB). Memory `project_condition_screen_full_universe_oom`.
- **/tmp is wiped on reboot** — regenerate request JSONs from the candidate spec above.

## Branch (NOT pushed — open a PR only when the user says so)
`feat/minervini-trend-template-conditions`. Latest commits: `f06aaed` (doc: full funnel + Component Firewall), `3fb03a9` (heap cap + mem_limit + doc), plus the earlier RS-gate/condition work. `.claude/memory/` is the user's personal store — leave untracked.

## Reference
- Doc: `strategy_exploration/MINERVINI_VCP_STRATEGY_DEVELOPMENT.md` (authoritative)
- ADR 0009 (RS percentile), ADR 0007 (condition-screen leakage)
- Skills: `/create-condition`, `/verify-promotion`, `/strategy-screen`, `/validate-candidate` (for gate definitions to adapt), `/monte-carlo` (final step)
- Conditions: `udgaard/.../backtesting/strategy/condition/entry/`
