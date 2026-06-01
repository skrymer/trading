---
name: feedback-get-expert-review-before-persisting
description: "When encoding domain-expert decisions into reusable artifacts (skills, configs, durable docs), get expert review BEFORE persisting — not after. The first draft routinely drifts from the expert's intent on units, thresholds, or framing."
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 7571f7f6-71bf-476b-8e88-d8d2a44467a6
---

When persisting a domain expert's decisions into a reusable artifact (skill file, ADR, config template, gate-spec doc, etc.), **get the expert to sanity-check the draft before writing the file**. Do not write the file first and review only if the user asks.

**Why:** The first encoding routinely drifts. Examples observed in this project:
- Unit conversion errors (engine reports `aggregateOosEdge` as % return, expert spec said "R-multiples" — quant caught the conversion mistake on review).
- Threshold dilution (a "5/7 windows positive" gate missed the median-positive AND-clause the quant intended).
- Misleading labels (window labelled by start date "2008-01-02 OOS" instead of regime "W1 GFC OOS" — invisible to a future reader on a different date range).
- Downstream advice that should be hard gates (multi-comparison hygiene promoted from "do this later" → "screen-level G5 gate").

**How to apply:** When writing a skill / ADR / spec that encodes another expert's threshold or methodology, split into two steps:
1. Draft the artifact's content as text (in the chat, NOT in a file).
2. Send the draft to the relevant `voltagent-domains:*` expert agent for sanity-check.
3. Apply corrections, then write the file.

If the expert was already consulted within the same session (and their verdict is in conversation context), still re-run them on the **drafted artifact wording** — verdict-in-context is not the same as wording-review. The wording is where drift happens.

Linked: [[feedback-plans-include-tests]] — same anti-pattern in a different domain (skills that omit test coverage). The general principle: durable artifacts get expert re-validation on their final wording, not just their conceptual approval.
