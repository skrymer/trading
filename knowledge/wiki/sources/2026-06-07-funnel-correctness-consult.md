---
type: source
title: Quant consult — funnel correctness pre-resume (G13 status · RS-momentum deprecation · RSP span)
summary: Quant pre-resume — G13 is ADVISORY not binding; RS-momentum-rotation downgraded to untested (only the George ranker is earned-dead); RSP #99 done but a Block-A 2003-span caveat remains.
status: stable
tags: [methodology, quant-consult]
sources: [".claude/skills/validate-candidate/SKILL.md", ".claude/skills/validate-candidate/REFERENCE.md", ".claude/skills/validate-candidate/scripts/g13_aggregate.py"]
related: ["[[parameter-robustness-g13]]", "[[component-firewall]]", "[[george]]", "[[btc-tyr]]", "[[2026-06-05-funnel-deepresearch-findings]]", "[[purpose]]"]
updated: 2026-06-07
---

# Quant consult — funnel correctness pre-resume (2026-06-07)

Before resuming candidate exploration, the quant adjudicated three correctness questions surfaced while
consolidating the wiki (#121). Verdicts below are checked against the **executable authority** (the
`/validate-candidate` skill + `g13_aggregate.py`), not just narrative pages.

## Q1 — G13 is ADVISORY, not binding (wiki was wrong)

**Verdict: G13 runs and is reported on every TRADABLE candidate but does NOT change the verdict — a
yellow flag, exactly like informational Block C.** This is a factual discrepancy, not a judgment call:
`SKILL.md` (§G13: "Advisory / calibration-pending — does not bind the verdict yet"), `REFERENCE.md`, and
`g13_aggregate.py` (`"binding": False`) all agree. The wiki pages calling G13 "the binding firewall gate"
overstated it.

- **Binds only after two calibration sweeps pass:** (1) a **known-failer** sweep confirming the buggy
  centre REJECTs at ±1 with the failing neighbour tripping **G5 or G7** (the CoV-explosion / chop-sign-flip
  mechanism); (2) a **known-passer** sweep confirming G13 doesn't downgrade a legitimate passer. No
  known-passer strategy exists yet (zero have cleared the firewall), so the passer sweep runs against the
  first strategy to clear it. Flipping to binding is a one-line change once both pass.
- **Step sizes (±1 discrete / ±10% continuous) are signed off as the *design* to run** — but not yet
  calibration-confirmed, which is exactly what the bind is gated on. "Design frozen" ≠ "binding."
- **Two-tier scoping** (Tier-1 invented → full sweep; Tier-2 external-provenance → one-time confirmatory,
  no-retune, cliff *demotes*) is endorsed conceptually, but is **design intent not yet wired into the
  executable scope** (`g13_neighbors.py` classifies by the discrete/continuous param-name map only; until
  wired, every tunable is swept Tier-1 — conservative over-testing). Mark `^[inferred]`.
- **Today a G13 failure is a flag, not a REJECT.** It surfaces (`g13_parameter_fragile` /
  `g13_regime_sensitive_neighbor`) alongside the verdict; it does not flip TRADABLE → REJECTED. The
  operator may treat a wide-margin fragility flag as a discretionary stop.

## Q2 — RS-momentum-rotation: downgraded from "deprecated" to "untested hypothesis"

**Verdict: the blanket "deprecated, avoid" status on *cross-sectional RS-momentum rotation* is premature.**
What actually ran and died is **[[george]]** — a *52-week-high anchoring ranker* that lost to a Random
baseline ([[beta-delivery]]); that death is earned and stays. But the *premise class* was deprecated **on
theory with no run**, and the methodology deep-research ([[2026-06-05-funnel-deepresearch-findings]] G1)
holds the twin-death analogy is *probably too strong*: momentum splits into **factor-momentum** (dies in
narrow leadership) + a durable **stock-specific/idiosyncratic** component that narrow leadership can *feed*.
Deprecating the whole class on George conflates "the one RS-flavoured ranker we ran is index beta" (true)
with "no cross-sectional RS-momentum premise can survive" (untested).

- **Encode:** three premise classes are earned-dead; RS-momentum-rotation is **downgraded to
  untested-hypothesis** — only the George (price-level anchoring ranker) flavour is dead; a
  **factor-neutral idiosyncratic-RS** variant is un-ruled-out.
- **Cheapest correct test:** a `/strategy-screen` on a **rank-and-hold** candidate whose differentiator is
  a cross-sectional **idiosyncratic-momentum rank** (relative strength after stripping the dominant
  factor/beta — e.g. residual momentum), NOT raw price momentum and NOT 52wk-high anchoring (that's
  George). `/condition-screen` is the wrong tool (can't isolate cross-sectional *selection* skill).
- **Mandatory Random-ranker baseline** (`feedback_random_ranker_baseline_mandatory`): must beat a
  byte-identical Random ranker on **blended CAGR AND per-trade edge**. The discriminator for genuine
  selection skill vs entry-universe/factor beta is **per-trade edge > Random p95 that persists in
  narrow-leadership windows (2021-2023, 2024)** — if the edge only lives in broad-leadership windows
  (2003, 2009, 2013, 2020), it's factor-momentum and the original deprecation was right after all.
- Confirm the residual-momentum inputs span Block A (2000-start) before scoping
  (`feedback_signal_must_span_firewall_window`). **Follow-up, not a blocker** — does not displace BTC+Tyr.

## Q3 — BTC+Tyr / RSP #99: data block lifted, Block-A span caveat permanent

**Verdict: #99 (RSP ingest) is CLOSED-COMPLETED (2026-06-04) — the *data-availability* block is lifted and
the RSP/SPY leadership leg is buildable — but a permanent domain caveat remains, so the status is
"un-blocked on data, span-caveated on Block A," NOT "fully unblocked."** RSP launched **2003-04-30**, so the
RSP/SPY ratio is undefined before 2003: it covers the screen window, GFC, COVID, 2022 and all of Blocks
B/C, but **truncates firewall Block A to 2003-2014, missing the 2000-2002 dot-com bear** (the 2008 GFC G6
mandate is unaffected). Disclose the dot-com gap on any RSP-based candidate; prefer the *internal*
breadth-thrust signal (full Block-A span) as the primary regime signal, with RSP/SPY as the corroborating
broad-vs-narrow detector.

## Q4 — Other issues, triaged

- **Blockers fixed in this consult's edits:** the G13 binding overstatement (Q1) — directly relevant since
  BTC+Tyr sweeps an ADX threshold for ARS, so a reader believing G13 binds could mis-adjudicate; and the
  "four deprecated classes" headline (Q2) asserting an untested exclusion as fact.
- **Follow-ups (not blockers):** report **PBO** alongside the G11 edge-decay heuristic; harden G13 to a
  **joint** grid (doubly non-urgent while G13 is advisory); **calibrate the within-regime D2 null p95** for
  cross-correlation among co-firing stress entries (Mitchell–Stafford). The D2 item is the
  highest-value follow-up and becomes a real correctness concern **the moment BTC+Tyr's breadth-thrust gate
  goes to a timing-NULL** (its gate is structurally akin to [[gjallarhorn]]'s) — flag it then.

## Pages this updated

[[parameter-robustness-g13]] · [[component-firewall]] · [[george]] · [[btc-tyr]] · [[purpose]] · [[overview]]
