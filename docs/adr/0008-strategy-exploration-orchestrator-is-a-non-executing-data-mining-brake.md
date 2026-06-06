# Strategy-exploration orchestrator is a non-executing data-mining brake

The platform has a 7-skill funnel that takes a strategy candidate from condition design to a tradability verdict (`/condition-screen` → `/create-condition` → `/strategy-screen` → `/validate-candidate` → promotion+`/verify-promotion` → `/monte-carlo`). The `strategy-exploration` skill orchestrates that funnel. We decided it is a **non-executing state-machine**: it tracks each candidate's state, prints the next leaf-skill to run, and never itself calls the API or fires a backtest. Its defining behaviour is to **refuse to advance a config that has already failed a binding firewall layer** — making config-tweak-and-re-run structurally impossible rather than merely discouraged. The skill is non-executing; a separate **driver layer** (see "Autonomous driver mode" below) may execute the funnel on the operator's behalf, but only ever through the same brake — it cannot fire a step the skill would refuse, and it makes no forward decision the operator's per-step approval used to make.

## Why non-executing

The team's cardinal validation rule is *a failed binding firewall layer is final — modifying the config and re-running is data-mining, not validation*. An orchestrator that fires the funnel back-to-back would put a single button on the most dangerous transition in the system (a binding-layer FAIL → re-run with a tweak) and would have to re-implement the show-POST-then-wait approval gate every leaf skill already owns. A non-executing orchestrator cannot fire a backtest, so it enforces the three hard constraints — sequential backtests (the engine OOMs on concurrent runs), never-fire-without-approval, and failed-layer-is-final — *structurally* instead of by convention. An executor that re-runs on command is a data-mining accelerator; a state-machine that withholds the next command when a config is dead is a data-mining brake.

## The interlock

Each candidate is fingerprinted by a `config_hash` over the design-isolation freeze set that G10 freezes (`entryStrategy`, `exitStrategy`, `ranker`, `rankerConfig`, `maxPositions`, `entryDelayDays`, `positionSizing`, `randomSeed`; dates excluded because they vary per firewall block by design). A hash whose `/validate-candidate` verdict is `REJECTED` or `NEAR_MISS` is **dead**: both are binding-layer failures, and re-running the identical failing config is precisely the data-mine the brake exists to stop. The skill hard-refuses re-running a dead hash *and* refuses its single-step G13 neighbours — the configs reachable by one G13 perturbation (`±1` step for discrete tunables, `×0.9`/`×1.1` for continuous ones, reusing `g13_neighbors.py`'s classification map) — with no override. The neighbour refusal matters most for `NEAR_MISS`, which is margin-close by construction: its neighbours are the ones most likely to round into a pass, which is the data-snooping the brake removes. The only forward path is a redesigned candidate on a new lineage, and registering it is refused until a spawned `quant-analyst` returns a parsed `DISTINCT` token (vs `NOT_DISTINCT`/`UNSURE`) judging the redesign structurally distinct from the dead one.

## Verdict → interlock mapping

The orchestrator parses the `/validate-candidate` verdict token and maps it to one of four interlock states:

- `REJECTED`, `NEAR_MISS` → **DEAD**: refuse re-run of the hash and its single-step G13 neighbours; only a `DISTINCT`-judged successor on a new lineage advances.
- `PROVISIONAL`, `TRADABLE` → **SETTLED**: advance; never re-run for a *better* verdict (both already cleared all binding layers; re-running to chase an upgrade is the same data-mine).
- `INCONCLUSIVE_G11` → **BLOCKED, not dead**: binding layers passed but G11 cross-block decay was not applicable (missing data or a non-positive Block A). The config is neither advanced nor burned; the operator resolves the anomaly, then re-records.
- `INCOMPLETE`, `ERROR` → **methodology/data fault, not a verdict**: a missing or failed eval, or a non-comparable G14 diff. Re-run is permitted once the fault is fixed.

## The dossier

Candidate state lives in a git-tracked, append-only JSONL dossier — one self-contained JSON event per line, the last well-formed line being the authoritative state. This format is chosen for crash recovery: a mid-write crash truncates at most the final line, leaving all prior events intact, and an in-flight backtest is recorded as a `FIRED…PENDING` line with no later matching `RECORD`, so a resume after a crash knows to check for a completed `backtestId` before re-firing. Verdicts enter the dossier only via `record <artifact>`, which parses the machine verdict token from `summarize.py`, re-hashes the template and asserts it matches the `FIRED` event's `config_hash` (catching "printed config X, ran tweaked Y"), and spawns the matching analyst sub-agent (firewall-/strategy-screen-/monte-carlo-/condition-screen-/post-backtest-analyst) to fold its narrative into the entry — never by operator-typed verdict.

The dossier is the **sole surviving artifact under `strategy_exploration/`** (issue #121): when the lab-notebook `strategy_exploration/*.md` dev docs are consolidated into the `knowledge/` wiki as the single source of truth for research narrative, `strategy_exploration/dossier/*.jsonl` stays put — it is the machine ledger this ADR governs, not documentation, and the brake (`explore.py` / `run-pipeline.sh`) reads & writes it in place. Relocating it is out of scope; the scripts and this ADR would have to move with it.

## Pre-live gates

A `TRADABLE` verdict is "validated", not "cleared for capital". The dossier marks a candidate `LIVE_READY` only when it carries a `TRADABLE` or `PROVISIONAL` verdict **plus** a recorded Monte Carlo run, and — for candidates authored with inline `script` conditions — a recorded G14 implementation-invariance `PASS` on the promoted first-class config. Standalone `/walk-forward` is kept off the funnel critical path: `/validate-candidate`'s Block A is itself the full-period binding walk-forward, so a separate Block-A walk-forward is a second firewall-grade run over the same period that adds only a re-run temptation.

## Autonomous driver mode

Per-step human approval of an hours-long sequential backtest funnel is impractical for the sole operator. Because the **brake — not the human — is what provides anti-data-mining safety**, the human can be removed from the per-step loop without removing the safety, provided the brake stays un-overridable, capital stays human, and the driver makes no result-conditional forward decision. A **driver** (the main agent, or a loop) may therefore execute the funnel autonomously under a single explicit, bounded **batch authorization** (named candidates + named stop-boundary), recorded with a freeze timestamp, replacing per-step approval. The skill itself remains non-executing; the driver is the executor, and it can only fire what the brake permits.

Invariants (binding):

- **Brake un-overridable.** The driver runs `explore.py check` before every fire and halts on REFUSE. No `--force`, ever.
- **Sequential.** One backtest at a time (the engine OOMs on concurrent runs).
- **Capital is human-only.** Autonomy scope is `strategy-screen → validate-candidate` only. The driver NEVER runs Monte Carlo, promotion, or anything past a `VALIDATED` verdict; LIVE_READY and deployment are always human.
- **No result-conditional forward decision.** The candidate set, the full frozen template (including `randomSeed`), and the stop-boundary are fixed in the batch authorization *before the first Block A fires*, and the freeze is timestamped as a dossier event that provably precedes that fire. Within `/validate-candidate` the driver fires Block A → Block B → 25y aggregate **unconditionally** — it never re-scopes, re-seeds, or re-derives any 25y degree of freedom from interim OOS results. This is what makes removing the human's per-step proceed/abort safe: optional stopping on an interim OOS result is itself a data-mining vector, and the driver structurally cannot perform it.
- **G10 auto-satisfied by decision-precedence, not by config identity.** The 25y aggregate's design-isolation gate (G10) is auto-confirmed in autonomous mode *because* every degree of freedom of the 25y run was frozen in the batch authorization before any OOS result existed, the template is one file overridden only on dates, and `record`'s re-hash asserts the 25y `config_hash` equals Block A's and Block B's. Config identity alone is insufficient (the brake already re-checks it); the auto-confirmation is licensed by the timestamped freeze-precedence event, which is the autonomous analogue of the human attesting "unchanged, and I did not let an interim result steer the proceed decision." Absent that precedence event, the driver MUST stop at G10 and ask the human.
- **No autonomous redesign.** On a death (`REJECTED`/`NEAR_MISS`) the driver records the firewall-analyst post-mortem, HOLDS that candidate, and continues the other live candidates. It never auto-generates a successor — that is the lottery-search the firewall exists to prevent. A successor needs a human premise and an INDEPENDENT `DISTINCT` judgment.
- **No self-review.** The driver applies only the MECHANICAL gates (deterministic screen G1–G4 / firewall v4 eval). Every genuine judgment (the `DISTINCT` successor gate; narrative interpretation) is delegated to a FRESH `quant-analyst` instance. The agent that drives never judges its own successors.
- **Cross-batch DISTINCT.** The `DISTINCT` gate binds across batches, not only within one. Any candidate named in a new batch authorization that is a single-step G13 neighbour of — or otherwise reachable by tweak from — a dead hash in any prior batch is refused until a fresh `quant-analyst` returns `DISTINCT`. Re-nominating near-neighbours of dead candidates in a fresh batch is the family-wise version of config-tweak-and-re-run.
- **Halt + ping conditions.** Any death, any `TRADABLE`/`PROVISIONAL` verdict, any `ERROR`/`INCOMPLETE`/`INCONCLUSIVE_G11`/config-mismatch/ambiguity, or all candidates terminal.
- **Auditable.** The batch authorization (with its freeze timestamp and candidate set), the autonomous-mode flag, and the auto-confirmed-G10 precedence event are recorded as dossier events, so the full run is reconstructable after the fact.

Autonomous mode changes WHO pulls the trigger, never WHAT is permitted: every fire still passes the same interlock, the same sequential constraint, and the same capital firewall a human-driven run does — and the driver's unconditional, pre-frozen execution makes it *stricter* on optional-stopping bias than the per-step human loop it replaces.

## Rejected alternatives

- **Pure executor** that fires the funnel back-to-back. Rejected: re-implements each leaf skill's approval gate and puts a one-button path on a binding-layer FAIL → re-run.
- **Hybrid** that auto-fires only the cheap, leakage-safe, no-verdict steps (`/condition-screen`). Rejected: the moment it can fire anything it must own the approval gate, reintroducing what the leaf skills already do. The skill's value is memory + the brake, not saving keystrokes.
- **Operator-typed verdicts / soft successor gate.** Rejected: every other gate in the funnel parses a token from an artifact or a spawned agent; trusting operator attestation at the one judgment that decides whether a redesign is real is the inconsistency the brake exists to remove.

## Not decided here

- The exact dossier JSONL event schema and the verb surface (`status`/`next`/`record`/`new`/`abandon`) live in the skill's REFERENCE, not here.
- Whether the dead-neighbour refusal radius should widen beyond one G13 step (to also cover G13's `±2` second-step probe — discrete `±2`, continuous `×0.8`/`×1.2`) for ARS-shaped dead configs is deferred until that `±2` probe is itself promoted from advisory to binding inside G13.
