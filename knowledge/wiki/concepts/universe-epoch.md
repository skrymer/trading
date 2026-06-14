---
type: concept
title: Universe epoch — a global universe change supersedes verdicts without resetting the dead-config brake
summary: A global one-time universe change (ADR 0026) marks prior firewall verdicts stale-but-not-erased without resetting the dead-config brake; re-fire a corpse only with a structural-interaction judgment.
status: stable
tags: [methodology, firewall, universe, data-mining, anti-snooping]
sources: ["docs/adr/0026-tradable-universe-is-liquidity-gated-and-universe-changes-open-an-epoch.md", "docs/adr/0008-strategy-exploration-orchestrator-is-a-non-executing-data-mining-brake.md", "CONTEXT.md"]
related: ["[[component-firewall]]", "[[aliased-regime-sensitivity]]", "[[the-funnel]]"]
updated: 2026-06-14
---

# Universe epoch (ADR 0026)

A **universe epoch** is the state opened by a deliberate, **one-time, global, pre-registered,
candidate-blind** change to the platform's *trading universe* (the tradable or measurement population
— CONTEXT.md "Trading universe"). The first instance is the #173 tradable-universe liquidity filter
(price ≥ $5, 20d-median dollar-volume ≥ $1M, ≥ 252 bars). It is the answer to a question ADR 0008
otherwise can't: *what happens to the firewall ledger when the data population itself changes?*

## Why it isn't just "re-run everything"

The *config hash* (ADR 0008's dead-config fingerprint) deliberately **excludes the universe** — it
covers entry/exit/ranker/sizer/maxPositions/entryDelay/seed only. The dead-config brake exists to stop
**optional stopping on the same population** (see a FAIL, turn a knob, roll again — the
[[aliased-regime-sensitivity]] family at the search level). A universe change is **categorically not a
knob-turn**: it is global, candidate-blind, and motivated by an independent correctness reason (the
10 bps cost model is fiction on sub-$10 / illiquid names). So prior verdicts become legitimately
**stale** — they were earned on a population that is no longer the tradable population — *without* being
wrong-by-data-mining.

## What the epoch does (the rule)

- Prior firewall verdicts are marked **`SUPERSEDED-BY-UNIVERSE-EPOCH`** — **stale, not erased**. The
  append-only dossier (ADR 0008) keeps every prior line; the change is a recorded epoch event.
- The **dead-config brake still binds.** A `REJECTED`/`NEAR_MISS` corpse may be re-fired on the new
  universe **only** with a recorded **structural-interaction judgment** (its death cause plausibly
  interacts with the change) registered via the existing **lineage / `DISTINCT`** gate — never a silent
  re-fire of the dead hash. A universe-orthogonal death earns no re-run.
- The brake **re-arms inside the epoch**: once a config has a verdict on the new universe, tweak-and-re-run
  is forbidden again.
- **Loophole guard:** the reset is legitimate *only because* it is one-time / global / recorded /
  candidate-blind. A **per-candidate** universe knob ("change the universe to revive my corpse") is the
  same data-mine in disguise and is forbidden.
- **`N_eff` accounting:** a re-validation observes a binding stitched-OOS Sharpe on the shared tape, so it
  **counts as a firewall trial** (+1 to `N_high`); as a within-lineage near-clone it adds ≈0 to the
  haircut `N_low` and **carries its prior trial cost** (no clean slate). Re-running everything is a *paid*
  look — so re-validate only configs with a recorded reason to flip.

## How to apply it (the re-validation policy) `^[inferred — quant-adjudicated 2026-06-14]`

When a universe epoch opens, do **not** mass-re-run. Classify each candidate by whether its death cause
**interacts** with the change:

- A *shrinking* liquidity gate (#173) can only rescue a corpse that died **trading illiquid junk whose
  costs were fiction** *and* whose real edge survives the cut. Most deaths are universe-orthogonal
  (participate-and-lose, ARS, cadence, beta-delivery, single-instrument) → **stay dead**.
- At the #173 epoch the funnel was **empty** (no TRADABLE/PROVISIONAL configs), so re-validation cost was
  ≈0. Priority re-validate: **none**; opportunistic: only `quality-profitability-tilt` (the one real
  non-beta edge) *iff* the quality class is revisited.
- **Corroborating tell:** the cheap-name-driven corpses (DV1, MR3 have on-record `minPrice≥5` re-fires
  that *collapsed* their CAGR) get **more** dead under the gate — direct evidence the change is a
  *correctness fix*, not a corpse-revival lever.

## Instances

- **#173 tradable-universe liquidity filter** (ADR 0026) — the first universe epoch. Default-on price /
  liquidity / age gate; supersedes all pre-#173 verdicts; re-validate-nothing policy applied.

## Related

The firewall mechanics it extends: [[component-firewall]], [[the-funnel]]. The search-level data-mining
failure it is carefully *not* an instance of: [[aliased-regime-sensitivity]].
