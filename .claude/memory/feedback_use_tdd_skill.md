---
name: Use the /tdd skill when developing code
description: When implementing any feature/bug-fix/refactor that produces code (not just docs), invoke the /tdd skill — vertical slices via red-green-refactor, never horizontal "all tests first then all code".
type: feedback
originSessionId: 905edcc1-6b5a-4473-a3fb-5b891b5c76fd
---
When the work involves writing or changing production code (a feature, a bug fix, or a refactor that ships behaviour change), invoke the `/tdd` skill at `/home/skrymer/.claude/skills/tdd/SKILL.md` rather than free-handing it.

**Why:** The TDD skill enforces vertical-slice development — one test → one implementation → repeat. Writing all tests first then all code produces tests that verify *imagined* behaviour and shape (signatures, data structures) rather than actual behaviour, and they survive refactors only because they don't really test anything. The skill explicitly guards against this anti-pattern.

**How to apply:**
- Whenever a task requires implementing or modifying production code, invoke `/tdd` early in the flow rather than starting with code.
- This includes the per-PR architecture-deepening work (Phase 1.5 PR A, PR B, etc.) — the skill applies even when the entity-API design has already been grilled out.
- Skip for: doc-only changes, status-marker commits, plan write-ups, exploration/audits where no production code is produced.
- The skill itself contains the red→green→refactor cadence and mocking guidelines; follow what it says rather than substituting personal judgement on test ordering.

This is a behavioural rule about *which skill to use*, not a debate about TDD itself — the user has already chosen TDD; the rule is that I should reach for the skill instead of winging it.
