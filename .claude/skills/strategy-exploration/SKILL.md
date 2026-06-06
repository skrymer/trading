---
name: strategy-exploration
description: Track a strategy candidate through the full tradability funnel (condition-screen → strategy-screen → validate-candidate → promotion/verify-promotion → monte-carlo) as a non-executing state-machine. Use to start a new candidate, see where one is, record a step's result, or register a redesign after a rejection. It never fires a backtest — it prints the next leaf-skill to run and refuses to advance a config that already failed the firewall.
argument-hint: "[verb] [candidate]"
---

# Strategy Exploration — the funnel orchestrator

Answers one question: **"Where is this candidate in the funnel, and what may it do next — without letting a dead config sneak back in?"**

This skill is a **non-executing state-machine** (ADR 0008). It never calls the API or fires a backtest. It tracks each candidate in a crash-safe dossier, prints the next *leaf skill* to run, and **hard-refuses to advance a config that already failed a binding firewall layer** — making config-tweak-and-re-run structurally impossible, not merely discouraged. The leaf skills (`/condition-screen`, `/strategy-screen`, `/validate-candidate`, `/verify-promotion`, `/monte-carlo`) keep all execution + their own show-POST-then-approve gates.

This skill is strategy-neutral. Substitute the user's actual candidate in every example.

## The funnel it tracks

```
/condition-screen → /create-condition → /strategy-screen → /validate-candidate
   → (inline-script only: promote via /create-condition → /verify-promotion G14)
   → /monte-carlo → LIVE_READY
```

Stages: `DRAFT → SCREENED → VALIDATING → VALIDATED → LIVE_READY`; side-exits `REJECTED` (dead) / `ABANDONED`; inline-only `PROMOTED` between VALIDATED and LIVE_READY.

## The dossier

One git-tracked, append-only JSONL file per candidate under `strategy_exploration/dossier/<candidate>.jsonl`. One self-contained event per line; the **last well-formed line is the authoritative state**. A mid-write crash truncates at most the final line — on resume, read the dossier and surface any in-flight backtest (a `FIRED…PENDING` line with no later `RECORD`) so the operator checks for a completed `backtestId` before re-firing. **Write immediately on every transition, never batch.**

## The verbs (deterministic mechanics live in `scripts/explore.py`)

| Verb | What you do |
|---|---|
| `status <candidate>` | `python3 scripts/explore.py status <dossier>` → render current stage + any in-flight backtest. |
| `next <candidate>` | From the current stage, print the **exact leaf-skill invocation** to run next (e.g. `run /validate-candidate MR4 <template>`). Never the raw API call. |
| `check <candidate>` | `python3 scripts/explore.py check <dossier-dir> <template>` → interlock decision. **REFUSE ⇒ stop.** |
| `fire <candidate>` | After the operator runs the leaf skill, `explore.py fire …` records the `FIRED…PENDING` event. |
| `record <candidate>` | `explore.py record <dossier> <summarize.json> <template>` → parses the verdict, **asserts the fired config matches** what was printed, appends the `RECORD`, **spawn the matching analyst**, then **`/wiki-ingest`** the durable findings (see "Wiki maintenance"). After a `/validate-candidate` RECORD, also run the **Deflated-Sharpe flag** (below). |
| `dsr-flag <candidate>` | After a firewall RECORD: `explore.py dsr-flag <dossier-dir> <summarize.json> <n-obs>` assembles the search-agnostic `POST /api/risk/deflated-sharpe` payload + the itemized lineage list. You POST it, then append a `DSR_FLAG` event. See "Deflated-Sharpe flag". |
| `new <candidate> [--lineage <corpse>]` | **`/wiki-lint` first** (consult clean knowledge before scoping), then register a DRAFT. A successor to a dead candidate is gated — see "The brake". |
| `abandon <candidate>` | Append an `ABANDONED` event. |

## The brake (non-negotiable — ADR 0008)

A `config_hash` whose `/validate-candidate` verdict is `REJECTED` or `NEAR_MISS` is **dead**. `check` refuses a dead hash **and any single-step G13 neighbour** of one (reusing `g13_neighbors.py`'s classification — discrete ±1, continuous ×0.9/×1.1). **There is no `--force`.** The only forward path is a redesigned candidate on a new lineage, and registering it requires a parsed quant verdict:

- On a death, **auto-spawn `firewall-analyst`** for a post-mortem and fold it into the dossier.
- Before accepting `new … --lineage <corpse>`, **spawn `quant-analyst`** with the corpse's dossier + the new premise and require it to end with a structured token `DISTINCT` / `NOT_DISTINCT` / `UNSURE`. **Only `DISTINCT` opens the lineage.** `NOT_DISTINCT`/`UNSURE` ⇒ refuse, surface the reasoning, operator sharpens the premise.

Re-running a dead config — even one parameter step away — is data-mining, not validation. The skill exists to make that impossible.

## record always annotates

`record` parses the machine token only (never an operator-typed verdict), asserts the hash, then **always spawns the matching analyst** to fold a narrative into the dossier: `firewall-analyst` (validate-candidate), `strategy-screen-analyst` (screen), `monte-carlo-analyst` (MC), `condition-screen-analyst` (condition screen), `post-backtest-analyst` (backtest).

## Deflated-Sharpe flag (multiple-testing readout — ADR 0014)

The whole funnel is one multiple-testing experiment: every firewall trial competes on the same
2000–2025 data. After a `/validate-candidate` RECORD (any verdict — a REJECTED run still consumed a
look at the binding Sharpe), assemble a reported Deflated-Sharpe flag — **never an auto-reject**, it
only tells the operator how much of a survivor's Sharpe is plausibly best-of-search luck.

The engine owns the math (search-agnostic `RiskMetricsService.deflatedSharpe`); this skill owns the
*search* half — the global trial register and the effective trial count:

1. `python3 scripts/explore.py dsr-flag <dossier-dir> <summarize.json> <n-obs>` — collects the
   global firewall-trial register (`registry.collect_firewall_trials`, RECORD with a validate
   verdict, deduped by `config_hash`, across **all** dossiers), clusters by lineage (one dossier
   file = one cluster) for `N_high`, reads each trial's `layers.25y.aggregate_sharpe`, de-annualizes
   to per-observation units, and prints the `POST /api/risk/deflated-sharpe` payload + the itemized
   lineage list. `n-obs` = the candidate's stitched-OOS trading-day count (from its 25y result).
2. `POST` the payload to `/api/risk/deflated-sharpe`.
3. Append a `DSR_FLAG` event to the candidate's dossier: `{"ev":"DSR_FLAG","hash":<candidate>,
   "deflated_sharpe":<resp.deflatedSharpe>,"n_high":<N>,"tier":"AMBER"|"CLEAR","lineages":[…]}`.
   **AMBER** when `deflatedSharpe < 0.95`, else **CLEAR**. The DSR_FLAG is the only newly persisted
   datum — no dossier schema change (the trial population is derived from existing RECORD fields).

Phase 1 ships the conservative `N_high` endpoint only (amber-or-clear); the `N_low` correlation
haircut (the red tier) is a fast follow. `firewall-analyst` surfaces the flag with the phase-1
caveat. The flag **augments**, does not replace, the binding screen-stage G5 gate.

## Wiki maintenance — the knowledge that compounds (#84/#108)

The `knowledge/` strategy-research wiki only survives if maintenance hangs off the funnel steps you
already run (a passive wiki decays like the handover docs it replaced). Two triggers, both **skill-step,
no hooks**:

- **On `new` → `/wiki-lint` first.** Before scoping a candidate, health-check the wiki so you consult
  *clean* knowledge — and so the candidate gets checked against the documented failure modes
  (`knowledge/wiki/concepts/`) up front.
- **On `record` → `/wiki-ingest`.** After the analyst returns (and emits its `KNOWLEDGE-UPDATE:` lines),
  fold the durable findings into the wiki: the candidate's `entities/` page, the failure-mode `concepts/`
  page it hit, a dated `sources/` summary, the `index`/`log`. `/wiki-ingest` reads the **dossier RECORD
  event** (not the evaluator's output), so it survives the #54 backend migration.

`/wiki-query` is on-demand (no trigger) — use it to ask "what do we know about X?" before designing.

## Verdict → interlock state

`REJECTED`/`NEAR_MISS` → **DEAD** (refuse re-run + neighbours). `PROVISIONAL`/`TRADABLE` → **SETTLED** (advance; never re-run for a better verdict). `INCONCLUSIVE_G11` → **BLOCKED** (passed binding but G11 unevaluable — resolve the anomaly, then re-record; not dead, not advanced). `INCOMPLETE`/`ERROR` → **FAULT** (methodology/data error — re-run allowed after the fix).

## Pre-live gates the dossier enforces

`LIVE_READY` requires a `TRADABLE`/`PROVISIONAL` verdict **plus** a recorded Monte Carlo run **plus** — for inline-`script` candidates only — a recorded G14 `PASS` on the promoted first-class config. A `TRADABLE` verdict alone is "validated", not "cleared for capital". Standalone `/walk-forward` is **off** the critical path: `/validate-candidate`'s Block A is the full-period binding layer.

## Autonomous driver mode (ADR 0008)

The skill stays non-executing, but a **driver** (the main agent) may execute the funnel autonomously under one explicit, bounded **batch authorization** — replacing per-step approval — *only* under these binding rules (full rationale in ADR 0008):

1. **Freeze first, timestamped.** Before the first Block A fires, record a batch-authorization dossier event capturing the named candidate set, each frozen template's `config_hash` (incl. `randomSeed`), the stop-boundary, and a freeze timestamp. Everything the run may do is fixed here, *before any OOS result exists*.
2. **Brake before every fire.** Run `explore.py check` and halt on REFUSE. No `--force`.
3. **Sequential.** One backtest at a time.
4. **Unconditional within validate-candidate.** Fire Block A → Block B → 25y aggregate without re-scoping/re-seeding on interim results. Never optional-stop on an interim OOS verdict.
5. **G10 auto-confirm by precedence, not identity.** Auto-confirm the 25y G10 gate only because the freeze-precedence event proves no interim result steered the proceed decision (and `record` re-hashes config identity). Absent that event → MUST stop at G10 and ask the human. (Requires the `G10_AUTOCONFIRM` bypass in `run-pipeline.sh` — see implementation note below; until that exists, validate-candidate runs are NOT autonomous.)
6. **Scope = screen → validate only.** Never auto-run Monte Carlo, promotion, or anything past `VALIDATED`. Capital is always human.
7. **On death: post-mortem, HOLD, continue others.** No autonomous redesign. A successor needs a human premise + a FRESH `quant-analyst` `DISTINCT` judgment — and that gate binds **across batches** (a new-batch candidate that is a single-step G13 neighbour of any prior dead hash is refused until `DISTINCT`).
8. **No self-review.** The driver applies only mechanical gates; every judgment goes to a fresh `quant-analyst`.
9. **Halt + ping** on: any death, any `TRADABLE`/`PROVISIONAL`, any `ERROR`/`INCOMPLETE`/`INCONCLUSIVE_G11`/config-mismatch, or all candidates terminal.

**Implementation note (pending):** `run-pipeline.sh` reads `confirmed` from `/dev/tty` for G10. Autonomous validate-candidate needs a `G10_AUTOCONFIRM` env flag (set by the driver *only after* emitting the freeze-precedence event) that makes `g10_confirmation` assert hash-equality-across-blocks instead of reading the tty — never a silent "no tty → skip". Until wired, `/strategy-screen` runs autonomously but `/validate-candidate` stops at G10 for a human.

## Tests

`python3 scripts/test_config_hash.py` (and `test_dossier`, `test_interlock`, `test_record`, `test_registry`). The verb CLI (`explore.py`) is thin glue over these tested modules.
