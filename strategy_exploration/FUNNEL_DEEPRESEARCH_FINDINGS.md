# Backtesting funnel vs. best-practice quant methodology — deep-research findings

_Generated 2026-06-05 from a `/deep-research` pass over `FUNNEL_DEEPRESEARCH_BRIEF.md`. 24 sources fetched, 25 claims put through 3-vote adversarial verification (20 survived, 5 killed — kills were over-statements of phrasing, not of substance; siblings survived). **Status: research output, not yet woven into `BACKTESTING_FUNNEL.md`.** Per the brief, funnel/skill/ADR edits await a quant wording-review._

> **Sourcing note.** Findings marked **[verified]** survived 3-vote adversarial verification. Findings marked **[sourced]** come from the extracted-claim corpus but were outside the top-25 that got verified (the verify budget capped at 25) — they're single-source and should be treated as strong leads, not settled. Source quality is tagged `[primary]` (peer-reviewed / institutional), `[secondary]`, `[blog]`.

---

## The one finding that dominates everything else

**The funnel has no explicit multiple-testing / trial-count correction, and the literature is near-unanimous that this is the single most important control for a multi-candidate search.** Every primary source on backtest methodology converges here:

- The number of trials attempted is *the* decisive piece of metadata; a reported Sharpe or CAGR judged against a **fixed** bar, with no deflation for how many configurations were searched, is methodologically incomplete. **[verified, primary]** (Bailey–López de Prado DSR; GARP/LdP)
- The expected **maximum** Sharpe across N zero-skill trials grows ~√(2·log N); selecting the best of many backtests *guarantees* a high apparent Sharpe even at true Sharpe = 0. This is the formal engine behind your E1 "lottery/regime-detector". **[verified, primary]** (GARP/LdP; Bailey DSR)
- After only **7** configurations you expect to find a 2-year backtest with annualized Sharpe > 1 at *true OOS Sharpe = 0*. Roughly **20** holdout/OOS iterations on the same data make a spurious 5%-significant pass the *expected* outcome. **[verified, primary]** (Bailey-Borwein-LdP-Zhu, MinBTL; GARP/LdP)
- You have run ~10 premises × many variants on one 2000-2026 dataset. The brief's C1–C4 controls (design-isolation, dead-config, lineage) are **good hygiene but not a correction** — they reduce *deliberate* re-fitting of a known-bad window, but they do not adjust the significance bar for the size of the search. Two configs tried ⇒ overfitting is *always* present to some degree (PBO framing). **[verified, primary]** (Bailey-López de Prado PBO/CSCV)

**What a rigorous shop does differently:** compute a **Deflated Sharpe Ratio** (deflates for trial count N, cross-trial Sharpe variance, *and* non-normal skew/kurtosis), and/or run a **multiple-testing-corrected hurdle**. Harvey–Liu and Harvey–Liu–Zhu recommend **BHY (Benjamini-Hochberg-Yekutieli) FDR control** specifically for finance — Bonferroni is too stringent, BHY controls the false-discovery *proportion* under cross-test correlation. Concretely (Harvey–Liu, 240 monthly obs, 10% vol): a single-test strategy needs ~4.4%/yr; under 300 tests the BHY hurdle rises to ~7.4%/yr — **~70% higher**. New-factor t-ratio bar moves from 2.0 → **>3.0**. **[verified, primary]**

→ **Funnel implication:** add a trial-count register and a DSR/BHY-corrected gate. Your fixed **0.7 stitched-OOS Sharpe (B1)** and fixed **30% CAGR floor (B4)** are *uncorrected* bars — the literature says the bar must *rise* with the number of variants swept. Your G5 ("if >50 variants, raise G1 and require 6/7") is a crude step-function approximation of exactly this; the principled version is DSR.

---

## A. Validation-harness design

**A1 — single fixed 2005-2015 walk-forward, 7 OOS windows. → MISCALIBRATED; CPCV is the literature's preferred harness.**
- A single-path walk-forward produces **high-variance** performance estimates and exhibits **weak false-discovery prevention, high temporal variability, weak stationarity** — empirically the *weakest* of the compared OOS harnesses. **[verified, primary]** (ScienceDirect 2024 CPCV study; Wikipedia Purged-CV)
- **CPCV is empirically superior** to walk-forward and (purged) k-fold, measured by **lower PBO and higher DSR**; it yields a *distribution* of Sharpe ratios over C(N,k) train/test paths instead of one likely-overfit point estimate. **[verified, primary]** (ScienceDirect; SSRN PBO/CSCV; GARP/LdP)
- On window count: 3 is too few (one outlier dominates); **6-8 is the floor, 10-20 preferred**. Your 7 sits just over the floor. Your 36/12 IS/OOS = **3× ratio sits inside** the recommended 2-5× band. **[sourced, blog]** (stratbase)
- **Caveat the verifiers flagged:** the claim "CPCV is *the correct* remedy" was *killed* (2/3 refuted) as an over-statement — CPCV is *a* strong remedy, not the only one, and it has its own assumptions (it still needs purging+embargo, and combinatorial paths overlap so they're not independent). The *substance* — single fixed WF is inadequate — survived through multiple sibling claims. **[verified]**
- **Required if you keep walk-forward:** purging + embargoing at fold boundaries to prevent label-overlap leakage. Standard k-fold is *invalid* on time series (assumes IID). **[verified, secondary]**

**A2 — most-recent block (2021-2025) is informational/never-binding. → QUESTIONABLE; defensible only as overfit-control, not as relevance-weighting.**
- No source directly endorses down-weighting the most recent block. The CSCV/CPCV literature's whole point is that a *single fixed* OOS period (whichever calendar slice) "cannot correctly assess overfitting" because it ignores trial count — so privileging *or* demoting any one block is the wrong axis; the fix is combinatorial splits. **[verified, primary]** (SSRN PBO)
- Defensible reading: holding 2021-2025 as a never-binding sanity check **protects it from becoming a 4th tuning surface** (each binding block you can fail-and-retry against is another holdout iteration → ~20 iterations = guaranteed false pass). That's a legitimate anti-snooping motive. But the brief's worry is real: recent data is the most decision-relevant, and a regime you *never* let veto is a regime you're not validating against. **Net: keep it informational to limit iterations, but treat a *failed* Block C as a hard flag for manual review, not a silent pass.**

**A3 — per-window WF cannot validate <1-event/yr strategies ("cadence ceiling"). → SOUND, and the literature sharpens *why*.**
- Hold-out is "statistically inadequate for low-frequency / small-sample strategies" — explicitly cited rule: don't use hold-out when a strategy trades infrequently. **[verified, primary]** (SSRN PBO)
- Event-study power **collapses as the event horizon lengthens / count falls**: a 1-day concentrated abnormal return needs ~6 names for 100% detection of a 10% effect; the *same* effect smeared over 6 months needs ~200 events for even 65% detection. Long-horizon tests have low power *and* are highly sensitive to the return-model assumption. **[verified, primary]** (Kothari–Warner)
- **Important adversarial caution on the proposed fix:** pooled event-study / stationary-bootstrap / matched-sample nulls **do not** automatically rescue you. For *non-random* event samples (firms self-selecting into the event), bootstrap and pseudo-portfolio nulls fail to replicate the sample's cross-correlation structure (Mitchell–Stafford). **[verified, primary]** (Kothari–Warner) → so A3's disqualification is correct, and the "pooled event study" escape hatch is itself fragile for self-selected samples.

**A4 — no slippage/commission/borrow. → The haircut is large and quantified.**
- Unmodeled **transaction costs erode 20-50%** of gross returns; **slippage a further 10-30%**. **[sourced, blog]** (hyper-quant) — directionally corroborated by primary sources: ignoring slippage can flip a sound strategy to negative live; Sharpe from cost-free backtests is a "before-costs" figure that *must* be discounted. **[verified/sourced, primary]** (Harvey–Liu; exegy)
- Daily-bar perfect-fill backtests **structurally cannot** capture intrabar execution cost — there is no tick path to fill against. **[sourced, blog]**
- **Funnel implication:** your 30% CAGR floor is a *gross* number. A blunt but defensible haircut: knock **25-40% off gross returns** for a daily-rebalanced book before comparing to the floor, or (better) model a per-trade cost in the engine. The thinner you trade and the more concentrated the names, the smaller the haircut.

---

## B. Gate thresholds

**B1 — 0.7 stitched-OOS Sharpe; edge floor 0.10×risk; GFC maxDD ≤ 2× median. → Folk-but-reasonable as a *screen*; not a *significance* bar.**
- 0.7 corresponds to the practitioner WFE band where >0.7 = "excellent/robust", 0.5-0.7 = "acceptable", <0.3 = overfit. So 0.7 is a *high-end* screen bar. **[sourced, blog]** (stratbase; hyper-quant uses 0.5)
- **But a flat Sharpe bar is the wrong shape.** The Sharpe *haircut* for trials is **nonlinear**: ~100% for Sharpe < 0.4, but ≤25% for Sharpe > 1.0. A strategy scraping 0.7 is exactly in the zone where the trial-count haircut bites hardest — so a fixed 0.7 with no trial correction systematically over-passes marginal strategies. **[verified, primary]** (Harvey–Liu backtesting)
- The GFC maxDD ≤ 2× median multiple has **no literature anchor** found — treat as a sensible heuristic, not a calibrated number.

**B2 — cross-block edge decay edge_B ≥ 0.5×edge_A. → Right *idea*, ad-hoc *number*.**
- The principled version is **PBO**: overfitting = the IS-optimal config's *expected OOS rank* falls below the median of all configs. Your 50%-retention rule is an informal scalar proxy for "doesn't collapse OOS" without computing the probability. **[verified, primary]** (SSRN PBO) → consider reporting PBO alongside (or instead of) the 50% rule. No source validates "50%" specifically.

**B3 — parameter robustness ±1 discrete / ±10% continuous, one-at-a-time. → Direction right; ±10% has support; one-at-a-time is the weak part.**
- **±10% perturbation requiring near-equivalent performance is an explicit practitioner standard**; a large drop at adjacent settings = fragile/overfit. **[sourced, blog]** (buildalpha)
- Best practice is a **parameter *plateau***, not a point: choose a region of stable performance, don't transfer the single best IS point. **[sourced, blog]** (harbourfront)
- **One-at-a-time is the gap.** Robust-parameter work searches **joint, multi-dimensional** spaces (2-6 dims via particle-swarm), because interactions between tunables create fragility that axis-by-axis perturbation misses. **[sourced, primary/blog]** (harbourfront) → your G13's one-at-a-time ±1/±10% can pass a config that's fragile to *joint* moves. Your own ARS finding (E2) is essentially a discovered instance of this. Consider a coarse joint grid on the 2-3 most sensitive tunables.

**B4 — hard 30% CAGR floor. → Operator appetite is fine as a *preference*, but it is extraordinarily high vs. objective expected returns, and it should be risk-adjusted.**
- AQR's objective expected-return proxies put **long-run real US equity-market return in the low-single-to-high-single digits** (CAEY + ~2% real growth), at/near record lows in 2021/2024. Long-run real EPS growth ≈ 1.8% (140y) / 3.6% (40y). **[sourced, primary]** (AQR)
- A **30% nominal unlevered long-only CAGR is ~4-6× the market's objective expected return.** It is *attainable* in-sample by selection but is exactly the magnitude that the DSR/multiple-testing math says is most likely manufactured by search. **The higher your CAGR floor, the *more* you need the trial-count correction**, not less.
- Risk-adjusted is the literature-standard framing: report **MAR/Calmar and (deflated) Sharpe** as the binding gate, with CAGR as a secondary appetite filter. A 30% CAGR at 60% maxDD is not 30% of anything tradable.

**B5 — Jaccard >0.5 redundant / >0.7 near-clone, selectivity bands, ARS 2×SE. → No external calibration found; reasonable as uncalibrated defaults, which is how the brief already labels them.** No source spoke to these specific numbers; the brief's own "uncalibrated until a corpus exists" caveat is the correct posture.

---

## C. Anti-data-mining interlocks

**C1-C4 — design-isolation + dead-config + lineage. → Necessary, *not sufficient*. This is the headline gap (see top section).**
- Your controls limit *deliberate* refitting. They do **not** correct the significance bar for search size, and they do not stop the *passive* multiple-testing inflation that happens just by trying many premises against one dataset. **[verified, primary]** (Bailey-LdP; Harvey-Liu; Sullivan-Timmermann-White Reality Check)
- **White's Reality Check / Hansen SPA** is the named correct null for "is the *best* rule out of a universe better than chance?" — and when applied, the best technical rule's OOS edge becomes **not significant** at conventional levels. **[verified, primary]** (Sullivan–Timmermann–White)
- Specific method to adopt: **BHY FDR** (Harvey-Liu), or **DSR** (Bailey-LdP) which folds in non-normality. Bonferroni is too stringent. **[verified, primary]**
- One verifier *killed* the absolutist "most claimed findings in financial economics are false" (2/3 refuted as over-reading Harvey-Liu) — the defensible version is "many published factors are likely false positives; the search population is large." **[verified]**

**C5 — G14 trade-list diff by (entry_date, symbol) over 25y. → Sound; this is implementation-invariance, a different axis from data-mining and not challenged by any source.** No contradicting evidence; it's a deterministic equivalence check, appropriately strict.

---

## D. Null baselines

**D1 — Random-ranker null for selection skill. → Reasonable and aligned with the literature's logic.** Isolating the component "largely unaffected by factor exposure" to prove genuine stock-*specific* skill (vs factor/regime beta) is exactly the published method for separating selection skill from beta. **[sourced, primary]** (Financial Analysts Journal, factor- vs stock-specific momentum) Random-ranker is a clean, byte-identical-universe instance of that.

**D2 — within-regime conditional null (random entries from matched-stress population, beat p95). → Right *family* (matched-sample / characteristic-control), with one important caveat.**
- The matched-control-portfolio benchmark (subtract returns of size/prior-return-matched controls) is the **least-biased** event-study method and avoids the model-choice problem. Your within-regime null is a version of this. **[sourced, primary]** (Ahern; Kothari–Warner)
- **Caveat (verified):** matched-sample/bootstrap nulls **do not fix cross-correlation** when the "event" sample is self-selected — a random matched draw won't reproduce the covariance structure, so the p95 bar can be *miscalibrated* (over- or under-reject). **[verified, primary]** (Kothari–Warner / Mitchell–Stafford) → your D2 is the correct approach but its p95 is not automatically well-specified; the entries that fire together in a stress regime are cross-correlated.

**D3 — no buy-and-hold SPY risk-adjusted gate. → A gap worth closing.** The literature treats beating a passive benchmark on a risk-adjusted basis as a baseline, and your D1/D2 logic (strip out beta) implies it: a long-only book that doesn't beat SPY on MAR/Sharpe is delivering beta. Recommend adding an explicit "beat SPY total-return on Calmar/Sharpe over each block" gate.

---

## E. Failure-mode taxonomy — are these real, named correctly?

- **E1 lottery/regime-detector → YES, this is the expected-maximum-Sharpe phenomenon** (best-of-N inflation, ~√(2 log N)). Your "geometric compound of the lumpy sequence is the true number" is correct — and momentum's own edge is documented to concentrate in short post-regime windows. **[verified, primary]** (Bailey DSR; FAJ momentum)
- **E2 Aliased Regime Sensitivity → real phenomenon, your-coined name.** No source uses "ARS", but the *substance* — non-monotone pass/fail across a parameter neighborhood at stable trade counts ⇒ the parameter dimension is structurally inappropriate — is the **parameter-plateau / fragility** literature. Your ARS is a specific, sharp diagnostic for "no plateau exists here." Keep the name internally; cite it as a fragility/plateau-absence instance. **[sourced, blog]** (harbourfront; buildalpha)
- **E3 participate-and-lose in narrow leadership → consistent with momentum-crash literature** (regime-dependent tail; long-only avoids the worst long-short crashes but still carries the regime exposure). **[sourced, secondary]** (alphaarchitect)
- **E5 capital-aware ablation confound → no external name found**, but it's a sound internal observation; treat as a house diagnostic, not a literature term.

---

## F. Session additions

**F3 — validate a regime indicator's distribution before gating on a level. → Generalizes to a sound, literature-consistent lesson.** The CPCV/stationarity literature's core worry is non-stationary, regime-shifting financial data; gating on a *level* of an indicator whose distribution you haven't characterized is exactly the kind of naive assumption those methods warn against. Your breadth-oscillator finding (mean ~42, ≤15% ≈ 7th pctile, touched yearly) is a concrete, correct instance. Make "characterize the indicator's distribution first" a standing pre-condition.

**F4 — offline fixed-sleeve two-curve blend for a multi-strategy book. → Acceptable as a first-order approximation; not a substitute for true portfolio backtesting.**
- Fixed-sleeve blending ignores rebalancing, cross-sleeve correlation drift, and dynamic exposure. The literature's defensive-overlay result that matters: **volatility-scaling / dynamic exposure roughly doubles momentum's Sharpe** and is the validated way to manage a regime-dependent sleeve — which a *fixed* blend cannot capture. **[sourced, secondary]** (alphaarchitect) → for go/no-go on a defensive sleeve, a fixed blend can rule a sleeve *out* (if even the optimistic static blend doesn't help) but cannot confirm it *in*; that needs joint simulation with the actual rebalance rule. Long-only ⇒ defense=cash (ADR 0010) limits this anyway.

---

## G. Open hypotheses

**G1 — "narrow leadership kills cross-sectional RS-momentum" (assumed). → The literature suggests this is *not* obviously true and may partly invert.**
- Cross-sectional momentum crashes are **regime-dependent tail events concentrated in bear-market reversals** (when the loser leg's negative beta snaps back) — *not* a general "narrow leadership" death. **Long-only** momentum **avoids** the deep crashes (no short leg). **[sourced, secondary]** (alphaarchitect)
- Momentum decomposes into **factor-momentum** (the part that fares poorly) and a durable **stock-specific** component. Narrow leadership is a *factor*-concentration phenomenon — so it would hit the factor-momentum part, while the stock-specific part can persist. **[sourced, primary]** (FAJ)
- → **Your G1 analogy is unverified for a reason: it's probably too strong.** Narrow leadership can *feed* momentum persistence in the leaders even as it punishes breadth-dependent variants. This deserves an actual run before being treated as settled, exactly as the brief flags.

**G2 — premise-space partition (price-derived state / event-date underreaction / flow-calendar / defensive overlay) — complete? → Largely covers it; the literature confirms two of your classes as durable and hints at one more.**
- **Event-date underreaction is confirmed durable**: investors *underreact* to firm-specific public info (earnings announcements) but *overreact* to systematic-factor info — so earnings-drift-type premises are a genuinely distinct, factor-robust class. **[sourced, primary]** (FAJ)
- **Stock-specific (idiosyncratic) momentum** that does *not* long-run reverse is a distinct durable source separate from factor/price-state momentum — arguably a 5th class your partition collapses into "price-derived state." **[sourced, primary]** (FAJ)
- No source surfaced a proven long-only daily premise class *outside* your four — so the partition looks reasonably complete, with the caveat that "price-derived state" is hiding a real factor-vs-idiosyncratic split worth separating.

---

## Concrete recommendations (priority order)

1. **Add a trial-count register + Deflated Sharpe Ratio gate** (or BHY-FDR-corrected hurdle). This is the dominant, most-supported gap. Your fixed 0.7 Sharpe / 30% CAGR bars should *rise* with variants swept; G5 is a crude step toward this — replace with DSR. **[verified, primary, high-confidence]**
2. **Quote results net of a cost haircut** (~25-40% off gross, or model per-trade cost). The 30% floor is currently gross. **[primary/blog, high-confidence]**
3. **Report PBO** alongside the B2 50%-retention rule; consider CPCV (with purge+embargo) as the harness, or at minimum acknowledge the single-fixed-window variance limitation. **[verified, primary]**
4. **Make B3 robustness joint, not one-at-a-time**, on the 2-3 most sensitive tunables; frame as "plateau exists?" ARS (E2) is your discovered failure of this. **[blog, medium]**
5. **Add an explicit "beat SPY total-return on Calmar/Sharpe per block" baseline gate (D3).** **[medium]**
6. **Re-frame B4 as risk-adjusted (MAR/Calmar/deflated-Sharpe) primary, CAGR as appetite secondary.** 30% unlevered long-only is ~4-6× objective expected market return — defensible only with a correction-hardened edge. **[primary, medium]**
7. **Run G1 before relying on it** — the "narrow-leadership twin" death is probably overstated; narrow leadership may feed leader-momentum. **[primary/secondary, medium]**
8. **Calibrate D2's p95** for cross-correlation among co-firing regime entries; the matched-sample family is right but the null variance is not automatically well-specified. **[verified, primary]**

---

## Killed / unverified claims (transparency)

- *Killed (2/3 refuted, over-statement):* "most claimed findings in financial economics are false"; "CPCV is THE correct remedy"; "single-path WF easily overfit ⇒ CPCV" (absolute phrasing). In every case a softer sibling claim survived — the substance holds, the absolutism didn't.
- *Unverified (abstentions, no quorum — not adjudicated):* "number of trials is the single most important info"; the t-ratio 2.0→3.0 multiple-testing claim (though a sibling NBER version of the same point *did* verify).

## Sources (24 fetched)
Primary/peer-reviewed: Harvey–Liu "...Backtesting" (Duke P120); Harvey–Liu–Heqing Zhu "...and the Cross-Section of Expected Returns" (NBER w20592); Bailey–López de Prado "Deflated Sharpe Ratio"; Bailey-Borwein-LdP-Zhu "Pseudo-Mathematics / MinBTL" (SSRN 2308682); López de Prado "PBO/CSCV" (SSRN 2326253); Sullivan–Timmermann–White "Data-Snooping / Reality Check"; Kothari–Warner "Econometrics of Event Studies" (BU); Ahern "Sample Selection and Event Study Bias" (USC); AQR "Objective Expected Returns"; FAJ "Factor vs Stock-Specific Momentum" (2025); ScienceDirect CPCV comparison study (2024); GARP/López de Prado backtesting whitepaper. Secondary: Wikipedia Purged-CV / Walk-Forward-Optimization; alphaarchitect cross-sectional momentum. Blog/practitioner: stratbase, hyper-quant, exegy, buildalpha, harbourfront (used only for practitioner-default thresholds, flagged inline).
