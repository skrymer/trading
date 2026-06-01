---
name: feedback-dont-memorize-unverified-hypotheses
description: "When the user offers a hypothesis (\"could be X\", \"maybe Y\") to explain something unexpected, don't save it to memory as a fact. Save it only after verifying — or save the open question as a project followup, not as a feedback memory."
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 7571f7f6-71bf-476b-8e88-d8d2a44467a6
---

If the user proposes an explanation for unexpected behaviour using hedged language ("the issue could be...", "as a follow up we need to check...", "maybe this is because..."), treat that as a hypothesis to verify, not a settled fact.

**Why:** memories shape future reasoning. A saved hypothesis framed as fact becomes load-bearing in later sessions even though it was never actually checked. The user caught this when I framed a "doc baseline might be from DEV not PRD" guess as established truth about dataset differences.

**How to apply:**
- When the user says "could be X" / "might be Y" / "as a follow up we should check Z," the right artifact is either (a) a project-followup memory naming the open question and the verification step, or (b) no memory yet — flag it inline and ask whether to verify now.
- Avoid feedback-type memories that assert a causal mechanism we haven't actually demonstrated.
- "We need to add a drift check" → project followup is fine. "DEV and PRD have materially different datasets" → only after we measure and confirm.
