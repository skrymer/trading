---
type: concept
title: The Backtesting Funnel
summary: The 5-stage map from idea to tradable/rejected — condition-screen → strategy-screen → validate-candidate → promotion/G14 → monte-carlo.
status: stable
tags: [methodology]
sources: ["strategy_exploration/BACKTESTING_FUNNEL.md"]
related: ["[[component-firewall]]", "[[aliased-regime-sensitivity]]", "[[parameter-robustness-g13]]"]
updated: 2026-06-05
---

# The Backtesting Funnel

How a strategy idea becomes a tradable (or rejected) candidate. Each stage has a skill, an analyst,
a fixed window, and an artifact. Authoritative detail lives in the skills + ADRs; this page is the
map. Full source: `strategy_exploration/BACKTESTING_FUNNEL.md`.

```
 idea
  → /condition-screen     design-time pre-screen of ONE condition (lift, firing rate, ARS, regime, overlap). No verdict.
  → /strategy-screen      fast 10y (2005-2015) walk-forward triage, relaxed gates G1-G5. "Worth a full run?"
  → /validate-candidate   3-block firewall (binding) + Block C informational. Strict v4 + G10/G11/G13.
  → promotion + G14       inline script → first-class condition, then trade-list diff.
  → /monte-carlo          path risk / risk-of-ruin before sizing up.
  → 25% CAGR floor        final go/no-go [operator].
```

## Stages

| Stage | Answers | Window | Analyst |
|---|---|---|---|
| `/condition-screen` | Is this *condition* structurally sound? | design-safe (excl. Block C; ADR 0007) | `condition-screen-analyst` |
| `/strategy-screen` | Worth a full firewall run? | 2005-2015, 36/12/12 → 7 OOS windows | `strategy-screen-analyst` |
| `/validate-candidate` | Is the edge real OOS, all regimes? | A 2000-14 · B 2014-21.5 · 25y · C 2021-25 | `firewall-analyst` |
| promotion + G14 | Does the promoted condition reproduce the trades? | 25y trade-list diff | — |
| `/monte-carlo` | Path / ruin risk? | resampled paths | `monte-carlo-analyst` |

## Disciplines that span the funnel

- **Leakage boundary (ADR 0007):** the condition screen's `endDate` is hard-capped at Block C's start
  (2021-01-01) so eyeballing it can't leak the only true out-of-sample block.
- **G-RANDOM baseline** is binding for permissive-entry + ranker-selects candidates — see
  [[thinning-not-selecting]].
- **Conditional within-regime NULL** replaces G-RANDOM for *timing* candidates — see [[gjallarhorn]].
- **Dead-config refusal (ADR 0008):** a REJECTED `config hash` and its ±1 neighbours are dead;
  re-running them is data-mining, hard-refused by the exploration state-machine.
- The **screen is triage, not a verdict** — survivors are candidates, not winners; always flag
  survivors below the 25% CAGR floor.

## Why a funnel at all

Each stage is cheaper than the next and rejects on a different axis (structural soundness → fast OOS
triage → full multi-regime validation → implementation fidelity → path risk). The point is to spend
~80-minute firewall runs only on ideas that already survived cheap structural screens — and to make
every rejection teach something reusable (the failure-mode concept pages).

## Related

[[component-firewall]] · [[aliased-regime-sensitivity]] · [[lottery-vs-signature]] · [[crisis-timer-cadence-ceiling]]
