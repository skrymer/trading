---
name: regime-conditional-portfolio-framework
description: "Strategic framework for the trading platform's edge-search direction (quant-verified 2026-05-28). Search for individual regime-specialist strategies, not an uber-strategy. Once ≥3 components pass a relaxed regime-conditional bar with low cross-correlation, design a portfolio-level firewall on top."
metadata: 
  node_type: memory
  type: project
  originSessionId: 7571f7f6-71bf-476b-8e88-d8d2a44467a6
---

**Frame**: regime-conditional multi-strategy portfolio. Quant-verified 2026-05-28 after VZ3-s3 firewall completion (PASS A+B, FAIL C edge sign-flip in 2024 narrow-leadership regime).

**Why**: Uber-strategy search has hit diminishing returns. An uber-strategy requires `E[edge | any regime] > 0` for a premise rich enough to have edge in one regime — that set is approximately empty. VCP exists because momentum-breakout has weak-positive edge across most regimes; going higher than VCP requires orthogonality (regime-specialists), not depth (more conditions on a single premise).

**How to apply**:

### Component bar (relaxed but firm)
Each candidate strategy must:
1. Pass its target regime block with all 10 gates (cleanly, not borderline)
2. Be flat-or-better outside target regime (DD ≤ 1.5× in-regime DD)
3. **G6 crash survival non-negotiable** — every component must survive 2008 + 2020 standalone (classifier confidence is lowest in crises)
4. Edge-stability within target regime (no sign-flips across sub-periods of target regime)
5. Design-isolation — no reusing the same feature set with different thresholds across components

VZ3 likely passes this bar despite v4 REJECTION (Block A+B cleared in target regimes; Block C is "out of regime" not failure). MR3 wouldn't (in-regime Block A failure).

### Transition threshold to portfolio design
Need **all four** met before designing the portfolio:
1. ≥3 components passing relaxed regime-conditional bar (VCP counts as one)
2. Pairwise in-regime correlation ≤ 0.3, stressed-period correlation ≤ 0.5
3. Combined regime coverage ≥ 70% of trading days
4. Regime classifier passes its own v4 firewall

Currently: **1/4 met (VCP only)**. Stay in component-search mode.

### Search direction (regime gaps to fill)
Underrepresented regimes — priority order:
1. **Narrow-leadership / mag-7-concentrated trending tape** (where VZ3 died — highest-value gap, also hardest)
2. **Range-bound / low-breadth chop** (where VCP underperforms but doesn't bleed)
3. **Crisis / high-volatility tape** (anti-correlation value — defensive component with mediocre edge but stress-period protection)

**Stop firing candidates that target trending broad-participation** — VCP already covers that regime; more there is duplication, not diversification.

### Portfolio-level firewall (5 additional gates beyond component firewall)
When ready:
- **G_DD_joint**: sum-of-components DD ≤ 1.3× max-single-component DD
- **G_transition**: 20-day post-regime-switch return ≥ 0
- **G_classifier_robustness**: portfolio Sharpe drop ≤ 25% across alt classifier
- **G_correlation_stress**: pairwise correlation in drawdown periods ≤ 0.6 (even if full-sample ≤ 0.2)
- **G_capacity**: top-edge symbols shared across components ≤ 40%

Anti-patterns banned: weight-tuning to smooth equity curve, hindsight-fit regime boundaries, per-block component exclusions, classifier ensembles.

### Regime classifier is itself a strategy
Full v4 firewall before deployment. No "tune within portfolio firewall" — that creates circular optimization where classifier and weights co-adapt to historical data. Classifier needs: block-by-block accuracy validation, lag measurement, hysteresis tuning under single-OOS discipline.

### Realistic Sharpe expectation
Textbook says 3 uncorrelated Sharpe-1.5 → Sharpe-2.6. After haircuts (crisis correlation creep, classifier lag, capacity overlap, operational drag, backtest-to-live Sharpe overstatement): **live Sharpe 1.3-1.6**. The real gain is drawdown reduction and tail-risk reduction, not headline Sharpe. For a single-operator deployment that's the right trade.

### When this framework is wrong
If after ~6 more candidate runs targeted at the regime gaps you have <2 additional survivors, the honest answer is "regime-component-portfolio frame isn't producing components fast enough — stay with VCP solo and revisit in 6 months with different premises." Don't lower the bar to manufacture components.

Related: [[mean-reversion-pullback-known-weakness]], [[use-strategy-screen-for-scanning]], [[min-cagr-tradable]].
