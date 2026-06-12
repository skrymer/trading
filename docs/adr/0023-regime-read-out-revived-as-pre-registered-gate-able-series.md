# The 5-label regime read-out is revived as a pre-registered, gate-able series with a rescue-forbidden boundary

The regime-conditional portfolio program is abandoned (do not resume — see `knowledge/wiki/synthesis/regime-conditional-portfolio.md`), but its quant-confirmed 3-axis classifier spec was shelved with an explicit revival clause: *"revive only if regime-attribution is ever wanted as a research instrument."* The strategy assessment (ADR 0022) wants exactly that — per-regime trade decomposition and a current-regime line for deployment judgment — so we **build the read-out**: a backend daily series assigning every trading day since 2000 one of the five canonical market-regime labels (broad-rally thrust / low-dispersion grind / narrow-leadership / chop / crisis) from breadth (level + slope), the leadership-concentration gap (sign), and realized vol (band). A static hand-maintained date map was rejected (cannot label *today*, misses unlisted spells, never self-updates); narrative-only regime commentary was rejected (soft eyeballing invites bias).

## Discipline

- **One canonical frozen parameterisation** (the `LeadershipRegimeParams.FROZEN` pattern). Nobody tunes the classifier per strategy; it is market-defined and never fit to any strategy's good/bad years (that is Aliased Regime Sensitivity).
- **Quant sign-off on the revived pre-registration BEFORE implementation** (expert review precedes persistence), then `/tdd`.
- It is a **read-out the operator consults, not an auto-switcher** — reviving it does not revive the abandoned program.

## Gate-able, rescue-forbidden

The series IS exposed as entry/exit conditions (precedent: `LeadershipGapRegimeOnCondition`), because the alternative — report-only — pushes future legitimate regime premises into re-implementing classifier fragments ad hoc, which is worse pre-registration hygiene than one canonical frozen series. The danger is not the gate; it is *when the gate is added*. The boundary, enforced where lineage judgment already lives (the quant-DISTINCT successor gate of ADR 0008):

- **Forbidden (rescue):** a successor candidate whose change is adding a gate that excludes the regime its parent's assessment/report showed losing — a disguised re-run of a dead config, mechanically guaranteed to look better on the same 25-year path. Refused as not structurally distinct.
- **Legal (ex-ante premise):** a fresh-lineage candidate designed from the start around a regime premise, gate chosen by reasoning before any per-regime result was seen ("add regime gate from scratch in a NEW candidate, don't rescue this one").

## Consequences

- The assessment's regime table reports per-regime edge ± date-clustered SE and N, with a hard insufficient-N floor (~30 trades, the G8 rationale) below which a row reads "insufficient — do not infer" (crisis/chop will almost always trip it; that is correct, not a defect).
- The current-regime line is computable from live data — the deployment-judgment input the operator asked for.
- Per-regime decomposition is computed in the backend (testable Kotlin, reusing the condition screen's date-clustering machinery), not in skill scripts.
