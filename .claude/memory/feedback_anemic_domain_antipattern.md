---
name: Avoid anemic domain model — services are for orchestration only
description: Domain entities own their business logic; services orchestrate. The antipattern reference is Fowler. Captured in ADR 0001.
type: feedback
originSessionId: 905edcc1-6b5a-4473-a3fb-5b891b5c76fd
---
Domain entities own their business logic. Invariants and state-transition rules live as factory methods (`Entity.create(...)`) and copy methods (`entity.withX(...)`) on the data class itself, not in a service that wraps the repo with a 1-line rule. Services are for orchestration across multiple aggregates, repositories, or external systems — not for hosting one-line invariants over a single aggregate.

**Why:** This is the [Anemic Domain Model antipattern](https://martinfowler.com/bliki/AnemicDomainModel.html) (Fowler). The user explicitly anchored the principle to that reference. Anemic models scatter invariants across files and tempt callers to bypass them; rich domain models make invariants unmissable.

**How to apply:**
- When proposing a new service method that is a 1-liner over a single repo call plus a small rule (e.g., `now()` stamping, derived field), push back: the rule belongs on the entity as a factory or `withX(...)` copy method.
- When refactoring an existing service, apply the deletion test: if the service method survives without the entity, it's orchestration; if it just hosts a rule, the rule moves to the entity.
- Canonical example: `PortfolioService.createPortfolio/updatePortfolio/updateLastSyncDate` → `Portfolio.create(...)` / `withBalanceUpdated(...)` / `withSyncCompleted(...)`. See `docs/architecture/portfolio-service-deepening.md` for the worked plan.
- ADR: `docs/adr/0001-rich-domain-objects.md`.
