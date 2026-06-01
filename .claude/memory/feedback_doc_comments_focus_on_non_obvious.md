---
name: Doc comments only for non-obvious context
description: KDoc/Javadoc on classes and methods should add only what isn't already conveyed by the identifier and signature. No rephrasing the method name.
type: feedback
originSessionId: e9db63a6-5658-4517-8fe1-254dedcdebcf
---
KDoc / Javadoc lives or dies by what it adds beyond a well-named identifier. If a method is `findHolidayDates(exchange)` and returns `Set<LocalDate>`, a comment like "All non-trading dates for the given exchange" is dead weight — the name and return type already say that.

**Why:** the user prefers code that reads itself and reserves comments for context that genuinely needs explaining (a hidden constraint, an invariant, a workaround, a non-obvious return shape rationale, a pointer to where the data is sourced).

**How to apply:**
- Drop comments that paraphrase the method name or the parameter list.
- Keep comments that explain: *why* a particular type was chosen (e.g. `Set` for O(1) hot-path lookup), pointers to migrations or external systems that own data, invariants the compiler can't enforce, gotchas a future maintainer would otherwise rediscover.
- Class-level doc: one short sentence on what role the class plays in the broader system if non-obvious; otherwise skip it. Don't repeat the class name.
- Default parameter values rarely need a doc — they're visible in the signature. Document them only when the default has a non-obvious *reason* (e.g. a regulatory or domain choice).
- Apply this to comments inside methods too: `// Drop synthetic-filler bars (volume=0) at the ingest boundary so they never reach the breadth query` is good; `// loop over bars` is dead weight.
