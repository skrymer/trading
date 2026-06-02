# Minervini VCP Strategy Development

_Status: **COMPONENT-BUILD / PRE-ASSEMBLY** (2026-06-02). The Stage-2 trend-template GATE (5 conditions) is built and strategy-neutral; the market-relative-strength percentile indicator is live end-to-end on PRD; the RS gate condition passed `/condition-screen` with **PROCEED**. The strategy itself is **not yet assembled** — it still needs a pivot-breakout TRIGGER, a VCP base detector, and an exit. No tradability claim. Nothing has been through `/strategy-screen` yet._

## Hypothesis

Mark Minervini's SEPA / Trend-Template + VCP (Volatility Contraction Pattern) approach, adapted to a **price-only daily engine** (SEPA fundamentals are out of scope). The thesis: enter leadership names that are (a) in a confirmed Stage-2 uptrend, (b) showing high market-relative strength, (c) forming a tight VCP base (successive volatility contractions on drying volume), at the moment price (d) breaks out through the pivot on expanding volume.

This is a **trend-continuation / breakout** premise — structurally distinct from the mean-reversion-on-pullback class (VZ3, MR3) that is known to fail in narrow-leadership tape. See memory `feedback_mean_reversion_pullback_known_weakness`.

## Architecture: GATE + TRIGGER + BASE + EXIT

A real Minervini entry is **not** any single condition. The 5 trend-template conditions below are all **state filters (the GATE)** — none is a trigger. The full intended assembly:

| Layer | Role | Status | Candidate components |
|---|---|---|---|
| **Market gate** | "is the general market in a confirmed uptrend?" | exists | `spyTrendUp` / `marketUptrend` (Minervini-faithful, primary element) |
| **Stage-2 GATE** | "is this a leadership name in an uptrend?" | **BUILT** (5 conditions) | the 5 trend-template conditions below |
| **VCP base** | "is it forming a tight base w/ volume dry-up?" | **to build as inline scripts** | VCP-A progressive contraction ∧ VCP-B volume dry-up (see fidelity map §3); each `/condition-screen`'d, then promoted |
| **TRIGGER** | "did it just break the pivot?" | not built | `priceNearDonchianHigh` (pivot proxy) ∧ `volumeAboveAverage` (demand confirmation) |
| **EXIT** | stop / trend-break / let-winners-run | not built | percent stop ≈8% (script) + `priceBelowEma(50)` trend break — NOT a mean-reversion exit |

## The Stage-2 GATE — 5 trend-template conditions (BUILT, committed)

All in udgaard `.../backtesting/strategy/condition/entry/`, all **strategy-neutral** (described by mechanic, not branded "Minervini" — memory `feedback_conditions_strategy_neutral`), all `null ⇒ fail-closed`, all with `init` validation:

1. **`movingAverageStack`** — close > MA(fast) > MA(mid) > MA(slow); SMA/EMA. Default 50/150/200, requirePriceAboveFast.
2. **`movingAverageRising`** — MA today > MA N bars ago. Default SMA200 rising over 30 bars.
3. **`percentFrom52WeekHigh`** — within X% of 52-week high. Default ≤ 25%.
4. **`percentFrom52WeekLow`** — ≥ X% above 52-week low. Default ≥ 30%.
5. **`relativeStrengthPercentile`** — RS percentile ≥ minPercentile. Default 70.

### RS indicator pipeline (ADR 0009)

A stock's trailing 252-bar return ranked cross-sectionally vs the whole universe per day → 0–100 midpoint percentile. Computed in **Midgaard as ONE SQL window-function statement** (`QuoteRepository.recomputeRelativeStrengthPercentiles`: `LAG` return + `rank()`/`count()` window + single `UPDATE…FROM`, `work_mem=1GB` transaction-local). **Decoupled from ingest — manual trigger only** (`RelativeStrengthService.recomputeAllAsync`; button on midgaard `/ingestion` + `POST /api/ingestion/recompute-relative-strength`). Udgaard ingests onto `StockQuote.relativeStrengthPercentile` (null-preserving). Columns: midgaard `quotes` V18, udgaard `stock_quotes` V27.

**Operational caveat:** RS goes **NULL after any midgaard re-ingest** (fail-closed) until the operator (1) clicks Recompute Relative Strength (~5 min) AND (2) re-ingests udgaard. A strategy gated on RS admits nothing on un-ranked bars until then. By design.

## Findings log

### 2026-06-02 — `/condition-screen` on `relativeStrengthPercentile` (minPercentile=70) → **PROCEED**

Diagnostic only — a PROCEED is "worth wiring up", never validation.

**Run constraints (important):**
- Ran on the **reduced 300-symbol `sanity-universe-v1`**, NOT the full ~3,900 universe. The full-universe screen OOMs even a capped JVM: the gate's ~30% firing rate × full universe × 21y × the 3-cell ARS auto-sweep blows the heap. (Root-caused this session — see Infra notes.)
- Crucial nuance: **RS percentile is precomputed vs the full universe**, so firing-by-year (a ratio), the ARS sweep, and the SPY-regime breakdown are **faithful** on the reduced universe. Only Jaccard would have been distorted, so it was dropped (`referenceConditions: []`).
- Request: `/tmp/rs-screen-sanity.json`; response: `/tmp/condition-screen-relativeStrengthPercentile.json` (note: `/tmp` is wiped on reboot — regenerate from the sanity universe if needed).

**Results:**
- **Firing-by-year: STABLE** ~30% (26.6%–34.1%), GFC-robust (29% through 2008–09), no collapse, no early fail-closed cratering. (Load-bearing output for a gate.)
- **ARS sweep (63/70/77): CLEAN** — lift monotone-increasing with threshold, swing < 2×SE, fine grid (relativeStep 0.1). But **uninformative by construction** — a percentile coordinate moves monotonically, so a clean sweep is tautological. Real `minPercentile` robustness defers to the firewall **G13 ±1-step** gate.
- **dateCount ample** (~5,263/horizon) — nothing INCONCLUSIVE.
- **SPY-regime: a down→up lift sign-flip is present** (positive lift in down-tertile, negative in up-tertile, all horizons). Mechanically the §4 cross-sectional-ARS pattern, BUT firing is regime-flat (~30% in every tertile), lifts are near-SE, and for a **gate** this is the expected "leadership measured in isolation" / mean-reversion-of-leaders artifact — **non-disqualifying** because a separate trigger, not the gate, carries the alpha.
- **Lift sub-SE at all horizons** — expected for a 30%-firing gate (lift converges to base rate); not a red flag.

**Quant design-review guardrails applied (pre-screen, 2026-06-02):** lift is expected-uninformative for a gate; read ARS asymmetrically (clean=uninformative, sign-flip=real); a 2000–02 firing dip would be the N_min=100 fail-closed peer-floor guard not selectivity (did not bite); RS-vs-TrailingReturn-ranker distinctness is the WRONG test here — it's decided by **veto-rate at /strategy-screen** (RS is signal-correlated ρ≈0.9 with that ranker, distinct only as a *mechanism*: absolute market-wide floor vs intra-subset ordering).

**Full-universe re-attempt (2026-06-02, after raising heap to 18g + adding `mem_limit: 20g`): OOM — full-universe is INFEASIBLE on this host.** Fired RS + the OTHER4 Jaccard reference on the full ~3,900 universe. udgaard consumed the entire 18g heap (peaked 18.63g) and threw a **graceful `OutOfMemoryError: Java heap space`** (HTTP 500); `RestartCount=0`, `OOMKilled=false` — the container was never killed and the host was never at risk (the mem_limit cgroup backstop worked as designed). Since the prior 20g heap crashed the *host*, the screen wants more heap than this machine can safely provide. **Conclusion: full-universe `/condition-screen` of a high-firing gate is permanently infeasible here; use the reduced sanity universe.** This costs only the faithful Jaccard #6 — firing/ARS/regime were already faithful at 300 symbols (RS precomputed), and Jaccard #6 is "descriptive, not reject-triggering" (deferred to /strategy-screen veto-rate). The PROCEED verdict is unaffected.

**Still to confirm downstream (this screen could NOT test):**
1. **Re-confirm on the full ~3,900 universe** — this was 300 symbols. The full-universe confirmation happens naturally at `/strategy-screen` (backtest engine has a different memory profile than the screen's per-signal bookkeeping).
2. **Veto-rate vs the TrailingReturn ranker** (mechanism-distinctness) at `/strategy-screen`.
3. **`minPercentile` G13 ±1-step robustness** at the firewall.

### 2026-06-02 — `/condition-screen` VCP-A + VCP-B (inline scripts) → both **ARS-CLEAN, proceed to assembly**

Authored via `/create-condition` (P3/P4 fixes applied), screened individually on the reduced 300-symbol universe, 2000→2021, **ARS-read only** (base-context filters — standalone lift is the wrong rejection test per the quant build plan). Requests `/tmp/screen-vcp-{a,b}.json`, responses `/tmp/condition-screen-vcp-{a,b}.json`.

- **VCP-A** (progressive contraction, range-step proxy): firing 18.4%, stable 15.7–21.3%/yr, no dead/runaway year. `stepWindow` 7/10/13 — **no ARS** (no sign-flip, swing ≪ 2×SE at flat firing; the `pullback2of3` fingerprint is absent). 20d steepness ratio 0.94 (under the 1.0 threshold). The range-step encoding survives ARS scrutiny.
- **VCP-B** (volume dry-up): firing 14.0%, stable 11.3–19.5%/yr, crisis years in-band. All 3 tunables ARS-clean. `dryupRatio` flips at 5d/10d but swings < 2×SE **and** firing quadruples 0.6→0.8 (can't form ARS — ordinary threshold sensitivity). **Carry-forward: `dryupRatio=0.7` is the lift-sensitive knob → re-confirm under G13 ±1-step at validation.**
- Both regime down→up lift sign-flips are the **intended** base-context behavior (gated out by `spyTrendUp`), not defects.
- **Standing caveats:** reduced-universe is anti-conservative for the ARS 2×SE test (re-confirm at full-universe/firewall); the real lift test is the in-strategy ablation at `/strategy-screen`.

### 2026-06-02 — `gate ∧ VCP-base` firing pre-check (no trigger) → not over-constrained; trigger is the watch-point

Combined `spyTrendUp ∧ MA-stack ∧ MA-rising ∧ %52wH ∧ %52wL ∧ RS≥70 ∧ VCP-A ∧ VCP-B`, 300-symbol universe, 2000→2021. (MA-stack/MA-rising expressed as inline scripts reading `quote.sma50/150/200` — the registered MA conditions can't be auto-swept: the screen tried SMA period 49, unsupported. See the two-tier G13 note + screen-limitation below.) Request `/tmp/screen-gate-vcp-block.json`, response `/tmp/condition-screen-gate-vcp-block.json`.

- **Firing 0.55% overall, 5,806 signals/21y.** Up/normal years (2003–07, 2009–20): healthy 225–484 candidates/yr. Bear years **2001 (1), 2002 (5), 2008 (17)**: near-zero — `spyTrendUp` correctly sits in cash (the GFC-defensive property), NOT over-constraint.
- **VCP base did not over-collapse firing** — ample candidates in up-years for `maxPositions`.
- **Watch-point:** the trigger (pivot-breakout, a rare event) is still to be added and is the firing cut most likely to starve per-window trade counts. If `/strategy-screen` trade counts come in thin, the trigger is the likely cause → remedy is escalating the universe (300→1000+), not loosening VCP.

### 2026-06-02 — Stage-1 EXIT sweep (`/strategy-screen`, full universe) → EX-ATR20 primary, EX-ATR25 secondary advance

300-symbol universe **starved trades** (EX-ATR20: 38 OOS trades, 0–11/window — below the ≥20–30 floor; the trigger is a rare per-name event). **Escalated to the full ~3,900 universe** (walk-forward memory profile is fine — peaked ~7 GiB/20 GiB, unlike the condition-screen OOM). Full-universe EX-ATR20: 209 trades, W2–W7 = 27–47/window. All 6 exits fired full-universe, 2005–2015, 36/12/12, `ranker=Random(seed 42)` (neutral floor), AtrRisk(1.25%,2.0), maxPos 10. Results `/tmp/screen-minervini-full-EX-*-result.json`.

| Variant | Trades | Edge | Sharpe | CAGR | maxDD | pos/7 | gates |
|---|---|---|---|---|---|---|---|
| **EX-VCPOLD** (emaCross10/20+2.5ATR+stag3%/15d) | 262 | 4.08 | **1.12** | **15.4** | 21.8 | 5/7 | **PASS 4/4** — NEW FRONT-RUNNER (best Sharpe/CAGR/win%; WFE 1.94 ⚠) |
| **EX-ATR20** (2.0ATR+EMA50) | 209 | 3.12 | 1.10 | 13.9 | **18.2** | 5/7 | **PASS 4/4** (best DD, budget-consistent) |
| **EX-ATR25** (2.5ATR+EMA50) | 187 | **4.42** | 0.76 | 8.8 | 26.1 | 5/7 | PASS 4/4 (dropped — VCPOLD dominates) |
| CHANDELIER | 224 | 2.51 | 1.06 | 13.9 | 24.6 | 4/7 | FAIL G3 (Sharpe propped by 1-trade GFC window) |
| PIVOT | 189 | 2.88 | 0.65 | 7.6 | 24.3 | 5/7 | FAIL G2 |
| VOLFAIL | 260 | 2.15 | 0.74 | 7.5 | 19.9 | 5/7 | PASS but dilutive (volFail cuts winners early) |
| PCT5 *(flagged)* | 213 | 2.61 | 0.84 | 10.3 | 23.2 | 5/7 | comparison-only; ruler-inconsistent, NOT shipped |

- **"Cut losses short" won:** the tightest, budget-consistent **2.0 ATR** stop (EX-ATR20) led on Sharpe/Calmar/DD. The looser/chandelier variants did NOT lift CAGR and worsened DD. The flagged 5% percent-stop was not better than the ATR stops → validates the ATR-ruler decision.
- **CAGR 7.5–13.9% is a SELECTION gap, not an edge gap** (quant): per-trade payoff 4.06, expectancy +3.12%/trade (ATR20) under a *deliberately neutral Random ranker*; WFE 0.89 (not IS-overfit). A real ranker has a credible path to ≥30% → **worth the Stage-2 ranker sweep.**
- **⚠️ W4 (2011 OOS) — shared, ranker-INVARIANT structural hole.** Every variant negative in W4; trade counts stay full but win rate craters to 14–25% in the low-breadth 2011 chop (breadth 38, uptrend 48.8%). The long-breakout cousin of [[feedback_mean_reversion_pullback_known_weakness]] — `spyTrendUp` too coarse to keep the book out of false breakouts. A momentum ranker will **amplify** W4. **Hard rule: do NOT bolt on a regime filter post-hoc to rescue W4 (IS-fitting to W4 / ARS).** Under the regime-component framing (item 6 of the funnel), a weak/negative 2011 low-breadth window is **acceptable as a regime handoff** — that's exactly the tape where *other* portfolio components should carry, and this breakout component correctly has no edge. Disclose it as a coverage gap for the portfolio layer to fill; don't distort the component to paper over it.

**Stage 2:** sweep rankers on **EX-VCPOLD (primary)** + **EX-ATR20 (secondary, best DD)** — `TrailingReturn`, `RollingSectorStrength`, `SectorStrengthMomentum`, `Composite` baseline. (`SectorEdgeWithTightness` deferred — second-pass, see §7.) Drop EX-ATR25/CHANDELIER/PIVOT/VOLFAIL/PCT5. Carry the **WFE 1.94** flag on VCPOLD into validation (cross-block edge-decay check).

### 2026-06-02 — Stage-2a ranker sweep (EX-VCPOLD × 4 rankers, full universe) → SectorStrengthMomentum wins

Exit fixed = EX-VCPOLD; ranker varies; everything else as Stage 1 (full universe, seed 42, AtrRisk 1.25/2.0, maxPos 10, 2005–2015). Results `/tmp/screen-minervini-s2-VCPOLD-*-result.json`.

| Ranker | Trades | Edge | Sharpe | CAGR | maxDD | win% |
|---|---|---|---|---|---|---|
| **SectorStrengthMomentum** | 245 | **4.87** | **1.25** | **17.5** | 21.4 | **42.9** |
| Composite | 249 | 3.30 | 1.14 | 16.0 | **21.0** | 40.2 |
| RollingSectorStrength | 243 | 3.72 | 1.12 | 15.6 | 22.9 | 39.5 |
| *Random (Stage-1 floor)* | 262 | 4.08 | 1.12 | 15.4 | 21.8 | 39.3 |
| **TrailingReturn** | 249 | 4.10 | **0.87** | **11.1** | 22.4 | 41.4 |

- **Winner: `SectorStrengthMomentum`** (best Sharpe/CAGR/edge/win%). Sector-strength ranking (leading groups gaining strength) is the value-add — Minervini-faithful ("leaders in leading groups").
- **`TrailingReturn` LOST to Random** (CAGR 11.1 vs 15.4) — empirical confirmation of the ADR-0009 veto-rate concern: the momentum ranker is ρ≈0.9 redundant with the RS gate, over-concentrates into the same extended names, adds nothing/hurts. **Do NOT pair TrailingReturn with the RS gate.**
- W4/2011 negative (−3.0) across ALL rankers → exit- AND ranker-invariant; the regime handoff (portfolio layer covers it), not a defect to rescue.
- **WFE 1.46–1.94 across all** → OOS edge > IS consistently — yellow flag for the firewall cross-block edge-decay check. Carry forward.
- CAGR 17.5% as a regime-component (flat in bear/chop, Sharpe 1.25) is a credible piece toward the 25% *portfolio* target (item 6); component-contribution is a portfolio-construction question for the quant.

**Stage 2b (done):** EX-ATR20 vs EX-VCPOLD, both × SectorStrengthMomentum — **near-tie:**

| Combo | Trades | Edge | Sharpe | CAGR | maxDD | Calmar | win% | WFE |
|---|---|---|---|---|---|---|---|---|
| EX-VCPOLD×SSM | 245 | 4.87 | 1.250 | 17.5 | 21.4 | 0.82 | 42.9 | 1.49 |
| EX-ATR20×SSM | 195 | 4.22 | 1.245 | 17.0 | **17.9** | **0.95** | 33.8 | 1.59 |

Identical Sharpe/CAGR. VCPOLD: higher edge/win%/sample. ATR20: shallower DD, better Calmar, simpler/budget-consistent. **Ranker LOCKED = `SectorStrengthMomentum`.** **Decision (user 2026-06-02): advance BOTH exits to `/validate-candidate`** — the firewall arbitrates on cross-block stability (esp. the WFE~1.5 OOS>IS flag + the W4/2011 regime decay).

**Candidate configs for the firewall:** `/tmp/screen-minervini-s2-VCPOLD-SectorStrengthMomentum.json` and `/tmp/screen-minervini-s2b-ATR20-SectorStrengthMomentum.json`. **Reminder:** the entry uses inline-`script` VCP-A/VCP-B → even a TRADABLE firewall verdict is **void until promotion via `/create-condition` + `/verify-promotion` (G14)** (memory `feedback_script_conditions_must_be_promoted`). Tradability bar is the relaxed regime-component target (item 6), not standalone 30%.

### 2026-06-02 — Component Firewall methodology (quant) + W4/2011 finding → validation BLOCKED pending KEEP/REDESIGN call

**The standalone v4 firewall mis-fits a regime component** (G1 30% CAGR, G6/G7 regime-positive mandates, G8 30-trades/window all fail by design for a cash-in-crisis strategy). Quant drafted a **Component Firewall** (validate *alpha* in-market and *discipline* in-cash separately — never credit cash, never penalize it):

| Component gate | vs v4 | Population |
|---|---|---|
| **C1a** in-market (active-period) CAGR ≥ 30% | replaces G1 | in-market only — the real alpha bar |
| **C1b** blended CAGR ≥ 12% (anti-lottery) · **C1c** Sharpe≥0.8 ∧ Calmar≥0.5 | new / =G9 | whole/blended |
| **C6-STAND-ASIDE** crisis/cash windows: DD ≤ **3%**, in-market days ≤15% | replaces G6 (cash side) | cash windows (2008, COVID-crash, <N_min trades); excluded from edge gates |
| **C6-IN-MARKET** participating windows: edge > 0 (v4-strict) | =G6 (in-market side) | in-market windows |
| **C7** ≤1 negative participating window, passing the W4 acceptance rule | replaces G7 | in-market |
| **C2/C3** DD≤25% / worst-window≤20% · **C5** CoV≤1.5 (in-market only) · **C11** edge_B≥0.5·edge_A (in-market) · **C12** ≥100/block · **C14** scripts-promoted | = v4 | mixed |
| **C8** in-market windows ≥30 trades; cash exempt; N_min=5 | replaces G8 | per window |
| **Portfolio-blend G6** (book survives 2008+2020) | new | **DEFERRED to ≥2 components** |

Keep calendar blocks (coverage guarantee) but **regime-classify windows within them**; emit a regime-attribution table. Interim "standalone-as-component" KEEP bar = C1a ∧ C6-STAND-ASIDE ∧ C2/C3/C5/C9/C12/C14 ∧ (≤1 neg participating window passing W4 rule). True portfolio-contribution test deferred until ≥2 components exist. Make it a **reusable `/validate-component` skill** — but calibrate the 3 data-gated thresholds on this candidate + quant sign-off BEFORE persisting as code (per `feedback_get_expert_review_before_persisting`).

**W4/2011 finding (the deciding data, pulled from screen results):** the component **participated heavily** in 2011 (trades spread Mar–Dec, 29–34 of them) with `spyTrendUp` true, and got **chopped** (win rate 17–24% vs ~40%). This is "SPY-held-true-but-lost" — `spyTrendUp` is too coarse to detect 2011's narrow-breadth/mega-cap-masking chop. **Loss is bounded** (W4 maxDD 15.5–17.4% < 20%; only negative participating window). Per quant: this is an **alpha/regime-gate-coarseness flag**, not a clean handoff → the principled fix is a **breadth-confirmed market gate** (Minervini's "M" = *broad* market health; the battle plan explicitly warns against SPY-price-only gates). **Tension:** adding breadth must be a *from-scratch redesign re-screened as a new candidate*, NOT a post-hoc patch to rescue W4 (that's IS-fitting/ARS). **DECISION (user 2026-06-02): two tracks.**
- **Track 1 — validate current candidates** (EX-ATR20×SSM, EX-VCPOLD×SSM) through the Component Firewall, accepting the bounded W4/2011 loss as a *disclosed portfolio coverage gap* (other components own 2011-style chop). It's the first GFC-defensive *selector* component, bounded-acceptable.
- **Track 2 — breadth-gated variant as a NEW candidate** (re-screened from Stage 1, NOT a post-hoc W4 patch): add broad-breadth confirmation to the market gate (`marketUptrend` / breadth-EMA / sector-breadth alongside `spyTrendUp`) — Minervini's "M" = *broad* market health, the battle-plan-endorsed fix for SPY mega-cap masking. Queued.

N_min=5 confirmed; W4 bounded-accept confirmed; **days-in-market (C1a feasibility) still needs a trade-level export.**

**Track-1 prerequisites before a real TRADABLE verdict:** (a) no `/validate-component` skill exists yet → run the raw blocks (A 2000-14, B 2014-21, 25y, C) and apply the component gates above (manually / via a component-analyst), don't use the v4 `run-pipeline.sh` as-is (it applies G1/G6 → false REJECT); (b) **C14 — promote the inline VCP-A/VCP-B scripts** via `/create-condition` + `/verify-promotion` (blocks TRADABLE); (c) measure days-in-market for C1a; (d) finalize + quant-sign the component-gate thresholds before persisting `/validate-component`.

### G13 parameter scoping — two-tier framework (quant, 2026-06-02; pending skill-wording review)

G13/ARS has two purposes — **anti-snooping** (did *we* pick the value after seeing OOS?) and **robustness** (is the edge on a knife-edge at this coordinate?). External provenance kills the first, NOT the second. So parameters split two ways:

- **Tier 1 — full binding G13/ARS gate (can REJECT):** parameters *we invented* — VCP-A `stepWindow`; VCP-B `dryupWindow`/`baseWindow`/`dryupRatio`; trigger Donchian distance (1.5%) + volume multiplier (1.3×/20d); exit stop (~8%). Full ±1-step / ±10% sweep.
- **Tier 2 — one-time confirmatory robustness check, NO-RETUNE (cliff ⇒ demote):** Minervini-spec constants — SMA 50/150/200, RS≥70, ≤25% below high, ≥30% above low. Evaluate neighbors once at validation; **may not re-pick a passing value** (that re-introduces snooping — keep the spec value regardless); a cliff/sign-flip in a binding window does NOT let you tune — it **demotes the verdict** (TRADABLE→PROVISIONAL/worse) and flags the premise as period-fragile.
  - 25%/30% are cheaply continuously-sweepable → run the Tier-2 check on them (exempting a free check is pure downside).
  - RS≥70 is **NOT retired** by the clean 63/70/77 condition-screen sweep (that tested the isolated condition's forward-return signal, not the assembled cross-block edge) → Tier-2 at validation.
  - SMA periods: only {50,150,200} precomputed (nearest step 150, relativeStep≈2.0 — not an ARS neighborhood). Tier-2 via whatever neighbors can be computed cheaply (e.g. 40/60); else **document as untestable-by-construction and flag as a known unhedged assumption in the verdict** — never silent full exemption.
- **Where it lives:** the *two-tier framework* belongs in the G13/validate-candidate spec (general rule keyed on provenance); each candidate's *parameter classification* goes in its dossier with the provenance citation. **Not yet persisted into the skill** — the exact Tier-2 "confirmatory / no-retune / cliff⇒demote" clause wording needs quant review first (it's where the snooping guard actually lives), per `feedback_get_expert_review_before_persisting`.

### Known screen limitation (surfaced 2026-06-02)
`/condition-screen` auto-sweeps registered numeric tunables assuming a continuous ±10% / ±1-step grid. For **MA-period conditions** (`movingAverageStack`, `movingAverageRising`) this generates **unsupported SMA periods** (e.g. 49) and the whole request 400s (`SMA does not provide period 49`). Workaround for firing measurement: express MA conditions as inline scripts reading `quote.sma50/150/200` (scripts aren't auto-swept). Candidate follow-up issue: the screen should restrict period sweeps to the precomputed set (or skip un-sweepable discrete params) instead of 400-ing.

## Minervini fidelity map — faithful mappings & documented sidesteps

Goal: follow Minervini's published method (SEPA / Trend Template / VCP, *Trade Like a Stock Market Wizard*) as closely as a **price-only daily engine** allows. Each pillar below is marked **FAITHFUL** or **SIDESTEP** with rationale. Sidesteps are deviations forced by the engine/data, not choices of convenience.

### 1. Trend Template (Stage-2 filter) — **FAITHFUL** (all 8 criteria covered)

| Minervini criterion | Engine condition |
|---|---|
| 1. Price > 150-MA and > 200-MA | `movingAverageStack` |
| 2. 150-MA > 200-MA | `movingAverageStack` |
| 3. 200-MA trending up ≥ 1 month | `movingAverageRising(SMA200, 30 bars)` |
| 4. 50-MA > 150-MA > 200-MA | `movingAverageStack` |
| 5. Price > 50-MA | `movingAverageStack` (requirePriceAboveFast) |
| 6. Price ≥ 30% above 52-week low | `percentFrom52WeekLow(30)` |
| 7. Price within 25% of 52-week high | `percentFrom52WeekHigh(25)` |
| 8. RS rank ≥ 70 | `relativeStrengthPercentile(70)` |

MA type = **SMA** (Minervini specifies simple MAs) — faithful.

- **SIDESTEP 1a (RS metric).** Our RS = single trailing **252-bar total-return** cross-sectional **midpoint percentile**. Minervini/IBD use the **quarter-weighted blend (0.4/0.2/0.2/0.2) on a 1–99 scale**. Per ADR 0009 the IBD blend is "the more faithful Minervini construction but a *different indicator with its own parameter surface* — deferred to a v2 candidate." We also do **not** require the RS line to be *trending up* (Minervini's stated preference). Impact: our gate is a slightly coarser leadership filter than IBD RS Rating ≥ 70.

### 2. SEPA fundamentals — **SIDESTEP (entire pillar out of scope)**

Minervini's SEPA core is **fundamental**: earnings & sales acceleration, margin expansion, "code 33", institutional sponsorship, float, group leadership by fundamentals. The engine is **price/volume only** — none of this is available. **This is the single largest deviation: we implement the *technical half* of Minervini's method only.** A name passing our gate is technically-qualified but fundamentally-unvetted.

### 3. VCP base — **BUILD FAITHFULLY AS INLINE SCRIPTS** (decision 2026-06-02)

True VCP = **2–6 successive contractions, each tighter than the last** (e.g. 25% → 12% → 6%), with **volume drying up progressively**, ending in a tight final contraction at the pivot, over a 3–65 week base. The existing single-window `volatilityContracted` tool captures only final-contraction tightness — **it is explicitly NOT a constraint.** Decision: build the real VCP as **inline `script` conditions**, then promote to first-class conditions later via `/create-condition` + `/verify-promotion` (G14). Faithful decomposition into independently-screenable scripts:

- **VCP-A — progressive volatility contraction.** Captures the defining "stepwise tightening" (T-count). Two candidate mechanics, to be decided/screened:
  - *Range-step proxy (lower ARS risk):* normalized high-low range over consecutive sub-windows is monotonically decreasing, e.g. `range(0–10d) < range(10–20d) < range(20–30d)` (range normalized by price or ATR).
  - *Swing-based (higher fidelity, higher ARS risk):* detect pivot highs/lows, measure each pullback depth, require ≥2–3 successively shallower pullbacks. Closest to the literal VCP but parameter-heavy — this is the `pullback2of3` ARS hazard class.
- **VCP-B — volume dry-up.** `avg(volume, last 5–10d) < k · avg(volume, base 30–50d)`, k ≈ 0.7 (Minervini: volume diminishes, often to multi-week lows, before the breakout).

VCP base = **VCP-A ∧ VCP-B**. **ARS discipline:** every lookback/step tunable in A and B is declared as a `scriptSweeps` entry and run through `/condition-screen` (ARS sweep) **before** the script is wired into the strategy — the screen exists precisely to catch the `pullback2of3` failure mode at design time (memories `feedback_aliased_regime_sensitivity`, `feedback_parameter_fragility_must_be_verified`). Reduced-universe screening is the norm here (full universe is infeasible on this host — see Findings log).

**Vetted scripts (2026-06-02, via `/create-condition`).** Both fixed for P3 (no `indexOfFirst` — use public O(log N) `quotesInRange`) and P4 (no boxing — `sumOf`/`maxOf`/`minOf`, no `.map{}.average()`); lookahead-safe (`quotesInRange(…, quote.date)` is inclusive-today/exclusive-future); fail-closed on insufficient history; current bar **included** in all windows (S1). VCP-A chose the **range-step proxy** (i).

```kotlin
// VCP-B — volume dry-up
val win = stock.quotesInRange(quote.date.minusDays(400), quote.date)
val n = win.size
if (n < {{baseWindow}}) { false } else {
  val recent = win.subList(n - {{dryupWindow}}, n)
  val base = win.subList(n - {{baseWindow}}, n)
  val recentAvg = recent.sumOf { it.volume }.toDouble() / recent.size
  val baseAvg = base.sumOf { it.volume }.toDouble() / base.size
  baseAvg > 0.0 && recentAvg < {{dryupRatio}} * baseAvg
}   // scriptSweeps: dryupWindow(10,3), baseWindow(50,10), dryupRatio(0.7,0.1)

// VCP-A — progressive contraction (range-step proxy)
val win = stock.quotesInRange(quote.date.minusDays(400), quote.date)
val n = win.size
val w = {{stepWindow}}
if (n < 3 * w) { false } else {
  val normRange = { from: Int, to: Int ->
    val seg = win.subList(from, to)
    val mc = seg.sumOf { it.closePrice } / seg.size
    if (mc <= 0.0) Double.MAX_VALUE else (seg.maxOf { it.high } - seg.minOf { it.low }) / mc
  }
  val r1 = normRange(n - w, n); val r2 = normRange(n - 2 * w, n - w); val r3 = normRange(n - 3 * w, n - 2 * w)
  r1 < r2 && r2 < r3
}   // scriptSweeps: stepWindow(10,3); window-count fixed at 3 (a discrete count tunable would be the pullback2of3 hazard)
```

### 4. Pivot-breakout trigger — **FAITHFUL-ish**

Minervini buys as price clears the **pivot** (high of the final contraction / "line of least resistance") on a **volume surge**. Map: `priceNearDonchianHigh` (price at/through the recent high = clearing the pivot) **+** `volumeAboveAverage(≈1.3–2×)` (the demand confirmation).
- **SIDESTEP 4a (pivot definition).** The pivot is approximated by the **Donchian-N high**, not the exact high of the detected final contraction. Reasonable when the base sits inside the Donchian window; imprecise otherwise.

### 5. Market timing — **FAITHFUL** (add a market-uptrend gate)

Minervini only buys breakouts in a **confirmed general-market uptrend** and raises cash in corrections ("don't fight the tape"). Map: add `spyTrendUp` (or `marketUptrend`) to the entry stack. **Note:** this is a *primary* element of Minervini's method, so including it from the start is faithful — the memory caution against "adding a regime filter to rescue a failing strategy" (`feedback_mean_reversion_pullback_known_weakness`) does **not** apply, because this isn't a post-hoc rescue of an OOS miss.
- **SIDESTEP 5a.** We do not model his *discretionary* exposure scaling / progressive-exposure model — just a binary market-uptrend gate.

### 6. Risk management / exit — **FAITHFUL-ish** (exit is a SWEEP, not a single config)

Minervini's selling is two-sided, and the holding period is **asymmetric by design — winners run weeks-to-months (50-day trail), losers are cut in days**. That asymmetry IS the edge (sub-50% win rate carried by a high payoff ratio). The exit must do two different jobs: bail fast when underwater, give room when winning.

| Minervini rule | Engine mapping | Fidelity |
|---|---|---|
| Cut losses fast, 5–8% below entry (user pref: fast) | percent stop (sweep **5/7/8%**) or **2.5 ATR** (`stopLoss`) | FAITHFUL (percent); ATR is volatility-adaptive hedge |
| "If it doesn't work right away, something's wrong" | failed-breakout cut (red within N days of entry) | FAITHFUL |
| Trail the 50-day (core) / 21-day (faster trades) | `priceBelowEma(50)` / `priceBelowEma(21)` / `trailingStopLoss(2.5 ATR)` | FAITHFUL |
| Follow the market — raise cash on market top | `marketAndSectorDowntrend` / `marketBreadthDeteriorating` | FAITHFUL |
| Cut dead money (time/opportunity stop) | `stagnation(thresholdPct, windowDays)` | FAITHFUL |
| Sell *into strength* / scale out partials | — | **SIDESTEP** (discrete full exits, no partials) |

**Guiding star: cut losses short, let winners run.** Every exit variant is judged against that. The quant review (2026-06-02) reshaped this section — three engine facts are decisive:

1. **No first-class percent stop, and a percent stop is ruler-inconsistent with the sizer.** `AtrRiskSizer` sizes shares off `nAtr=2.0×ATR` but **places no stop** — the loss-cut lives entirely in the exit stack. A fixed-% stop fires *inside* 2 ATR on high-vol names (whipsaws out of the volatile breakouts VCP targets, realized risk ≪ 1.25%) and *outside* 2 ATR on low-vol names (realized loss > 1.25% budget). **Fix: express the loss-cut in ATR so it shares the sizer's ruler.** `stopLoss(2.0 ATR)` = budget-consistent baseline (realized risk ≈ 1.25% by construction); the user's **2.5 ATR** = deliberate "0.5 ATR more room than the sizing stop." Stop-tightness sweep is a clean 1-D ATR sweep **{2.0, 2.5, 3.0}**. Percent stops kept only as a *flagged comparison arm*.
2. **`priceBelowEma(21)` does NOT exist** — `PriceBelowEmaExit` supports only {5,10,20,50}. Use **20**, or put 21-EMA logic in a `script`.
3. **Critical entry/exit interaction (Q5).** The trigger buys *at* the Donchian-high breakout, before follow-through. A stop measured from the **entry close** fires on the normal post-breakout retest of the pivot — cutting trades that would have worked. **The loss-cut must reference the breakout pivot / base low, not the entry close** (the actual Minervini rule: stop below the pivot or base low). This makes "cut losses short" *correct* (exit when the breakout truly fails) rather than twitchy.

**Revised exit sweep (Stage 1, ranker = `Random` per quant Q1 — neutral, removes selection-induced exit bias):**
- **EX-ATR20** (budget-consistent baseline): `stopLoss(2.0 ATR)` OR `priceBelowEma(50)`
- **EX-ATR25**: `stopLoss(2.5 ATR)` OR `priceBelowEma(50)` — user-requested
- **EX-CHANDELIER** (quant's likely winner; embodies the guiding star): `trailingStopLoss(2.5 ATR)` (highest-high-keyed give-back ceiling) OR `script(close < base-low − buffer)` (the failure floor)
- **EX-PIVOT**: `script(close < base-low/pivot)` OR `priceBelowEma(50)` — pure failure-line cut + MA trail
- **EX-SPLIT** (revised EX-ASYM): script — underwater ⇒ tight ATR cut / pivot break; in profit ⇒ ATR chandelier trail (do NOT cap the upside)
- **EX-VOLFAIL** (overlay on EX-CHANDELIER): OR `script(close < pivot on rising volume = distribution)` — highest-signal failed-breakout tell
- **ATR sub-sweep {2.0/2.5/3.0}** applied to the winning structure.
- *Flagged comparison only:* EX-PCT5 (`script` 5% from entry OR `priceBelowEma(50)`) — to SEE percent behavior; ruler-inconsistent, not a candidate to ship.
- **EX-VCPOLD** (user-requested 2026-06-02): the **old `VcpExitStrategy`** recovered from git history (deleted in `947dccc`, pre-deletion source) = `emaCross(10,20) OR stopLoss(2.5 ATR) OR stagnation(3%, 15d)`. All first-class conditions (no scripts). Distinct profile: faster 10/20 EMA-cross trend exit + 2.5 ATR stop + aggressive 3%-in-15-days dead-money cut. Run on the same Stage-1 footing (full universe, Random seed 42) to compare head-to-head with EX-ATR20/25. (Memory `feedback_vcp_exit_strategy`: this was the canonical VCP exit.)

**Do NOT** add `percentGain`/`profitTarget` as a primary exit — a hard target caps the right tail and is anti-thesis for a fat-payoff-ratio strategy ("let winners run"). `stagnation` fires only on exactly `windowDays` (single-shot, leaky) → overlay-only; for capital recycling prefer a daily-re-evaluated `script` (below-entry AND >N days).

**Stage 2:** fix best 1–2 exits, sweep rankers — see §7. ~12–14 variants total (under G5's 50).

- **SIDESTEP 6a (stop type).** Minervini is percent-based; we express the loss-cut in **ATR** for sizer-consistency (quant), with a flagged 5%-percent comparison arm so the deviation is measured, not assumed.
- **Exit-script vetting:** every `script` exit (pivot-floor, volume-failure, split-regime, base-low computation) goes through `/create-condition` — rule **S3** (`entryQuote` frozen at entry: base-low window is anchored to `entryQuote.date`, "down X%"/"N days" read `entryQuote`, not running values).
- **G13:** ATR multiple, trail EMA period, base-low window, buffer, volume-failure threshold are all **Tier-1** (our choices) → full ±1-step sweep at validation.

### 7. Ranker (which leaders to buy first) — **FAITHFUL**

Buy the strongest leaders in leading groups → **ranker sweep (Stage 2)** — quant-revised order:
- **`SectorEdgeWithTightness`** — strongest group **+ base-tightness tiebreak** ("strongest group, tightest base"), the most on-thesis ranker — **but a SECOND-PASS ranker, implementable only AFTER we have a working strategy.** It needs a `sectorRanking` (sector-priority order), and that order is **not arbitrary/user-picked** (which would be data-snooping): the intended workflow is **run the strategy, extract each sector's measured edge from the results, then feed that ordering back as the ranker's `sectorRanking`.** Leakage discipline: the sector ranking must be **IS-derived for OOS** (WalkForwardService already does IS-derived sector ranking for OOS) — never rank OOS on edges measured on the same window. **DEFERRED** until a Stage-2 strategy exists to extract sector edges from; revisit as a second pass once a base ranker is chosen.
- **`TrailingReturn`** (12-1 momentum; ρ≈0.9 with the RS gate by design — ranks the strongest *of the qualifiers*).
- **`RollingSectorStrength`** / **`SectorStrengthMomentum`** (leading / accelerating industry groups — Minervini emphasis).
- **`Composite`** baseline only — double-counts extension via `DistanceFrom10Ema` (we want *proximity to the pivot*, i.e. base tightness, not 10-EMA distance); don't expect it to win.
- **MISSING — potential new-build (DEFERRED, user 2026-06-02):** a **pure base-tightness / contraction-quality ranker** (rank by the VCP-A contraction tightness already computed at entry) = "buy the tightest breakout" directly, vs the coarse `Volatility(ATR%)` proxy. The single most premise-aligned ranker — **build it only after the existing rankers are tested in Stage 2**, and add it if a ranker edge looks promising.
- Watch for rank-preserving duplicates (memory `feedback_ranker_rank_preserving_transforms`).

### 8. Position sizing / concentration — **SIDESTEP**

Minervini runs **concentrated** (4–8 names), **pyramids** into winners, and scales total exposure with market health. Engine map: `AtrRisk(~1.25%)` fixed risk-per-trade + fixed `maxPositions`. No pyramiding, no dynamic exposure. **SIDESTEP** on concentration dynamics; the risk-per-trade discipline itself is faithful.

### Sidestep summary (for quick reference)
1a RS = 252d total-return percentile, not IBD quarter-weighted RS Rating (ADR 0009 v2 follow-up).
2 SEPA fundamentals entirely absent (price-only engine) — biggest deviation.
3 VCP base structure (multi-contraction + volume dry-up) not yet built — **open fork**.
4a Pivot = Donchian-high proxy, not exact final-contraction high.
5a Binary market gate, not discretionary exposure scaling.
6a Stop type (percent vs ATR) — recommend percent for fidelity.
6b No sell-into-strength / partial exits.
8 No pyramiding / dynamic concentration.

## Quant-approved build plan (2026-06-02, APPROVE-WITH-CHANGES)

The full candidate spec and the screen-before-assemble sequencing were quant-reviewed. Approved with 5 changes, all folded in below:

1. **VCP scripts are screened for ARS-rejection ONLY.** `/condition-screen` anchors to *standalone* forward-return lift, but VCP-A/B are **base-context filters** whose edge is conditional on the trend gate already being true (volume dry-up carries no edge in a downtrend). Do **not** reject a VCP script for weak standalone lift — read only the ARS sweep + firing rate. The real lift test is the in-strategy ablation (step 4).
2. **Objective (i)→(ii) escalation for VCP-A.** Start with the range-step proxy (i). Escalate to swing-based (ii) **only if** (i) is ARS-clean *but* the assembled strategy fails the step-4 ablation. If (i) is **ARS-flagged, redesign the encoding — do NOT escalate** (swing-based is structurally *more* ARS-prone, not less).
3. **Per-window trade-count floor before trusting any assembled screen.** The 10-clause AND (market ∧ 5 gate ∧ 2 VCP ∧ 2 trigger) can collapse firing rate by an order of magnitude. On thin per-window counts, **ARS is unfalsifiable** (can't separate sign-flips from small-sample noise). If any OOS window has **< ~20–30 trades**, the screen verdict is untrustworthy → escalate universe before reading it. *Also: screen `gate ∧ VCP base` as one block before adding the trigger, to attribute any firing-rate cliff to VCP vs trigger.*
4. **`priceNearDonchianHigh` vs `percentFrom52WeekHigh` overlap — RESOLVED.** Donchian period = **5 days** (`donchianUpper5`), not 252 — a short-window pivot breakout, dimensionally distinct from the 252-day-high proximity gate. No tautology.
5. **Universe for `/strategy-screen` (step 4) stated explicitly.** Full-universe screening is infeasible on this host, so the assembled screen runs on the reduced universe and is **ARS / plumbing validation only — NOT the edge verdict.** The edge verdict (and the RS gate's veto-rate + ablation) needs the **full universe at `/validate-candidate`**, where the backtest engine's lighter memory profile handles it.

Other confirmations: G14 promotion correctly deferred (inline TRADABLE verdict is void until `/verify-promotion` PASS); RS-gate ρ≈0.9 with TrailingReturn ranker is justified (rank-strongest-of-qualifiers), validated by veto-rate at step 4; exit stack (8% stop OR priceBelowEma(50)) is a sound Minervini map.

## The funnel (after the gate is screened)

1. Assemble GATE + VCP base + TRIGGER + EXIT (likely inline `script` for the base/trigger first).
2. `/strategy-screen` (10y 2005–2015) — survivors only; flags screen survivors with CAGR < 30%.
3. `/validate-candidate` (3-block firewall: Block A 2000–2014 binding, Block B COVID-inclusive binding, 25y aggregate binding, Block C informational; v4 gates + G13 parameter-robustness).
4. Promote inline `script` conditions via `/create-condition` + `/verify-promotion` (G14 trade-list diff) — **a candidate using inline scripts is NOT tradable even after a TRADABLE verdict** (memory `feedback_script_conditions_must_be_promoted`).
5. `/monte-carlo` (path risk + edge confidence).
6. **Tradability bar — RELAXED for this candidate (user, 2026-06-02):** this is a **regime-conditional COMPONENT**, not a standalone strategy — it deliberately sits in cash in bear/low-breadth tape (2001/2002/2008/W4-2011), where *other* strategies cover. So the standalone **30% CAGR gate does NOT apply**; it will be combined with other strategies and the **portfolio target is ≥ 25% CAGR**. Judge this strategy on its *contribution* to that blend (regime coverage, cash-overlap, correlation), not a standalone number. The exact component-acceptance criterion vs the 25% portfolio target is a portfolio-construction question to settle with the quant. Connects to [[project_regime_conditional_portfolio_framework]]; supersedes the standalone `feedback_min_cagr_tradable` 30% for this candidate.

Track the candidate through `/strategy-exploration` (the non-executing state-machine dossier).

## Infra notes (this host — learned the hard way 2026-06-02)

- **udgaard heap set to `-Xmx18432m`** (18 GB) in `udgaard/Dockerfile:17`, `-Xms2048m`. History: original `-Xmx20480m` (20g) let a heavy full-universe screen exhaust RAM+swap and **crash the container once, then the whole laptop once** — but that was *while dev containers were also competing*. Briefly dropped to 10g (host-safe but a high-firing full-universe gate screen OOMs the JVM at 10g — graceful HTTP 500, host survives), then raised to 18g for future full-universe screening. **At 18g the host headroom is only ~4–6 GB, so "dev containers stay stopped during heavy PRD screens" is load-bearing, not optional** (18g udgaard + ~4g midgaard + ~2g postgres + OS ≈ 25–27g on a 30.8g host). **A `mem_limit: 20g` cgroup cap is set on the udgaard service in `compose.prod.yaml`** (~2g over -Xmx for off-heap, so the JVM throws a graceful heap OOME before the cap) — this makes the host crash-proof for this workload: a JVM/off-heap overshoot is killed at the container level (`OOMKilled=true`, auto-restart) instead of the kernel OOM-killing the laptop (which is what happened at the original 20g heap with no mem_limit and dev containers competing). **The Dockerfile heap change + the compose mem_limit are committed-pending — include both in the branch.**
- A full-universe `/condition-screen` of a **high-firing gate** is the heavy case (gate ~30% firing × ~3,900 symbols × 21y × 3-cell ARS sweep = millions of signals). 18g is the budget for attempting it; the reduced sanity universe remains the safe fallback (faithful for precomputed indicators like RS), and the full-universe check also rides `/strategy-screen` (different, lighter memory profile).
- Backtests/screens run against **PRD: port 9080, `X-API-Key` header** (memory `feedback_prd_backtest`). Dev containers are **not** to be used for backtesting; stop them to free host RAM when running heavy PRD work.
- Always show the POST and get an explicit "go" before firing any screen/backtest (memory `feedback_backtest_user_approval`).

## Branch / commit state

Branch: `feat/minervini-trend-template-conditions` — built but **NOT pushed** (open a PR only when the user says so). Contains the 5 trend-template conditions, ADR 0009 + CONTEXT.md, the RS indicator pipeline (midgaard compute + udgaard ingest, V18/V27), the recompute trigger + perf fix, and the udgaard heap cap (pending commit).

## Reference

- ADR `docs/adr/0009-market-relative-strength-percentile-is-a-persisted-cross-sectional-indicator.md`
- ADR `docs/adr/0007-condition-screen-diagnostic-and-leakage-boundary.md`
- CONTEXT.md term "Market-relative strength percentile"
- Memory `project_rs_gate_design_adr0009`
- Conditions: `udgaard/.../backtesting/strategy/condition/entry/`
- RS compute: `midgaard/.../service/RelativeStrengthService.kt`, `.../repository/QuoteRepository.kt`
