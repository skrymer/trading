# Validate-Candidate Reference

Gate rationale, block-boundary justification, cross-block decay math, and operational notes. See [SKILL.md](SKILL.md) for the workflow and quick-start.

## Block-boundary rationale

Quant verdict (2026-05-28): no date gaps between blocks. A regime intentionally hidden from validation is a regime the firewall doesn't cover.

| Block | Range | Macro regimes captured |
|---|---|---|
| A | 2000-01-01 → 2014-01-01 | dot-com bust (2000-02), GFC (2008-09), 2010-13 QE-era trend |
| B | 2014-01-01 → 2021-06-30 | EU debt aftermath, 2015 chop, 2018Q4 sell-off, 2020 COVID crash + V-recovery |
| C | 2021-01-01 → 2025-12-31 | 2022 inflation bear, 2023-24 narrow-leadership bull, 2025 |

Earlier drafts had a 2020-2021 gap to "cleanly separate" Block B from Block C. **Rejected**: COVID is the single most valuable OOS regime in the last 15 years; deliberately excluding it removes the strongest tail-risk test.

**Block B range correction (2026-05-28).** Original boundary was 2020-12-31; verification-run finding showed that with 36/12/12 cadence ending 2020-12-31, **no OOS window actually covers 2020** (the last OOS terminates 2020-01-01). G6 was structurally unreachable. Extended to 2021-06-30 so the W4 OOS window (2020-01-02 → 2021-01-01) covers the COVID crash + V-recovery. 6-month overlap with Block C is contained in C's IS warm-up; Block C's first OOS is 2024 under 36/12/12 cadence.

## Why 75% positive windows breaks on short blocks

`75% of 2 = 1.5 → ceil to 2 → effectively 100%`. A step-function gate where a single flat OOS window kills the candidate, regardless of magnitude. Replaced with two complementary rules for `N < 4`:

- **G4a**: no single OOS window worse than `−5% CAGR` (catches actual blowups)
- **G4b**: block-aggregate CAGR ≥ G1 threshold (catches "barely positive" stitches)

G4 ≥ 75% is retained only when `N ≥ 4` — large enough for the percentage to be meaningful.

## Why one regime mandate per block

Earlier drafts had 2008 + 2022 in G6 globally, requiring BOTH to be OOS+ in every block evaluation. This breaks on blocks that don't contain those years. The quant-verified split is **one named stress per block**:

- Block A → 2008 (only block with the GFC OOS)
- Block B → 2020 (only block with COVID OOS — possible because the COVID-inclusive range is the corrected B boundary)
- Block C → 2022 (only block with the inflation bear OOS)

Each block has exactly one regime mandate. Strategies that handle ONE crisis but not the others get rejected at the relevant block, not earlier or later.

## G7 chop regimes — block-specific

The 2018Q4 sell-off + the 2011 EU-debt chop + the 2015-H2 chop are all in different blocks now. Splitting G7:

- Block A: ≥1 of {2004, 2011, 2015-H1} positive
- Block B: ≥1 of {2015-H2, 2018-Q4} positive
- Block C: skipped (5-year block doesn't contain a documented chop regime; 2023's range-bound period is implicit in G1/G2)

A strategy that can't survive any chop period in a block is a momentum-only one-trick pony — G7 catches that.

## G10 — design isolation (Block C only)

The firewall only works if Block C is genuinely OOS for the candidate's design. G10 enforces this with a manual confirmation step before Block C fires:

1. Skill prints the candidate's full config (entry/exit/sizer/ranker/seed)
2. Skill prints the date Block B passed (the "freeze date")
3. User must type `confirmed` to fire Block C

If the config changed since the freeze date, **Block C is no longer firewalled**. The right path is to re-enter the firewall from Block A with the modified config.

## G11 — cross-block edge decay

A strategy that passes all three blocks but with sharply degrading edge from A → C was likely fit to the early regime. Even if Block C technically passes, the trajectory is a warning.

**Math:**
- `edge_decay = (edge_A − edge_C) / edge_A`
- `cagr_decay = (cagr_A − cagr_C) / cagr_A`
- G11 passes if `edge_C ≥ 0.5 × edge_A` AND `cagr_C ≥ 0.5 × cagr_A`

| Outcome | Verdict |
|---|---|
| All 3 blocks pass, G11 ok | TRADABLE |
| All 3 blocks pass, G11 fail | **PROVISIONAL** — paper-trade only |

Provisional candidates are not tradable. They've shown decay; live deployment without further diagnosis is reckless.

## NEAR_MISS — why a fifth verdict tier

Without NEAR_MISS, the firewall returns binary `TRADABLE` / `REJECTED` (plus `PROVISIONAL` / `INCONCLUSIVE_G11` edge cases). That collapses two operationally distinct cases:

1. **Structurally broken candidate** — e.g. fails G6 (regime mandate) catastrophically, or fails 5 gates by large margins. Re-design needed.
2. **"One design iteration away"** candidate — e.g. fails G3 by 0.47pp and G4 by 1 window, otherwise clean. A focused tweak (sizer change, regime filter) plausibly flips both.

Both currently get the same `REJECTED` verdict. The operator has to dig into per-gate margins to tell them apart. NEAR_MISS makes the distinction structural.

**Critical design principle**: NEAR_MISS does NOT relax the gates themselves. The deterministic gate evaluation is unchanged. NEAR_MISS is computed AFTER, by looking at HOW close the failures were to passing. The firewall stays one-way per `(strategy, sizer, ranker, position-count)` tuple — NEAR_MISS just records "one iteration away" distinctly so the operator picks the right remediation track.

### Why ≤ 2 tight failures cap

A candidate that fails 3 gates by tight margins isn't "almost passing 3 different gates" — it's "systematically slightly-off-mandate across multiple dimensions". The pattern says structural drift, not iterational. Capping at 2 (per quant 2026-05-28) prevents death-by-paper-cuts NEAR_MISS where 6 tight misses are treated as "almost tradable".

This applies to the **total** tight failures across all completed blocks. Two tight failures in Block A alone, or one tight failure in each of Block A and Block B, both qualify. Three anywhere = REJECTED.

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
| `/tmp/validate-<candidate>-block{A,B,C}.json` | Raw walk-forward result per block |
| `/tmp/validate-<candidate>-eval-block{A,B,C}.json` | Per-block gate evaluation |
| `/tmp/validate-<candidate>-summary.json` | Final summary including G11 + verdict |
| `strategy_exploration/validate-<candidate>.md` | Human-readable final report (durable) |

## Walk-forward cadence

Same cadence as `/strategy-screen`: **36-month IS / 12-month OOS / 12-month step.**

| Block | Range | OOS windows |
|---|---|---|
| A | 14y | 11 windows (2003-2013 starts) |
| B | 7.5y | 4 windows (2017-2020 starts) — last OOS covers 2020 COVID |
| C | 5y | 2 windows (2024-2025 starts) |

A 7.5y Block B gives 4 OOS windows — enough for G4's ≥75% positive rule. A 5y Block C gives only 2 — falls back to G4a + G4b.

## Wall time

Sequential per block, restart udgaard between blocks (via `/tmp/v3-fire.sh`):

| Block | Trade volume (rough) | Wall time |
|---|---|---|
| A | 14y × full universe | ~15-25 min |
| B | 7y × full universe | ~7-12 min |
| C | 5y × full universe | ~5-8 min |
| **Total (pass)** | | **~30-45 min** |
| **Total (fail at A)** | | ~15-25 min |
