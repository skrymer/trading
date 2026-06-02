# Component Firewall — validation plan (Track 1)

_Created 2026-06-03. **This is a scoping/review doc, not executable and not yet a skill.** Nothing fires until the run plan (§3) is approved; no `/validate-component` skill is persisted until the gate thresholds (§4) + days-in-market definition (§6) get quant sign-off (memory `feedback_get_expert_review_before_persisting`). Adapts the quant-drafted Component Firewall table in `MINERVINI_VCP_STRATEGY_DEVELOPMENT.md` (2026-06-02)._

> **Quant review folded in (2026-06-03).** The framework was pressure-tested as text before firing. Four structural fixes were mandated and are incorporated below: **(1)** add **C-PARTICIPATE** (minimum-participation floor — without it "always in cash" trivially passes every gate); **(2)** §5 classifier **OR → AND** (the OR rule let a low-count *participating loss* masquerade as cash discipline — the worst laundering leak); **(3)** **C1c splits** — Calmar on the in-market series, Sharpe stays **blended** (the blended-Sharpe lock is what prevents laundering a weak edge); **(4)** every ★ threshold must be derived from an **external anchor** (portfolio arithmetic / statistical power / position-arithmetic ceiling) and **frozen before** the EX-ATR20×SSM runs are read for pass/fail — the runs *check* the bars, never *choose* them. Sections §4–§7 + §9 reflect these.

## 0. Where the candidate is

The Minervini VCP **breakout** strategy is a **regime-conditional COMPONENT** (flat in bear/chop; portfolio target 25% CAGR, NOT standalone 30%). Promotion + G14 are done:

- VCP-A → `narrowingRange(stepWindow=10)`, VCP-B → `volumeDryUp(10/50/0.7)` — first-class, unit-tested, deployed (udgaard 1.0.81).
- **G14 `/verify-promotion` PASS** (946=946 trades, Jaccard 1.0, 0 divergences over 25y). C14 satisfied — the candidate is no longer void-on-inline-scripts.

**Two candidates share this entry stack**, differing only in exit (G14 covers both, since it isolates the entry-condition implementation):
- **EX-ATR20×SSM** — `stopLoss(2.0 ATR)` OR `priceBelowEma(50)` (best DD, budget-consistent)
- **EX-VCPOLD×SSM** — `emaCross(10,20)` OR `stopLoss(2.5 ATR)` OR `stagnation(3%,15d)` (best edge/win%/sample)

Ranker LOCKED = `SectorStrengthMomentum`. Sizer `AtrRisk(1.25%, 2.0 ATR)`, maxPos 10, capital 100k, leverage 1.0, full universe `STOCK`, seed 42, entryDelayDays 1, 36/12/12 cadence.

## 1. Why not the v4 `/validate-candidate run-pipeline.sh` as-is

The v4 firewall **false-rejects a cash-in-crisis regime component by design** (quant 2026-06-02):

| v4 gate | Why it mis-fires on a regime component |
|---|---|
| **G1** CAGR ≥ 30% | Blended whole-period CAGR is dragged down by the windows where the component correctly sits in cash. The 30% floor belongs on the *in-market* period, not the blend. |
| **G6/G7** regime-positive mandates | Mandate the component be *positive* in 2008/2020/chop. A component whose thesis is "stand aside in those regimes" has ~no trades there — G6/G7 are unreachable, not failed. |
| **G8** ≥30 trades/window | A stand-aside window has near-zero trades by design; G8 reads that as a liquidity failure. |

The Component Firewall **validates alpha in-market and discipline in-cash separately — never credits cash, never penalizes it.**

## 2. What stays IDENTICAL to v4 (do not re-litigate)

- **Block structure + ranges:** A 2000-01-01→2014-01-01 · B 2014-01-01→2021-06-30 (COVID-inclusive) · 25y 2000-01-01→2025-12-31 · C 2021-01-01→2025-12-31 (informational). Quant-verified boundaries.
- **Cadence:** walk-forward 36/12/12 (IS 36mo / OOS 12mo / step 12mo), IS-derived sector ranking for OOS (WalkForwardService already does this — no leakage).
- **G10 design isolation** (freeze config across blocks), **G11 cross-block edge decay** (edge_B ≥ 0.5·edge_A), **G14** (done).
- **3 binding layers + informational Block C** philosophy (Block A + Block B + 25y aggregate bind; C is a yellow-flag sanity check).

## 3. Raw runs to fire (the data the gates need)

Two data sources are required because **walk-forward exposes no per-trade list** (same limitation that forces G14 to use single backtests):

| Run kind | Endpoint | Purpose | Per candidate |
|---|---|---|---|
| **Walk-forward per layer** | `POST /api/backtest/walk-forward` | per-window OOS edge/CAGR/DD/Sharpe/trade-count + `outOfSampleStatsByEntryMonth` (regime sub-gates) + `aggregateOosRiskMetrics` | 4 runs: A, B, 25y, C |
| **Single backtest per block** | `POST /api/backtest` → `GET /{id}/trades` | per-trade entry/exit dates → **days-in-market** (C1a) + window regime classification | up to 4 runs (or reuse the 25y G14 export for the 25y layer) |

- **Run ONE candidate end-to-end first** (propose **EX-ATR20×SSM**, the cleaner all-first-class exit) to *calibrate* the gate thresholds + the days-in-market formula, get quant sign-off, **then** run EX-VCPOLD×SSM through the frozen gates. Don't calibrate on both simultaneously (that's two-config snooping).
- **Sequential only** (engine OOMs on concurrent backtests). Stop dev containers first. Rough cost ≈ v4 pipeline (~60–90 min WF per candidate) + ~4 single backtests (~minutes each).
- The walk-forward template is a `WalkForwardRequest` with the candidate's `entryStrategy`/`exitStrategy`/`ranker`/`positionSizing` + `inSampleMonths 36, outOfSampleMonths 12, stepMonths 12`; the pipeline overrides only `startDate`/`endDate` per block.

## 4. Component Firewall gates — DRAFT (★ = data-gated, PENDING QUANT SIGN-OFF)

Population column states which trades each gate reads. **In-market** = participating windows only; **cash** = stand-aside windows (see §5); **blended** = whole period including cash.

| Gate | Threshold | Population | v4 origin |
|---|---|---|---|
| **C1a ★** in-market (active-period) CAGR | **≥ 30% (NEEDS-DATA — derive from portfolio arithmetic, §6/§7)** | in-market | replaces G1 (the real alpha bar) |
| **C1b** blended CAGR (anti-lottery) | **≥ 12%** | blended | new |
| **C1c-Sharpe** Sharpe ≥ 0.8 | as stated | **blended** | = G9 (the anti-laundering lock — see review item 1) |
| **C1c-Calmar** Calmar ≥ 0.5 | as stated | **in-market** | = G9 (blended Calmar is mechanically diluted by cash calendar time) |
| **C2** aggregate max DD | ≤ 25% | blended | = G2 |
| **C3** worst participating-window DD | ≤ 20% | in-market | = G3 |
| **C5** CoV of per-window edges | ≤ 1.5 | **in-market only** | = G5. **Binds only on the 25y layer** (in-market N large enough); advisory on A/B when in-market N < ~8 |
| **C6-STAND-ASIDE ★** crisis/cash windows: DD ≤ **3%** | as stated | cash | replaces G6 (cash side). The "≤15% days" lives ONLY in the §5 classifier now (no double-definition) |
| **C6-IN-MARKET** participating windows | edge > 0 (v4-strict) | in-market | = G6 (in-market side) |
| **C7** ≤ 1 negative participating window/block (≤ max(1, 10% of participating windows) on 25y), with magnitude bound + named W4 carve-out | as stated | in-market | replaces G7 |
| **C8 ★** in-market windows ≥ 30 trades; cash windows exempt; **N_min = 5** | as stated | per window | replaces G8 |
| **C11** edge_B ≥ 0.5 · edge_A (reads **participating-window** edges, not blended-block edge) | as stated | in-market | = G11 |
| **C12** ≥ 100 trades per block | ≥ 100 | blended | = G12 |
| **C14** scripts promoted + G14 PASS | ✅ done | — | = G14 |
| **C-PARTICIPATE ★** (NEW, binding) participating windows ≥ X% of OOS windows AND in-market trading-days ≥ Y% of block OOS trading-days | X/Y NEEDS-DATA | in-market | **new — the dual of the cash gate; without it "always in cash" trivially passes everything** |
| **C-CASHOVERLAP** (NEW, DEFERRED stub) stand-aside windows must not all coincide with a partner component's cash windows | DEFERRED | — | new — *coverage* gate (distinct from the *survival* Portfolio-blend G6); needs ≥ 2 components |
| **Portfolio-blend G6** (book survives 2008 + 2020) | DEFERRED | — | new — needs ≥ 2 components |

**Interim KEEP bar (standalone-as-component):** C1a ∧ **C-PARTICIPATE** ∧ C6-STAND-ASIDE ∧ C2 ∧ C3 ∧ C5 ∧ C1c-Sharpe ∧ C1c-Calmar ∧ C12 ∧ C14 ∧ (≤ 1 negative participating window passing the C7 magnitude bound + named W4 carve-out). The true portfolio-contribution test (C-CASHOVERLAP + Portfolio-blend G6) is deferred until a 2nd component exists.

**W4/2011 acceptance rule (pre-registered — quant review item 6).** The single permitted negative participating window is **pre-named: the 2011-OOS window, bound maxDD ≤ 20%** — NOT "the worst negative window, whatever it turns out to be" (a movable exemption invites post-hoc absorption of a *second* bad window). W4 = component held trades with `spyTrendUp` true, got chopped in 2011's narrow-breadth tape (win 17–24% vs ~40%; maxDD 15.5–17.4% < 20%). It is a **disclosed portfolio coverage gap**, bounded-accepted for Track 1 — **not** rescued with a post-hoc regime filter (IS-fitting/ARS). A second negative participating window, or 2011 breaching DD ≤ 20%, fails C7. Track 2 addresses 2011 structurally as a *new* candidate.

## 4b. FROZEN pre-registered thresholds (2026-06-03, before any run)

Per the §7 anti-snooping rule, every data-gated bar is fixed here from an **external anchor** — none is read off the candidate (the candidate's in-market CAGR, cash-window DDs, and participation fraction are all still unknown). The runs only (a) populate the regime-attribution table and (b) *check* realized values against these frozen bars.

| Gate | FROZEN value | External anchor (independent of candidate results) | Run checks |
|---|---|---|---|
| **C1a** in-market CAGR (geometric, §6 def-a) | **Formula primary:** `(1+g)^f·(1+p)^(1-f) − 1 ≥ 25%`. **Interim floor: g ≥ 30%** (lower bound); blend math puts the real bar at **~31–38%** | Portfolio target 25%; this component deploys in the *best* tape so it must over-deliver vs defensive partners running below target in their windows. Symmetric floor = 25%; with partner CAGR p≈8–12% and active fraction f≈0.6–0.7 → g≈31–38%. f comes from the run; p is portfolio-era. 30% is the floor it may not go below | f (deployment fraction); finalize g-floor once f known + partner p set |
| **C6-STAND-ASIDE** cash-window DD | **≤ 3%** | 0.60× the **5% position-arithmetic ceiling** (a <5-trade stand-aside window can lose at most 4×1.25% = 5% if all stops hit concurrently); the 2pp gap is the discipline margin | realized 2001/02/08 cash DDs: ≤3% pass · 3–5% discipline flag · **>5% = classifier error** (the window wasn't really cash) |
| **C-PARTICIPATE** windows (X) | **≥ 40% of OOS windows** | anti-benchwarmer minimum — below 40% the component is idle most of the time and can't be a portfolio contributor. NOT the expected ~60–70%; a floor set so "always cash" fails. **Breakout-archetype-specific — a crisis specialist legitimately participates < 40%; re-derive per archetype, do NOT inherit these values for a defensive component** (quant confirm 2026-06-03) | participating-window fraction (must be emitted as a first-class field) |
| **C-PARTICIPATE** days (Y) | **≥ 30% of block OOS trading-days** | anti-benchwarmer minimum (dual of the day-based cash boundary) | in-market days / block OOS days |
| **C7** permitted negative participating window | **maxDD ≤ 20% AND in-market CAGR ≥ −10% AND edge ≥ −5%**, window **pre-named = 2011-OOS** | W4 reference (−3.0 edge, ~17% DD) + margin; three-way AND (each bound closes a distinct laundering path — do not collapse to DD-only); a *second* negative window or 2011 breaching any bound fails. **Watch-item (quant 2026-06-03): the −10% CAGR bound has no direct W4 anchor (W4 in-market CAGR not in the reference) — if the 2011 re-run reports W4 in-market CAGR worse than −10%, that is a disclosed FINDING (the bound was too tight vs its own reference), surfaced per §9, NOT silently widened to pass** | the 2011-OOS window vs all three bounds; assert no other negative participating window |
| **§5 classifier** | in-market days **≤ 15%** AND trades **< 5** | "< ~1-in-7 days deployed ≈ not deployed"; N_min=5 statistical-power floor | ±1-step ARS check (15%→14%/16%, N_min 4/6): a reclassification that flips a gate = classifier is ARS-broken → redesign |
| **C8** | **≥ 30 trades/in-market window; cash exempt; N_min = 5** | v4 G8 + statistical-power floor | per-window counts; confirm no participating window wrongly exempted |

**Inherited-fixed (not data-gated — standard v4 ratio/percentage floors, carried verbatim):** C1b blended CAGR ≥ 12% · C1c-Sharpe ≥ 0.8 (blended) · C1c-Calmar ≥ 0.5 (in-market) · C2 ≤ 25% · C3 ≤ 20% · C5 ≤ 1.5 (in-market, 25y-binding) · C11 edge_B ≥ 0.5·edge_A · C12 ≥ 100/block.

**These bars are frozen. After the EX-ATR20×SSM runs, the calibrate-after step (§9) only plugs `f` into the C1a formula and checks realized values against the frozen ceilings — it may NOT move a bar to make the candidate pass** (`feedback_parameter_fragility_must_be_verified`).

## 5. Regime classification (window-level) — DRAFT

Each OOS window is labelled exactly once:

- **STAND-ASIDE (cash)** iff the window's in-market days ≤ 15% **AND** trade count < N_min (5) — **both** signals must agree (quant review item 3: the original OR let a window with ~18% deployment but only 4 *losing* breakout attempts — W4's exact signature — escape into "cash discipline," laundering a participating loss). Stand-aside windows are evaluated ONLY against C6-STAND-ASIDE (DD ≤ 3%) and are **excluded** from C1a / C3 / C5 / C6-IN-MARKET / C7. A window that participated by *either* measure stays PARTICIPATING and faces the alpha gates (the firewall's conservative default: assume risk was taken unless both signals say otherwise).
- **PARTICIPATING (in-market)** otherwise. These carry the alpha gates.

Emit a **regime-attribution table**: window → label → edge / CAGR / DD / trades-taken / **signals-generated** / in-market-days. The `signals-generated` column (item 3) surfaces a "many signals fired, few survived" window so it can be manually reclassified instead of silently swept into cash. The classification must be auditable, not a black box.

**ARS check on the classifier (item 7).** The classifier's own knobs (15%, N_min=5) are component-invented continuous tunables → run a one-time ±1-step confirmatory sweep on the EX-ATR20×SSM run: if 15%→14%/16% or N_min 4/6 reclassifies a window and flips a gate, the classifier itself has Aliased Regime Sensitivity (`feedback_aliased_regime_sensitivity`) and must be redesigned, not tuned.

## 6. Days-in-market computation (C1a) — the open definitional question

**It is now computable** (the per-trade export carries `startDate` + `quotes[]` date spans + `tradingDays`). But the *right denominator* is unsettled, and it changes C1a materially:

**Grounding fact (25y, EX-ATR20×SSM, from the G14 export):** the union of all days with ≥1 position open ≈ **5,410 of ~6,540 trading days (~83%)**. That **portfolio-union fraction is the WRONG denominator for C1a** — it says "something was almost always open" because the book holds up to 10 concurrent names across 25y of mostly-bull tape. It does not isolate "participating vs standing aside."

**DECIDED (quant review item 2): definition (a), with geometric compounding.**
- **(a) Participating-window annualization** — drop the STAND-ASIDE windows (§5); annualize the realized return over only the participating windows' calendar span. Chosen: one denominator, one classifier, matches the C6 split.
- **(b) Capital-deployment-weighted** — REJECTED: conflates *sizing* with *regime* (it measures the sizer, not the selector).
- **Sharp edge pinned down:** "annualize over participating calendar span" means **geometric compounding of the per-window returns over participating calendar time**, NOT an arithmetic mean of per-window CAGRs (an arithmetic mean flatters a lumpy series — `feedback_lottery_screen_diagnostic`: the geometric compound of the lumpy CAGR sequence *is* the true number).

C1a's **30%** floor is **NEEDS-DATA**, not assumed: it was inherited from the standalone gate, but in-market CAGR sits on a *smaller* base, so 30% is not transferable by analogy — quant prior is that the in-market bar should be *higher* than 30% (the drag is removed). Derive it from portfolio arithmetic (§7), do not read it off the candidate.

## 7. Calibration of the ★ thresholds — the anti-snooping rule (quant review item 7)

**Calibrating a pass/fail bar on the same run you then evaluate is circular** — setting C1a's floor after seeing EX-ATR20×SSM's in-market CAGR = choosing the bar to pass the candidate (`feedback_parameter_fragility_must_be_verified`, `feedback_aliased_regime_sensitivity`). The rule:

> **Every ★ threshold is derived from an EXTERNAL ANCHOR (portfolio arithmetic, statistical power, or a position-arithmetic ceiling) and written into the frozen gate table BEFORE the EX-ATR20×SSM runs are read for pass/fail. The runs are used only to (a) populate the regime-attribution table and (b) sanity-check that realized cash-window DDs sit under their mechanical ceilings — NEVER to choose the thresholds.**

Per-knob anchor:

1. **C1a in-market CAGR floor** — anchor to the **portfolio target**: if the book targets 25% CAGR and this component carries weight w deployed a fraction f of the calendar, the component's in-market CAGR must clear ~25% / (w · f). The *deployment fraction f* is read from the run (a structural property); the *bar* is derived from the 25% target. State the formula; plug in f.
2. **C6-STAND-ASIDE DD ≤ 3%** — anchor to a **position-arithmetic ceiling**: max possible DD when ≤15% of days are deployed at 1.25% risk × maxPos 10. Set the bound from that ceiling, then *check* the realized 2001/02/08 cash DDs fall under it. A realized cash window *exceeding* the ceiling is a **classifier error** (the window wasn't really cash), not a reason to loosen the gate.
3. **C8 / N_min = 5** — a **statistical-power floor** (below 5 trades a per-window edge sign is noise). Derive from power; re-confirm it doesn't *exclude a participating window*; never *raise* it to disappear a marginal window.
4. **C-PARTICIPATE X% / Y%** — anchor to "what minimum participation makes the component a *contributor* not a benchmarker"; read the candidate's actual participating-window fraction against it (do not set the floor below the candidate's number).
5. **C7 magnitude bound** — anchor to the W4 reference (−3.0 edge / ~17% DD); confirm no *second* window breaches.
6. **§5 classifier 15% / N_min** — set from structural reasoning (< ~1-in-7 days ≈ not deployed), freeze, then run the ±1-step ARS check (§5).

## 8. Orientation numbers (25y SINGLE backtest — NOT the firewall inputs)

⚠️ These are a **single backtest over 25y** (no IS/OOS split, optimistic vs walk-forward) — orientation only. The real gate inputs come from the §3 walk-forward runs.

| Metric (EX-ATR20×SSM, 25y) | Value | Note vs draft gate |
|---|---|---|
| Total trades | 946 | C12 (≥100/block) almost certainly fine |
| Blended CAGR | 10.4% | **under C1b 12%** — but blended; C1a (in-market) is the real test |
| Edge / trade | 3.63% | healthy |
| Win rate | 33.9% | sub-50%, payoff-carried (on-thesis) |
| Profit factor | 2.47 | strong |
| Sharpe | 0.77 | **just under C1c 0.8** (blended; depressed by cash drag) |
| Sortino | 1.08 | — |
| Calmar | 0.21 | **well under C1c 0.5** (blended) — flag for quant: is C1c the right population? |
| SPY correlation / beta | 0.36 / 0.43 | low — good diversifier (portfolio thesis) |

**The Calmar 0.21 / Sharpe 0.77 / CAGR 10.4% blended numbers are exactly the tension the Component Firewall exists to resolve** — they're depressed by the cash/crisis drag the v4 firewall would penalize. Whether C1c should read *blended* or *in-market* is the single biggest calibration question. Do NOT pre-judge from these single-backtest numbers — the walk-forward OOS per-block decomposition is what decides it.

## 9. Settle-before-firing vs calibrate-after (quant review)

**SETTLE BEFORE FIRING** (change what the runs must emit, or are anti-snooping decisions that cannot be made after seeing results — all now resolved above, pending user OK):
1. **C-PARTICIPATE** added (binding) — run must emit participating-window fraction as a first-class field. *Non-negotiable.*
2. **§5 classifier OR → AND** — closes the participating-loss-as-cash leak.
3. **C1c split** — run emits Sharpe AND Calmar on BOTH populations (in-market Calmar improvement is audited, not asserted).
4. **C1a denominator = (a), geometric compounding** stated.
5. **C7 magnitude bound + named W4 carve-out** (2011, DD ≤ 20%) pre-registered.
6. **C6 ≤15% redundancy removed** — one definition of the cash boundary (the §5 classifier).
7. **Anti-snooping calibration rule** (§7) — external anchors, frozen before reading runs.
8. **C-CASHOVERLAP** named DEFERRED stub added.

**CALIBRATES AFTER THE RUNS** (numbers from external anchors, then *checked* against the regime-attribution table — never chosen to pass): C1a 30%→derived floor · C6 DD ceiling · C-PARTICIPATE X/Y · C7 magnitude bound · C8/N_min re-confirm · §5 classifier ±1-step ARS check.

**Then:** approve §3 run plan (EX-ATR20×SSM first) → fire → calibrate-after items → freeze → run EX-VCPOLD×SSM through frozen gates → only after all that, build `/validate-component` as a skill (reuse v4 block-firing + WF plumbing, swap the evaluator). Track 2 (breadth-gated *new* candidate) stays queued.

## 10. RESULT — EX-ATR20×SSM **REJECTED** (2026-06-03, quant-confirmed)

Fired the 4 binding walk-forwards (full universe, 36/12/12). Results `/tmp/cf-wf-{blockA,blockB,25y,blockC}-result.json`; evaluator `/tmp/eval-cf.py`. **Six binding gates fail; verdict REJECTED — decisive, not a near-miss.**

| Gate | Block A | Block B | 25y | |
|---|---|---|---|---|
| **C1a** in-mkt geom CAGR ≥30% | 16.9% ❌ | 20.8% ❌ | **9.6%** ❌ | FAIL all 3 (below even the 25% symmetric floor) |
| C1b blended CAGR ≥12% | 10.1% ❌ | 20.8% ✓ | 12.7% ✓ | A fail |
| C1c-Sharpe (blended) ≥0.8 | 1.31 ✓ | 1.27 ✓ | 0.88 ✓ | pass |
| C1c-Calmar (in-mkt) ≥0.5 | 0.87 ✓ | 1.45 ✓ | 0.42 ❌ | 25y fail |
| C2 maxDD ≤25% | 22.6 ✓ | 20.1 ✓ | **42.3** ❌ | 25y fail |
| C3 worst-part DD ≤20% | 19.5 ✓ | 14.3 ✓ | 22.6 ❌ | 25y fail |
| C5 in-mkt edge CoV ≤1.5 | 1.26 ✓ | 1.29 ✓ | 1.86 ❌ | 25y fail |
| **C7** ≤1 neg participating window | **3** ❌ | 0 ✓ | **8** ❌ | FAIL A + 25y |
| C8 in-mkt ≥30 trades | 2@29 ⚠ | ✓ | 3<30 ⚠ | near |
| C11 edge_B≥0.5·edge_A | — | 0.95 ✓ | — | pass |
| C12 ≥100/block | 353 ✓ | 152 ✓ | 755 ✓ | pass |
| C-PARTICIPATE ≥40% | 91% ✓ | 100% ✓ | 95% ✓ | pass |

**Failure mode (quant): participate-and-lose in narrow-leadership chop — the breakout cousin of [[feedback_mean_reversion_pullback_known_weakness]].** NOT ARS (stable high trade counts 30–47, clustered consistent losses, no parameter flip) and NOT lottery (Block B has a genuine broad-regime edge: 0 neg windows, 20.8% in-mkt CAGR, real 2020 +56.5% recovery alpha). **`spyTrendUp` is too coarse** — it only stands the book aside in outright crisis (2008: 1 trade, 0.8% DD). In narrow-breadth-but-index-up tape (2015–16, 2021–23) it stays deployed with full trade counts and bleeds (2023 −19.4%, 2015 −14.7%, 2021 −10.3% CAGR). 8 of 21 participating windows negative on 25y.

**Diagnostic tell — the in-market geom CAGR 9.6% < blended 12.7% inversion is REAL, not a methodology artifact:** geometric compounding of the lumpy active-window sequence is the true alpha number; the cash/partial-year stitching *smooths* the blend, so the active-only sequence compounding *below* it means returns are **dispersion-dominated, not alpha-dominated** (`feedback_lottery_screen_diagnostic`).

**Why the 10y screen passed it:** the 2005–2015 strategy-screen (Sharpe 1.25, CAGR 17.5) straddled the broad 2009–13 recovery and never saw a sustained narrow-leadership cluster. The 25y firewall doing its job.

**EX-VCPOLD×SSM — NO-GO (quant):** an exit change cannot fix an entry-population failure (it can't un-take the failed breakouts); EX-VCPOLD trades looser/more in exactly the negative windows; and its only role was the post-calibration leg, moot once the shared entry is rejected. Skipped — wasted compute.

**Forward — Track 2 validated as the structural fix:** a **breadth-confirmed market gate** replacing the binary `spyTrendUp`, as a **NEW candidate re-screened from Stage 1** (breadth trustworthy from 2000 = Block A start, fully firewall-testable). **Keep the breakout premise + the promoted G14-PASS conditions** (Block B proves the edge is real in its native regime) — replace ONLY the regime selector. **NOT a post-hoc breadth filter bolted onto this REJECTED config** (IS-fitting/ARS on the single 25y realization; memory `feedback_mean_reversion_pullback_known_weakness`). Do not re-touch this config.

## Reference
- `MINERVINI_VCP_STRATEGY_DEVELOPMENT.md` (authoritative candidate record; Component Firewall table 2026-06-02)
- `.claude/skills/validate-candidate/` (v4 firewall — block ranges, cadence, G10/G11/G14, eval-block.py to adapt)
- Memories: `feedback_get_expert_review_before_persisting`, `feedback_min_cagr_tradable`, `project_regime_conditional_portfolio_framework`, `feedback_mean_reversion_pullback_known_weakness` (W4 discipline)
