---
type: source
title: DCA benchmark question + tactical-DCA (buy-more-below-200EMA)
summary: DCA the benchmark? SKIP — it washes out the relative edge (keep time-weighted). Tactical DCA (buy more below SPY 200-EMA) loses to blind DCA every block — beta-timing, not a strategy.
status: active
tags: [methodology, benchmark, dca, deployment-policy, beta-delivery, not-a-candidate]
sources: ["docs/adr/0013-spy-buy-and-hold-is-a-binding-calmar-only-firewall-baseline.md", "project_cash_leg_earns_sgov_ibkr_3pct"]
related: ["[[beta-delivery]]", "[[spy-trend-timing]]", "[[component-firewall]]", "[[purpose]]", "[[2026-06-10-leverageable-calmar-spy-timing-screen]]"]
updated: 2026-06-10
---

# DCA benchmark + tactical-DCA (2026-06-10)

Two operator questions about how the firewall benchmarks buy-and-hold, and whether timing the
*contributions* beats blind monthly DCA. Both settled; neither changes the funnel.

## Q1 — Should the firewall benchmark (and strategy) use monthly DCA instead of lump-sum? — SKIP

Today the G16 / absolute-Calmar baseline (ADR 0013) is **lump-sum** SPY buy-and-hold, **time-weighted**
(verified [[2026-06-10-leverageable-calmar-spy-timing-screen]]: SPY 25y Calmar 0.141, 55% GFC maxDD). The
operator's point is fair — lump-sum-at-t0 is the worst-case and not how a saver lives (a monthly DCA
investor had little capital exposed in 2008). But the quant verdict is **SKIP it as a gate:**

- **DCA is a contribution-*schedule* transform applied identically to the strategy and to SPY — common-mode.**
  It changes *which dollars are exposed when*, not *the return each dollar earns* (the edge the firewall
  measures). So the **relative edge (strategy vs SPY) is essentially unchanged**; only the **absolute Calmar
  bar rescales** (DCA drawdown-on-capital ~47% vs 55% lump → all Calmars jump, the 1.5 bar would need
  recalibration) — for **zero gain in discriminating power.** The cost-averaging tailwind is real (blind DCA
  lifts SPY's own return from ~8.0% lump-CAGR to ~11.4% IRR, +3.3pp) but **both books inherit it equally.**
- **Time-weighted vs money-weighted are two different questions:** time-weighted (today's basis) measures
  *manager skill*, contribution-timing-independent — the correct **edge gate**; money-weighted IRR measures
  *the saver's lived experience* — a personal-planning read that must **not** leak into the gate (a great
  strategy can post a mediocre IRR on unlucky deposit timing, and vice versa).
- The "0.141 is a harsh bar" worry is **moot** — it's already a doormat (G16-relative is trivially cleared);
  the binding wall is the **absolute G15 1.5**, which DCA doesn't make easier to *earn*, only easier to
  *clear arithmetically*. **Keep time-weighted.** A money-weighted IRR + drawdown-on-contributed-capital read
  is worth building only as a **personal investor-experience tool**, never as a gate — and not needed now.

## Q2 — Tactical DCA: deploy more when SPY < 200-EMA vs blind monthly — LOSES

The *opposite sign* of the rejected [[spy-trend-timing]] timer (which sold weakness to dodge drawdown and
whipsawed to Calmar 0.341); this **buys** weakness to accumulate cheap. Different objective (accumulate, not
avoid DD), and for a long-horizon accumulator buy-weakness is the structurally sound side — so it earned a
test. Quant ran it on SPY total-return closes (the engine can't express periodic contributions), budget-matched
(same $/month, reserve held for sub-200EMA dips):

| Window | Blind DCA IRR | Reserve-for-dips IRR | Δ |
|---|---|---|---|
| Full 2000–2025 | **11.37%** | 11.17% | **−0.20pp** |
| Block A (both crashes) | 8.24% | 7.74% | **−0.50pp** |
| Block B | 16.98% | 16.88% | −0.10pp |
| Block C | 16.62% | 16.26% | −0.36pp |

- **Loses in every block, monotonically worse the more you hold back** — and Block A (which contains 2008 *and*
  2000-02) has the **largest** penalty, not a benefit. The **cash-reserve drag** (parking dollars out of a
  market above its 200-EMA ~70% of the time) costs more than the dip discount saves, even after buying the 2009
  bottom. Robust across EMA windows 150–250 (all negative). Deploying *extra* on dips only raises terminal value
  by injecting more capital — capital-normalized IRR is **worse** than blind (11.31 vs 11.37).
- It is a **beta-timing bet** on below-200-EMA mean-reversion (the [[beta-delivery]] cousin — no selection, no
  tail-truncation), and operationally a **deployment policy, not a firewall strategy** (no cross-sectional unit,
  no exit logic, can't populate OOS folds). **It does not belong in the funnel.**
- Crediting the reserve's ~3% T-bill yield (the SGOV/IBKR cash-leg fact, memory
  `project_cash_leg_earns_sgov_ibkr_3pct`) narrows the loss but cannot flip the sign (forgone equity
  compounding ~8–11% ≫ 3%).

## Durable takeaways

- **Firewall benchmark stays time-weighted lump-sum** — DCA washes out the relative edge; don't add the mode as
  a gate (ADR 0013 basis unchanged).
- **For the operator's own savings: buy blind, monthly, deployed immediately** — time-in-market dominates the
  200-EMA discount. ^[inferred — this personal-deployment recommendation is the operator-facing distillation of
  the quant's budget-matched result]
