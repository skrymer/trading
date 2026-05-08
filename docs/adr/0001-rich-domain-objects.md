# Rich domain objects

Domain entities own their business logic. Invariants and state-transition rules — "a new portfolio's `currentBalance` equals its `initialBalance`", "a balance edit bumps `lastUpdated`", "a sync stamps both timestamps" — live as factory methods (`Portfolio.create(...)`) and copy methods (`portfolio.withBalanceUpdated(...)`) on the data class itself, not in a service that exists only to host a one-liner over the repo.

Services are for orchestration only — coordinating across multiple aggregates, repositories, or external systems (`BrokerIntegrationService`, `PortfolioStatsService.calculateStats`, `PositionService.closePosition`). A service method that is a 1-line wrapper over a single repo call plus a small rule is a sign the rule belongs on the entity.

The antipattern this guards against: Martin Fowler — [Anemic Domain Model](https://martinfowler.com/bliki/AnemicDomainModel.html).
