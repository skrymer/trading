# Verify-Promotion Reference

Quant sign-off (2026-05-29) on the G14 / Implementation Invariance methodology, the diff
semantics, and design rationale. See [SKILL.md](SKILL.md) for the workflow.

## Why a trade-list diff, not a metric comparison

The motivating bug (Idunn) flipped a binding gate while the *headline metrics barely moved*:
Block B aggregate edge +0.48% → +0.36%, trades 912 → 914. A metric-tolerance check would
wave that through. But the 2020 COVID OOS window — the binding G6 gate — went +0.31% →
−0.07%, a sign flip across zero. The trade *population* shifted: a few more thin-history
symbols fired. Only a trade-by-trade identity check surfaces a population shift that
aggregate metrics smear away. G14 is an **identity gate, not an edge gate** — its job is to
certify that the inline verdict transfers to the shippable code.

## Q1 — Match key: (entry_date, symbol)

One trading decision is uniquely a `(symbol, date)` event under the daily timeframe (one
position per symbol-date). Exit date and profit are **not** part of the key: folding them in
would mislabel one held-longer trade as both a phantom add and a phantom drop, double-counting
a single real divergence. Three buckets are reported off the key:

- **ENTRY** — keys present in one list but not the other. The population shift; the Idunn mode.
- **EXIT** — matched key, different exit date (`quotes.last().date` — Trade has no stored exit quote).
- **PNL** — matched key, same exit, `profit` beyond tolerance.

## Q2 — Tolerances

- **Entry-set membership: EXACT (Jaccard == 1.0), no tolerance.** A single differing entry is,
  by construction, a real population shift — there is no rounding that adds or drops a whole
  trade without crossing a decision boundary, and a boundary cross is exactly what we must catch.
- **Exit date: exact** (a calendar date — no tolerance concept).
- **P&L on matched (entry, exit) trades: 1e-3 relative.** The quant specified 0.1% of entry
  notional, but `sharesReserved` is `@JsonIgnore`'d on the Trade DTO so notional isn't
  serialized. We realize the same intent — tolerate float noise that changes no decision — as a
  relative tolerance on the `profit` magnitude (`|a−b| ≤ 1e-3·max(|a|,|b|,ε)`). `profit` scales
  with notional, so this is equivalent for the harmless-noise purpose. Float noise is ~1e-9
  relative; 1e-3 is generous headroom while still catching a real threshold-boundary flip.

## Q3 — Binary verdict, no DIFFERS-MINOR

PASS / DIFFERS / ERROR only. An identity gate with a tolerance band on *which trades exist* is
a contradiction; Idunn proved a ~2-trade population shift flips a binding gate. The aggregate
edge delta is emitted alongside DIFFERS as a **diagnostic** so the human sees whether a binding
gate is at risk — it is never a pass lever. ERROR (configs not the same logical comparison) is a
methodology fault, not a soft DIFFERS, and hard-stops before any backtest fires.

## Q4 — Firewall precedence: DIFFERS voids, does not auto-reject

The issue's draft said "G14 fail = REJECTED regardless of other gates." The quant corrected this:
DIFFERS **voids the inline verdict and mandates fresh promoted-version validation** — it is not
an independent rejection reason. Precedence:

1. **PASS** → inline verdict transfers to the promoted config. Proceed (or reuse the firewall result).
2. **DIFFERS** → the inline-script firewall result is *discarded* (it described a trade population
   the shippable code does not produce). The promoted config must independently run the full
   binding firewall (Block A + Block B + 25y aggregate) and meet TRADABLE on its own. The inline
   result is never blended in or used as partial credit.
3. Promoted config independently TRADABLE → TRADABLE stands on the promoted result alone.
4. Promoted config fails any binding layer → REJECTED.

The escape valve and the gate are the same mechanism. The net effect for a lazy promoter
(DIFFERS + no re-run) is identical to REJECTED — the right default — without a special case for
the legitimate "intentional fix / dependency bump" path.

## Scope

G14 fires on **any change to a first-class condition's per-bar evaluation logic**, not only
inline→promotion. Any diff touching a registered `EntryCondition`/`ExitCondition`'s `evaluate()`
(or its private helpers / engine-side history-buffer constants) invalidates the most-recent
firewall verdict of *every* strategy whose config references that condition — the Idunn bug lived
in a buffer constant and the same silent shift can be reintroduced by any later edit.

Implementation: this needs a registry mapping condition-class → strategies with a live firewall
verdict, run as `old-code-build vs new-code-build` over the same window. CI has no Docker (project
norm), so ship this half as a `/pre-commit`-adjacent **advisory** that lists which TRADABLE
verdicts a condition edit puts at risk; escalate to binding once the registry exists. The
promotion case (inline→named) is the binding half that ships now.

## Q6 — No static introspection; surface the culprit from runtime data

G14 does **not** parse Kotlin to auto-detect hidden constants (like the 28-day buffer). A static
detector is brittle — it must model every way a buffer can be expressed (`max(20, …)`, an injected
default, a config field) and will both miss real culprits and false-alarm. The trade-list diff is
a complete, robust oracle for *whether* behavior diverged. To still point at the cause, the diff
emits the **first divergent (symbol, entry_date)**; the human inspects that symbol's bar
coverage / history-window at that date (the actual Idunn signature) — surfacing the cause from
runtime data, not source analysis.

## Q7 — Diff window: full 25y

G14's signal strength is monotonic in trade count, and its purpose is to catch a rare population
shift on historically-thin symbols. A Block-A-only window under-samples the COVID-era 2020 bars
where Idunn flipped. The default is the full 25y (union of all binding-layer windows). Both runs
use the same window + the configs' (equal) capital settings, with a forced-equal `randomSeed` —
a single-path backtest with differing seeds false-DIFFERS on noise, which `config_equivalence.py`
catches as ERROR.

## Data-flow notes

- Walk-forward exposes only window aggregates — **no per-trade data**. G14 must use the single
  `/api/backtest` endpoint, which returns a `backtestId`; the trade list comes from
  `GET /api/backtest/{id}/trades?startDate=&endDate=` (filters by entry date, so the full window
  returns every trade entered in it).
- Per trade the JSON carries `stockSymbol`, `entryQuote.date`, `entryQuote.closePrice`, `quotes`
  (last element's `date` = exit date), `profit`, `profitPercentage`, `exitReason`. `sharesReserved`
  lives on the `@JsonIgnore`'d `entryContext`, so notional is not available — hence the
  profit-magnitude-relative P&L tolerance above.

## Output files

| Path (under `/tmp/verify-promotion-<label>/`) | Content |
|---|---|
| `equivalence.json` | Config-equivalence precondition result (ERROR detail if mismatched) |
| `inline-resp.json` / `promoted-resp.json` | Backtest response DTOs (edge / cagr / totalTrades) |
| `inline-trades.json` / `promoted-trades.json` | Full per-trade lists from the trades endpoint |
| `diff.json` | The G14 verdict — also the value the firewall reuses |
| `report.md` | Human-readable diff narrative |
