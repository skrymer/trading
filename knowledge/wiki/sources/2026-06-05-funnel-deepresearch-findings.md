---
type: source
title: Funnel vs. best-practice methodology — deep-research findings
summary: A /deep-research pass (24 sources, 3-vote verified) grading the funnel vs the quant literature — multiple-testing correction the dominant gap; several fixes have since landed.
status: stable
tags: [methodology, research]
sources: ["strategy_exploration/dossier/"]
related: ["[[the-funnel]]", "[[component-firewall]]", "[[parameter-robustness-g13]]", "[[lottery-vs-signature]]", "[[crisis-timer-cadence-ceiling]]"]
updated: 2026-06-07
---

# Funnel vs. best-practice methodology — deep-research findings (2026-06-05)

A `/deep-research` pass grading the [[the-funnel]] + [[component-firewall]] against the quant
backtesting-methodology literature: 24 sources fetched, 25 claims put through 3-vote adversarial
verification (20 survived). `[verified]` = survived verification; `[sourced]` = single-source lead.
Primary anchors: Harvey–Liu, Bailey–López de Prado (DSR / PBO / CSCV), Sullivan–Timmermann–White,
Kothari–Warner, AQR, FAJ.

## The dominant finding — no multiple-testing / trial-count correction

The literature is near-unanimous that **trial count is the decisive piece of metadata**, and the funnel
had **no explicit correction** for it. Expected *maximum* Sharpe across N zero-skill trials grows
~√(2·log N); after only **7** configs you expect a 2-year Sharpe > 1 at true OOS Sharpe = 0; ~**20**
holdout iterations make a spurious 5%-significant pass the *expected* outcome. Design-isolation /
dead-config / lineage controls limit *deliberate* refitting but **do not** adjust the bar for search
size. **[verified, primary]**

→ **Landed:** the **Deflated Sharpe Ratio** multiple-testing flag (search-agnostic DSR/PSR, takes
`nEff` + trial-Sharpe variance) shipped in #105 / **ADR 0014**. This was the single most-supported gap and
is now closed at the report level.

## What landed vs. what is still open

| Recommendation | Status |
|---|---|
| Trial-count register + Deflated Sharpe gate | ✅ **landed** — DSR flag (#105, ADR 0014) |
| Quote results net of a cost haircut | ✅ **landed** — per-trade cost, net 10 bps default (#101) |
| Beat-SPY risk-adjusted baseline gate (D3) | ✅ **landed** — G16 SPY-Calmar baseline (#102, ADR 0013) |
| Report PBO alongside the 50%-edge-decay rule (B2) | ⬜ open |
| Make G13 robustness **joint**, not one-at-a-time (B3) | ⬜ open — see below |
| Re-frame CAGR floor as risk-adjusted-primary (B4/B6) | ◑ partial — absolute Calmar G15 + Sharpe G9 added (ADR 0015); CAGR floor retained as appetite |
| Run G1 before relying on it (narrow-leadership twin) | ⬜ open hypothesis |
| Calibrate D2's p95 for cross-correlation (D2) | ⬜ open |

## Durable methodology caveats (uncaptured elsewhere)

- **A1 — single fixed walk-forward is the *weakest* OOS harness.** Combinatorial Purged CV (CPCV) is the
  literature-preferred harness (lower PBO, higher DSR; yields a *distribution* of Sharpes, not one point
  estimate). 6-8 windows is the floor (our 7 just clears it); our 36/12 = 3× IS/OOS ratio sits inside the
  recommended 2-5× band. **If we keep walk-forward, purging + embargo at fold boundaries is required.** The
  absolutist "CPCV is THE remedy" was *killed* in verification — it is *a* strong remedy with its own
  assumptions. **[verified, primary]**
- **A2 — demoting Block C to informational is defensible only as overfit-control**, not relevance-weighting
  (it stops Block C becoming a 4th tuning surface). But **a *failed* Block C should be a hard flag for
  manual review, not a silent pass.** **[verified, primary]** — consistent with [[component-firewall]]'s
  current Block-C treatment.
- **B3 — one-at-a-time G13 is the weak part.** Robust-parameter work searches **joint** multi-dimensional
  plateaus; axis-by-axis perturbation misses interaction fragility. **Our own [[aliased-regime-sensitivity]]
  is a discovered instance of joint fragility.** A coarse joint grid on the 2-3 most sensitive tunables
  would harden G13. **[sourced]** — see [[parameter-robustness-g13]].
- **B4 — a 30% (now 25%) unlevered long-only CAGR is ~4-6× the market's objective expected return** (AQR
  CAEY + ~2% real growth) — exactly the magnitude the DSR math says is most likely manufactured by search.
  *The higher the CAGR floor, the more trial-count correction you need.* **[sourced, primary]** — feeds
  [[2026-06-06-gate-basis-and-cagr-floor-feasibility]].
- **A3 / D2 — rare-event nulls are themselves fragile.** Pooled event-study / bootstrap / matched-sample
  nulls **do not** fix cross-correlation when the event sample is self-selected (Mitchell–Stafford). So the
  [[crisis-timer-cadence-ceiling]] disqualification is correct *and* the within-regime conditional null's
  p95 is not automatically well-specified (co-firing stress entries are cross-correlated). **[verified,
  primary]**

## Open hypotheses for the search

- **G1 — "narrow leadership kills cross-sectional RS-momentum" is probably *too strong*.** Momentum
  decomposes into factor-momentum (fares poorly) and a durable stock-specific component; narrow leadership
  is a *factor*-concentration phenomenon, so it can *feed* leader-momentum even as it punishes
  breadth-dependent variants. The twin-death analogy deserves an actual run before being treated as
  settled. **[sourced, primary]**
- **G2 — the premise-space partition looks ~complete**, with one refinement: "price-derived state" hides a
  real **factor-vs-idiosyncratic** split; stock-specific (idiosyncratic) momentum that does not long-run
  reverse is arguably a distinct 5th class. Event-date underreaction is confirmed durable and
  factor-robust. **[sourced, primary]**

## Pages this updated

[[parameter-robustness-g13]] (joint-fragility note) · [[purpose]] (open hypotheses G1/G2) ·
[[component-firewall]] · [[crisis-timer-cadence-ceiling]]
