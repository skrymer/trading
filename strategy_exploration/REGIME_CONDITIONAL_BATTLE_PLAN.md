# Regime-Conditional Battle Plan — Trading Plan

_Created: 2026-06-01 · Last revised: 2026-06-03 · **Status: ABANDONED (2026-06-03, operator decision).**_

> # ⛔ PROGRAM ABANDONED — 2026-06-03
>
> The regime-conditional-**portfolio** thesis is dead. Operator decision after a full
> design grilling + quant program-level review. **Do not resume it; do not re-propose a
> regime specialist.** The next move is **single-strategy search in a genuinely fresh
> premise class** (see post-mortem). The market-regime *vocabulary* (THRUST/NARROW/GRIND/
> CHOP/CRISIS + the leadership-concentration gap) survives as useful market-structure
> language in `CONTEXT.md`; the *portfolio program built on it* does not.
>
> ## Why abandoned (the post-mortem — read before re-proposing anything regime)
>
> 1. **Long-only engine ⇒ no defender component** (ADR 0010). Defense = cash, not a
>    strategy with alpha. So "≥2 components" became a *coverage* requirement, not
>    attack/defend.
> 2. **No viable 2nd long component exists.** Narrow-leadership long = the breakout's
>    *twin* (dies the same way); the low-dispersion-**grind** specialist is non-viable
>    because that tape *by definition has no cross-sectional dispersion to harvest*, and the
>    concentration ceiling that decorrelates it from the breakout is the same ceiling that
>    caps its CAGR. Every premise that clears a CAGR bar in calm tape loads into the
>    crowded leaders = the banned twin. Boxed by construction.
> 3. **So the "portfolio" reduces to ONE timed strategy (the shelved breakout) + cash** —
>    which is a market-timing overlay on a single mediocre strategy, not a portfolio. The
>    multi-component Sharpe-via-orthogonality thesis is gone.
> 4. **The arithmetic kills the 25% target** (quant 2026-06-03, estimates — `f` not yet
>    measured): breakout cross-block in-market CAGR ≈ **12%** (NOT the cherry-tested Block-B
>    20.8%; blocks were 9.6/20.8/9.2), active fraction **f ≈ 0.32** → **blended CAGR ≈
>    4–6%** (incl. ~3% cash yield on the idle ~68%, via SGOV / IBKR interest). 25% blended
>    would need ~120% in-market — a ~6× gap that *is* the leverage the engine forbids. **25%
>    target is dead; max defensible ≈ 5–7%.**
> 5. **The only honest pitch was MAR, not CAGR** ("~half of SPY's return at lower
>    drawdown"): ~4–6% CAGR / sub-20% DD → MAR ≈ 0.25 vs SPY ≈ 0.15 — only **~1.5–2×**,
>    and *entirely contingent* on an unbuilt read-out beating `spyTrendUp` (the exact thing
>    that failed before). For a single operator that complexity is not worth it unless DD
>    reduction is dramatic (MAR ≥ ~3× SPY) — judged not worth pursuing.
> 6. **Breakout-extension-hold rejected** — raises active fraction but holds names into the
>    breakout's documented give-back tape (2015 −14.7%, 2021 −10.3%, 2023 −19.4%); net
>    neutral-to-negative.
>
> ## What survives (reusable; the program is dead, the parts are not)
> - The **shelved Minervini breakout** + its promoted G14-verified conditions (PR #85).
> - The **market-regime vocabulary + leadership-concentration gap** definition (`CONTEXT.md`).
> - **ADR 0010** (long-only ⇒ defense-is-cash) — a durable engine truth.
> - **`REGIME_READOUT_PREREGISTRATION.md`** — a clean, quant-signed classifier spec, SHELVED
>   (revive only if regime-attribution is ever wanted as a research instrument).
>
> ## Next move
> Single-strategy search in a **fresh premise class** — must avoid all FOUR deprecated:
> long-pullback mean-reversion · breakout-in-uptrend · leveraged-ETF timing · cross-sectional
> RS-momentum rotation.
>
> _Everything below this banner is the pre-abandonment plan, kept for provenance only._

---

_Original framing (HISTORICAL — program abandoned, see banner):_ A regime-conditional
**risk-on system** for a strictly **long-only** engine. The book is in exactly one regime
state at a time; we deploy a long specialist in the up-tapes where it has a real edge and
**sit in cash** through every other tape. This document was the strategic plan only — it
fired no backtest.

## Origin / framing

The exercise began as "is `market breadth >50% & rising` + `sector breadth >50% & rising`
a good *base* for a bullish stock-selection strategy?" That premise was **abandoned**: a
base that only fires when the whole market is rising is dominated by SPY beta, and any
measured "edge" risks being index correlation in disguise. The useful survivor of that
idea is breadth as a **regime detector** (an allocation signal), not a stock-selection
entry filter.

This plan sits under the standing mandate: build a portfolio of regime-**specialist**
components, not one uber-strategy.

---

## Current state & findings (2026-06-03)

The framework was pressure-tested in a design session (engine constraints + quant). Five
findings now govern the plan:

1. **The engine is strictly LONG-ONLY** — no short, no direction/side, no negative
   quantity; P&L is hardcoded `exit − entry` (`BacktestService.kt:244`). "Make money
   when the market falls" has only three expressions and all are disqualified: inverse 3×
   ETFs (the already-capped thin-ETF family **plus** daily-rebalance decay drag),
   bonds/gold (no instrument before ~2002, so no coverage of the 2000–02 crisis window,
   and the same thin-palette problem), and pure cash (no edge — exactly what `C-PARTICIPATE`
   exists to reject).

2. **There is no "defender" component. Crisis defense = the read-out routing every long
   specialist to cash.** Defense is an **allocation state, not a strategy with alpha.**
   Portfolio-blend G6 (book survives 2008 + 2020) is a *survival* gate satisfied by shared
   crisis cash — it rewards *not losing*, never *winning* — **contingent on the read-out's
   crisis classifier being sharper than `spyTrendUp`** (which stayed deployed-and-bleeding
   in narrow-down tape).

3. **"≥2 components" is a *coverage* requirement, not attack/defend.** The expressible
   components are all long risk-on specialists; they all correctly share cash in crisis
   (mandatory, credited by G6) but must be active in **different non-crisis up-tapes** so
   the book is rarely all-cash when opportunity exists. `C-CASHOVERLAP` was re-scoped
   accordingly: **crisis cash is exempt; the gate measures stand-aside coincidence in
   *non-crisis* windows only** (`COMPONENT_FIREWALL_PLAN.md`). The diversification axis is
   "which up-tape each specialist harvests."

4. **Single-name cross-sectional RS-momentum rotation is the breakout's TWIN — DROPPED.**
   Its edge source is *cross-sectional dispersion*, and dispersion lives in
   **narrow-leadership** tape (2H-2021, 2023, 2024) — the exact participate-and-lose death
   surface the breakout already proved. The ADR-0009 RS floor is *relative*, so in narrow
   tape it routes *into* the crowded mega-caps, not away; rank-and-hold sells *after* the
   correlated break. "Rotation" is cosmetic. This was the prior "preferred Phase 2
   direction" and it is now rejected on premise.

5. **Track #1 is re-premised as a low-volatility / quality-grind specialist.** Splitting
   "the breakout is off" into its two sub-regimes exposed the only clean second tape:
   - **(A) Narrow-leadership** — index up, breadth weak, *high* dispersion, mega-cap
     concentration → the twin's tape. No long component survives here. Stand aside.
   - **(B) Low-dispersion slow grind** — index up, breadth positive-but-*flat*, *low*
     dispersion, low vol, no washout (so no bases for the breakout to fire on): 2013,
     2017, stretches of 2019, 2021-H1. The breakout is flat here → cash windows genuinely
     diverge → real coverage credit. **This is Track #1's native tape.**
   RS-momentum's edge (dispersion) and its survivable tape point in *opposite* directions —
   which is why the harvester of (B) is a **low-vol/quality** selector, not a rotation
   selector. (Quant ranked alternatives below; low-vol/quality won on non-overlap +
   long-only expressibility + avoidance of the deprecated failure classes.)

**Honest hedge:** the framework's survival now rests on the grind component validating. If
a low-vol/quality grind cannot show positive long-only edge in the 2013/17/19/21-H1
windows **with** non-overlapping non-crisis cash vs the breakout, the framework reduces to
**one component (the shelved breakout) + a cash read-out**, and `C-CASHOVERLAP` /
Portfolio-blend G6 stay permanently deferred. Validation is the arbiter, not this plan.

---

## The regimes

Working taxonomy (precise boundaries/thresholds are Track #2's pre-registration +
OOS-validation work — issue #83 — **not** frozen here). Market-structure signatures only;
regime is a property of the *market*, never fitted to a strategy's good/bad years.

| Regime | Market-structure signature | Stance | Component |
|---|---|---|---|
| **Broad-rally thrust** | breadth thrusting + **high** dispersion resolving upward + bases firing; SPY > trend | **TRADE** | **Minervini breakout** — validated-real, **SHELVED** (Block B: 20.8% in-market CAGR, 0 negative windows); deploy once Track #2 exists |
| **Low-dispersion grind** | index up, breadth positive-but-**flat**, **low** dispersion, low vol, no washout | **TRADE** | **Low-vol / quality grind specialist** — Track #1, to build (with a mandatory dispersion/concentration ceiling) |
| **Narrow-leadership** | index up, breadth **weak**, **high** dispersion, mega-cap concentration | **STAND ASIDE (cash)** | **None** — the twin death tape; no long component survives long-into-narrow exposure here |
| **Chop** | range-bound, no breadth trend, whipsaw | **CASH** | None |
| **Crisis / bear** | breadth collapsing, SPY < trend, correlated down | **CASH (read-out)** | **None** — the long-only engine cannot express a positive-edge bear specialist; defense = read-out → cash |

Two TRADE regimes (each with a long specialist), three CASH regimes. **Architecture:**
each component self-gates via its own entry conditions (its regime stack *is* its gate) —
no engine changes. The unified regime read-out (which tells the operator which regime
we're in, so they deploy the right specialist) is **Track #2** below.

---

## Coverage, not defense — why ≥2 components

The portfolio wants more than one specialist for **diversification across risk-on
sub-regimes**, *not* an attack/defend pairing. All components are long; they all share
cash in crisis (mandatory survival, credited by G6). They earn their separate keep only by
being active in **different non-crisis up-tapes** — the breakout in broad-rally thrust, the
grind specialist in the low-dispersion grind. A second component that is only on when the
first is on collapses into the first's beta and earns **zero** coverage credit. The
re-scoped `C-CASHOVERLAP` test (non-crisis stand-aside coincidence below a frozen ceiling)
is exactly the demonstration the grind specialist must pass.

---

## The read-out's three axes (quant-confirmed 2026-06-03)

The read-out classifies on **three cheap orthogonal axes**, all full-history to 2000, *no*
expensive full-universe pass. A single magnitude-only "dispersion" signal is **ruled out**
— it is sign-blind and cannot separate broad-rally thrust from narrow-leadership (both have
wide cross-sectional spread, in *opposite* directions of leadership).

1. **Breadth (have it) — participation.** `% of STOCK-type symbols in uptrend`
   (`breadthPercent`) + its `ema10` + slope. Equal-weight, one-stock-one-vote — the
   cap-weight-immune participation tool. **Never** confirm participation with a SPY-price
   gate ("SPY > EMA50"): SPY is cap-weighted, so a few mega-caps hold it up while breadth
   rots underneath (2H-2021, 2023–24).

2. **Leadership-concentration gap (NEW, cheap) — the thrust/narrow discriminator.** The
   **signed 20-day rolling-return gap** `SPY(20d) − EW(20d)`, where EW = **mean daily return
   of the STOCK universe**, computed in the *same daily pass* as breadth (definition-stable,
   full history to 2000), cross-checked against **SPY − RSP** where RSP exists (2003+). The
   **sign is the tell** and it flips structurally between the two high-spread up-tapes:
   - **gap < 0** (equal-weight leads — small/mids surge): broad participation ⇒ **thrust**.
     Confirmed: 2003 / 2009-H2 / 2020-H2 thrusts had RSP > SPY.
   - **gap > 0** (cap-weight leads — mega-caps carry it): **narrow-leadership**. Confirmed:
     2H-2021 / 2023 (cap-weight S&P +24% vs equal-weight low-single-digits) / 2024.
   EW-beats-CW *requires* broad participation; CW-beats-EW *requires* concentration — so the
   gap's sign **is** the participation question, measured with no estimation noise. A
   single-day gap is noise; use the multi-week drift. This was previously listed only as an
   "informational cross-check" — it is now a **primary classifier axis**.

3. **Realized vol (have inputs) — pins the grind.** SPY trailing 20-day realized vol,
   low/high band. Promoted from "light input" to co-equal axis: it is the *only* thing
   separating GRIND (trade the low-vol specialist) from a quiet-but-deteriorating
   early-narrow drift (stand aside). GRIND requires the low band.

**Labels:** THRUST = breadth high/rising ∧ gap<0 · NARROW = breadth weak/falling ∧ gap>0 ·
GRIND = breadth flat-positive ∧ gap≈0 ∧ vol low ∧ no breadth washout · CHOP / CRISIS = the
cash residual (crisis = breadth collapsing + correlated down).

**The exact cuts are FROZEN, quant-signed pre-registration** —
`REGIME_READOUT_PREREGISTRATION.md` (v1, 2026-06-03): causal-only statistics; Schmitt
hysteresis on the 50% breadth cut; gap dead-band = ±1 `gapBand` (overlapping-20d-gap stdev
over 252d); vol GRIND < 16% annualized, CRISIS ≥ 90th-pct trailing-252d; strict precedence
CRISIS→NARROW→THRUST→GRIND→CHOP; **5 labels are read-out-only — only the 3-bucket collapse
(PARTICIPATE-BROAD = THRUST∪GRIND / STAND-ASIDE-NARROW = NARROW∪CHOP / CRISIS) may flip a
binding firewall gate**; ±1-step ARS sweep frozen before any run is read; OOS-validate on the
dot-com→2003 span. Do not tune any value after a run is read.

---

## Component A — Broad-rally specialist: the shelved Minervini breakout

The broad-rally TRADE slot is **already filled** by a validated-real component. The
Minervini **breakout** is a broad-risk-on / post-washout-uptrend specialist: real edge in
broad thrust, dies (participate-and-lose) in narrow-leadership chop, correctly stands aside
in crisis. It earned its shelf on Block B (0 negative windows, 20.8% in-market CAGR) and is
**SHELVED** — deploy it only once a validated regime read-out (Track #2) exists to keep it
out of its death tape. Its VCP-base conditions (`NarrowingRange`/`VolumeDryUp`) and the
Minervini trend-template conditions are promoted, G14-verified, and merged (PR #85). See
`COMPONENT_FIREWALL_PLAN.md` for its full gate decomposition.

> **REJECTED prior approach — leveraged 3× SPXL broad-rally timer.** An earlier attempt to
> fill the broad-rally slot by riding real SPXL/UPRO bars on a breadth timer (and a Heimdall
> rotation variant) was **REJECTED** — the premise class is capped (a ranker bake-off had
> Random tie the smart rankers; thin ETF universe limits the palette). See
> `project_phase1_leveraged_long_etf_attempts`. The `StockPair` signal/trade-split plumbing
> it used is preserved under *Key engine references* in case leverage is ever revisited at
> the portfolio level.

---

## Component B — Low-dispersion-grind specialist (TRACK #1, to build)

**Premise:** a long specialist whose native tape is the **low-dispersion slow grind**
(regime (B) above) — low realized vol, positive-but-flat breadth, no washout — where the
breakout has no bases to fire on and is therefore flat. Active here, flat everywhere else.

**Instrument shape (quant Rank 1 — low-vol / quality grind):** a cross-sectional selector
ranking on **low realized volatility / low ATR-normalized range + trend-intact**, held
while a low-vol regime gate is on. Long-only native, no engine change. Its on-switch is the
structural *complement* of the breakout's high-dispersion thrust — that complementarity is
what earns coverage credit.

**The one non-negotiable design constraint — a PER-NAME concentration CEILING, not just a
low-vol ranker, and NOT the market gap.** Tapes (A) and (B) are adjacent and the boundary is
fuzzy; a low-vol selector with an unconstrained universe will drift into holding the crowded
leaders and quietly become the twin. The guard is a **position-level** measure — cap the
held name's **ADR-0009 market-relative-strength percentile** below a ceiling (forbid
already-extreme leaders) and/or cap per-name extension (price vs its EMA stack / ATR) — so
the selector stays in the *broad middle* of the participation distribution where its low-vol
edge lives. This is a **different layer** from the read-out's market-level
leadership-concentration gap: the **market gap** decides *when* the specialist may deploy
(regime gate, Track #2); the **per-name ceiling** decides *what* it may hold (selection
constraint, Track #1). A scalar market gate cannot see which names the selector holds —
conflating them is the "thinning-not-selecting" error (`TRACK2 §8`). **The per-name ceiling
is the first thing `/condition-screen` must stress.**

**Rejected / lower-ranked alternatives (do not silently revive):**
- *Single-name cross-sectional RS-momentum rotation* — the **twin** (edge source =
  dispersion = the death tape). Dropped on premise.
- *Long-the-pullback / buy oversold dips in a range* — the deprecated mean-reversion class
  (`feedback_mean_reversion_pullback_known_weakness`).
- *Early-trend leader that turns on before broad breadth confirms* (Rank 2) — only *partial*
  non-overlap (a phase-lead of the same broad-rally tape; bleeds into the breakout as
  breadth confirms) + whipsaw + a design-isolation challenge vs the breakout.
- *Sector-ETF RS rotation* (Rank 3) — a *diluted* twin: baskets mute single-name blow-up
  risk but its edge is still dispersion-sourced, so it's most-on in tape (A), not (B).

**Validation track:** funnel as a cross-sectional component — `/condition-screen` (stress
the concentration ceiling first) → `/strategy-screen` → **Component Firewall** (frozen
gates; `C-PARTICIPATE` and the §5 classifier **re-derived for this archetype**, NOT
inherited from the breakout) → `/monte-carlo`. The decisive evidence is **positive
long-only edge in the 2013/17/19/21-H1 grind windows AND non-overlapping non-crisis cash
vs the breakout** (the re-scoped `C-CASHOVERLAP` demonstration). Standard go/no-go: ≥30%
in-market CAGR floor still applies (`feedback_min_cagr_tradable`).

**Immediate next step:** scope the grind specialist as a design doc first (premise; the
low-vol ranker + the concentration ceiling + which existing conditions apply; the
archetype-specific `C-PARTICIPATE` re-derivation) → quant review → `/condition-screen`.
Do NOT inherit the breakout's gate thresholds blindly.

---

## The regime read-out (TRACK #2, issue #83)

**What it is (single-user app):** a **read-out the operator consults** — a small set of
market-defined regime labels + the underlying signals — so the user picks which
specialist(s) to deploy. NOT an auto-switcher; manual deployment stays with the operator.
It is also what keeps the shelved breakout out of its death tape and routes everything to
cash in crisis (the G6 contingency above).

**Inputs = the three axes above** (breadth · leadership-concentration gap · realized vol),
all cheap and full-history to 2000 — see "The read-out's three axes". The **one new derived
signal to build** is the leadership-concentration gap (`SPY(20d) − EW-universe-mean(20d)`),
added to the existing daily breadth pass (`MarketBreadthService`, persisted on
`MarketBreadthDaily`); cross-check vs SPY−RSP from 2003. The expensive full-universe
cross-sectional return-dispersion pass is **ruled out** (collinear; changes no stance). Data
via `GET /api/breadth/market-daily` (`quoteDate`/`breadthPercent`/`ema10`) + SPY/RSP bars +
`/api/breadth/sector-daily/{XLK..}` (`bullPercentage`, informational).

**⚠️ Hard discipline:** the classifier MUST be **market-defined, pre-registered, and
OOS-validated — NEVER fit to a strategy's good/bad years** (labelling 2011/21-23 "bad"
*because* the breakout lost there = IS-fitting / ARS). Regime is a property of the market,
derived independently of any strategy P&L. Breadth trust floor = 2000-01-01.

**Dual use:** the same principled labeller serves (1) the live read-out and (2) backtest
**regime-attribution** — it makes the Component Firewall's §5 window classifier principled
and shared across components. Build once, use both.

**Immediate next step:** a build (Kotlin regime-classifier service reading breadth/SPY +
dispersion, + a Vue card on `mission-control`/`breadth`). Start by drafting the **taxonomy +
signal thresholds + hysteresis as text for quant review** (pre-registration), then implement
TDD. No backtest to start; OOS-validate the labeller on held-out history before trusting it.

---

## Carry-forward risks / flags

1. **Twin relapse (Track #1's #1 risk).** A low-vol selector with an unconstrained universe
   drifts into the crowded leaders and becomes the narrow-leadership twin. The
   dispersion/concentration ceiling is the guard; `/condition-screen` must stress it first.
2. **Collapse to one component.** If the grind specialist doesn't validate, the framework
   honestly reduces to the shelved breakout + a cash read-out (no `C-CASHOVERLAP` / G6).
   This is an accepted possible outcome, not a failure to paper over.
3. **G6 contingency on classifier sharpness.** Shared-crisis-cash survival only holds if the
   read-out actually fires the cash state in 2008/2020 rather than leaving a specialist
   deployed-and-bleeding (`spyTrendUp` was too coarse for exactly this). That sharpness is
   Track #2's burden.
4. **Survivorship bias in the breadth series.** Market breadth = % of *today's surviving*
   STOCK-type symbols in uptrend → a survivorship tilt that worsens further back.
   **Mitigation: trust floor at 2000-01-01** (coincides with Block A start; firewall
   unaffected). Crisis years validate qualitatively (2008 avg 26.7, 2022 avg 31.9), so treat
   residual within-2000-2014 tilt as a modest caveat on Block A *levels*, not a blocker.
5. **No genuinely *leading* broad signal exists** in the breadth primitives — breadth is
   coincident on an already-aggregated trend. Design read-out + entries for
   "fast-confirmed, faster-exit," not "predictive."
6. **`MarketBreadthIncreasing` is ARS-fragile** (strict consecutive monotonicity; one flat
   day resets the streak) and non-terminating under the `/condition-screen` ARS auto-sweep
   — robustness check is a manual ±1 grid.

---

## Build order

The two tracks are **not** independent-parallel: they share one build dependency. Track #1's
grind specialist self-gates on the **leadership-concentration gap**, and that gap is a *new
derived signal* — so it must exist before either track does its real work. Build it once,
first; the tracks parallelize only after.

0. **Shared prerequisite — the leadership-concentration gap signal.** Add
   `SPY(20d) − EW-universe-mean(20d)` to the existing daily breadth pass
   (`MarketBreadthService`, persisted on `MarketBreadthDaily`); cross-check vs SPY−RSP from
   2003. Small, TDD'able, no backtest. **This is the cheapest possible falsification of the
   whole read-out:** if the gap's *sign* does not separate thrust from narrow on held-out
   history, the three-axis classifier is in trouble before any specialist is built. Do this
   first.
1. **Track #1 — low-dispersion-grind specialist** (needs §0): design doc → quant review →
   `/condition-screen` (per-name concentration ceiling first) → `/strategy-screen` →
   Component Firewall (archetype-re-derived `C-PARTICIPATE`/classifier) → `/monte-carlo`.
   Kill-or-continue; this is what proves the framework is >1 component.
2. **Track #2 — regime read-out** (needs §0): can run in parallel with Track #1 once §0
   lands. Pre-register taxonomy/thresholds/hysteresis as text → quant review → TDD →
   OOS-validate the labeller. Deploys the shelved breakout and routes crisis → cash. Note
   the grind specialist consumes the *raw axes as entry conditions* (self-gating), not a
   pre-baked "GRIND" label — so the full classifier/labels/card are NOT a Track #1 blocker;
   only the §0 signal is.
3. **Portfolio overlay (later):** mutual-exclusion / transition-hysteresis / cash-routing
   state-machine, added only once ≥2 components prove out and `C-CASHOVERLAP` /
   Portfolio-blend G6 become testable.

---

## Key engine references

- `backtesting/service/BacktestService.kt:244` — `profit = exit − entry`; the long-only
  P&L that makes a bear "defender" component inexpressible.
- `data/model/AssetType.kt` — `STOCK / ETF / LEVERAGED_ETF / INDEX / BOND_ETF /
  COMMODITY_ETF`; inverse 3× ETFs live under `LEVERAGED_ETF` (capped family).
- `backtesting/model/StockPair.kt` — signal-stock / trading-stock split (signal on SPY,
  execute on real SPXL bars). Preserved for a possible future portfolio-level leverage
  revisit; the standalone leveraged-timer premise is REJECTED.
- `backtesting/strategy/condition/entry/MarketBreadthIncreasingCondition.kt` /
  `MarketBreadthRecoveringCondition.kt` — breadth entry primitives (read-out inputs).
- ADR 0009 — market-relative-strength percentile pipeline (universe infra; reusable as the
  grind specialist's universe, NOT as a momentum ranker).
