---
type: source
title: Quant consult — gate basis vs new engine + 25% CAGR floor feasibility (2026-06-06)
summary: Pre-restart quant review — KEEP all firewall gates on the realistic cost+idle-cash engine; 25% CAGR floor is reachable, the funnel dies on regime-survival not the return floor.
status: stable
tags: [methodology, quant-consult]
sources: ["docs/adr/0015-absolute-calmar-floor-and-g9-sharpe-recalibration.md", "docs/adr/0016-idle-cash-earns-the-historical-short-rate.md", "docs/adr/0005-walk-forward-aggregation-methodology.md", "strategy_exploration/validate-VZ3-s3-final.md"]
related: ["[[component-firewall]]", "[[lottery-vs-signature]]", "[[participate-and-lose]]", "[[long-premise-in-narrow-leadership]]", "[[parameter-robustness-g13]]"]
updated: 2026-06-06
---

# Quant consult — gate basis + 25% CAGR floor feasibility (2026-06-06)

Two quant-analyst adjudications run before restarting strategy testing, after the #101 cost model,
#103 idle-cash crediting, and #106 gate recalibration all landed. Both are quant-stated; the cross-cut
framing at the end is `^[inferred]`.

## Consult 1 — gate calibration basis vs the new default engine

**Verdict: KEEP every firewall gate as written (G1 25%, G9 0.5, G15 1.5, G16, screen gates); run the
firewall on the realistic default basis — `costBps` 10 bps ON + idle-cash crediting ON. Sanity
re-confirmation only, no recalibration.**

- **Idle-cash crediting is gate-neutral by design.** Sharpe-neutral by construction (ADR 0016 wires the
  same per-day `rf_step(t)` as both the cash credit and the Sharpe rf) → **zero effect on G9**. It only
  *eases* Calmar (G15/G16) in the economically correct direction for cash-heavy books (real T-bill coupon
  at no incremental drawdown), by a sub-CAGR-point amount well inside the gate margins.
- **10 bps round-trip cost** is a turnover-proportional drag: ~−0.2 to −0.4 pp CAGR, ~−0.03 Sharpe,
  ~−0.03 Calmar for a plausible candidate — inside every gate's margin, and it nets out of WFE (both IS
  and OOS halves carry it) while correctly lowering the absolute OOS floors.
- For a real part-in-cash candidate the cost drag and the idle-cash lift **roughly cancel**.
- **ADR 0016 supersedes ADR 0015's "may revisit G9 0.5 upward" note** — the aligned-rf wiring removed the
  cash drag that revisit anticipated, so 0.5 stays. The 10 bps cost argues weakly *down* on Sharpe but
  that's exactly the headroom 0.5 already carries.
- **Do not pin runs to the old cost-free / 0%-cash basis** — that re-introduces the two biases #101/#103
  fixed (flattering cash-heavy Sharpe, ignoring turnover cost), the dangerous direction.

## Consult 2 — is the 25% CAGR floor realistic?

**Verdict: 25% is REALISTIC — HOLD it.** The floor remains operator appetite (ADR 0015); the quant only
sized the feasible set. Appetite and achievability coincide here.

- **The joint region is non-empty and demonstrably reachable.** At the floor, G15 (Calmar ≥ 1.5) makes
  the binding target `CAGR ≥ 25% with maxDD ≤ 16.7%` (the Calmar identity — tighter than the G2 25% pain
  cap). Two candidates have landed *inside* the full joint region {CAGR ≥ 25%, maxDD ≤ 16.7%, Calmar ≥ 1.5,
  beat-SPY, Sharpe ≥ 0.5}: **VZ3-s3** (25y aggregate 30.7% CAGR / 12.6% DD / Calmar 2.43 / Sharpe 2.21)
  and **Idunn Block A** (41% / 11.7% / Calmar 3.54). Both numbers predate the cost+idle engine (roughly a
  wash net).
- **Why Calmar ≥ 1.5 is the natural output of a working timer, not a unicorn:** the stitched-OOS curve
  understates maxDD (omits IS-window crashes, ADR 0005) so the *measured* Calmar is biased high, and
  part-in-cash timing truncates the left tail of the drawdown distribution. The strategy class the funnel
  is built for is structurally aligned with the metric that binds.
- **Where 25% sits:** ~85–90th percentile of the *honest* survivor distribution, below the demonstrated
  clean ceiling (~30–40%). Median real-edge long-only daily timer ≈ 15–22% in-market; lottery/overfit
  artifacts *look* like 30–50% aggregate but collapse to single-digit *geometric* CAGR.

### The key insight — the return floor is not the binding wall

**The funnel is empty NOT because 25% is unreachable, but because every premise class dies on
regime-survival / fragility / beta gates (G4 / G6 / G7 / G11 / G13) two stops downstream of G1.** Not one
candidate in the recorded history was rejected merely for a real-but-sub-25% edge — VZ3-s3 cleared G1 at
30%; Idunn's Block A cleared 41% and died on Block B G5 CoV + G7 chop-sign ([[parameter-robustness-g13]],
[[aliased-regime-sensitivity]]); the breakout died on [[participate-and-lose]] (8/21 negative windows);
George lost to Random ([[thinning-not-selecting]]).

So **lowering G1 is strictly worse**: it admits more [[lottery-vs-signature]] artifacts (lumpy aggregate
CAGR clears a lower bar while geometric reality is single-digit) and **zero additional robust edges** — the
genuine ones already clear 25%. ^[inferred] The diagnosis re-points effort at the real constraint: the
regime-survival wall in narrow-leadership tape ([[long-premise-in-narrow-leadership]]), not the return
floor.

### Optional appetite lever (if more permissiveness is ever wanted)

Lower G1 to ~20% but **hold G15 = 1.5 and G2 = 25%** — this tightens the implied DD budget to 13.3%,
correctly demanding more risk-discipline from lower-return candidates. But there is **zero demonstrated
demand**: every near-miss on record (DV1 29.86%, Idunn-B 29.36%) was already above 20% and died on other
gates. The cost of holding 25% is **patience** (many premise classes per shipped candidate), not a
structurally empty set.

## What this updated

- [[component-firewall]] — gate basis re-confirmed against the cost+idle-cash engine (KEEP, no recalibration).
- [`../purpose.md`](../purpose.md) — 25% floor validated reachable; the binding wall named as regime-survival,
  not the return floor; the idle-cash/cost "does it move a gate" open question resolved.
- [overview.md](../overview.md) — stale Live-threads rows corrected (#101/#103/#105/#106 landed).
