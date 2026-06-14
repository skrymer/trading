---
type: source
title: Regime classification v3 — deep-research findings (methods, discriminating axes, pre-registrable designs)
summary: Deep-research — return+vol models can't see NARROW; split GRIND/NARROW/CHOP via a cross-sectional concentration axis + a multi-week trend-efficiency axis, validated vs RW-surrogate + frozen-OOS.
status: seed
tags: [methodology, regime, classifier, pre-registration, deep-research, breadth, trend-efficiency]
sources: ["knowledge/wiki/queries/regime-classification-v3-research-brief.md", "docs/adr/0024-regime-read-out-v2-accepted-with-limitations.md", "docs/adr/0023-regime-read-out-v2.md"]
related: ["[[regime-classification-v3-research-brief]]", "[[regime-read-out]]", "[[strategy-assessment]]", "[[aliased-regime-sensitivity]]"]
updated: 2026-06-14
---

# Regime classification v3 — deep-research findings

The answer to the [[regime-classification-v3-research-brief]] prompt: *how to pre-registrably classify
THRUST / CRISIS / GRIND / NARROW / CHOP from daily US-equity data back to ~2000, and especially how to
resolve the GRIND/NARROW/CHOP trichotomy the v2 daily axes could not separate (ADR 0024).*

## How this was produced (provenance + verification)

Run via the `/deep-research` harness: 5 search angles → 20 sources fetched → 92 claims extracted →
adversarial 3-vote verification (≥2/3 refutes kills a claim) → synthesis. The first pass hit the
Anthropic session limit mid-verification, abstaining 7 trend/chop-axis claims and killing the synthesis
step; a small failure-tolerant finish-run then re-verified those 7 (all **confirmed 3-0**) and
synthesized. **No claim used below was killed by abstention** — the 20 in [Appendix A](#appendix-a) each
carry a real adversarial verdict.

Provenance discipline (per `knowledge/CLAUDE.md`): the **[Synthesis](#synthesis) section is Claude's
cross-cut `^[inferred]`** over the adversarially-verified base — its rankings, designs, and
recommendations are inference, not source statements. The **appendices are the source-backed layer**
(each claim cited + vote). Individual inline citations in the synthesis point at verified claims; the
*conclusions drawn from them* are the inference.

Headline numbers: 20 confirmed claims · 5 adversarially refuted · 20 sources (10 primary academic,
4 secondary practitioner, 6 blog).

---

<a name="synthesis"></a>
## Synthesis: pre-registrable five-state regime classification (GRIND/NARROW/CHOP is the binding problem) `^[inferred]`

> ⚠ **CORRECTION 2026-06-14 (post-grill, 3 quant reads) — Design 1's "Axis A" is overturned.** The
> recommendation below to resolve **NARROW** with a cross-sectional **dispersion** axis (top−bottom-half
> return) does **not** survive review and must not be built. It is cap-blind (a small-cap surge and a
> mega-cap surge give identical dispersion but opposite regimes — cap-identity *is* the NARROW signal),
> ~1.60σ-collinear with the full-universe stdev CONTEXT.md ruled out 2026-06-03 (a finding this report
> never engaged), and already shown *fail-blind* on our own data. The synthesis's other half — the
> **multi-week trend-efficiency axis** (Choppiness/Kaufman ER) for **GRIND-vs-CHOP** — stands and is the
> v3 core. **NARROW** instead needs a **cap-weighted top-N return-concentration** axis over a point-in-time
> **top-N-by-cap** universe (the count-equal STOCK universe is structurally blind to mega-cap concentration);
> it depends on a new market-cap primitive (issues #173 / #174). See the corrected design on issue #168 and
> in [[regime-read-out]]. The appendix claims (cap-blindness aside) are unaffected — the error is in the
> *inference* that dispersion isolates NARROW, not in the underlying source claims.

The decisive finding across the evidence base: **CRISIS and THRUST are a solved problem** (return+vol
separates them — MDPI JRFM 13/12/311, LSEG market-regime-detection), and **the entire research risk
lives in the GRIND/NARROW/CHOP trichotomy**, which no single axis resolves. The trichotomy is not
one-dimensional — it requires *two orthogonal* strategy-blind axes plus an explicit precedence rule. Any
design that tries to manufacture all three from a single time-series model is structurally doomed.

### (a) Ranking of axes/methods by power to resolve GRIND/NARROW/CHOP

**Tier 0 — cannot resolve the trichotomy at all (use only as the CRISIS/THRUST backbone or as validation):**

- **HMM / HSMM / Markov-switching on index returns (+vol).** These have **no information channel to
  NARROW** — NARROW is a cross-sectional/breadth fact, and a model fed only index-level returns is
  structurally blind to it (SSRN 3678004, explicit; MDPI 13/12/311 — a 3-state Gaussian HMM recovers
  low-vol bull≈GRIND, extreme-vol bear≈CRISIS, high-vol "kangaroo"≈CHOP, but **no NARROW**; LSEG —
  GMM/HMM/agglomerative on price-return features collapse to **two** states). They also flip-flop at
  daily/weekly frequency and fail to extract low-frequency trend (Toronto tecipa-369). **Verdict:
  backbone for CRISIS/THRUST, not a trichotomy classifier.**
- **Rule-based turning-point dating (Pagan & Sossounov / modified Bry-Boschan; ScienceDirect
  S0275531921002245).** Canonical strategy-blind, pre-registrable — but resolves **exactly two states**
  (bull/bear), zero resolving power on sub-structure. **Verdict: it is the bull/bear spine and the
  ground-truth lens, never the trichotomy splitter.**

**Tier 1 — load-bearing and irreplaceable:**

1. **Cross-sectional concentration/breadth axis → the ONLY axis that isolates NARROW.** Two computable forms:
   - **Cap-weighted minus equal-weighted spread** (RBC Wealth Mgmt: ~32% cap-over-equal over the 3y
     through 2025, exceeding the ~31% late-1990s narrowing — a calibrated, episode-anchored gauge).
   - **Cross-sectional return dispersion** = top-half-constituent mean return minus bottom-half mean
     (Morgan Stanley IM: Russell 1000 2019 = 52.2% − 8.5% = 43.7pp). Distinct from the SPY-minus-median
     gap and **constructible from constituent prices alone** — preferred for clean 2000-coverage.
2. **Multi-week trend-efficiency/persistence axis → splits GRIND (efficient up-drift) from CHOP
   (directionless).** Must be measured over a **multi-week window**; daily Hurst is essentially white
   noise (macrosynergy: DAX H=0.54 on 1-day returns vs 0.82 on 50-day). Best candidates, in order:
   - **Kaufman Efficiency Ratio** = net change / Σ|bar-to-bar change| over N (quantifiedstrategies.com)
     — 0–1, directly the GRIND/CHOP construct; empirically discriminates regimes
     (alvarezquanttrading.com), though sign must be *tested* not assumed.
   - **Choppiness Index** (eodhd.com) — literature-fixed thresholds (≥61.8 ranging, ≤38.2 trending) are
     *ideal for pre-registration* (no fitted cutoff).
   - **Hurst at multi-week horizon** (Springer 40854-022-00394-x: >0.5 trending, <0.5 mean-reverting,
     ≈0.5 RW) — theoretically distinct (persistence) but operationally redundant with ER; keep as a
     confirming, not primary, read.

**Tier 2 — supporting trend-strength reads (complement, don't replace Tier 1.2):** **ADX**
(fractalcycles.com: >25 strong, <20 weak/absent) measures directional *strength* not persistence — a
useful third coordinate to disambiguate weak-but-directional from strong-but-choppy, but not a
substitute for efficiency.

### (b) The 2–3 most promising pre-registrable designs

**Design 1 (RECOMMENDED) — deterministic two-axis decision table layered over the CRISIS/THRUST backbone.** `^[inferred]`
- *Axis A (concentration):* cross-sectional dispersion from constituent returns (preferred) and/or
  RSP-vs-SPY spread. *Axis B (efficiency):* Choppiness Index or Kaufman ER on SPY over a 20–60-day window.
- *Decision table, with explicit precedence* CRISIS > THRUST > NARROW > {GRIND | CHOP}: up-tape & high
  concentration → NARROW; else up-tape & efficient → GRIND; else up-tape & inefficient → CHOP.
- *Data:* SPY OHLCV (vol/efficiency, 2000+); point-in-time index constituents + market cap (dispersion &
  cap-vs-equal, 2000+); RSP from 2003 (optional corroborant). All EODHD-sourceable.
- *Why:* fully deterministic, every threshold pre-registrable, strategy-blind, and each state lives on
  the axis that *can* express it. Choppiness' fixed 38.2/61.8 thresholds remove a degree of fitting
  freedom outright.

**Design 2 — hybrid: HMM/HSMM backbone for CRISIS/THRUST + cross-sectional overlay for the trichotomy.**
Use the time-series model only for what it provably does (vol/return → CRISIS/THRUST), then split the
residual up-tape mass with Axes A+B. Respects the no-breadth-channel limit. **Fix K a priori from theory
— do NOT select K by information criterion** (arXiv 2308.04374). More moving parts ⇒ more overfit
surface than Design 1.

**Design 3 — validation-first spine: Pagan-Sossounov bull/bear chronology, then label bull phases by
Axes A+B.** Most defensible provenance (the spine is *the* canonical strategy-blind generator),
trichotomy reduces to phase-labeling. Slightly coarser timing than Design 1.

**Validation approach (cross-cutting, mandatory):**
- **External rule-based dating as ground truth** (Toronto tecipa-369): simulate surrogates, apply
  Bry-Boschan **and** Lunde-Timmermann to both simulated and real SPY/CRSP series, flag where the
  model's 0.70 density interval misses the real statistic.
- **Random-walk benchmark gate** (Wiley JAE 10.1002/jae.664): a pure RW reproduces bull/bear features —
  so the trichotomy axes must demonstrably carry information a calibrated RW surrogate does **not**,
  before any state is declared real.
- **Frozen-parameter OOS** (arXiv 2601.05716): estimate every threshold through a cutoff (e.g.
  2020-12-31), apply unchanged to 2021–2025; require **label stability**, not downstream P&L (that
  paper's regime-conditional framework collapsed OOS, Sharpe −1.65 / MaxDD −48%, precisely from fitting
  sample-specific regime characteristics into non-stationarity).

### (c) Overfit-traps to avoid

- **Random-walk artifact** (Wiley JAE 664): never assume a finer taxonomy is real — beat an RW surrogate first.
- **IC-based K-selection** (arXiv 2308.04374): AIC/BIC/ICL/DIC/WAIC do not identify the true number of
  states; fix K from theory.
- **Daily flip-flop** (Toronto tecipa-369): daily/weekly classifiers thrash and lose low-frequency trend
  — debounce/dwell-smooth published labels, or carry persistence via sub-states/window length. (The v2
  read-out already dwell-debounces — keep that.)
- **Frozen-param OOS collapse** (arXiv 2601.05716): non-stationarity punishes fitted cutoffs —
  pre-register, freeze, test forward on labels.
- **Daily-Hurst-is-noise** (macrosynergy): measure efficiency/persistence over multi-week windows only;
  a daily-Hurst gate is white noise.
- **Fitting taxonomy to outcomes:** strategy-blind is non-negotiable — never tune thresholds to a
  downstream strategy's Sharpe; that *is* the frozen-param collapse mechanism in disguise. (This is the
  [[aliased-regime-sensitivity]] hazard at the classifier level.)

### Taxonomy critique — is the 5-state set coherent? `^[inferred]`

Partly. CRISIS and THRUST are well-posed, separable, and gateable. The trichotomy is **not a clean
one-dimensional partition**: the evidence shows return+vol models collapse it to two (low-vol bull +
high-vol sideways — MDPI 13/12/311, LSEG). NARROW is real and measurable (RBC, Morgan Stanley IM) but
lives on a **different (cross-sectional concentration) axis** — it can *co-occur* with GRIND-like or
CHOP-like index behavior, so the five states are **multi-axis, not mutually exclusive**. The taxonomy is
coherent only if reframed as three axes (return/vol → CRISIS/THRUST; trend-efficiency → GRIND/CHOP;
concentration → NARROW) with an explicit **precedence order**, not a flat 5-way classifier. Strongest
caveat: **CHOP may not be distinguishable from a random walk** (Wiley JAE 664), and GRIND↔CHOP may be a
continuum (efficiency ratio) rather than a dichotomy. **Decisive pre-registration recommendation:** gate
decisions only on the validated CRISIS/THRUST pair (as v2 already does — ADR 0024); ship
GRIND/NARROW/CHOP as **descriptive (non-gateable)** labels until the concentration and
multi-week-efficiency axes clear the RW-surrogate and frozen-OOS gates above — promote them to gateable
only post-validation.

### EODHD data-sourcing flags

- **Cap-weight vs equal-weight:** RSP exists only from **2003** inception; pre-2003 requires
  constituent-built equal-weight, which forces point-in-time membership reconstruction. **Prefer
  building both cap- and equal-weighted returns directly from EODHD constituent prices + market cap** for
  clean 2000-coverage.
- **Cross-sectional dispersion (Morgan Stanley method):** computable from constituent prices alone, **no
  ETF dependency** — the most robust NARROW input back to 2000.
- **Point-in-time index membership / survivorship is the deepest sourcing risk** for *any*
  breadth/dispersion axis: survivorship-inflated membership biases early breadth upward (cf. the known
  ~2000 breadth trust floor). Reconstruct membership; do not use a current-constituents shortcut.
- **VIX term structure: not clean pre-~2008** (VIX futures launched 2004; liquid term structure only
  ~2008). Use **SPY realized vol** as the strategy-blind vol input back to 2000; do not gate on VIX term
  structure for the pre-2008 sample.
- **Options surfaces (skew/put-call) and credit spreads (HY-OAS/CDX): not EODHD-sourceable — avoid
  entirely**; they are outside the price/volume/breadth constraint and would break
  strategy-blindness/pre-registrability for the early sample.

---

## What this changes for us `^[inferred]`

- The v2 finding (ADR 0024 — only CRISIS + THRUST gateable, GRIND/NARROW/CHOP below the daily axes'
  resolving power) is **corroborated, not contradicted**: return+vol *cannot* manufacture NARROW, by
  construction. v2's restriction to CRISIS/THRUST gating was the right call.
- The v3 path is now concrete: **add two strategy-blind axes** — a cross-sectional **concentration** axis
  (NARROW) and a multi-week **trend-efficiency** axis (GRIND vs CHOP) — built from the EODHD constituent
  universe we already ingest, layered as a deterministic precedence table over the existing CRISIS/THRUST
  backbone. This is the engine-extensible palette, not a new data dependency.
- GRIND/NARROW/CHOP stay **descriptive-only** until the new axes clear the RW-surrogate + frozen-OOS
  gates — same discipline the [[strategy-assessment]] applicability ratings already assume.

## Open questions / next steps

- Pick the concentration axis form (cap−equal spread vs top/bottom-half dispersion) and the efficiency
  window length (20–60d) — these are pre-registration choices, to be frozen *before* seeing label-quality
  results.
- Build the uncontaminated ground-truth label set: Pagan-Sossounov/Bry-Boschan + Lunde-Timmermann
  chronology + a published NARROW-episode list (late-1990s, 2023–24 mega-cap) to anchor NARROW.
- The two future-dated arXiv IDs (2601.05716, 2308.04374, 2602.07066) should be re-confirmed by a human
  before any are cited in an ADR — they carry the OOS-collapse and K-selection warnings.
- On adoption, `/wiki-ingest` should upgrade [[regime-classification-v3-research-brief]] off
  `status: seed` and update [[regime-read-out]].

---

<a name="appendix-a"></a>
## Appendix A — Adversarially-confirmed claims (20)

Vote = (confirm−refute) of 3 adversarial voters. Claims 1–13 from the first pass; 14–20 re-verified in
the finish-run after the session-limit abstention.

| # | Claim (abridged) | Source | Vote |
|---|---|---|---|
| 1 | HSMM on index returns alone resolves mean/vol/duration regimes — **no channel to a breadth-defined NARROW** | SSRN 3678004 | 3-0 |
| 2 | Hurst discriminates trending (H>0.5) vs mean-reverting (H<0.5); H=0.5 = random walk | Springer 40854-022-00394-x | 3-0 |
| 3 | Pagan & Sossounov rule-based turning-point dating = canonical strategy-blind, pre-registrable bull/bear chronology — but **2 states only** | Wiley JAE 10.1002/jae.664 | 3-0 |
| 4 | A pure random walk reproduces bull/bear features as well as complex models → finer taxonomy must beat an external benchmark | Wiley JAE 10.1002/jae.664 | 3-0 |
| 5 | Price-only turning-point algo resolves **2 states** (direction only), no grind/narrow/chop power | ScienceDirect S0275531921002245 | 3-0 |
| 6 | Canonical Markov-switching = binary bull/bear; extra latent sub-states only for intra-regime rallies (4-state spec) | Toronto tecipa-369 | 3-0 |
| 7 | 2-state Markov-switching on daily/weekly **flip-flops** & misses low-freq trend; sub-states add persistence | Toronto tecipa-369 | 3-0 |
| 8 | Validate regime models against **external** dating algos (Bry-Boschan + Lunde-Timmermann), 0.70 density-interval flag | Toronto tecipa-369 | 3-0 |
| 9 | Frozen-param OOS (params through 2022-12-31 → 2023-24) collapsed: Sharpe −1.65, MaxDD −48%; overfit + non-stationarity | arXiv 2601.05716 | 2-1 |
| 10 | 3-state Gaussian HMM = steady-bull≈GRIND / bear≈CRISIS / high-vol "kangaroo"≈CHOP — **no NARROW** | MDPI JRFM 13/12/311 | 3-0 |
| 11 | IC minimization (AIC/BIC/ICL/DIC/WAIC) does **not** guarantee the right number of states | arXiv 2308.04374 | 3-0 |
| 12 | Cap-weighted minus equal-weighted spread = narrow-leadership gauge (~32% 3y thru 2025 > ~31% late-1990s) | RBC Wealth Mgmt | 3-0 |
| 13 | Cross-sectional dispersion = top-half mean − bottom-half mean return (R1000 2019: 43.7pp); from prices alone | Morgan Stanley IM | 3-0 |
| 14 | ADX = directional **strength** (not persistence); >25 strong, <20 weak/absent | fractalcycles.com | 3-0 |
| 15 | Choppiness Index: ≥61.8 ranging, ≤38.2 trending (literature-fixed thresholds → pre-registrable) | eodhd.com | 3-0 |
| 16 | Higher Kaufman ER ↔ better returns, ER<20 below-average — ER discriminates regimes; **sign must be tested** | alvarezquanttrading.com | 3-0 |
| 17 | Kaufman ER = net change / Σ\|bar-to-bar change\| over N → 0–1 directionality measure | quantifiedstrategies.com | 3-0 |
| 18 | Hurst is **horizon-dependent**: daily ≈ white noise (DAX H=0.54 1-day vs 0.82 50-day) → use multi-week window | macrosynergy.com | 3-0 |
| 19 | Hurst thresholds map to TRENDING-vs-CHOP: >0.5 trending, <0.5 mean-reverting, ≈0.5 random walk | fractalcycles.com | 3-0 |
| 20 | Unsupervised GMM/HMM/agglomerative on price-return features resolves only **2** states (normal vs crash) | LSEG developers | 3-0 |

<a name="appendix-b"></a>
## Appendix B — Adversarially refuted claims (5, killed 0-3)

Tested and killed — **do not rely on these**; listed for traceability.

| Claim (abridged) | Source | Vote | Note |
|---|---|---|---|
| The data-supported up-tape split is by **volatility tiers** (low-vol bull / high-vol bull / bubble), not breadth | SSRN 3678004 | 0-3 | Verifiers found the source does not support this — so we **cannot** assert the breadth split is unsupported |
| A specific Gaussian Markov-switching 3-regime partition with exact Bull/Normal/Crisis mean+vol numbers | arXiv 2601.05716 | 0-3 | Specific figures unverified |
| A specific HMM used **only** return + 10-day rolling vol as features | MDPI JRFM 13/12/311 | 0-3 | Feature-set detail unverified |
| The number of regimes (3) was chosen by a **log-likelihood elbow** | MDPI JRFM 13/12/311 | 0-3 | K-selection detail unverified |
| A macrosynergy restatement of the Hurst scalar thresholds | macrosynergy.com | 0-3 | The **same Hurst threshold concept survives** via Springer (claim 2/19); only this sourcing was killed |

<a name="appendix-c"></a>
## Appendix C — Sources (20)

Primary = peer-reviewed / working paper; secondary = institutional practitioner; blog = practitioner blog.
Sources with no confirmed/refuted claim were fetched but their extracted claims did not reach the verified top-set.

| Source | Quality | Used in |
|---|---|---|
| SSRN 3678004 (HSMM + model selection) | primary | claim 1; refuted R1 |
| Springer 40854-022-00394-x (Hurst) | primary | claim 2 |
| Wiley JAE 10.1002/jae.664 (Pagan & Sossounov) | primary | claims 3, 4 |
| ScienceDirect S0275531921002245 (turning-point) | primary | claim 5 |
| Toronto tecipa-369 (Markov-switching + validation) | primary | claims 6, 7, 8 |
| arXiv 2601.05716 (frozen-param OOS) | primary | claim 9; refuted R2 |
| MDPI JRFM 13/12/311 (3-state Gaussian HMM) | primary | claim 10; refuted R3, R4 |
| arXiv 2308.04374 (IC / K-selection) | primary | claim 11 |
| RBC Wealth Mgmt (the great narrowing) | secondary | claim 12 |
| Morgan Stanley IM (dispersion & alpha) | secondary | claim 13 |
| macrosynergy.com (Hurst horizon) | secondary | claim 18; refuted R5 |
| LSEG developers (ML regime detection) | secondary | claim 20 |
| fractalcycles.com (Hurst vs ADX) | blog | claims 14, 19 |
| eodhd.com (Choppiness Index) | blog | claim 15 |
| alvarezquanttrading.com (ER & mean reversion) | blog | claim 16 |
| quantifiedstrategies.com (Efficiency Ratio) | blog | claim 17 |
| arXiv 2602.07066 | primary | fetched; no verified claim |
| NBER w34015 | primary | fetched; no verified claim |
| medium.com (spurious Markov regimes) | blog | fetched; no verified claim |
| cube.exchange (HMM regime detection) | blog | fetched; no verified claim |

> ⚠ The three future-dated arXiv IDs (2601.05716, 2308.04374, 2602.07066) should be human-confirmed
> before citing in an ADR — they carry the OOS-collapse and K-selection warnings this page leans on.
