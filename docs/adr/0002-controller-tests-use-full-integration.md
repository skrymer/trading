# Controller tests use full integration via `AbstractIntegrationTest`

Controller-level tests extend `AbstractIntegrationTest` (full `@SpringBootTest` + Testcontainers Postgres + `TestRestTemplate`) and exercise endpoints over real HTTP. They live in the `e2e/` package and follow the `*E2ETest` naming convention already used by `WalkForwardE2ETest`, `IBKRBrokerImportE2ETest`, `MonteCarloE2ETest`, etc.

We do *not* use `@WebMvcTest` slice tests. The risks that controller tests in this codebase need to catch — `@Transactional` boundaries actually applied, DB-level `ON DELETE CASCADE` actually firing, jOOQ queries returning the expected shape, real Spring Security filter chain behaviour — are out of scope for `@WebMvcTest`. A passing `@WebMvcTest` would create false confidence that the integration works.

## Considered options

- **Mock-based controller unit tests** — fast, but silently miss `@Transactional` annotation problems, validation pipeline issues, JSON serialisation bugs, and CASCADE behaviour. Rejected.
- **`@WebMvcTest` slice tests** — catch validation, serialisation, status codes, but not transactions or DB behaviour. Rejected as primary approach: would establish a new pattern that doesn't catch the failure modes that matter most in this codebase. May be revisited if we ever have a controller whose failure surface is purely HTTP-mapping (no DB, no transactions).
- **`AbstractIntegrationTest` (chosen)** — slower (~3-5s after Postgres container warmup), but the only style that catches the load-bearing risks. Existing infrastructure is already in place; the marginal cost of adopting it is zero.

## Trade-off

Controller integration tests are slower than mocked tests. We accept that cost because the failure modes we'd silently ship with mocked tests (broken transactions, missing CASCADE, wrong JSON shape) are exactly the kind that production manifests as data corruption. The shared Postgres container amortises across all integration tests in a run, so per-test cost is small.

## Scope

Applies to: tests that exercise REST controllers (`*ControllerIT`).
Does *not* apply to: pure entity tests (plain JUnit), service-orchestration unit tests (mock the dependencies), domain-rule tests on `data class` factories/copy methods (plain JUnit). Each test type stays scoped to what it can meaningfully cover.
