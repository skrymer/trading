# Validate-Candidate Reference

Layer rationale, block-boundary justification, cross-block decay math, 25y aggregate methodology, and operational notes. See [SKILL.md](SKILL.md) for the workflow and quick-start.

## Refined framework rationale (quant 2026-05-28, 7th consultation)

The skill ran an empirical test on VZ3-s3: it cleared Block A v4 and Block B v4 cleanly (10/10 gates each) but the original Block C (2021-2025, 1 OOS window = 2024 only) rejected on a single −0.11% edge window. When the same candidate was re-run as a 25-year aggregate walk-forward (2000-2025, 22 OOS windows), it cleared all 10 v4 gates with comfortable margins (CAGR 30.71%, DD 12.63%, Sharpe 2.21, Calmar 2.43, CoV 0.87, 18/22 positive windows). The contradiction exposed Block C's structural underpowering: under 36/12/12 cadence, a 5-year range fits only one OOS window, and a single-window verdict on edge sign is a Type I error generator.

**The refined framework** keeps Block A and Block B as binding (each properly powered, regime-separated for edge-decay detection) AND adds 25-year aggregate v4 as a third binding layer for statistical power. Block C demotes to informational — it surfaces 2024-style regime risk as a yellow flag, but a single window cannot reject a candidate that cleared the other binding layers.

| Layer | Range | Windows | Binding? | What it catches |
|---|---|---|---|---|
| **Block A v4** | 2000-01-01 → 2014-01-01 | ~10 OOS | **Binding** | Crash-survival (2008 G6), dot-com regime, QE-era trend |
| **Block B v4** | 2014-01-01 → 2021-06-30 | ~6 OOS | **Binding** | COVID-survival (2020 G6), 2018Q4 sell-off, 2015 chop |
| **25y aggregate v4** | 2000-01-01 → 2025-12-31 | ~22 OOS | **Binding** | Statistical power — G5 CoV with N=22 is a real test, G4 positivity % is well-calibrated, edge-decay if present is detectable in the per-window distribution |
| **Block C (informational)** | 2021-01-01 → 2025-12-31 | 1 OOS | Informational | 2022 inflation bear + 2024 narrow-leadership regime risk — yellow flag if negative, but doesn't bind verdict |

**Why each layer is necessary, individually:**

1. **3-block separation alone is too weak on Block C** — single-window verdicts generate Type I errors (this is what motivated the refinement).
2. **25y aggregate alone misses edge decay** — a strategy with 1.5% edge in 2003-2009 and 0.1% edge in 2018-2024 can post 30% aggregate CAGR + low CoV (because dispersion is within-block) while being structurally degraded. Block-separated edge comparison surfaces edge decay; 22-window aggregate does not. Previously-investigated dead strategies had a Block-A-edge >> Block-B-edge signature that an aggregate would mask.
3. **Block C demotes** because 5-year-range + 36/12/12 cadence = 1 OOS window. Type I error generator if binding. Demoted but not removed — its non-catastrophic check ([edge] ≤ 0.5%, DD ≤ 20%) is the gate that catches a fundamentally broken 2024-style regime without rejecting on the −0.11% noise floor.

## Block-boundary rationale

Quant verdict (2026-05-28): no date gaps between blocks. A regime intentionally hidden from validation is a regime the firewall doesn't cover.

| Block | Range | Macro regimes captured |
|---|---|---|
| A | 2000-01-01 → 2014-01-01 | dot-com bust (2000-02), GFC (2008-09), 2010-13 QE-era trend |
| B | 2014-01-01 → 2021-06-30 | EU debt aftermath, 2015 chop, 2018Q4 sell-off, 2020 COVID crash + V-recovery |
| C | 2021-01-01 → 2025-12-31 | 2022 inflation bear, 2023-24 narrow-leadership bull, 2025 — **informational only** |
| 25y aggregate | 2000-01-01 → 2025-12-31 | All of the above as 22 OOS windows under 36/12/12 cadence — full population for statistical-power gates |

Earlier drafts had a 2020-2021 gap to "cleanly separate" Block B from Block C. **Rejected**: COVID is the single most valuable OOS regime in the last 15 years; deliberately excluding it removes the strongest tail-risk test.

**Block B range correction (2026-05-28).** Original boundary was 2020-12-31; verification-run finding showed that with 36/12/12 cadence ending 2020-12-31, **no OOS window actually covers 2020** (the last OOS terminates 2020-01-01). G6 was structurally unreachable. Extended to 2021-06-30 so the W4 OOS window (2020-01-02 → 2021-01-01) covers the COVID crash + V-recovery. 6-month overlap with Block C is contained in C's IS warm-up; Block C's first OOS is 2024 under 36/12/12 cadence.

## Why 75% positive windows breaks on short blocks

`75% of 2 = 1.5 → ceil to 2 → effectively 100%`. A step-function gate where a single flat OOS window kills the candidate, regardless of magnitude. Replaced with two complementary rules for `N < 4`:

- **G4a**: no single OOS window worse than `−5% CAGR` (catches actual blowups)
- **G4b**: block-aggregate CAGR ≥ G1 threshold (catches "barely positive" stitches)

G4 ≥ 75% is retained only when `N ≥ 4` — large enough for the percentage to be meaningful.

## Why one regime mandate per block

Earlier drafts had 2008 + 2022 in G6 globally, requiring BOTH to be OOS+ in every block evaluation. This breaks on blocks that don't contain those years. The quant-verified split is **one named stress per block**:

- Block A → 2008 GFC (binding — only block with the GFC OOS) — **NEVER passed via 25y aggregate dilution**: if 2008 OOS edge ≤ 0, Block A FAILS regardless of 25y result.
- Block B → 2020 COVID (binding — only block with COVID OOS — possible because the COVID-inclusive range is the corrected B boundary). **Split into G6a (crash survival, Jan–Apr 2020 entries, edge ≥ −0.5%) + G6b (recovery, May–Dec 2020 entries, edge > 0)** — see "Why G6 is strictly strict" below.
- Block C → 2022 inflation bear (informational only — the 1-window range makes a binding G6 here structurally untestable)
- 25y aggregate → 2008 GFC (binding — confirms the Block A G6 result is reproduced in the full-history pass over the same year; structural sanity check)

Each binding layer has exactly one regime mandate. Strategies that handle ONE crisis but not the others get rejected at the relevant binding layer, not earlier or later. The 25y aggregate's G6 reuses Block A's 2008 mandate (same year, same window) — it's a consistency check, not a new gate.

## G7 chop regimes — block-specific

The 2018Q4 sell-off + the 2011 EU-debt chop + the 2015-H2 chop are all in different blocks now. Splitting G7:

- Block A: ≥1 of {2004, 2011, 2015-H1} positive
- Block B: ≥1 of {2015-H2, 2018-Q4} positive
- Block C: skipped (5-year block doesn't contain a documented chop regime; 2023's range-bound period is implicit in G1/G2)

A strategy that can't survive any chop period in a block is a momentum-only one-trick pony — G7 catches that.

## G10 — design isolation (binding layers beyond Block A)

The firewall only works if each binding layer is genuinely OOS for the candidate's design. G10 enforces this with a manual confirmation step before firing any binding layer beyond Block A:

1. Skill prints the candidate's full config (entry/exit/sizer/ranker/seed)
2. Skill prints the date the previous binding layer passed (the "freeze date")
3. User must type `confirmed` to fire the next binding layer

If the config changed since the freeze date, **the new layer is no longer firewalled**. The right path is to re-enter the firewall from Block A with the modified config.

Block C does not require G10 since it is informational only — but the pipeline still uses the same frozen config so the operator sees the consistent picture.

## G11 — cross-block edge decay (between binding blocks)

A strategy that passes all binding layers but with sharply degrading edge from Block A → Block B was likely fit to the early regime. Even if both blocks technically pass and the 25y aggregate clears, the trajectory is a warning.

**Math (applied between binding blocks A and B):**
- `edge_decay = (edge_A − edge_B) / edge_A`
- `cagr_decay = (cagr_A − cagr_B) / cagr_A`
- G11 passes if `edge_B ≥ 0.5 × edge_A` AND `cagr_B ≥ 0.5 × cagr_A`

| Outcome | Verdict |
|---|---|
| All 3 binding layers pass, G11 ok, Block C non-catastrophic | **TRADABLE** |
| All 3 binding layers pass, G11 ok, **Block C catastrophic** (\|edge\| > 0.5% OR DD > 20%) | **PROVISIONAL** — paper-trade only |
| All 3 binding layers pass, **G11 fail** (edge degraded > 50% A→B) | **PROVISIONAL** — paper-trade only |
| Any binding layer fails | **REJECTED** |

**Why G11 is A→B not A→C anymore.** The earlier framework's G11 (A→C) is no longer the binding decay test because Block C is informational. The cross-binding-block decay is now A→B; Block C's negative-edge windows are surfaced separately as the non-catastrophic check rather than folded into G11.

Provisional candidates are not tradable. They've shown decay; live deployment without further diagnosis is reckless.

## NEAR_MISS — why a fifth verdict tier

Without NEAR_MISS, the firewall returns binary `TRADABLE` / `REJECTED` (plus `PROVISIONAL` / `INCONCLUSIVE_G11` edge cases). That collapses two operationally distinct cases:

1. **Structurally broken candidate** — e.g. fails G6 (regime mandate) catastrophically, or fails 5 gates by large margins. Re-design needed.
2. **"One design iteration away"** candidate — e.g. fails G3 by 0.47pp and G4 by 1 window, otherwise clean. A focused tweak (sizer change, regime filter) plausibly flips both.

Both currently get the same `REJECTED` verdict. The operator has to dig into per-gate margins to tell them apart. NEAR_MISS makes the distinction structural.

**Critical design principle**: NEAR_MISS does NOT relax the gates themselves. The deterministic gate evaluation is unchanged. NEAR_MISS is computed AFTER, by looking at HOW close the failures were to passing. The firewall stays one-way per `(strategy, sizer, ranker, position-count)` tuple — NEAR_MISS just records "one iteration away" distinctly so the operator picks the right remediation track.

### Why ≤ 2 tight failures cap

A candidate that fails 3 gates by tight margins isn't "almost passing 3 different gates" — it's "systematically slightly-off-mandate across multiple dimensions". The pattern says structural drift, not iterational. Capping at 2 (per quant 2026-05-28) prevents death-by-paper-cuts NEAR_MISS where 6 tight misses are treated as "almost tradable".

This applies to the **total** tight failures across all binding layers (Block A + Block B + 25y aggregate). Block C tight margins are not counted toward the NEAR_MISS cap because Block C is informational. Two tight failures in Block A alone, or one tight failure in each of Block A and 25y aggregate, both qualify. Three anywhere = REJECTED.

### Why G6 is strictly strict (no near-miss)

The regime mandates exist BECAUSE crash survival is a binary safety check. A strategy that "almost survived" 2008 GFC is one that lost money in a crash. That's not a near-miss — that's the failure mode the gate was designed to catch. Per quant: "Keep the firewall hard here."

**G6a/G6b inherit this (Block B, issue #51).** Block B's G6 splits into G6a (2020 crash survival — trades entered Jan–Apr 2020, OOS edge ≥ −0.5%) and G6b (2020 recovery — trades entered May–Dec 2020, OOS edge > 0). "2020 positive overall" can mask a strategy that bled through the March crash and was rescued by the V-recovery rally; the split forces a separate read on crash survival vs regime re-entry — the exact failure mode the COVID firewall exists to catch. The Jan–Apr / May–Dec boundary is asymmetric and COVID-specific (not a calendar half-year). Each half's edge is recomputed in `eval-block.py` from the walk-forward window's per-month entry-date buckets (`outOfSampleStatsByEntryMonth`, ADR-0006) — Edge is non-linear over subsets, so the additive raw fields are summed across the relevant months and Edge recomputed once. Both sub-gates are `strict`: a failure is structural (REJECTED), never a tight-margin NEAR_MISS. Other blocks keep the single window-aggregate G6.

### Why ratio gates use a wider band (20% vs 5%)

G5 (CoV) and G9 (Sharpe/Calmar) are ratio-of-ratios metrics — they're noisier than absolute percentage gates. A 5% relative band is too tight for the underlying signal's natural variance. 20% reflects that CoV / Sharpe oscillate more across sample paths even when underlying behaviour is unchanged. Per quant: "15% is too tight for ratio-of-ratios metrics."

### `remediation_hint` derivation

The hint is derived deterministically from which gates failed, not from per-candidate analysis. It's a "first-look" token that points the operator at the most likely remediation axis — but it's not a prescription. See the firewall-analyst sub-agent for the cross-block interpretation that picks the actual track.

## G12 — block-aggregate trade count floor

G8 enforces ≥ 30 trades per window. But Block B (2 windows) × 30 = 60 trades, which is too few for block-aggregate stats to be trustworthy. G12 raises the per-block floor to ≥ 100 trades. Below that, the gate readings are noise.

## G13 — Parameter Robustness

G13 is a **fragility tripwire**, not a robustness measurement: its job is to catch the specific failure mode where a TRADABLE verdict is an artifact of a single lucky parameter value. Empirically, a one-step shift in a single tunable has moved Block B aggregate edge from clearly-positive to noise-band, flipped a chop-window edge sign (G7), and exploded G5 CoV by 4× — far beyond what the trade-sample's sampling error can explain. A sharper variant is **Aliased Regime Sensitivity (ARS)**: a non-monotone {PASS, FAIL, FAIL, PASS} pattern across the parameter neighborhood with stable trade counts and aggregate edge in the noise band — a structural disqualifier worse than simple brittleness.

After a candidate clears the binding layers and reaches TRADABLE (or TRADABLE-pending-promotion), G13 perturbs each in-scope tunable by one step and re-fires Block A + Block B on each neighbor.

### Advisory status (calibration-pending)

G13 ships **advisory** — it runs and is reported but does not change the verdict — until a known-passer sweep confirms it does not false-positive-reject a legitimate strategy. A tripwire never shown to let a good strategy through is uncalibrated in the direction that matters for adoption. There is currently no known-passer strategy, so the passer calibration runs against the first strategy to clear the firewall (or a designated reference passer once one exists). Flipping G13 to binding is a one-line change once the acceptance criteria below are met.

### Scope — which tunables are tested

`{all entry/exit condition numeric params} ∪ {maxPositions, entryDelayDays} ∪ {leverageRatio if not covered by a sizer sweep}`, MINUS any `positionSizing.sizer.*` param provably covered by a passed multi-config sizer sweep for this exact config (each excluded param is named in the report with its sweep reference). Categorical/structural fields (ranker, sectorRanking, assetTypes, IS/OOS cadence, dates, startingCapital, the script string) are not tunables.

`randomSeed` perturbation is deferred — seed dispersion is owned by the upstream multi-seed sizer sweep, which is a different question (is the edge real) from G13's (is the parameter choice fragile). The report asserts that precondition; if a candidate reaches G13 without having gone through the sizer sweep, seed perturbation must run.

**v1 implementation note:** the sizer-coverage exclusion is the target design but is not yet wired — v1 has no sweep-reference input, so it tests *all* sizer params (`riskPercentage`, `nAtr`, `leverageRatio`, …) alongside the alpha tunables. This is conservative over-testing (the safe direction per the classification rationale): it costs extra neighbor fires but cannot mask fragility. Wiring the "minus sizer params covered by a passed sweep" exclusion is a follow-up once a sweep-reference is threaded in.

### Classification — discrete vs continuous

An **explicit param-name map is the source of truth**:

- DISCRETE (± 1 step): `maxPositions`, `entryDelayDays`, `lookbackDays`, any `*Days` history requirement.
- CONTINUOUS (× 0.9 / × 1.1): `riskPercentage`, `nAtr`, `atrMultiplier`, `leverageRatio`, `*Pct`, `*Multiplier`, `*Fraction`.

JSON numeric subtype (int→discrete, float→continuous) is only the fallback for an *unrecognized* tunable name, and the fallback emits a loud warning ("classified by subtype fallback: add to map"). The map is the source of truth because mis-classifying a discrete param as continuous silently under-tests — a small nominal rounds its ±10% neighbor back onto itself — which is exactly the hole the motivating bug slips through. Mis-classifying continuous as discrete merely over-tests (the safe direction).

### Step computation + boundaries

- Discrete: `nominal − 1` and `nominal + 1`.
- Continuous: `nominal × 0.9` and `nominal × 1.1`, rounded to the number of decimals in the nominal literal (ties away from zero). The report prints **both** the requested and the actually-fired (rounded) value.
- **Fail-closed guard rail:** if a continuous ±10% step rounds back to the nominal (perturbed config byte-identical to center), that param is *not tested*, not *passed* — the step widens to the smallest representable step at its precision and is flagged. A no-op neighbor reads as a false PASS.
- **Floor-flag:** if a discrete tunable sits on its natural floor so only the +1 neighbor is valid (e.g. `maxPositions` 1, `lookbackDays` 1), the +1 neighbor must still pass AND the floor-pinning is recorded. A center with ≥1 floor-flagged tunable caps at **PROVISIONAL** (never TRADABLE) — one-sided is survivorship reasoning, not robustness.

### Verdict aggregation (when binding)

A neighbor PASSES iff both its Block A and Block B evals return `overall == PASS`. Neighbors fire **Block A + Block B only** — never the 25y aggregate (its overlapping rolling windows smear the per-window sign-flips G13 keys on) and never Block C (single-window).

| Outcome | Verdict |
|---|---|
| All neighbors PASS | **TRADABLE** retained |
| Exactly 1 neighbor fails, *near-miss on a continuous gate* (G1 within 10% rel of threshold, **G5 failing but ≤ 1.65 — within 0.15 absolute above the 1.5 ceiling**, G9 within 10% rel) AND one-directional (opposite ±1 neighbor passes clean) | **PROVISIONAL** (reason `g13_regime_sensitive_neighbor`) — *subject to the ±2 carve-out below, which may downgrade to REJECTED* |
| Any neighbor failure on a regime/binary gate (G4, G6, G7), any continuous-gate failure outside its near-miss band (G1/G9 beyond 10% rel; **G5 above 1.65, i.e. beyond 0.15 absolute above the 1.5 ceiling**), or ≥2 failing neighbors | **REJECTED** (reason `g13_parameter_fragile`) |

**No gate-specific escape valve.** An earlier draft forgave a single neighbor that failed only on G5 or G7 — but those are precisely the gates the motivating failure tripped (CoV explosion, chop sign-flip). Whitelisting them fits the gate to the known failure and reopens the exact door G13 exists to close. The only allowance is a continuous-gate *near-miss* (within sampling error of the threshold), which is genuine noise, not fragility.

**±2 carve-out (ARS protection where it can change a verdict).** ±1 is sufficient for the verdict — both a monotone edge cliff and ARS produce a ±1 binding-gate neighbor failure, so both REJECT either way; the base sweep does not widen to ±2. The one exception: if G13 would award PROVISIONAL on a continuous near-miss and that tunable's *opposite* ±1 neighbor PASSED, fire the ±2 neighbor on the failing side. ±2 also fails → edge cliff confirmed → REJECTED; ±2 recovers → the near-miss was noise → PROVISIONAL stands. Labeling cliff-vs-ARS is a v2 diagnostic; it does not change the v1 verdict.

### Calibration acceptance criteria

The step size is the right resolution when it is the smallest perturbation still **meaningfully larger than the per-window edge's sampling SE** on that tunable. Integer tunables: ±1 is forced (the quantum). Continuous: ±10% is right iff a 10% move shifts behaviour by more than sampling error — so G13 **records margins, not just pass/fail**, and compares them to the sample SE.

- **Known-failer sweep (confirms ±1 is not too coarse):** the buggy center is REJECTED at ±1, with the failing neighbor failing **G5 or G7** (matching the CoV-explosion / chop-sign-flip mechanism). A different failing gate means the mechanism doesn't match the diagnosis — investigate. Confirmatory, not blocking (G13 is designed to reject this; if it didn't, that's a logic bug).
- **Known-passer sweep (confirms ±1/±10% is not too fine — no false-positive):** swept on its alpha params, G13 must **not downgrade the passer below PROVISIONAL** (all-pass TRADABLE, or at worst one continuous near-miss with ±2 recovery). A wide-margin neighbor failure means real fragility the other gates missed; a near-miss failure means ±10% is too fine for that param (relax to ±5% and re-test). **Blocking** for verdict authority.

## G14 — Implementation Invariance

G14 closes the gap between a *validated research candidate* and the *shippable code*. A candidate authored with inline `{"type":"script"}` conditions is validated as text in a request JSON; promotion to a named first-class condition (via `/create-condition`) can silently change behaviour through a hidden tunable the firewall never saw. Idunn (2026-05-29) is the empirical case: the promoted `Pullback2of3Condition` used a dynamic history buffer `max(20, lookbackDays*2+10)` = 28 days where the inline script hardcoded 20. The 8 extra days admitted ~2 more bars of history per stock-date, firing the condition on a few more thin-history symbols. Headline metrics barely moved (Block B edge +0.48% → +0.36%, 912 → 914 trades) but the binding 2020 COVID OOS edge flipped +0.31% → −0.07% across the G6 zero-threshold. An identity check on the trade list would have caught it instantly.

**G14 is an identity gate, not an edge gate.** It diffs the promoted config's trade list against the inline-script config's, keyed by `(entry_date, symbol)`, over the full 25y binding window (the longest window maximizes the population-shift signal on thin-history symbols). The mechanism is the `/verify-promotion` skill; the firewall reuses its `diff.json` verdict.

**Verdict precedence (quant 2026-05-29).** The issue's first draft said "G14 fail = REJECTED regardless of other gates." The quant corrected this — those two halves (auto-REJECT + an escape valve for intentional fixes) are in tension. The right framing:

- **PASS** (entry-set Jaccard 1.0, no exit/PnL divergence) → the inline verdict transfers to the promoted config.
- **DIFFERS** → the inline-script firewall result is *discarded* (it described a trade population the shippable code does not produce). The promoted config must run the full binding firewall (Block A + Block B + 25y aggregate) on its own and meet TRADABLE independently. The pipeline does exactly this when it continues past a DIFFERS, so the verdict it computes IS the promoted config's own — G14 never flips that binding-PASS to REJECTED. The net effect for a lazy promoter (DIFFERS + no real re-validation) is identical to REJECTED, the right default, without a special case.
- **ERROR** (the two configs differ in anything but condition representation — universe, dates, sizer, ranker, maxPositions, capital, seed) → methodology fault; halt before firing. A single-path backtest with mismatched seeds false-DIFFERS on noise, so seed mismatch is an ERROR precondition.

**Match key + tolerances** are detailed in [verify-promotion/REFERENCE.md](../verify-promotion/REFERENCE.md): entry-set membership EXACT (a single different entry is a real population shift), exit dates exact, P&L on matched trades within 1e-3 relative (harmless float noise only). Binary PASS/DIFFERS — no graded "minor", because an identity gate with a tolerance band on *which trades exist* is a contradiction.

**Scope.** G14 also fires on any change to an existing first-class condition's per-bar `evaluate()` logic (or a history-buffer constant it depends on), not only inline→promotion — that change invalidates the firewall verdict of every strategy referencing the condition. That half ships as a `/pre-commit`-adjacent advisory until a condition-class → live-verdict registry exists; the promotion case binds now.

**Why G14 is not in `eval-block.py`.** `eval-block.py` evaluates one walk-forward result against the per-window v4 gates. G14 is a cross-*config* trade-list diff over a single backtest — a fundamentally different computation with no per-window structure. It lives in `/verify-promotion` (invoked by `run-pipeline.sh` before Block A) and is surfaced by `summarize.py`, not folded into the per-block evaluator.

## Failure handling

**Reject on first-block failure.** No retries with different sizer / seed / position count. Each retry is another shot at the same target — exactly the multiple-comparison leak the firewall exists to prevent.

Legitimate remediation: re-design the variant, re-survey it via `/strategy-screen`, and re-enter the firewall from Block A. The firewall is a one-way valve per `(strategy, sizer, ranker, position-count)` tuple.

The skill MUST refuse to "try one more seed" on a rejected candidate. If the user insists, point them at `/strategy-screen` for re-survey.

## Output files

| Path | Content |
|---|---|
| `/tmp/validate-<candidate>-g14.json` | G14 trade-list diff outcome (promoted vs inline-script) — only when an inline template is supplied |
| `/tmp/validate-<candidate>-block{A,B}.json` | Raw walk-forward results — binding layers |
| `/tmp/validate-<candidate>-25y.json` | Raw walk-forward result — binding statistical-power layer |
| `/tmp/validate-<candidate>-blockC.json` | Raw walk-forward result — informational only |
| `/tmp/validate-<candidate>-eval-block{A,B}.json` | Per-block gate evaluation — binding layers |
| `/tmp/validate-<candidate>-eval-25y.json` | 25y aggregate gate evaluation — binding layer |
| `/tmp/validate-<candidate>-eval-blockC.json` | Block C informational gate report (yellow flags surfaced, no verdict binding) |
| `/tmp/validate-<candidate>-summary.json` | Final summary including G11 (A→B), Block C non-catastrophic check + verdict |
| `strategy_exploration/validate-<candidate>.md` | Human-readable final report (durable) |

## Walk-forward cadence

Same cadence as `/strategy-screen`: **36-month IS / 12-month OOS / 12-month step.**

| Layer | Range | OOS windows | Notes |
|---|---|---|---|
| Block A | 14y | 11 windows (2003-2013 starts) | Binding |
| Block B | 7.5y | 4 windows (2017-2020 starts) — last OOS covers 2020 COVID | Binding |
| 25y aggregate | 26y | 22 windows (2003-2024 starts) | **Binding** — full-history statistical-power layer |
| Block C | 5y | 1 window (2024 start) | **Informational only** — single-window verdict was the Type I error generator that motivated the refinement |

A 7.5y Block B gives 4 OOS windows — enough for G4's ≥75% positive rule. The 25y aggregate gives 22 windows — well-powered for every v4 gate. A 5y Block C gives only 1 OOS window — too few to bind.

## Wall time

Sequential per layer, restart udgaard between layers (via `/tmp/v3-fire.sh`):

| Layer | Trade volume (rough) | Wall time |
|---|---|---|
| Block A | 14y × full universe | ~20-30 min |
| Block B | 7.5y × full universe | ~7-12 min |
| 25y aggregate | 26y × full universe | ~35-45 min |
| Block C | 5y × full universe | ~5-8 min |
| **Total (pass)** | | **~70-95 min** |
| **Total (fail at A)** | | ~20-30 min |
| **Total (fail at B or 25y)** | | ~50-80 min |

The 25y aggregate is the longest single run — it traverses the full history with the most OOS windows. Time is well-spent: it's the binding layer with the highest statistical power, and it catches strategies whose Block A + Block B numbers were within-sample fits to the early regime.

## 25-year aggregate methodology

The 25y aggregate is a single walk-forward run over 2000-01-01 → 2025-12-31 with the same 36/12/12 cadence as the per-block layers. All 10 v4 gates apply to this aggregate as the third binding layer.

**Why 36/12/12 not a different cadence:** consistency with the per-block layers means the first ~10 OOS windows of the 25y run are identical to Block A's 11 windows (same dates, same trades). This makes the per-block verdicts and the 25y aggregate verdict commensurable — they're slicing the same underlying trade tape, just with different aggregation boundaries.

**G6 in the 25y aggregate** reuses Block A's 2008 GFC OOS mandate. The 25y aggregate's W6 OOS window (2008-01-02 → 2009-01-01) is the same window as Block A's W6 — so the G6 check is a consistency reproducibility check, not a new gate.

**G7 in the 25y aggregate** uses Block A's chop list (2004, 2011, 2015-H1) because those are the years that exist in the 25y aggregate's 22-window distribution (Block B's 2018-Q4 is a smaller-time-resolution check that the annual-cadence 25y aggregate can't directly test). Block B's G7 still applies in the Block B binding layer.

**What 25y catches that the per-block layers miss:**
- Underpowered gates: G5 CoV is much more meaningful at N=22 vs N=11 (Block A) or N=4 (Block B).
- Per-window stability across the full available history: 18/22 positive (81.8% positivity) is a real fact that block-separated stats fragment.

**What 25y misses that the per-block layers catch:**
- Edge decay across regimes: a strategy with declining edge from 2003-2008 to 2018-2024 can still post 30% aggregate CAGR with low CoV if the decay is gradual. The 3-block split surfaces this; the aggregate doesn't.
- Crash survival is a specific test, not a statistic: G6 in Block A binds on the single 2008 window specifically, regardless of how many other strong windows surround it. Aggregate G6 reuses the same mandate but the per-block layer prevents 22-window dilution arguments.
