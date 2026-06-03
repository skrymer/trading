# Strategy Ledger — tested, deprecated, and testable

_Last updated: 2026-06-03._

A single registry of every trading strategy / premise class this project has **tested**,
**deprecated**, or has **catalogued to test**. Purpose: stop re-running dead configs and
re-proposing dead premise classes, and keep the open candidate queue honest. The unit here
is the **premise class** first (the thing that lives or dies), then the concrete candidates
inside it.

**Funnel for any candidate:** `/condition-screen` (design-time) → `/strategy-screen` (10y
2005–2015 triage) → `/validate-candidate` (3-block firewall) → promote inline scripts
(`/create-condition` + `/verify-promotion` G14) → `/monte-carlo`. A **REJECTED** config is
dead — never iterate it; the only way forward is a redesigned candidate on a new lineage.

---

## A. Tested / evaluated — with verdicts

| Code | Premise class | Verdict | Date | Primary finding |
|---|---|---|---|---|
| **VZ3-s3** | Mean-reversion on pullback (to EMA20) | **REJECTED — Block C** | 2026-05-28 | Per-trade edge sign-flip +0.48% → −0.11% in 2024 narrow tape; passed A + B, capped by regime |
| **MR3-s1** | Mean-reversion proper (3-day pullback + up-day) | **REJECTED — Block A** | 2026-05-28 | Multi-dim drift (G3 DD 20.5% / G4 8-of-11 / G5 CoV 1.77 all tight); edge bleeds in quiet chop |
| **Idunn** (promoted VZ3-s3) | Mean-reversion on pullback | **REJECTED — Block B (ARS)** | 2026-05-29 | Off-by-one in lookback exposed Aliased Regime Sensitivity across lb {8,9,10,11}; non-monotone pass/fail; no robust operating point |
| **DV1** | Bullish divergence + EMA20 | **REJECTED — near-miss Block A** | 2026-05-28 | G1 CAGR 29.86% (−0.14pp), G6 2008 edge −0.48%; needs full redesign, not iterable |
| **Minervini VCP breakout** (EX-ATR20×SSM, EX-VCPOLD×SSM) | Breakout in uptrend | **REJECTED — Component Firewall** | 2026-06-03 | Participate-and-lose in narrow-leadership chop; in-market CAGR 9.6/20.8/9.2 ≪ 30%. All three market-gate fixes (spyTrendUp, +breadth, +sector-breadth) failed or made it worse → STRIKE 3. Edge is **real in broad-thrust** (Block B: 0 neg windows, 20.8%) → **SHELVED** as a risk-on building block, not tradable standalone |
| **Broad-rally leveraged-ETF timer** (+ Heimdall rotation) | Leveraged-ETF timing | **REJECTED — premise capped** | (Phase 1) | Both attempts rejected; a ranker bake-off had **Random tie the smart rankers** → no selection edge; thin ETF universe caps the palette |
| **Cross-sectional RS-momentum rotation** | RS-momentum rotation | **⚠ UNVERIFIED — deprecated on theory, ZERO runs; one screen needed** | 2026-06-03 | *Hypothesised* to be the breakout's twin (edge = dispersion = narrow-leadership death tape). **But never run** — and the rigor audit (§B audit) found the analogy contestable and possibly *inverted* (narrow leadership may *feed* momentum persistence). Needs one confirming `/strategy-screen` (blocked on PR #73 single-name data). Program stays abandoned regardless (the arithmetic, not the twin, was the driver) |
| **Low-vol / quality grind specialist** | Low-vol / grind | **NON-VIABLE on premise** (no full run) | 2026-06-03 | The low-dispersion grind tape *by definition* has no cross-sectional dispersion to harvest; the concentration ceiling that decorrelates it from the breakout also caps its CAGR. ~15–22% in-market at best, short of a re-derived bar. Boxed by construction |
| **VCP (original, pre-breakout)** | Breakout / volatility-contraction | **INVALIDATED** | (PR #34) | Order-block condition had a look-ahead bug; all pre-fix backtests invalid. Never re-validated through the current firewall |

Details + full gate tables for the firewall-run candidates live in
`VZ3_STRATEGY_DEVELOPMENT.md`, `MR3_STRATEGY_DEVELOPMENT.md`,
`MINERVINI_VCP_STRATEGY_DEVELOPMENT.md`, `validation-candidates.md`,
`COMPONENT_FIREWALL_PLAN.md`, and `project_phase1_leveraged_long_etf_attempts` (memory).

---

## B. Deprecated premise classes — DO NOT re-run or re-propose

The next single-strategy search must avoid these, plus the structural cousins. **Variant-exhaustion
audit (quant 2026-06-03):** for each class, is the rejection STRUCTURAL (the death is in the
mechanism → every variant shares the death surface → genuinely exhausted) or EMPIRICAL (only a
few configs tried → an untried variant could change the verdict)? Three are structural and
exhausted; the fourth was **deprecated on theory with zero runs** and is NOT yet exhausted.

1. **Long-pullback mean-reversion** — "buy the dip toward EMA in an uptrend." Reps: VZ3, MR3, Idunn, DV1. **STRUCTURAL → EXHAUSTED.** Two strikes: regime sign-flip (laggards drift, leaders don't pull back) + ARS on the pullback-detection sub-condition. The designed-but-unrun **MR4** (ATR-floor + min pullback depth + hold ≥5d) does *not* escape — depth/vol/hold knobs only re-select *between the two documented loser populations*, never restore the bounce. **Do not run MR4.** _Revisit trigger:_ sustained broad-participation bull (breadth EMA10 > 60% for 6+ months). `feedback_mean_reversion_pullback_known_weakness`.
2. **Breakout-in-uptrend (entry-time-regime-gated)** — Minervini VCP and cousins. **STRUCTURAL → EXHAUSTED.** Confirmed across **three pre-registered selector classes** (none / scalar breadth / per-name sector-breadth) — all thin *toward* losers. The loss is in the breakout's *follow-through failure at a fresh high*, not in detection, so non-VCP triggers (Donchian / 52wk / order-block) share the same death. **Do not open a 4th selector (Track-2c).** ⚠ *Precise claim:* it is **entry-time-regime-gated breakout** that's exhausted — the **breakout edge itself is real** (Block B: 0 neg windows, 20.8% in-market CAGR) and is **shelved**, contingent on a separately-validated regime-transition layer (the program that would have provided it is abandoned — `REGIME_CONDITIONAL_BATTLE_PLAN.md`). "Exhausted" ≠ "edge is fake."
3. **Leveraged-ETF timing** — 3× SPXL/UPRO broad-rally timer + Heimdall rotation. **STRUCTURAL → EXHAUSTED.** Random ranker tying the smart rankers is the *universe's* signature (~2 effective bets in a 12-correlated-ETF cross-section → no signal to sort), not a ranker-palette artifact; and the instruments post-date 2009 → the leverage multiple can never be regime-validated (no dot-com). Both walls are properties of the universe. **Do not iterate rankers/timers.** (The `spyTrendUp+uptrend` GFC-defensive gate is preserved as a reusable gate, not a resurrection.)
4. **Cross-sectional RS-momentum rotation** — long the relative-strength leaders; incl. sector-ETF rotation. **⚠ EMPIRICAL — NOT EXHAUSTED. ZERO runs.** *Hypothesised* the breakout's twin, but never screened, and the audit found the analogy contestable and possibly inverted (narrow leadership may *feed* momentum persistence; rotation doesn't trigger on a fresh high, so Class-2's follow-through death doesn't obviously transfer). **One confirming `/strategy-screen` is owed** before this is written as deprecated — read per-window edge in the 2021–23 narrow-leadership windows: Class-2 signature (full counts, clustered losses, in-mkt < blended) ⇒ twin confirmed; positive ⇒ twin false, class alive. **Blocked on PR #73** single-name universe data being populated. (Program stays abandoned regardless — driven by the one-component arithmetic, not the twin claim.)

Structural cousins that are also off-limits (same death surfaces): **low-vol / grind selection** (CAGR-capped + boxed by its own concentration ceiling — also a premise-level argument, not a firewall run), and any **bear / inverse / "defender" component** (the long-only engine cannot express a positive-edge bear strategy — ADR 0010; defense = cash).

---

## C. Testable candidates (the "can test" queue)

Proposed but **not yet run.** Two groups: the previously-catalogued queue (collision flags
now quant-confirmed) and the quant-proposed new premise classes (2026-06-03).

### C.1 Previously-catalogued queue — collision flags CONFIRMED (quant 2026-06-03)

Several were drafted on 2026-05-29, *before* the breakout rejection and the twin finding, so
their framing predates the §B deprecations. The quant confirmed each verdict below. Sources:
`NEW_CANDIDATES_2026-05-29.md`, `ALTERNATIVE_STRATEGY_PROPOSALS.md`.

| Code | Name | Premise class | Verdict vs §B | Note |
|---|---|---|---|---|
| **PEDQ** | Post-Earnings Drift Quality | PEAD / earnings catalyst | ✅ **Fresh, but re-scoped** | Event-anchored (distinct). ⚠ **Classic PEAD selection needs the earnings-surprise SIGN (SUE) — engine-confirmed UNAVAILABLE** (dates only, no surprise/estimates). George-Hwang-Li: PEAD is strong only for *positive* surprises near the 52wk-high. So only **date-proximity / announcement-premium / post-announcement price-reaction-proxy** variants are implementable; the proxy overlaps the **George** `close/52wk-high` field. Still needs an earnings-data quality audit |
| **Tyr** | Institutional Breakout | Order-block event + breadth recovery | 🟡 **Partial** | Breakout-cousin *trigger*, but the breadth-recovery regime gate is genuinely fresh (a transition-timer, akin to Gjallarhorn). Worth screening for the gate, not the trigger |
| **BTC** | Breadth Thrust Continuation | Breadth-momentum / trend-expansion | 🟡 **Partial — Gjallarhorn's sibling** | Fresh vs the deprecated four, but breadth-thrust *continuation* and Gjallarhorn's *exhaustion-reversal* are two halves of one breadth-event family. **Screen together; don't double-count the regime** |
| **Fenrir** | Sector Rotation Momentum | Sector-breadth acceleration | ❌ **Collides §B-4** | Sector-momentum = the diluted RS-momentum twin; the `SectorStrengthMomentum` ranker already operationalizes the mechanic. Drop or relabel as a variant |
| **PLM** | Persistent Leadership Momentum | Momentum / leadership persistence | ❌ **Collides §B-4** | RS-momentum + a persistence filter = same dispersion edge, same death tape. Was "top priority" 2026-05-29; demoted |
| **SRS** | Sector-Rotation Strength | Sector-momentum concentration | ❌ **Collides §B-4** | Narrow-leadership long specialist (no SPY gate) — the diluted twin. Drop |
| **Baldr** | Value Zone Accumulation | Accumulation near EMA20 | ❌ **Collides §B-1** | "Buy into the discount/value zone" = the dip-buy premise. Same narrow-leadership death |

### C.2 New premise classes — quant-proposed + research-surfaced (2026-06-03/04)

A verified deep-research pass (George-Hwang 2004, Antonacci 2012, Moskowitz-Ooi-Pedersen 2012,
McLean-Pontiff; adversarially verified, quant-signed integration) added the headline **George**
class and re-scoped several entries. The key correction: the §B "price-derived state exhausted"
claim **mis-partitioned** the space — it killed return-**CHANGE** mechanics but wrongly swept in
price-**LEVEL** anchoring, which is distinct and alive (see §C.3).

| Code | Premise class | Anchor | Distinctness / caveat |
|---|---|---|---|
| **George** ⭐NEW | **52-week-high anchoring RANKER** — rank cross-sectionally by `close / 52-week-high`, **long the top decile**; *no* proximity gate, *no* breakout trigger | George & Hwang 2004 (J.Finance) | **The headline new class.** Price-**LEVEL** ratio, not return-**CHANGE** → genuinely distinct from RS-momentum rotation, and **not the breakout twin *as a ranker*** (selects names *approaching* the anchor across the cross-section; never requires a fresh-high *event*, so the breakout follow-through-failure death does not transfer). Beat JT + industry momentum head-to-head (0.65 vs 0.38 vs 0.25 %/mo, Jul1963–Dec2001 CRSP) — **that 0.65 is the long-SHORT spread; the long-only top-decile leg captures the winners-near-high underreaction, not the full spread.** **Cheapest new build on the board:** one `StockRanker` reading the existing `high52Week` field (on `StockQuote`, via `entryQuote` in `score()`); engine has no anchoring ranker today (`TrailingReturn` is JT-12-1). |
| **Mimir** (re-scoped) | 52-week-high **proximity CONDITION** — `PercentFrom52WeekHigh` within ~5–8% (not at it) + `NarrowingRange`, exit into `PriceNearDonchianHigh` | George-Hwang (same effect, *gate* form) | **Still the breakout-twin-risk candidate** — George's academic distinctness does NOT immunize a *proximity-gated* form ("near the high" is exactly where the breakout follow-through-failure death lives). **Still owes its falsification `/condition-screen`.** All three conditions already exist first-class (**no build** — assembly + falsification only). Run AFTER George: George lives + Mimir dies ⇒ death localized to the proximity/fresh-high form |
| **Gjallarhorn** | Breadth-thrust **exhaustion-reversal** (crisis-bottom re-entry timer) | Zweig Breadth Thrust + capitulation reversal | Index-level breadth-state mean-reversion *at the bottom* (no uptrend required) — fills the crisis/transition gap the firewall keeps flagging as cash-only. Primitives built (`MarketBreadthNearDonchianLow` + `MarketBreadthRecovering`). Likely **PROVISIONAL** (few, lumpy trades → lottery-diagnostic + false-bottom risk) |
| **Forseti** | Ovtlyr-signal persistence/confirmation (vendor-signal alpha) | None academic — empirical vendor edge | Only *orthogonal-input* premise. Coin-flip but **cheap** (conditions built). Reject hard if it correlates with breadth/momentum (then it's the twin); suspect positives (un-auditable vendor pipeline) |
| **Heimskringla** | Calendar / seasonality deployment timing (turn-of-month, Halloween) | Ariel 1987; Bouman-Jacobsen 2002 | Strongest distinctness (dispersion-orthogonal, works in all regimes) but **low-CAGR → a deployment GATE on a stock-picker, not a standalone.** `script` on `quote.date`. ⚠ the FOMC variant is dead (below); turn-of-month/Halloween are the live ones |
| **Absolute-momentum overlay** | Per-name "hold only if it beats a cash hurdle over ~12mo, else exit to cash" | Antonacci 2012 (dual momentum) | **0.5 class — a defensive GATE, not a standalone** (halves max-DD −23% vs −54%; no standalone CAGR → fails the 30% floor by construction). Distinct from the relative `spyTrendUp` index gate (per-name absolute-vs-cash). TS/MA trend-timing (Moskowitz-Ooi-Pedersen 2012) **folds in here** (long-flat under no-shorting; Huang-2020 contested). Appears only as "picker × this overlay," never a standalone screen row. → also §D |
| **Sleipnir** | Vol-compression time-in-coil | Vol-clustering + low-vol anomaly | ⚠ Likely a low-vol COUSIN collision (CAGR-capped). Low priority — rule out explicitly. *(Research-confirmed: the long-only low-beta/low-vol tilt earns less than the published long-short BAB factor.)* |

**Considered and rejected (do not re-propose):** **FOMC pre-announcement drift** — decayed (0.445%→0.092% post-2016) **AND** intraday (2pm-2pm window) → not daily-implementable. **Vidar** (IPO/young-issue drift) — **STILL INFEASIBLE**: `listingDate`/`delistingDate` exist on `Stock`, but `listingDate` is *derived* as the first available quote date (`sortedQuotes.firstOrNull()?.date`) — a **coverage-start proxy, NOT a provider issue/IPO-date primitive** (it mis-ages any symbol whose coverage starts after its real listing) — compounded by survivorship-thinning of the young-issue cohort. The field's existence does **not** resurrect it.

**Ranked screen order** (built primitives, cheapness, distinctness): **George** (build the ranker → `/strategy-screen`) → **Mimir** (falsification `/condition-screen`, read against George) → **Forseti** (cheap, orthogonal) → **Gjallarhorn** (full screen; crisis-gap value) → **PEDQ** (re-scoped, after the earnings-data audit) → **BTC + Tyr** (breadth gates, screen *together* — don't double-count the regime) → **Heimskringla** (gate on top of the best surviving picker). **Absolute-momentum** = a gate applied to a survivor, never a standalone screen.

**Universe directive (McLean-Pontiff):** ~35% post-publication decay, but residual edge persists — and decay is **worst in large-cap / liquid / low-idio-risk** names, so surviving edges live **down-cap.** Market cap is **not plumbed to the engine** (in Midgaard, not propagated to udgaard — a data task); **dollar-volume (`volume × close`) + idio-risk (ATR/beta) *are* computable today.** Do NOT move the *first* screens down-cap (confounds "premise alive?" with "alive down-cap?"); add a **down-cap robustness LEG to survivors** (dollar-volume-filtered lower-liquidity sub-universe — free) — an edge that *strengthens* down-cap is McLean-Pontiff-consistent and more believable. Defer market-cap plumbing until a survivor earns it.

### C.3 The honest meta-answer — mis-partitioned, not exhausted (research-corrected 2026-06-04)

The earlier "feasible space mostly exhausted (~3 classes)" was **too pessimistic — the space was
MIS-PARTITIONED, not exhausted (~4.5 live classes).** The §B deprecation of "price-derived state"
over-generalized: it correctly killed **return-CHANGE** mechanics (momentum, RS-rotation,
breakout, mean-reversion — all genuinely dead) but wrongly swept in **price-LEVEL anchoring**
(George-Hwang), a distinct, academically-validated, still-working, *unbuilt* mechanic. The
operator's pushback ("we've only touched a handful of premises") is vindicated: the deprecations
were over-generalized from a few mechanisms to "all price-state."

Live classes (~4.5):
1. **Event-anchored underreaction** — PEDQ (re-scoped: SUE unavailable → proxy variants only).
2. **Breadth-event regime-transition timing** — Gjallarhorn (+ Tyr/BTC gates).
3. **Time-conditional flow timing** — Heimskringla calendar (gate, not standalone).
4. **52-week-high anchoring RANKER** — George (**NEW** — the missed class).
5. **Absolute-momentum defensive overlay** — 0.5 class, gate-only.

Plus two non-class slots: **Mimir** (breakout-twin falsification test) and **Forseti** (vendor
flyer). A long-only daily no-fundamentals engine expresses edge as price-derived state
(return-change exhausted; **level-anchoring alive**), event-date underreaction, flow/calendar
timing, and defensive overlays. _Budget ~35% post-publication decay (McLean-Pontiff) and hunt
down-cap on survivors._

---

## D. Reusable artifacts (the strategies are dead; the parts are not)

**Promoted first-class conditions** (G14-PASS, unit-tested, deployed udgaard 1.0.81):
`NarrowingRange` (volatility contraction), `VolumeDryUp` (volume dry-up).

**Built trend-template conditions** (strategy-neutral): `movingAverageStack`,
`movingAverageRising`, `percentFrom52WeekHigh`, `percentFrom52WeekLow`,
`relativeStrengthPercentile`.

**Infrastructure / methodology:** the market-relative-strength percentile pipeline
(ADR 0009); the **market-regime vocabulary + leadership-concentration gap** (`CONTEXT.md`);
the Component Firewall methodology + the **C-CASHOVERLAP gate-bug fix**
(`COMPONENT_FIREWALL_PLAN.md`); the shelved regime read-out classifier spec
(`REGIME_READOUT_PREREGISTRATION.md`); ADR 0010 (long-only ⇒ defense-is-cash); the
ARS / lottery / capital-aware-ablation diagnostics (memories).

**Defensive gates (reusable across any picker):** the preserved `spyTrendUp + uptrend`
GFC-defensive regime gate; and a **to-build absolute-momentum exit-to-cash overlay** (Antonacci
2012 — per-name "hold only while it beats a cash hurdle over ~12mo, else cash"; halves max-DD,
no standalone CAGR). Both are DD-reducers layered on a selector, consistent with ADR 0010
(defense = cash) — not standalone strategies.

**The shelved Minervini breakout** itself — a known-real broad-thrust edge, available if a
validated regime-transition layer is ever built (the program that needed it was abandoned).
