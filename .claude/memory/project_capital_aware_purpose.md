---
name: Capital-aware engine purpose
description: The capital-aware trade selection feature (commit 15c9fe2) exists specifically to surface real-world capital constraints in backtests. Not an optimization — a correctness fix.
type: project
originSessionId: a1232822-4edf-4931-a833-5f468c42f49d
---
The `feature/capital-aware-selection` branch introduced capital-aware trade selection so backtests reflect what a real trader can actually achieve with their capital. Before this, the engine implicitly assumed unlimited leverage on signal-dense days.

**Why:** A real trader at $10K with 1.5% risk / 2.0 ATR fits 6-7 concurrent positions, not 15. Previous backtest results overstated what could be traded, producing inflated CAGR/MDD/edge metrics relative to what a real account would experience.

**How to apply:**
- Results diverging from pre-merge baselines are not regressions — they're corrections.
- The VCP plan's "15 max positions" guidance predates this fix; effective position count is structurally capped at 6-7 under current sizing (1.5% risk / 1.0 leverage) regardless of starting capital.
- Instrumentation added 2026-04-17 (`EntryDecisionContext` on Trade, `/api/backtest/{id}/missed-trades` endpoint) surfaces cash, open notional, cohort rank, and capital skips for post-hoc validation.
- Walk-forward / MC results under the new engine validate the strategy a real trader actually runs, not an idealized version.
