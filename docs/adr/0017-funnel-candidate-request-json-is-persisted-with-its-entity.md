# A funnel candidate's request JSON is persisted with its wiki entity

A research candidate's identity is its **exact backtest request JSON** — entry stack, exit, ranker, sizer, `maxPositions`, window/cadence, seed. Until now those request files were written to `/tmp` (the `/strategy-screen` and `/validate-candidate` skills both save to `/tmp/screen-<label>.json` / `/tmp/screen-req-<name>.json`), and the human-readable spec lived in `strategy_exploration/*_DEVELOPMENT.md` dev docs. When those dev docs were retired into the wiki (#121/#129), the exact configs were lost: only prose descriptions survived.

This bit hard on #135. George's class-deprecation had to be re-validated against the now-seeded (#130) Random baseline, but George's request JSON was unrecoverable — it had to be **reconstructed from prose** (entry-condition names, the `daysInTrade ≥ 126 OR close/high52Week < 0.75` script exit, the equal-weight sizer), then validated by a control run against George's documented metrics. Roughly half a session went to rebuilding a config that should have been one file in the repo.

## Decision

**Every funnel candidate persists its exact, validated request JSON in the repo, as a sibling of its wiki entity, named `<entity>.request.json`, referenced from the entity's frontmatter (`request:`) and a "Reproducing" section.**

- The persisted request is the **validated** skeleton — the one whose run was confirmed faithful (for #135, George's skeleton was accepted via the universe-invariant four-metric match, not by chasing a stale trade count). Do not commit an unverified reconstruction as canonical.
- A candidate whose only variation is a single field (e.g. the **Random baseline** = same file with `ranker: "Random"` + a swept `randomSeed`) does **not** get its own file — the entity's "Reproducing" section documents the one-field edit. One canonical skeleton per candidate.
- The first instance is `knowledge/wiki/entities/george.request.json` (this ADR's worked example).

## Why a sibling file, not `/tmp` or the dossier

- **`/tmp` is ephemeral** — it is exactly what failed. A re-run, a reboot, or a doc retirement loses it.
- **Sibling-to-entity is maximally discoverable** — the config sits next to the prose that explains it; a reader opening `george.md` finds `george.request.json` in the same directory. (Operator's chosen location over a separate `requests/` tree.)
- **Not the `strategy_exploration/dossier/` system** — that was being retired into the wiki (#129); reviving it would re-fragment provenance.

## Consequences

- **Reproducibility is a first-class property of a candidate**, on par with its metrics — re-running a year-old verdict is `udgaard-post.sh @<entity>.request.json`, not an archaeology project. This matters most for the verdicts the firewall *reuses* (a deprecation re-tested when the engine, universe, or baseline changes — exactly #135).
- **Follow-up (not blocking): wire it into the skills.** `/strategy-screen` and `/validate-candidate` should write the request into the candidate's entity path on a PASS/validated result, not only to `/tmp`. Until then, persistence is a manual step at write-up time.
- **Follow-up (not blocking): backfill** the surviving dossier candidates (tyr, fenrir, baldr) whose request JSON is still recoverable.
- A persisted request is a **snapshot under the engine/universe of its run** — it reproduces the *skeleton*, not the *numbers* (those move with the universe; see the #135 re-baseline note in the George prereg source). The entity's provenance notes carry the as-of universe.
