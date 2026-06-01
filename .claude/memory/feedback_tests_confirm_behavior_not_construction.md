---
name: feedback_tests_confirm_behavior_not_construction
description: "Tests must confirm real behaviour, not that a getter returns a constructor arg"
metadata: 
  node_type: memory
  type: feedback
  originSessionId: dd36dde1-f31a-4458-8772-e00627a556a3
---

A test whose assertion only proves a property returns the value passed into the constructor (the degenerate passthrough / "else" branch) is not worth writing — it tests the shape of the object, not what it does.

**Why:** the user rejected a `strategyGroupKey` tracer-bullet test that asserted a real strategy name passes through unchanged. The load-bearing behaviour was the *collapsing rule* (null/blank/`"Broker Import"` → `"(Unassigned)"`); the passthrough case confirms nothing the system actively does.

**How to apply:** in TDD, make the tracer bullet one of the *real* behaviour cases, not the trivial identity case. A passthrough/identity test is only worth keeping as a guard when collapsing-too-aggressively would itself be a bug — and even then it is never the tracer bullet. Sharper application of [[feedback_use_tdd_skill]].

**Same trap — caching:** the user also rejected an `assertSame(compileX(s), compileX(s))` test for a compile-once cache. A cache is a pure performance optimisation with **no functional observable** — a non-caching implementation behaves identically — so an object-identity assertion tests the implementation and breaks on valid refactors. Don't test caches at all: the behaviour tests already confirm cached results are correct, and "compiled once" is perf, not behaviour.
