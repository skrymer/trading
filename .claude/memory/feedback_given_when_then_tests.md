---
name: Given / When / Then test structure
description: Tests should be written with explicit `// Given`, `// When`, `// Then` section comments separating setup, action, and assertions.
type: feedback
originSessionId: bf9ab2ae-922b-4ac8-b95e-79588cf96477
---
Every Kotlin test should be structured in three clearly commented sections:

```kotlin
@Test
fun `foo does bar when baz`() {
  // Given: what the world looks like before the action
  val fixture = ...
  whenever(mock.x()).thenReturn(...)

  // When: the single action under test
  val result = service.foo(...)

  // Then: the expected outcome
  assertEquals(expected, result)
  verify(mock).y()
}
```

**Why:** existing ScannerServiceTest cases use this structure (see `scan returns matching stocks from predefined strategy`, `checkExits detects triggered exit signals`, `validateEntries returns valid when...`). Matching it keeps the file consistent for readers scanning many tests at once, and the comments make intent unambiguous — especially for reviewers who don't already know the production code.

**How to apply:** every new `@Test` method uses the three `// Given` / `// When` / `// Then` comments, even for short tests. Prose comments explaining *why* a fixture is shaped a particular way go under `// Given`; assertion rationale goes under `// Then` (or beside the individual `assertEquals` message).
