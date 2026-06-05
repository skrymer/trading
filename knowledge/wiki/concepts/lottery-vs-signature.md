---
type: concept
title: Lottery vs Signature
status: stable
tags: [failure-mode]
sources: ["feedback_lottery_screen_diagnostic", "feedback_conditional_within_regime_null"]
related: ["[[crisis-timer-cadence-ceiling]]", "[[participate-and-lose]]", "[[thinning-not-selecting]]"]
updated: 2026-06-05
---

# Lottery vs Signature

A candidate whose attractive headline edge is **concentrated in 1-2 OOS windows** is a *regime detector*,
not a *trade selector* — a lottery. The geometric compound of its lumpy window sequence is the true
number, and it is small. Reject at the screen stage, without iteration.

## Definition

The entry has alpha in only ~25-30% of tape regimes; the rest is trade-everything-hope-a-strong-regime-
shows-up = regime beta on a factor. The blended/arithmetic edge looks fine because two huge windows carry
it; the **geometric** compound (what you actually earn) is dragged to the floor by the negative windows.

## How to detect (reject at `/strategy-screen`)

- Headline per-trade edge looks attractive (> 1.0%/trade) **BUT**
- edge is concentrated in 1-2 of N windows (e.g. 2 of 7 carry > 8% while the other 5 are < 1.5%), **AND**
- **5+ of N windows are negative by realized CAGR** (not edge — CAGR), **AND**
- path is lumpy: maxDD ≥ 25%, Calmar < 0.5, Sharpe < 0.7.

**Worked example — MJV-s1 (2026-05-28):** aggregate edge +2.50%, but 2009 (+49.7% CAGR) and 2013
(+50.8%) carried five negative-CAGR years (2008 −7, 2010 −3, 2011 −9, 2014 −18, W3 −3). Geometric compound
of `{+50, −9, −7, +7, +51, −18, −3}` ≈ **7.43%/yr** — that *is* the strategy, not "CAGR left on the table
from under-sizing." Operationally **unholdable**: 5 of 7 negative years = real-money capitulation before
the next payday window.

## Why it kills — and why not to rescue it

Do **not** attempt: a **sizer sweep** (variance-mining; bigger size on a lumpy engine ruins the account),
a **faster exit** (destroys the rare big runners that produce all the CAGR), or a **slower exit** (already
what produced the lumpiness). The only real fix is **explicit regime gating in the entry** — but at that
point you're designing a *new* candidate from scratch. File the original REJECTED and start over.

## The signature test for timing candidates — the conditional within-regime NULL

A *timing* rule (selects *when* to be in the market, not *which* names) is judged against a **conditional
within-regime NULL**, not the standard Random-ranker baseline: random entry days drawn **only from the
comparably-stressed population** (e.g. `breadthPercent ≤ 25`) at the candidate's matched firing rate,
everything else byte-identical, 20 seeds → a distribution.

- A **uniform-random** null lands mostly in calm/bull tape, so beating it only proves "crisis-recovery
  days ≠ average days" = **crisis beta** (a false GO).
- The within-regime null makes both arms "buy dips"; beating it proves the rule's *specific* dip
  selection adds value = genuine **timing alpha** (a true signature).
- Primary metric: **per-trade edge > null p95** (≥~2σ); confirm with blended CAGR > null median.

[[gjallarhorn]] passed at **+22σ** (edge +2.19% vs null mean −0.17%, all 20 seeds negative) — random
same-regime dip-buying *loses* (catching falling knives) while sustained-washout-then-recovery timing
makes money. That is the lottery's opposite: a signature.

## Related

[[crisis-timer-cadence-ceiling]] · [[participate-and-lose]] · [[thinning-not-selecting]] · [[gjallarhorn]]
