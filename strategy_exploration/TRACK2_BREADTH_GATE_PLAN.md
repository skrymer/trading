# Track 2 — Breadth-confirmed breakout candidate (scoping)

_Created 2026-06-03. **Scoping/design doc — nothing fires until the design choice (§3) is approved.** Track 2 is the quant-validated structural fix for the [[project-minervini-vcp-breakout-rejected]] failure: a breadth-confirmed market gate replacing the binary `spyTrendUp`, as a **NEW candidate re-screened from Stage 1**, NOT a post-hoc patch on the rejected config._

## 1. Why Track 2 (recap of the Track-1 death)

EX-ATR20×SSM was REJECTED at the Component Firewall: **participate-and-lose in narrow-leadership chop**. `spyTrendUp` (SPY price > 200-EMA) is too coarse — it only stands the book aside in outright crisis (2008), but stays true through 2015–16 and 2021–23 *narrow-breadth-but-index-up* tape, where the book deployed 30–47 trades/window and bled (2023 −19.4%, 2015 −14.7% CAGR). 8 of 21 participating windows negative on 25y; in-market CAGR 9.6%.

The diagnosis points at exactly one component: **the market gate cannot see breadth rot.** A single binary index-trend gate can't distinguish "broad healthy uptrend" from "narrow index-held-up-by-megacaps uptrend," and the latter is precisely where breakouts fail back. Block B proves the breakout *premise* is real in its native broad-uptrend regime (0 negative windows, 20.8% in-mkt CAGR, genuine 2020 +56.5% recovery). **The premise is sound; only the regime selector is broken.**

## 2. What changes — ONLY the market gate

Everything is held identical to the rejected candidate EXCEPT the market-gate element. This is deliberate: it isolates the one structural change so the firewall attributes any improvement to the breadth gate, not a confounded redesign.

| Layer | Track-1 (REJECTED) | Track-2 |
|---|---|---|
| **Market gate** | `spyTrendUp` | **breadth-confirmed (see §3)** ← the only change |
| Stage-2 trend-template gate | MA-stack ∧ MA-rising ∧ %52wH ∧ %52wL ∧ RS≥70 | identical |
| VCP base | `narrowingRange(10)` ∧ `volumeDryUp(10/50/0.7)` (promoted, G14-PASS) | identical |
| Trigger | `priceNearDonchianHigh(1.5)` ∧ `volumeAboveAverage(1.3,20)` | identical |
| Exit / Ranker / Sizer | EX-ATR20 / SSM / AtrRisk 1.25%/2.0, maxPos 10 | identical |

The promoted `NarrowingRange`/`VolumeDryUp` conditions (G14-PASS, unit-tested, deployed) carry over untouched — no re-promotion, no new G14.

## 3. The design choice — which breadth gate (DECISION NEEDED)

The failure was a low *breadth level* while index trend held → the fix is a **breadth-level** gate (not a breadth-momentum gate, which can be true off a low base). Three principled constructions:

| Option | Market gate | Rationale | ARS surface |
|---|---|---|---|
| **A (recommended)** | `spyTrendUp` ∧ `breadthEma10Above50` | Keep SPY-trend for **proven crisis defense** (2008 — the one thing Track-1 did right) AND add smoothed-breadth-level>50% for **narrow-leadership defense**. Two independent regime dimensions, two distinct failure modes. Minervini's "M" = broad market health (price + breadth). | **None** — `breadthEma10Above50` is parameter-free (50% hardcoded = "majority participating", structural not fitted) |
| **B** | `breadthEma10Above50` only (replace spyTrendUp) | Pure breadth-level gate. In 2008 breadth was also low so it likely covers crisis too, but discards the *proven* GFC-defensive spyTrendUp element. | None (parameter-free) |
| **C** | `spyTrendUp` ∧ `marketBreadthAbove(50)` | Like A but raw breadth% (un-smoothed) ≥ 50. Twitchier; the 50 is a tunable → a small ARS surface to defend. | `threshold` tunable |

**Recommendation: Option A.** It (i) preserves the proven crisis-defense element, (ii) adds the missing breadth-level dimension, (iii) is **parameter-free on the new element** — there is no threshold to tune, so the candidate cannot be ARS-fitted to make the bad windows pass (the single strongest anti-snooping property available here). The 50% breadth level is structural ("a majority of stocks participating"), set by Minervini's M, not by what passes.

**Option D — purpose-built condition (fallback, not first choice).** We can author a new market condition if the existing palette proves too blunt (e.g. an explicit *index-up-but-breadth-weak divergence* gate: `spyTrendUp` true AND breadth ema10 below some divergence band — fires precisely on the narrow-leadership signature rather than on absolute breadth level). Hold this in reserve: it adds an ARS parameter surface and build cost (TDD + `/create-condition` + lookahead audit), and Option A's parameter-free gate already discriminates the failure windows (see §3b). Reach for Option D only if Option A *partially* works — most likely to rescue the borderline 2023 case — and bring it in as its own re-screened candidate with full G13/ARS discipline.

### 3b. Pre-test mechanism check (breadth data, NOT fitting)

Breadth coverage is full and non-zero 2000–2025 (252 rows/yr; §7 satisfied). Reading the parameter-free 50% line against the raw breadth ema10 in each Track-1 window (this validates the *mechanism* — does breadth distinguish the regimes — it does NOT tune anything; the 50 is fixed):

| Window | Track-1 | breadth ema10 | `breadthEma10Above50` |
|---|---|---|---|
| 2008 | clean aside | 15.5 | FALSE → aside ✓ |
| 2011 (W4) | participate-lose | 40.6 | FALSE → aside ✓ |
| 2015 | participate-lose −14.7% | 32.1 | FALSE → aside ✓ |
| 2021 | participate-lose −10.3% | 35.8 | FALSE → aside ✓ |
| 2022 | participate-lose | 17.4 | FALSE → aside ✓ |
| **2023** | participate-lose −19.4% | **51.4** | **TRUE → participate ⚠** |
| 2025 | broad | 59.3 | TRUE → participate ✓ |

The gate would stand aside in nearly every failure window on the structural 50% line — strong pre-test evidence the mechanism is aimed right. **2023 is the one borderline window** (ema10 ~51) and the most likely residual fail / Option-D trigger. (Single mid-year snapshots; the gate evaluates daily — confirmed properly only by the firewall run.)

## 4. Anti-IS-fitting discipline (the crux — why this is legitimate, not a patch)

Memory `feedback_mean_reversion_pullback_known_weakness` forbids adding a regime filter to a *failing* candidate and re-testing — that's IS-fitting to the single 25y realization. Track 2 stays on the legitimate side via four locks:

1. **New candidate, full re-screen** — not a re-run of the rejected config with a filter appended. It enters the funnel from Stage 1; the 2015–16/2021–23 windows are out-of-sample relative to the gate's *design*.
2. **Parameter-free gate (Option A)** — there is no breadth threshold to tune, so the gate physically cannot be snooped to make specific windows pass. The mechanism (broad participation) is justified *before* any test.
3. **Mechanistic justification precedes the test** — the breadth gate is Minervini's stated "M" (broad market health), the battle-plan-endorsed fix for SPY mega-cap masking. We are adding the *named missing ingredient*, not searching filters until one fits.
4. **Frozen gates, no re-tuning** — Track 2 runs against the **same frozen Component gate table** (`COMPONENT_FIREWALL_PLAN.md` §4b). The bars do not move for Track 2.

**Disclosed residual risk:** even a principled parameter-free gate, tested on the same history, could pass by luck (the breadth signal happening to correlate with the bad windows). Mitigation = the §5 success-signature check: demand the gate works by *standing aside in narrow tape*, not by uniformly thinning all trades.

## 5. Success signature — what "fixed" must look like (set BEFORE the run)

A pass is only credible if the breadth gate fixes the *specific* failure, not if it just passes gates:

- **Stand-aside in the narrow-leadership windows:** 2015–16 and 2021–23 OOS windows should flip from PARTICIPATING-and-losing (30–47 trades, negative CAGR) to **near-cash** (trades < N_min=5, DD ≤ 3% → classified STAND-ASIDE by §5). That is the mechanism working.
- **Preserve the Block B broad-regime alpha:** the 2017–2020 windows (esp. 2020 +56.5%) must remain participating and positive — the gate must not throw out the genuine edge with the chop.
- **C1a + C7 clear:** in-market CAGR rises toward the 30% floor (fewer chop-window losses dragging the geometric compound) and negative participating windows drop to ≤ 1.
- **Red flag (reject even if gates pass):** if the gate improves metrics by uniformly cutting trade counts everywhere (including Block B good windows) rather than specifically standing aside in narrow tape, it's thinning, not selecting → not a real fix.

## 6. Funnel for Track 2 (efficient — infra already built)

1. **Assemble** the Option-A entry stack (swap the market gate in the existing config). Regenerate request JSONs from the Track-1 generators (`/tmp/gen-cf-runs.py`) with the gate swapped.
2. **Stage-1 sanity (light):** the 10y 2005–2015 `/strategy-screen` is **mostly blind to the failure regime** (2015–16/2021–23 are post-2015; only 2011 overlaps) → it's a plumbing/trade-count sanity check + 2011-behavior tell, NOT the discriminating test. Don't over-weight a screen PASS.
3. **Component Firewall = the real test.** Re-fire the 4 walk-forwards (same 36/12/12, full universe) with the breadth gate; evaluate against the **frozen** §4b gates + the §5 success signature. Reuse `/tmp/fire-cf-wf.sh` + `/tmp/eval-cf.py` (swap the config).
4. If it clears: it's the first Component-Firewall-passing regime component → then `/monte-carlo`, then the deferred portfolio-blend gates (needs a 2nd component).

## 7. Prerequisites to verify before firing

- **Breadth data coverage 2000–2025 on PRD — ✅ VERIFIED (2026-06-03).** Market-breadth daily rows are full and non-zero across the span (252/yr, 2000→2025; full span 1995–2026 but pre-2000 is below the trust floor and not used). Block A/B/25y/C all covered. Prerequisite satisfied.
- **`breadthEma10Above50` fail-closed behavior:** returns false when no breadth row exists → on un-covered dates the book stands aside (safe, but could over-suppress if coverage is patchy). Confirm coverage density.
- **`/condition-screen` perf cliff** (memory `project_condition_screen_perf_cliff`): `marketBreadthIncreasing`/`sectorBreadthIncreasing` are non-terminating under the auto-sweep. `breadthEma10Above50` is parameter-free so it won't auto-sweep — but do NOT condition-screen the breadth-momentum variants. (We're skipping the isolated condition-screen anyway — a market-level regime gate's real test is the in-strategy firewall, not standalone forward-return lift.)

## 8. Open decisions
1. **Approve the gate construction** (§3 — Option A recommended).
2. **Verify breadth coverage** (§7) before assembling.
3. Then: assemble → light Stage-1 sanity → Component Firewall (frozen gates) + §5 success-signature check.

## Reference
- [[project-minervini-vcp-breakout-rejected]], `COMPONENT_FIREWALL_PLAN.md` (frozen gates §4b, result §10)
- `MINERVINI_VCP_STRATEGY_DEVELOPMENT.md` (candidate record), `REGIME_CONDITIONAL_BATTLE_PLAN.md`
- Memories: `feedback_mean_reversion_pullback_known_weakness` (IS-fitting discipline + breakout-cousin confirmation), `project_breadth_trust_floor_2000`, `project_condition_screen_perf_cliff`, `project_regime_conditional_portfolio_framework`
