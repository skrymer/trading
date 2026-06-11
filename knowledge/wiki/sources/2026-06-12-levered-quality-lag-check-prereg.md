---
type: source
title: Levered-quality circuit-breaker — lag-check PRE-REGISTRATION (locked before data)
summary: Pre-registered pass/fail (L1-L4) for the empirical lag check that gates the levered long-quality + washout circuit-breaker build. Locked 2026-06-12 BEFORE any breadth/price query. Quant-authored.
status: stable
tags: [candidate, fundamentals, quality, leverage, circuit-breaker, pre-registration]
sources: ["knowledge/wiki/sources/2026-06-12-validate-quality-profitability-tilt.md", "docs/adr/0010-crisis-defense-is-an-allocation-state-not-a-long-only-defender-component.md", "docs/adr/0015-absolute-calmar-floor-and-g9-sharpe-recalibration.md"]
related: ["[[quality-profitability-tilt]]", "[[gjallarhorn]]", "[[the-funnel]]", "[[participate-and-lose]]"]
updated: 2026-06-12
---

# Levered-quality circuit-breaker — lag-check PRE-REGISTRATION

**Locked 2026-06-12, before any breadth or price query was run.** This pins the pass/fail for the empirical
lag check that gates the build of the **levered long-quality + book-level washout liquidate-to-cash
circuit-breaker** premise (the path forward after [[quality-profitability-tilt]] was REJECTED at Block A —
real ~17.8% CAGR / ~0.9 Calmar unlevered edge, structurally below the 25%/1.5 floor; leverage for G1 CAGR +
washout-cash for the G15 Calmar denominator is the only theory with a path to both floors).

## What the build is gated on

The circuit-breaker mechanism is **(B) book-level liquidate-to-cash** (close the whole book to cash when the
washout fires; net-new engine code) — not an entry-gate, which would re-run the 2008 failure with leverage
amplifying it. The frozen washout trigger (`sustainedWashoutActive`, `LeadershipRegimeParams.FROZEN`: ≤15%
breadth for ≥10 consecutive readings within trailing 40) fires ~10 trading days into a crash. The existential
risk: a **1.5× levered** book takes the pre-trigger decline at amplified size before going to cash, and a
*fast* crash may bottom before the trigger fires (liquidate-at-the-lows). The lag check measures whether the
frozen trigger fires early enough that a 1.5× book survives the firewall drawdown caps in 2008 and 2020.

## Pre-registered pass/fail (quant-authored — do not move after seeing data)

- **L = 1.5** (top of the scoped 1.4–1.5× band). If 1.5 fails but 1.4 passes, that is a **finding to report,
  not license to ship 1.4** — re-registration + re-grill required (picking the leverage that passes after
  seeing the result is data-snooping).
- **Binding number = the worse (more negative) of SPY and a quality basket** (equal-weight of the
  `qualityPercentile ≥ 80` names going into each crash). SPY = conservative bound; quality basket = the real
  book.
- **Trigger date T** = first date `sustainedWashoutActive` is true under FROZEN params, from the stored
  `MarketBreadthDaily` series.
- **D_pre** = peak-to-T drawdown of the proxy equity path × L. **D_trap** = peak-to-trough × L.
- **±2 percentage points of any threshold → FAIL** (the proxy approximates the real levered book; margin on
  the conservative side).

**PASS requires ALL of:**

| # | Criterion | Threshold | Maps to |
|---|-----------|-----------|---------|
| **L1** | D_pre_levered (2008) | ≥ −20% | G3 worst-window DD ≤ 20% |
| **L2** | D_pre_levered (2020) | ≥ −20% | G3 worst-window DD ≤ 20% |
| **L3** | max-episode D_pre_levered | ≥ −25% | G2 portfolio DD ≤ 25% |
| **L4** | T fires AND T ≥ 5 trading days **before** each episode's trough | true for both | fast-crash lock-in guard |

**L4 is the kill-switch.** If T lands at/after the episode trough (the fast-crash lock-in case; plausibly
COVID), the circuit-breaker liquidates into the hole and (B) is actively harmful. **If L4 fails for COVID
2020, the premise is timing-dead at 1.5× and the build does NOT proceed** — no "add a faster overlay" rescue
(re-tuning the frozen trigger is forbidden). **1987 is out of measurable scope** (breadth trust floor is
2000-01-01) — an acknowledged un-hedged tail, not a gate input.

## What a PASS authorizes / does not

A PASS authorizes **building the (B) subsystem and validating it through the normal firewall**. It does
**NOT** pre-clear G1/G15 — a lag-surviving book still has to clear 25% CAGR / 1.5 Calmar with the leverage
borrow-cost drag and the cash-drag during washout-cash spells modeled. The lag check is necessary, not
sufficient.

## Result — FAIL (premise timing-dead). NO-GO; build does not proceed.

Run 2026-06-12 against the locked criteria above. **The frozen washout trigger fires ~10 trading days into
each crash, at ~−18% to −20% drawdown — too late** to protect a levered (or even unlevered) book whose G3
cap is −20%.

| Crash | Trigger T | DD at T (SPY / quality) | worse-of D_pre ×1.5 |
|---|---|---|---|
| 2008 GFC | 2008-07-10 | −18.7% / −17.5% | **−28.0%** |
| 2020 COVID | 2020-03-09 | −18.9% / −19.7% | **−29.5%** |

| Gate | Binding value | Threshold | Verdict |
|---|---|---|---|
| L1 (2008 D_pre ×1.5) | −28.0% | ≥ −20% | **FAIL** |
| L2 (2020 D_pre ×1.5) | −29.5% | ≥ −20% | **FAIL** |
| L3 (max D_pre ×1.5) | −29.5% | ≥ −25% | **FAIL** |
| L4 (T ≥5 days before trough) | 2008: 166d, 2020: 10d | true | PASS |

**L4 PASSES** (the trigger leads the trough in both — it is *not* the fast-crash liquidate-at-the-lows
failure). The kill is the *other* mode: the trigger is **structurally too slow**. By the time the sustained
washout is confirmed (≤15% breadth for 10 consecutive days), the market is already down ~19% — past the G3
cap *unlevered* (within the ±2pp margin), and ~−28% at 1.5×. Even L=1.0 fails on 2020 (D_pre −19.7% within
2pp of −20%); L=1.4 fails too. **The premise is timing-dead at any tradable leverage.**

**Durable insight:** the sustained-breadth-washout classifier is a **crisis-*bottom* detector, not a
crisis-*avoidance* circuit-breaker.** It fires *near bottoms* — which is exactly why [[gjallarhorn]] used it
as a +22σ bottom-*timer* and why it is useless as a top-*exit* for a deployed book (you need to be in cash
*before* the −19%, not confirm it 10 days in). No faster signal is available without re-tuning the frozen
params (forbidden). This also retro-explains why no washout overlay could have rescued the unlevered
[[quality-profitability-tilt]] in 2008.

**Disposition:** the quality premise is **fully exhausted as a tradable candidate** — unlevered it is below
the 25%/1.5 floor ([[quality-profitability-tilt]] REJECTED), and the only return-lifting path (leverage +
washout-cash) is timing-dead here. The quality gate + `FundamentalQualityRanker` + deterioration exit remain
**shelved, validated assets**. Next: a fresh premise class.
