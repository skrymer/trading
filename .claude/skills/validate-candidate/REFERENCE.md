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
2. **25y aggregate alone misses edge decay** — a strategy with 1.5% edge in 2003-2009 and 0.1% edge in 2018-2024 can post 30% aggregate CAGR + low CoV (because dispersion is within-block) while being structurally degraded. Block-separated edge comparison surfaces edge decay; 22-window aggregate does not. Every dead strategy in the session (VCP under lookahead fix, MR3 with regime gates, DV1) had a Block-A-edge >> Block-B-edge signature that an aggregate would mask.
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
- Block B → 2020 COVID (binding — only block with COVID OOS — possible because the COVID-inclusive range is the corrected B boundary)
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

### Why ratio gates use a wider band (20% vs 5%)

G5 (CoV) and G9 (Sharpe/Calmar) are ratio-of-ratios metrics — they're noisier than absolute percentage gates. A 5% relative band is too tight for the underlying signal's natural variance. 20% reflects that CoV / Sharpe oscillate more across sample paths even when underlying behaviour is unchanged. Per quant: "15% is too tight for ratio-of-ratios metrics."

### `remediation_hint` derivation

The hint is derived deterministically from which gates failed, not from per-candidate analysis. It's a "first-look" token that points the operator at the most likely remediation axis — but it's not a prescription. See the firewall-analyst sub-agent for the cross-block interpretation that picks the actual track.

## G12 — block-aggregate trade count floor

G8 enforces ≥ 30 trades per window. But Block B (2 windows) × 30 = 60 trades, which is too few for block-aggregate stats to be trustworthy. G12 raises the per-block floor to ≥ 100 trades. Below that, the gate readings are noise.

## Failure handling

**Reject on first-block failure.** No retries with different sizer / seed / position count. Each retry is another shot at the same target — exactly the multiple-comparison leak the firewall exists to prevent.

Legitimate remediation: re-design the variant, re-survey it via `/strategy-screen`, and re-enter the firewall from Block A. The firewall is a one-way valve per `(strategy, sizer, ranker, position-count)` tuple.

The skill MUST refuse to "try one more seed" on a rejected candidate. If the user insists, point them at `/strategy-screen` for re-survey.

## Output files

| Path | Content |
|---|---|
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
