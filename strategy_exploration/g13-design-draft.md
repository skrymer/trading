# G13 — Parameter Robustness — design (quant-signed-off 2026-05-29)

Status: **Quant-reviewed (G13 sign-off 2026-05-29).** Issue #57. The top-level shape was
endorsed earlier; the seven implementation specifics below carry the refinements from the
sign-off pass. One item (Q7) gates G13's *verdict authority* on calibration sweeps that have
not yet run — see "Activation prerequisite".

A second wording-review pass on the encoded skill text (Q3a bands + Q7 acceptance criteria)
is owed to the quant before the skill change is final.

This document is strategy-neutral by design: it names no specific candidate. The empirical
failure that motivated G13 is described as a pattern, not a strategy.

## Purpose

G13 is a **fragility tripwire**, not a robustness measurement. It fails any TRADABLE verdict
whose immediate ±-step parameter neighbors don't also pass. The motivating pattern: a candidate
with an off-by-one in an N-trading-day lookback (effective N−1 vs N) validated TRADABLE at the
buggy value and FAILED at the corrected value — a 1-day shift moved Block B aggregate edge
~+0.48%→+0.12%, flipped a chop-window edge sign (G7), and exploded G5 CoV ~0.70→2.86, far
beyond what the ~220-trade sample's noise can explain. The center value that happens to pass is
data-snooping if its neighbors fail.

A sharper variant is **Aliased Regime Sensitivity (ARS)**: a non-monotone {PASS, FAIL, FAIL, PASS}
pattern across a parameter neighborhood with stable trade counts and aggregate edge in the noise
band — a structural disqualifier, worse than simple brittleness.

## Signed-off design

### Placement & trigger
- Runs **after G7**, on a center config that has already passed G1–G11 and reached **TRADABLE**
  (or TRADABLE-pending-promotion). No point sensitivity-testing a non-TRADABLE center.
- **Skip** if the strategy has zero numeric tunables.
- Neighbors fire **Block A + Block B only**, never the 25y aggregate (Q5 — 25y's overlapping
  rolling windows smear the per-window sign-flips G13 keys on) and never Block C (single-window).

### Q1 — parameter classification (REFINED)
- **Explicit two-way classification map keyed by param name is the source of truth.** Known
  tunables get an explicit `DISCRETE` or `CONTINUOUS` entry:
  - DISCRETE: `maxPositions`, `entryDelayDays`, `lookbackDays`, any `*Days` history requirement.
  - CONTINUOUS: `riskPercentage`, `nAtr`, `atrMultiplier`, `leverageRatio`, `*Pct`, `*Multiplier`, `*Fraction`.
- **Subtype is only the fallback** for an *unrecognized* tunable name (int→discrete, float→continuous),
  and the fallback **emits a loud warning** in the report ("classified by subtype fallback: add to map").
- **Fail-closed guard rail:** if a CONTINUOUS param's ±10% step rounds back to the nominal
  (perturbed config byte-identical to center), that is *not tested*, not *passed* — widen that
  one param's step to the smallest representable step at its precision, and flag it.
- Rationale for map-as-truth: mis-classifying a discrete param as continuous silently under-tests
  (small nominals round their ±10% neighbor back onto themselves) — the exact hole the motivating
  bug would slip through. Mis-classifying continuous as discrete merely over-tests (safe direction).

### Q2 — step computation + boundaries (REFINED)
- Discrete: `nominal−1` and `nominal+1`.
- Continuous: `nominal×0.9` and `nominal×1.1`, rounded to the number of decimals in the nominal
  literal, ties-away-from-zero. Report prints **both requested and actually-fired (rounded)** values.
- **Floor-pinned tunable caps at PROVISIONAL.** If a discrete tunable sits on its natural floor
  so only the +1 neighbor is valid: the +1 neighbor must still pass, AND a **G13 floor-flag** is
  recorded. A center with ≥1 floor-flagged tunable can be at most **PROVISIONAL** (never TRADABLE) —
  one-sided is not robustness; "the only side we could test passed" is survivorship reasoning.

### Q3 — verdict precedence (REFINED — the {G5,G7} escape valve is REMOVED)
- The original "PROVISIONAL if exactly 1 neighbor fails on G5 or G7 only" is **deleted** — it
  whitelisted the precise gates the motivating failure tripped (fitting the gate to the known
  failure mode).
- Replacement:
  - All neighbors PASS → **TRADABLE** retained.
  - PROVISIONAL **only if** a single neighbor fails AND (a) the failure is a *near-miss on a
    CONTINUOUS gate* — G1 CAGR within 10% relative of threshold, G5 CoV **failing but ≤ 1.65
    (within 0.15 absolute above the 1.5 ceiling; G5 is `CoV ≤ 1.5`, so a failure is above it)**,
    G9 Sharpe/Calmar within 10% relative — AND
    (b) the failure is *one-directional* (the opposite ±1 neighbor passes clean). This PROVISIONAL
    is **non-final** — the ±2 carve-out below may downgrade it to REJECTED.
  - Any neighbor failure on a **binary/regime gate (G4 positive-window fraction, G6 regime mandate,
    G7 chop survival)** → **REJECTED**, no escape valve.
  - Any non-near-miss continuous-gate failure, or ≥2 failing neighbors → **REJECTED**.
- **ARS / ±2 (Q3b):** ±1 is sufficient *for the verdict* — both a monotone cliff and ARS produce a
  ±1 binding-gate neighbor failure → REJECTED either way. ±2 does NOT widen the base sweep. The one
  carve-out: when G13 would award PROVISIONAL on a continuous near-miss and that tunable's opposite
  ±1 neighbor PASSED, fire the **±2 neighbor on the failing side**. ±2 fails → edge cliff confirmed
  → **REJECTED**; ±2 recovers → near-miss was noise → **PROVISIONAL** stands. (Labeling cliff-vs-ARS
  is a v2 diagnostic; it does not change the v1 verdict.)
- A G13-induced downgrade keeps the existing verdict token (PROVISIONAL / REJECTED) and adds a
  `g13` block to the summary: per-neighbor results, which tunable/direction broke it, reason
  `g13_parameter_fragile` or `g13_regime_sensitive_neighbor`.

### Q4 — randomSeed (SIGN OFF, conditioned)
- Defer ±1-seed perturbation to v2. **But** the report must assert the precondition: "seed
  robustness relies on the upstream multi-seed sizer sweep having passed for this config." If a
  candidate reaches G13 *without* having gone through that sweep, seed perturbation must run.

### Q5 — layer scope (SIGN OFF)
- Block A + Block B only. 25y and Block C excluded.

### Q6 — sweep breadth (REFINED)
- Scope = **{all entry/exit condition tunables} ∪ {maxPositions, entryDelayDays} ∪ {leverageRatio
  if not covered by a sizer sweep}**, MINUS **{sizer params provably covered by a passed multi-config
  sizer sweep for this exact config}**. Each excluded sizer param is named in the report with its
  sweep reference. Alpha-defining entry/exit tunables are non-negotiable in v1.

### Q7 — calibration gating (REFINED) — **ACTIVATION PREREQUISITE**
- **A known-passer sweep BLOCKS G13's verdict authority.** Until it confirms G13 does not
  false-positive-reject a legitimate strategy, G13 ships **advisory / non-binding** (reported like
  Block C, does not change the verdict) — labeled "calibration-pending."
  - There is currently **no known-passer strategy** (0/N components passing). The passer calibration
    therefore runs against **the first strategy to clear the firewall** (or a designated reference
    passer once one exists). Until then G13 stays advisory.
- A known-failer sweep is **confirmatory, not blocking** (G13 is designed to reject the failure mode;
  if it didn't, that's a logic bug). Any rejected candidate serves; can land in parallel.
- **Acceptance criteria (record margins, not raw pass/fail):**
  - Known-failer (not-too-coarse): the buggy center is REJECTED at ±1, with the failing neighbor
    failing **G5 or G7** (matching the CoV-explosion / chop-sign-flip mechanism). A different failing
    gate ⇒ mechanism doesn't match the diagnosis ⇒ investigate.
  - Known-passer (not-too-fine / no false-positive): swept at ±1 / ±10% on its alpha params, **G13
    must not downgrade it below PROVISIONAL** (all-pass TRADABLE, or at worst one continuous near-miss
    with ±2 recovery). A wide-margin neighbor failure ⇒ real fragility the other gates missed; a
    near-miss failure ⇒ ±10% too fine for that param, relax to ±5% and re-test. **Record the margin.**
  - General principle written into the skill: a step is the right resolution when it is the smallest
    perturbation still **meaningfully larger than the per-window edge's sampling SE** on that tunable.
    Integer tunables: ±1 is forced (the quantum). Continuous: ±10% is right iff a 10% move shifts
    behavior by more than sampling error — hence margins are recorded and compared to sample SE.

## Activation prerequisite (summary)

G13 lands fully built but **advisory (non-binding)** until:
1. A known-passer sweep (the first firewall-clearing strategy) confirms no false-positive reject.
2. A known-failer sweep confirms the tripwire fires (confirmatory).

Both sweeps are multi-hour backtests requiring explicit user approval to run. Flipping G13 to
binding is a one-line change once both pass acceptance. Tracked as follow-up.

## Owed to quant

Second wording-review pass on the encoded SKILL.md / REFERENCE.md G13 sections — specifically the
Q3a near-miss bands and Q7 acceptance criteria — before the skill change is final.
