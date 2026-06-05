# Absolute Calmar floor (≥ 1.5) and G9 Sharpe recalibration

Lowering the operator CAGR-appetite floor from 30% to 25% (G1) dropped the firewall's *implied* risk-adjusted floor: G1 (CAGR ≥ 25%) ∧ G2 (maxDD ≤ 25%) together force only Calmar ≥ 25/25 = **1.0**, down from the 1.2 the 30% floor implied — the bare edge of tradability. This ADR adds an **explicit binding absolute Calmar floor of 1.5** and **recalibrates G9 to Sharpe-only ≥ 0.5**, resolving deep-research audit Rec 6 / B4. It is the *absolute* risk-adjusted floor, distinct from and complementary to the *relative* (≥ SPY) Calmar gate of ADR 0013.

## The choice

| Gate | Before | After |
|---|---|---|
| G1 — CAGR | ≥ 30% | **≥ 25%** (operator appetite; the trigger, not the decision) |
| G2 — aggregate maxDD | ≤ 25% | **unchanged** — a pure pain cap |
| G9 — risk-adjusted | Sharpe ≥ 0.8 **AND** Calmar ≥ 0.5 | **Sharpe ≥ 0.5 only** |
| **G15 — absolute Calmar** (new) | — | **Calmar ≥ 1.5**, binding on Block A, Block B, 25y aggregate; informational on Block C |

All gates evaluate on the stitched-OOS curve (ADR 0005), the same Calmar the ADR-0013 relative gate uses.

## Why 1.5 (and why an explicit gate, not a tighter maxDD)

- **1.0 is marginal, not tradable.** The practitioner MAR/Calmar ladder puts 1.0–2.0 as the tradable band; an **unlevered long-only** book needs the bar *higher*, not lower, because there is no leverage drag mechanically suppressing the ratio (the "MAR > 0.5 / > 1.0 is good" CTA heuristic is calibrated on levered multi-strategy programs). Calmar = 1.0 means the worst peak-to-trough equals a full year's return — the edge of single-operator tradability, not its interior.
- **The stitched-OOS curve understates maxDD** (it omits IS-window crashes — ADR 0005/0013), so a *measured* Calmar is biased **high** vs a live curve. The floor must sit above 1.0 to absorb that known upward bias.
- **Not 1.3** (inside the stitched-curve bias band — wouldn't reliably bind a live-marginal book), **not 2.0** (overfits to the single passer VZ3-s3 at ~2.4, risks an empty feasible set and snooping-to-the-survivor). **1.5** is the defensible minimum-tradable line with margin.
- **An explicit Calmar gate, not a tighter G2.** maxDD and Calmar are partially the same lever via Calmar = CAGR/|maxDD|, but they are **not interchangeable**: maxDD is the *appetite* dimension ("how much pain"), Calmar is the *quality* dimension ("return per unit of pain"). Tightening G2 to, say, 17% to imply Calmar ≥ 1.5 at 25% CAGR would also reject a 35%-CAGR / 22%-maxDD candidate (Calmar 1.59, comfortably tradable) purely for breaching a cap tightened to manufacture a ratio. Keep G2 = 25% as a pure pain cap; bind quality directly with G15. The binding region becomes the intersection — a 25%-CAGR candidate now needs maxDD ≤ 16.7%, a 40%-CAGR one can spend the full 25% DD budget. That shape correctly demands more risk-discipline from low-return candidates.

## Why G9 becomes Sharpe-only ≥ 0.5

- **Drop the Calmar conjunct (≥ 0.5):** fully dominated by G1∧G2's implied 1.0 — it caught nothing and *documented 0.5 as the firewall's stated risk-adjusted opinion*, which is misleading. Re-homed as the standalone G15 ≥ 1.5.
- **Separate gates, not a bundle:** Calmar-quality and Sharpe-quality are different failure modes; ANDing them in one gate obscures which dimension failed. Splitting them lets the verdict name the real cause.
- **Lower Sharpe 0.8 → 0.5:** the part-in-cash bias ADR 0013 used to drop the *relative* Sharpe gate attacks an *absolute* Sharpe floor too — cash days enter the daily series as zeros that drag the per-day mean (compounded by the 0%-vs-~3%-SGOV idle-cash bug). 0.8 is an always-invested-equity number; a part-in-cash timer running a true ~0.5–0.7 in-market Sharpe is genuinely tradable and would be wrongly rejected. But unlike the relative gate, an **absolute fixed** floor does not regime-fit to the bull tape (the relative gate's fatal flaw), and it still does useful work Calmar's single-extremum maxDD misses — catching a jagged/lumpy return path. So keep an absolute Sharpe floor, recalibrated to **0.5**.

## Relationship to other gates

- **ADR 0013 (relative Calmar ≥ SPY):** G15 is the *absolute* floor (minimum tradable quality regardless of regime); ADR 0013 is the *relative* floor (beat the passive alternative, regime-adaptive). They catch different failures and coexist — a candidate can clear absolute 1.5 yet lose to a 2.0-Calmar SPY block, or beat SPY yet sit below 1.5 in a weak-SPY block.
- **ADR 0014 (deflated-Sharpe flag):** the audit's Rec 6 proposed "Calmar ≥ 1.0 AND deflated-Sharpe-flag clear" as a binding gate. The DSR-flag-as-binding half is **rejected** — ADR 0014 fixed the DSR as a reported flag, never binding. G15 is Calmar-only.

## Consequences

- Implementation is a coordinated multi-file change: `eval-block.py` (G1 floor 30→25, drop G9 Calmar, lower G9 Sharpe, add G15), `summarize.py` GATE_METADATA, the gate tables in `validate-candidate` SKILL.md / REFERENCE.md, `firewall-analyst.md`, and tests. Tracked in its own issue.
- The idle-cash 0%→~3% fix (ADR 0016) was expected to shrink the Sharpe drag, suggesting a possible upward G9 revisit. **Superseded:** ADR 0016 wires the idle-cash rate as the Sharpe `rf` so the idle leg nets to zero excess — crediting is **Sharpe-neutral**, so G9's 0.5 needs no revisit. The credit does raise Calmar for cash-heavy candidates (G15/relative bar modestly easier — see ADR 0016), with no threshold change.

## What this does NOT decide

- The idle-cash crediting fix (its own issue) and the per-trade cost model (its own issue) — both change the curves these gates read, but the gate *thresholds* here are set against the current (cost-free, 0%-cash) engine and may warrant a revisit pass once those land.
