# Strategy Screen — Reference

Background, statistical power notes, and the quant's verdict that fixed the screening config. See [SKILL.md](SKILL.md) for workflow and the 5 gates.

## Why 10 years is enough for screening

Per-window stats at N=100-200 trades are mostly noise. What matters is the aggregate across windows. At 10y / 36IS / 12OOS / 12step you get **7 OOS windows** vs 12 at 15y. Pooled OOS trade count at ~150 trades/window: ~1,050 vs ~1,800.

**Minimum detectable edge** (two-sided, alpha=0.05, power=0.80, R-multiple sigma ~1.5):

| Pooled N | Minimum detectable edge |
|---|---|
| 1,050 (10y, 7 windows) | ~0.09R |
| 1,800 (15y, 12 windows) | ~0.07R |

Anything with a real >0.15R edge survives 10y screening comfortably. Below ~0.10R you're in noise territory regardless of window length.

## Window choice

| Option | Verdict | Why |
|---|---|---|
| 2000-2010 | Rejected | Regime-skewed: dot-com 2000-02 + GFC 2008 + sideways decade. Kills strategies that work in normal trend regimes (false negatives). |
| **2005-2015** | **Selected** | One major stress (GFC), two regimes (pre-GFC trend + QE-era grind). Furthest from Block C. Cleanest signal-to-overfit ratio. |
| 2014-2024 | Rejected | Overlaps Block C (2021-2025) — leakage by proximity. Survivors look better in Block C than they deserve. |

## Minimum defensible window

**8 years.** Below that, 36/12/12 yields only 4-5 OOS windows, which is too few for cross-window stability (you can't tell window-to-window variance from a 5-point sample). 10y is the sweet spot.

## Risks of screening (and mitigations baked into the gates)

| Risk | Mitigation |
|---|---|
| **Regime bias** — 2005-2015 is bull-leaning post-2009. Strategies that only work in QE regimes pass screen, fail Block C (2022 bear). | G4 (GFC stress check) — W1 must not catastrophe in 2008-09. |
| **5 marginal positives + 2 deep negatives can pass 5/7** | G3 adds median-edge-positive check. |
| **Multiple-comparison inflation** — faster iteration = more variants. Family-wise false-positive rate scales with sweep size. | G5 — variant-count >50 triggers tighter G1 and G3 thresholds. |
| **Cross-window stability blind spot** — 7 OOS windows enough for median + IQR but weak for tail behaviour. | Don't gate on "no window worse than -X%" with only 7 samples. Use median + 2x cap (G4 style). |

## Why G1 scales with riskPercentage

The engine reports `aggregateOosEdge` as a per-trade **percentage return**, not an R-multiple. A 0.10R edge means "10% of the risk-per-trade dollars" — that's 0.125% per-trade return at 1.25% risk, or 0.20% per-trade return at 2.0% risk. **Picking an absolute % threshold (e.g. ≥ 0.15%) silently loosens when risk-per-trade goes up.** Always express the floor as `0.10 × riskPercentage` (or `0.12 ×` under G5).

## Why G3 needs the median clause

5-of-7 positive is a binary count — it doesn't see magnitude. Imagine a strategy with 5 windows at +0.05% and 2 windows at -3.0%. Total pooled edge is negative; the strategy is broken; but it passes "5 of 7 positive". The median-edge-positive AND-clause closes this: with 7 windows, the 4th-ranked window's edge must be positive. That fails the example above and survives only on broader strength.

## Why G4 uses 2x median, not absolute %

DD scales with strategy volatility — a high-vol strategy with normally ~25% DDs shouldn't be penalised vs a low-vol strategy with ~5% DDs. The 2x-median check asks "does this strategy fall apart in 2008?" relative to its own normal behaviour. Absolute thresholds would over-reject high-vol strategies and under-reject low-vol ones with hidden tail risk.

## Why G5 is a HARD gate, not just downstream advice

Multiple-comparison hygiene as a downstream rule is too late: if 50 borderline-noise candidates pass the screen, the survivors are pre-selected for upward noise. Downstream tightening on full Block A doesn't recover from biased screen selection. G5 enforces tighter screening gates once the sweep size crosses the threshold so noise survivors get filtered before reaching the next stage.

## Quant verdict (verbatim)

The screening config was validated by `voltagent-domains:quant-analyst` in 2026-05-27. Full Q&A captured at the time:

- **10y is statistically sufficient for screening** (not for live-trading verdicts).
- Window: **2005-2015** (one major stress, two regimes, furthest from Block C).
- Minimum defensible range: 8y; below that, cross-window variance becomes unmeasurable.
- Gates are calibrated to filter obvious losers, not crown winners.

After initial draft, the quant re-reviewed the gates and corrected:
- G1: scale with `riskPercentage`, drop "R" framing.
- G3: keep 5/7 but add median-positive AND-clause.
- G4: rename window from "2008-01-02 OOS" to "W1 (GFC OOS)" — regime label, not start date.
- G5: promote variant-count tracking from downstream advice to a hard gate.
