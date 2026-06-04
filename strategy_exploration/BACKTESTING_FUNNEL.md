# Backtesting Funnel — methodology map

_Created 2026-06-04 · **DRAFT v1 — consolidation of existing (mostly quant-signed) pieces; pending a quant wording-review + an external deep-research verification pass.**_

The single map of how a strategy idea becomes a tradable (or rejected) candidate. Each stage,
gate, failure mode, and discipline rule lives authoritatively in a skill or ADR; this doc ties
them together and points there for detail — it does **not** restate gate tables that drift.

**How to read the provenance tags:** `[quant-signed]` = a quant fixed the threshold/rule;
`[operator]` = the single operator's risk-appetite/scope choice; `[open]` = not yet settled /
under verification.

---

## 1. The funnel at a glance

```
  idea
   │
   ▼
 /condition-screen   design-time pre-screen of ONE condition (or AND/OR stack)
   │                 → forward-return lift, firing rate, ARS sweep, regime lift, overlap
   │                 → NO verdict: rejects structurally-unsound conditions cheaply
   ▼
 /strategy-screen    fast 10y (2005-2015) walk-forward triage, RELAXED gates G1-G5
   │                 → "worth a full firewall run, or drop now?"
   ▼
 /validate-candidate 3-block firewall (binding) + Block C informational
   │                 → strict v4 gates + design-isolation (G10) + edge-decay (G11) + G13
   │                 → verdict: TRADABLE / PROVISIONAL / NEAR_MISS / REJECTED
   ▼
 promotion + G14     inline `script` → first-class condition via /create-condition,
   │                 then /verify-promotion (trade-list diff).  ← skip if built first-class
   ▼
 /monte-carlo        path risk / risk-of-ruin / edge confidence before sizing up
   │
   ▼
 30% CAGR floor      final go/no-go  [operator]
```

Tracked across stages by the **`/strategy-exploration`** dossier (a crash-safe append-only
JSONL per candidate; ADR 0008). Each candidate also gets a **`<NAME>_STRATEGY_DEVELOPMENT.md`**
dev doc, and its terminal verdict lands in **`STRATEGY_LEDGER.md`** (the registry).

| Stage | Answers | Skill | Analyst | Window | Artifact |
|---|---|---|---|---|---|
| Condition screen | Is this *condition* structurally sound? | `/condition-screen` | `condition-screen-analyst` | design-safe (excl. Block C) | — |
| Strategy screen | Worth a full firewall run? | `/strategy-screen` | `walk-forward-analyst` / `strategy-screen-analyst` | 2005-2015, 36/12/12 | `/tmp/screen-<x>.json` |
| Validate candidate | Is the edge real out-of-sample, all regimes? | `/validate-candidate` | `firewall-analyst` | A 2000-14 · B 2014-21.5 · 25y · C 2021-25 | `validate-<x>.md` |
| Promotion + G14 | Does the promoted condition reproduce the trades? | `/create-condition` + `/verify-promotion` | — | 25y trade-list diff | — |
| Monte Carlo | What's the path/ruin risk? | `/monte-carlo` | `monte-carlo-analyst` | resampled paths | saved MC json |

---

## 2. The stages

### 2.1 `/condition-screen` — design-time, single condition  [quant-signed; ADR 0007]
A diagnostic pre-screen of **one** entry condition (or AND/OR stack, incl. inline script) *before*
it is wired into a strategy. Emits forward-return **lift**, **firing rate**, an **ARS** parameter
sweep, SPY-regime lift, and Jaccard overlap with known-good conditions — **raw stats, no verdict**.
A condition that fails is dropped cheaply; one that passes is *not* validated and still runs the
full funnel. **Leakage boundary:** `endDate` is hard-capped at Block C's start (2021-01-01) so
eyeballing the screen can't leak the out-of-sample block (ADR 0007). _(Not every candidate needs
it — a pure ranker like George has no novel condition to screen; its tradability filters are
existing conditions.)_

### 2.2 `/strategy-screen` — 10y triage, relaxed gates  [quant-signed]
Fast walk-forward filter: **2005-01-01 → 2015-01-01**, IS/OOS/step **36/12/12** → **7 OOS windows**
(window + cadence are FIXED — don't tune them). Answers only *"worth a full firewall run, or drop
now?"*. **Pass all five relaxed gates to survive; fail any = reject, no softening:**
- **G1** pooled OOS edge ≥ `0.10 × riskPercentage` (per-trade edge floor, scales with risk).
- **G2** stitched aggregate OOS Sharpe ≥ **0.7**.
- **G3** ≥ **5 of 7** windows with OOS edge > 0 **AND** median per-window edge > 0.
- **G4** GFC window (OOS ~Jan2008-Jan2009) maxDD ≤ **2× median** across the 7 windows.
- **G5** if **> 50 variants** swept: raise G1 to `0.12×risk` AND require 6/7 in G3.

Survivors are **candidates, not winners**. Always **flag survivors with CAGR < 30%** so deeper
validation isn't wasted by default (the 30% floor is the operator's tradability bar, not a screen
gate). The screen request file IS the validate-candidate template. _Full detail: `/strategy-screen`
SKILL.md._

**G-RANDOM — the Random-ranker baseline (BINDING for permissive-entry + ranker-selects
candidates)  `[quant-signed]`.** When the entry stack is tradability-only and the **ranker** does
the selecting, the entire claimed alpha lives in the ranker — so run a **byte-for-byte identical
Random-ranker baseline** (only `ranker: Random`) and require the candidate to beat Random on
**blended OOS CAGR AND per-trade edge AND positive-window count**. A permissive entry defines a
long-biased basket whose ~1% per-trade beta Random harvests for free; only the Random swap
separates *ranker alpha* from *entry-universe beta*. **Beating Random only on win-rate/WFE is a
payoff-shape artifact, not signal** — don't let WFE launder a dead bottom line
(`feedback_random_ranker_baseline_mandatory`).

### 2.3 `/validate-candidate` — the 3-block firewall  [quant-signed]
The binding validation. **Block A 2000-2014 · Block B 2014-2021-H1 (COVID-inclusive) · 25-year
aggregate** are the three **binding** layers; **Block C 2021-2025** is an **informational** sanity
check (a yellow flag, never a binding fail). Strict **v4 gates** (the exact frozen table lives in
`/validate-candidate` + `COMPONENT_FIREWALL_PLAN.md §4b`) plus the anti-data-mining interlocks:
- **G10 design isolation** — the config (`config hash`) is frozen across blocks; you may not tune
  per block.
- **G11 cross-block edge decay** — `edge_B ≥ 0.5 · edge_A` (the edge must persist, not collapse).
- **G13 parameter robustness** — a TRADABLE verdict must survive **±1 step on every discrete
  tunable and ±10% on every continuous tunable**; picking the value that passes after seeing the
  OOS result is data-snooping. `[quant-signed; pending step-size sign-off in places]`
Verdict: **TRADABLE / PROVISIONAL / NEAR_MISS / INCONCLUSIVE / REJECTED**. _The exploration
state-machine treats a REJECTED `config hash` (and its ±1 neighbours) as a **dead config** —
re-running it is data-mining, hard-refused (ADR 0008)._

### 2.4 Promotion + G14 — implementation invariance  [quant-signed]
A candidate using inline `script` conditions is **NOT tradable** even on a TRADABLE verdict — the
script must be **promoted to a first-class condition** via `/create-condition`, then
`/verify-promotion` diffs the promoted trade list against the inline-script one by
`(entry_date, symbol)` over 25y (**G14**). `DIFFERS` voids the inline verdict and forces full
re-validation (a hidden tunable can shift the trade population and flip a gate). **Shortcut:**
build the conditions **first-class from the start** (via `/create-condition`, TDD) — then there is
no inline-script caveat and no G14 step. *(George's exit was built this way.)*

### 2.5 `/monte-carlo` — path risk  [quant-signed]
Resampling (block-bootstrap to preserve path dependence, or trade-shuffle) to quantify
drawdown-path risk, risk-of-ruin, and edge confidence before sizing up. Use **after** a passing
firewall verdict.

### Final gate — 30% CAGR tradable floor  [operator]
A strategy is not tradable below **30% CAGR** (final go/no-go after full validation). The screen
does NOT use it as a hard gate but always flags survivors below it.

---

## 3. Failure-mode library (the kill criteria)

Each has a signature, a meaning, and the stage it's caught at. A REJECTED config is dead; the
*premise* may or may not be.

| Mode | Signature | Reject at | Canonical example |
|---|---|---|---|
| **Lottery / regime-detector** | Edge concentrated in 1-2 OOS windows + several negative-CAGR windows; lumpy CAGR | screen | `feedback_lottery_screen_diagnostic` |
| **Aliased Regime Sensitivity (ARS)** | Non-monotone pass/fail across a parameter's ±1 neighbourhood + per-window edge sign-flips + stable trade counts | condition-screen / G13 | Idunn (`feedback_aliased_regime_sensitivity`) |
| **Participate-and-lose in narrow-leadership** | Full trade counts, clustered losses in narrow-breadth tape (2021-23); a scalar/market gate just *thins* | firewall | Minervini breakout, VZ3/MR3 (`feedback_mean_reversion_pullback_known_weakness`) |
| **Participate-and-lose in crisis** | **Distributed** positive edge across normal windows, but one undefended crisis window (2008) craters Sharpe + DD | screen (G2/G4) | George's *initial* (single-candidate) read — **corrected by G-RANDOM to capped premise** (§6) |
| **Capped premise / ties-or-loses-to Random** | A `Random` ranker on the identical skeleton matches/beats the candidate on blended CAGR + per-trade edge → the ranker carries no information; the "edge" is entry-universe beta. Win-rate/WFE wins are payoff-shape artifacts | screen (G-RANDOM) | **George** (52wk-high anchoring ranker; §6) · leveraged-ETF timing (`project_phase1_leveraged_long_etf_attempts`) |
| **Capital-aware ablation confound** | LOO/add-one ablation metrics are sizer/concurrency-contaminated; blended CAGR ~monotone in trade count | — (read qualitatively) | `feedback_ablation_metric_confound_capital_aware` |
| **Narrow-leadership "twin"** (⚠ UNVERIFIED) | Hypothesised: long-the-RS-leaders dies the breakout's death — **never actually run** | — | `STRATEGY_LEDGER.md` §B Class 4 |

---

## 4. Cross-cutting discipline (non-negotiable)

- **Never fire a backtest/screen without explicit approval** — always show the POST and wait for
  an explicit "go" (`/backtest`, `/walk-forward`, `/strategy-screen`, `/monte-carlo`). `[operator]`
- **The IS-fitting line.** You may NOT rescue a failed candidate by fitting a regime/parameter to
  the specific bad window you observed (that's data-snooping on one realization). You MAY add a
  design axis motivated **independently** of the observed failure (e.g. "a long-only momentum book
  has no exit-to-cash" is a deficiency identifiable a priori, generalizing across all crises).
  The difference is *what the change is justified by*, not whether it helps. `[quant-signed]`
- **Dead-config + lineage.** A REJECTED/NEAR_MISS `config hash` (and its ±1 neighbours) is dead —
  never re-run it. The only way forward is a redesigned candidate on a **new lineage**, and
  registering a successor requires a recorded quant analysis judging it **structurally distinct**
  from the corpse (ADR 0008; `CONTEXT.md` *Lineage*). `[quant-signed]`
- **Get expert review before persisting thresholds.** When encoding a quant threshold into a
  skill/ADR/config, draft as text → quant wording-review → then write. First encodings drift on
  units/labels (`feedback_get_expert_review_before_persisting`). `[operator]`
- **PRD deploy needs per-deploy permission; never push / open-or-merge a PR without explicit
  permission.** `[operator]`
- **Run sequentially** (the engine OOMs on concurrent backtests); **run against PRD** (port 9080,
  `X-API-Key`); **clean stale JARs** before a deploy build; **stop dev containers** before heavy
  PRD runs (they compete for the 18g heap). `[operator]`
- **Strategy-neutral naming.** Conditions and rankers describe the *mechanic* (no named-strategy in
  KDoc/metadata/tests); only the assembled *strategy* gets a Norse-god name. `[operator]`

---

## 5. Decision logic (the verdict tree)

After each stage, exactly one of:
- **ADVANCE** — passed; go to the next stage.
- **REJECT (config) — premise alive** — this config fails, but the *premise* shows real,
  distributed signal (e.g. healthy WFE, distributed edge). Carry the working *component* forward
  into a **separate, newly-designed lineage successor** with an independently-motivated fix —
  validated from scratch, NOT a rescue of the dead config. (George → defended successor.)
- **REJECT (premise)** — the *mechanism* is dead (structural failure across variants; ARS;
  capped premise that ties Random). Deprecate the premise *class* in the ledger; don't re-propose.
- **REDESIGN** — register a successor on a new lineage (requires the structural-distinctness quant
  analysis).

The hard question every rejection must answer: **is this the config or the premise?** Use the
failure-mode signature (§3) + the per-window distribution + the Random baseline to decide.

---

## 6. Worked example — George (end-to-end, 2026-06-04): how the Random gate caught a beta mirage

1. **Spec** (`GEORGE_STRATEGY_DEVELOPMENT.md`, quant-signed): a 52-week-high anchoring *ranker*
   (`min(close/52wk-high, 1.0)`), tradability-only entry, equal-weight, `maxHoldingDays(126)` OR
   `belowPercentOf52WeekHigh(25)` exit.
2. **Build** (TDD): the ranker + the two exits built **first-class** (so no G14 step), deployed to
   PRD.
3. **`/strategy-screen`** (2005-2015): aggregate OOS edge +1.01%/trade, **5/7** positive windows,
   **WFE 1.21** — but **G2 Sharpe 0.14** and **G4 GFC DD 44.7%** FAIL (the 2008 window, −34% CAGR).
4. **The trap:** the single-candidate read called this "participate-and-lose-in-crisis, premise
   alive (WFE 1.21, distributed edge), build a defended successor." **That was wrong** — it was
   crediting entry-universe beta to the ranker.
5. **G-RANDOM baseline** (byte-identical, `ranker: Random`): Random **matches** per-trade edge
   (1.08 vs 1.01) and **beats** George on blended CAGR (6.6 vs 1.1), positive windows (6/7),
   DD, and GFC survival (−2.1 vs −14.3). George wins only on win-rate (53.4 vs 50) and WFE — both
   payoff-shape artifacts.
6. **Verdict: CAPPED PREMISE — DEPRECATED.** The ~1% edge is entry-universe beta; the anchoring
   tilt is a *worse-than-noise* GFC liability (concentrates into the most-extended momentum names).
   The defended successor is **not built** — it would defend a no-information selector. The
   anchoring *class* was tested in its weakest habitat (long-only — the engine can't express the
   paper's short leg; liquidity-pre-filtered — stripped the down-cap names where the effect
   survives), so the class isn't strictly killed, but the only honest re-test (long-short decile,
   down-cap) is not buildable in a long-only engine → deprecated in the tradable universe.
7. **Lesson:** the Random baseline is what separated 1% of beta from "ranker alpha" — now a binding
   screen gate (G-RANDOM) for every permissive-entry/ranker-selects candidate.

---

## 7. Artifacts

| Artifact | What | Where |
|---|---|---|
| This map | the funnel methodology | `strategy_exploration/BACKTESTING_FUNNEL.md` |
| Per-candidate dev doc | one candidate's spec + result | `strategy_exploration/<NAME>_STRATEGY_DEVELOPMENT.md` |
| Dossier | crash-safe append-only JSONL of a candidate's transitions | `strategy_exploration/dossier/<candidate>.jsonl` |
| Ledger | the registry of tested / deprecated / testable premises | `strategy_exploration/STRATEGY_LEDGER.md` |
| Domain terms | Candidate, Config hash, Dossier, Dead config, Lineage | `CONTEXT.md` *Strategy exploration funnel* |

---

## 8. References

- **Skills:** `/condition-screen`, `/strategy-screen`, `/validate-candidate`, `/create-condition`,
  `/verify-promotion`, `/monte-carlo`, `/strategy-exploration`, `/backtest`, `/walk-forward`.
- **ADRs:** 0005 (walk-forward aggregation), 0007 (condition-screen leakage boundary),
  0008 (strategy-exploration orchestrator = non-executing data-mining brake), 0009 (RS percentile),
  0010 (long-only ⇒ defense-is-cash).
- **Frozen gate detail:** `COMPONENT_FIREWALL_PLAN.md §4b` (the regime-component variant; the
  standard v4 table lives in `/validate-candidate`).

---

## Status & provenance

This is a **consolidation draft**. The *stages and gates* are quant-signed in their source skills/
ADRs; this doc re-states families and points there for the frozen numbers (to avoid drift). Items
tagged `[open]` or `⚠ UNVERIFIED` are not settled. **Pending:** (1) a quant wording-review of this
consolidation; (2) an external **deep-research verification** of the approach against
best-practice quant methodology (operator, planned). Update the tags as those land.
