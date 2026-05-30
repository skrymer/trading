# Condition screen — diagnostic-only, with a hard Block-C leakage boundary

`/condition-screen` (`POST /api/conditions/screen`) is a **design-time, diagnostic-only** pre-screen of a single entry condition or AND/OR stack, run *before* the condition is wired into a strategy. It emits *Forward return* / *Lift* / *Firing rate* / *ARS* / regime stats (see CONTEXT.md *Condition diagnostics*) and **no pass/fail verdict**. It defaults to the window `2000-01-01 → 2021-01-01` and **hard-refuses any end date past `2021-01-01`** (the start of Block C).

## Why diagnostic-only with no verdict

The screen catches conditions that are structurally unsound — *Aliased Regime Sensitivity*, firing collapses, near-clones of existing conditions — cheaply, as a pure read, before any backtest engine-time is spent. A condition that fails the screen is rejected without further work; a condition that passes is **not** validated and still runs the full firewall. Emitting a verdict would invite the operator to treat a pass as a green light and skip validation — the screen has no exit, no sizing, and no portfolio context, so it *cannot* speak to tradability. The value is filtering obvious losers, not blessing survivors.

## Why hard-refuse Block C, not warn-and-override

The firewall has three blocks: A (2000–2014), B (2014–2021, COVID-inclusive), C (2021–2025). Block C is the **only** window with no downstream re-blinding — the single source of truth for "does this work on tape the design never touched."

A/B can be *seen* at design time because the firewall **re-gates them with teeth** (G5 CoV, per-window edge sign, cross-block edge-decay) that catch exactly the over-fit that seeing them enables: over-fitting to A/B at design time produces the in-sample-inflation signature those gates exist to detect. The leak is partially self-correcting. **C satisfies neither condition** — it is neither never-seen nor re-gated — so once an operator eyeballs C's lift and redesigns, C stops being out-of-sample and there is no fallback window. The leak is unrecoverable.

The guard is a **hard refuse**, not a warning, because the tool is verdict-free *precisely because the operator cannot be trusted not to tune*. Removing the verdict to prevent tuning and then handing back the Block-C numbers that enable it — with a sticky-note saying "don't look" — is internally inconsistent. The protection has to be that the numbers don't exist. `/strategy-screen` already refuses Block-C-touching windows; `/condition-screen` runs *earlier* in the lifecycle where more is downstream to corrupt, so it would be incoherent for it to be *more* permissive. The refuse error names Block C as the reason so the operator learns the discipline, not just the rule.

Start dates are freely overridable downward (earlier history only helps the diagnostic's regime breadth; `2000-01-01` is the data floor).

## The load-bearing assumption

Option (a) — exclude only C — rests on one claim: **the firewall's A/B gates can absorb design-time exposure of A/B.** If a condition class exists whose A/B over-fit signature those gates *cannot* detect, that claim fails and the safe fallback is `2005-2015` (matching `/strategy-screen`, which blinds B as well). This assumption is recorded here deliberately so a future firewall-design change that weakens the A/B gates triggers a re-evaluation of this boundary.

## Rejected alternatives

- **Full history 2000–today.** Maximal regime coverage, but leaks Block C under the "humans tune" reality the issue itself names.
- **Match `/strategy-screen` (2005–2015).** Tightest safety (blinds B too) but sacrifices pre-2005 and 2015–2020 regime coverage the diagnostic wants. Reserved as the fallback if the load-bearing assumption above is ever invalidated.
- **Reserve a never-seen sub-window of Block A as a design holdout.** Over-engineering: it attacks the diagnostic's core value (regime breadth) to defend a leak A's downstream re-gating already absorbs, and a per-condition holdout decays to zero integrity after first use. The pipeline-level Block C is the one genuine, structurally-enforced holdout.

## Not decided here

- The exact ARS-detection thresholds (sign-flip + 2× clustered SE + ±15% firing stability) and Jaccard advisory bands (>0.5 / >0.7) are quant-provided defaults, explicitly **uncalibrated** until a corpus of known-distinct / known-duplicate condition pairs exists. They live in the skill's REFERENCE.md, not here, so they can be re-derived without an ADR revision.
