---
type: source
title: George Random-baseline re-validation — pre-registered decision rule (#135)
summary: Locked-before-data discriminator for re-testing George's class-deprecation against the now-seeded (#130) Random baseline. Pre-registered to prevent data-snooping the verdict.
status: complete — deprecation HOLDS (affirmatively re-confirmed)
date: 2026-06-08
related: ["[[george]]", "[[beta-delivery]]", "[[thinning-not-selecting]]", "[[the-funnel]]"]
---

# George class-deprecation re-validation — PRE-REGISTERED rule (#135)

**Status: LOCKED before any data generation (2026-06-08). Not amendable after seeing results.**
Quant pre-registration. George stays non-tradable under every outcome (independent `/strategy-screen`
G2/G4 FAIL is final); this rule adjudicates ONLY the class-deprecation reclassification of the
52-week-high anchoring *ranker* (and the downstream lowered prior on the proximity *condition*).

## Seeds
- **Random baseline: fixed contiguous list `1..30`** (declared before run; if 0-based required, `0..29` — declare which, don't switch after). Empirical p95 = 29th-largest of 30 (no interpolation, no parametric tail fit).
- **George: single seeded run** (near-deterministic; multi-seed would manufacture spurious tie-break-noise spread). 3-seed eyeball permitted but MUST NOT enter the discriminator.

## Axes (George = a point vs the 30-seed Random distribution)
`R_p50` = median, `R_p95` = 29th-largest of 30.

| Axis | Statistic | ALPHA if | BETA if | Role |
|---|---|---|---|---|
| **E** — per-trade edge | George vs 30-seed Random | `G > R_p95` | `G < R_p50` | Driver (must clear p95 to overturn) |
| **C** — blended OOS CAGR | George vs 30-seed Random | `G > R_p95` | `G < R_p50` | Driver (must clear p95 to overturn) |
| **W** — positive-window count (0–7) | George vs 30-seed Random | `G > R_p95` | `G < R_p50` | Veto/corroborator (overturn needs `G ≥ R_p50`) |
| **d** — per-window edge | K = #windows George > Random per-window p95 | K ≥ 5 | K ≤ 2 | Corroborator (overturn needs K ≥ 3) |
| win-rate, WFE | — | — | — | **EXCLUDED** (payoff-shape artifacts; descriptive-only if printed) |

If `R_p95 == R_p50` on W (small integer): `G > value` = ALPHA, `<` = BETA, `==` = INCONCLUSIVE.

## Verdict
- **READ CHANGES (overturn deprecation — George carries ranker alpha):** E=ALPHA **AND** C=ALPHA **AND** W≥R_p50 **AND** K≥3. *(All four.)*
- **DEPRECATION HOLDS (beta-delivery):** E=BETA **OR** C=BETA **OR** (W=BETA **AND** d=BETA).
- **INCONCLUSIVE → deprecation RETAINED on prior:** anything else.

## Asymmetric prior
Incumbent deprecation is the **null**; the re-run must *overturn* it, it does not start neutral. (1) #130 fixed RNG *reproducibility*, not the result's *direction* — no prior reason the sign flips. (2) Independent screen FAIL stands → no tradability upside to overturning → high bar is costless. (3) George–Hwang anchoring tested in its weakest habitat (long-only, liquidity-filtered) → an attenuated/in-bulk result is *prior-consistent with deprecation*, not evidence the effect is alive.

**Modal expected outcome:** INCONCLUSIVE→HOLDS (George in/below the Random bulk on edge & CAGR), pre-registered so a confirming result isn't re-narrated as a surprise.

## Reporting
Per axis: George's point, `R_p50`, `R_p95`, label (ALPHA/BETA/INCONCLUSIVE); K-of-7; combined verdict; explicit note that win-rate/WFE were excluded. Below-median on edge/CAGR → "deprecation AFFIRMATIVELY re-confirmed"; in-bulk → "deprecation RETAINED (inconclusive re-test)".

---

## ANNOTATION 2026-06-08 (non-substantive — discriminator logic UNCHANGED)

Quant-reviewed (universe-change consult, 2026-06-08). The discriminator (seeds/axes/thresholds/prior/verdict) is **universe-internal by construction** — George-point vs same-universe Random distribution — so universe size is absorbed identically into both sides and **none of it changes**. Only provenance + scope + seed-count are annotated below.

**1. Universe provenance.** Universe = **4,997 stocks as of 2026-06-08** (`GET /api/stocks`), grown from the ~3,100-symbol universe of the documented George table (pre the 2026-06-01 V15 full re-ingest; midgaard V15≈5,135 symbols, purged by V17 to ~5,000; stable since 2026-06-02). The original George request JSON is unrecoverable. **This is a fresh seeded re-test on the current universe, not a bit-for-bit reproduction.** Universe is frozen across the George point and all Random seeds (shared identical basket).

**2. Reproduction-gate RETIRED.** The 556-trade target is a property of the prior (~3,100) universe and is not reproducible. Replaced by skeleton-fidelity via the **universe-invariant four-metric match** confirmed in the 2026-06-08 George control run (seed 42, this universe):

| Metric | Documented (old universe) | Control (new universe, 4,997) | Role |
|---|---|---|---|
| Per-trade edge | +1.01% | +0.96% | invariant — matches |
| Win rate | 53.4% | 54.6% | invariant — matches |
| Aggregate maxDD | 51.3% | 49.6% | regime-shape — softened (right direction) |
| 2008 GFC edge | −14.3% | −12.0% | regime-shape — softened (right direction) |
| Trades | 556 | 724 | universe-scaled (expected) |
| Blended CAGR | +1.1% | +1.75% | universe-scaled (expected) |
| Positive windows | 5/7 | 6/7 | universe-scaled (expected) |

Invariant metrics match; scaled metrics moved as adding names predicts → **skeleton accepted as faithful George.** This control run IS the George point for the discriminator.

**3. Scope-of-claim.** Verdict scopes to the 2026-06-08 universe. Per the weakest-habitat caveat (anchoring lives down-cap; the liquidity filter stripped that tier), if the additions skew down-cap a READ-CHANGES outcome reads as **caveat-confirmation (scoped down-cap revival), not refutation** of the original liquid-universe deprecation — and remains **non-tradable** (independent `/strategy-screen` G2/G4 FAIL is final, universe-invariant). The added-symbol cap/liquidity characterization was deemed non-blocking (interpretive color for a pass only); recorded post-hoc as a whole-universe liquidity proxy if the outcome is READ-CHANGES.

**4. Seed count: 30 → 20 with pre-committed conditional top-up** (runtime: ~15 min/run, not 3–5 → 30 seeds = ~7.5h; stakes low — George non-tradable regardless). **Declared BEFORE generating any Random draw:**
- Random seeds = **contiguous prefix `1..20`** (so a top-up just appends `21..30`, keeping the 20-set a faithful sub-distribution of the 30-set).
- **Run 20 first.** If George's edge AND CAGR points sit **at or below the Random bulk** (≤ ~p50) → STOP at 20 (a ≤-median point cannot be flipped by tightening p95). Verdict = INCONCLUSIVE→HOLDS or AFFIRMATIVELY-re-confirmed.
- **Top-up to 30** (append `21..30`) **only if** George's point lands **within the p95 noise band on a driver axis** (E or C) — i.e. p95 becomes load-bearing. Mechanical trigger (margin-to-p95), not "if I dislike the answer."
- George remains a **single** seeded run (control, seed 42); not multi-seeded.

---

## RESULT 2026-06-08 — verdict (rule applied verbatim)

**Run provenance.** George point = control run (`nearness52WeekHigh`, seed 42, 2026-06-08 universe of 4,997). Random baseline = seeds `1..17` (sweep stopped at 17 of the planned 20 by operator request; the remaining 3 seeds cannot flip a below-floor result). All runs byte-identical skeleton, walk-forward 2005–2015 / 36-12-12, `ranker` the only swap.

| Axis | George | Random p50 | Random p95 | Random range | Label |
|---|---|---|---|---|---|
| **E** — per-trade edge | +0.96% | 1.10% | 1.73% | 0.95–1.73% | **BETA** (below median, at the floor) |
| **C** — blended CAGR | +1.75% | 7.98% | 12.65% | **6.64–12.65%** | **BETA** (below the entire distribution) |
| **W** — positive windows | 6/7 | 6 | 6 | all 6 | INCONCLUSIVE (every seed = 6/7) |
| **d** — per-window K (George > Random per-window p95) | **0/7** | — | — | — | **BETA** |

**VERDICT: DEPRECATION HOLDS — affirmatively re-confirmed** (E=BETA ∧ C=BETA ∧ d=BETA). win-rate/WFE excluded per rule. No conditional top-up (George below the Random CAGR floor; p95 not load-bearing).

**Mechanism (reproduced on the new universe).** Every selector — George and all 17 Random — clears 6/7 positive windows; the discriminating window is **2008 GFC**, where George = **−12.0%** vs Random per-window p95 = **−1.22%**. The 52-week-high anchoring tilt concentrates into the most-extended momentum names, which crater hardest in the crash → a worse-than-noise GFC liability, exactly the original finding. K=0/7 means George never beats the random selector's top tail in a single window: the ranker carries no information.

**Stronger than the 2026-06-04 read.** The original was a single unseeded Random draw ("matched edge, beat CAGR"). The seeded 17-draw distribution shows George below the *entire* Random CAGR cloud and winning no window's tail — beta-delivery confirmed with a reproducible baseline, not a single point.

**Scope.** Verdict scopes to the 2026-06-08 universe (4,997). George remains non-tradable regardless (independent `/strategy-screen` G2/G4 FAIL, universe-invariant). Class deprecation of the 52-week-high anchoring ranker STANDS; the lowered prior on the proximity *condition* STANDS. A down-cap caveat-confirmation note was not needed (outcome is HOLDS, not READ-CHANGES).
