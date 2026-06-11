# The Block A 2008-GFC regime mandate (G6) is a crash-survival test, not a strict-positive profit test

> **Status: advisory (proposed), not binding.** Drafted 2026-06-12 after the first candidate to ever run a
> real Block A firewall ([[quality-profitability-tilt]]) was rejected on the strict 2008 G6. Quant-reviewed.
> Ships advisory — reported, non-binding — until a candidate first clears the full firewall (see *Consequences*).

The Block A G6 regime mandate currently requires the **2008 GFC out-of-sample window** to post **strictly
positive** per-trade edge (`edge > 0`, no near-miss). This ADR replaces that with a **bounded-loss
crash-survival test**: `edge ≥ −0.5%` on the 2008 window — the same threshold Block B's `G6a` already applies
to the 2020 COVID crash. The 2009 recovery is **not** added as a sub-gate: under the firewall's frozen
36/12/12 cadence it is a *separate* OOS window already gated by per-window positivity (G4). No new
crash-window drawdown sub-cap is added either — G2 and G3 already bind that window's drawdown.

`edge` here is the strategy's **per-trade percentage return** in the window (not a lift-vs-universe and not a
drawdown); `−0.5%` is a per-trade-edge floor.

## Why

- **A crash window tests *survival*, not *profit* (ADR 0010).** In a long-only engine the correct GFC
  behaviour is to be **in cash**, not deployed-and-bleeding — a cash-defended book posts ~0% edge on
  near-zero trades. A strict `> 0` bar on a −50% tape asks the book to *win* in the crash, and at the margin
  it can penalise even a correctly cash-defended book (a few stop-outs on the way in tip it slightly
  negative). The right crash-window test is **bounded loss** (`≥ −0.5%`), the survival semantics ADR 0010
  makes the long-only crash mandate.

- **It removes an unjustified inconsistency with the COVID gate.** Block B's `G6a` (the 2020 crash leg)
  already allows `edge ≥ −0.5%`; only the recovery leg `G6b` requires `> 0`. There is no principled reason
  the GFC crash should be held to a *stricter* bar than the COVID crash — both are "the long book got caught
  in a fast washout" tests. The strict-`>0` Block A G6 is the older, blunter form that predates the issue-#51
  `G6a/G6b` refinement and never got back-ported. This is calibration debt, not a deliberate asymmetry.

- **The cadence already separates the 2008 crash from the 2009 recovery — so no intra-window split is needed
  (this is where the COVID analogy breaks).** Under 36/12/12, the 2008 OOS window is
  **W6 = `2008-01-02 → 2009-01-01`** — it contains the Sept–Nov 2008 washout and **ends at the December-2008
  lows, before the 2009-03-09 SPY trough**. The recovery rally lives entirely in **W7 = `2009-01-02 →
  2010-01-01`**, a *different* OOS window. COVID is a *within-window* crash+recovery (crash Mar-2020 and
  V-recovery both fall inside Block B's single W4), which is exactly why a single full-window G6 could be
  *masked* by the recovery and why the intra-window `G6a/G6b` monthly split was invented. **2008 has no such
  masking hole:** W6 ends in the depths, so it is pure crash-survival by construction, and W7's recovery is
  already gated by its own per-window positivity (G4) and the 25-year aggregate. A single bounded-loss bar on
  W6 is therefore the correct *and complete* fix; porting the COVID intra-window split to 2008 is
  unimplementable on the real cadence (there is no recovery leg inside W6 to recompute).

- **This is NOT a forecast-based relaxation.** It explicitly rejects the "a GFC-magnitude event is unlikely
  in the next ten years, so don't reject on the 2008 window" argument that prompted the review: that welds a
  *macro forecast* into a *validator*, is refuted by the base rate (2008, a GFC-*speed* 2020, a −25% 2022 — a
  ≥20% drawdown roughly every six years), and inverts the cost asymmetry (rejecting a good strategy is
  recoverable patience; deploying capital into an undefended crash book is ruin). That forecast belongs in
  *live deployment/sizing*, never in the gate. This ADR tightens *consistency*; it does not lower the tail bar.

## Considered options

- **Keep G6 strict (`> 0`).** Rejected — measures profit, not survival, in a crash window; internally
  inconsistent with `G6a`; can penalise a correctly cash-defended book.
- **Port the COVID `G6a/G6b` intra-window crash/recovery split to 2008.** Rejected — **unimplementable on the
  real cadence.** The 2008 crash (W6) and 2009 recovery (W7) are different OOS windows; there is no
  intra-window recovery leg to recompute, and a `> 0` "recovery" sub-gate over 2009 entries would just
  double-count W7's existing per-window positivity gate. (This was the first-draft framing; the quant review
  caught that the COVID masking pathology does not occur in W6.)
- **Single bounded-loss bar (`edge ≥ −0.5%`) on the existing W6 window (chosen).** Mirrors `G6a`'s threshold,
  is consistent across blocks, and is correctly scoped — W6 is pure crash by cadence, with no masking to
  decompose. Strict in the sense that `G6a` is strict (a hard `≥ −0.5%`, no NEAR_MISS amnesty), but
  bounded-loss rather than strict-positive.
- **Demote the 2008 window to informational (like Block C).** Rejected firmly — Block C is informational
  because a single 1-window holdout generates Type-I false-rejects; the 2008 GFC window is the single
  highest-value tail-risk observation in the 26-year record. Demoting it *is* the forecast-relaxation
  smuggled into the gate structure.
- **Add a frozen crash-window max-DD sub-cap.** Rejected — G3 (worst-window DD ≤ 20%) already binds the 2008
  window specifically (the rejected candidate failed it at 30% on exactly this window), and a frozen
  crash-DD number chosen *after* seeing that 30% is the data-snooping pattern G13 exists to catch. G2
  (aggregate DD ≤ 25%) + G3 already cover the drawdown face; G6 covers the per-trade-edge face. A third
  sub-cap is redundant and snooping-prone.

## Consequences

- **Advisory until a passer exists.** No candidate has cleared the full firewall — [[quality-profitability-tilt]]
  was the first to run a real Block A (n = 0 passers). The bounded-loss W6 bar therefore ships **advisory**
  (reported, non-binding) until a genuine passer can be run through *both* the old strict G6 and the new
  bounded-loss bar to confirm it does not change a *good* candidate's verdict. Flipping to binding is a
  one-line change once a passer exists; retrofitting a binding gate change with n = 0 passers is calibrating
  in the dark.
- **Nothing to pin.** Because the fix is a single bounded-loss bar on the existing W6 window (not an
  intra-window split), there are no crash/recovery boundary dates to pin to the SPY trough. (For the record,
  the SPY GFC trough is 2009-03-09, which falls in W7.)
- **It does not reopen the [[quality-profitability-tilt]] verdict.** That candidate fails the bounded-loss W6
  bar anyway (2008 edge −0.99% ≪ −0.5%) **and** fails G1 (ex-2008 geometric CAGR ~17.8% < 25%) and G15
  (Calmar ~0.9 < 1.5) with the GFC fully excised — those are the bare return-quality floors, not crisis
  gates. Note G15 (absolute Calmar ≥ 1.5) and G16 (SPY-relative Calmar, which this candidate **passed** —
  0.512 vs SPY 0.164) bind independently; beating SPY 3× is not a near-pass. **REJECTED stands** regardless of
  this ADR.
- **Implementation is deferred.** Flipping the gate to binding touches `eval-block.py`
  (`BLOCK_CONFIG["A"]`'s G6 single-window branch) and the `G6` text in the `validate-candidate`
  `REFERENCE.md` + `SKILL.md`. This ADR records the decision only; the code change waits for the first
  firewall passer that lets the advisory gate be confirmed.
