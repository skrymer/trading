# Rich domain objects

Domain entities own their business logic. Invariants and state-transition rules — "a new portfolio's `currentBalance` equals its `initialBalance`", "a balance edit bumps `lastUpdated`", "a sync stamps both timestamps" — live as factory methods (`Portfolio.create(...)`) and copy methods (`portfolio.withBalanceUpdated(...)`) on the data class itself, not in a service that exists only to host a one-liner over the repo.

Services are for orchestration only — coordinating across multiple aggregates, repositories, or external systems (`BrokerIntegrationService`, `PortfolioStatsService.calculateStats`, `PositionService.closePosition`). A service method that is a 1-line wrapper over a single repo call plus a small rule is a sign the rule belongs on the entity.

The antipattern this guards against: Martin Fowler — [Anemic Domain Model](https://martinfowler.com/bliki/AnemicDomainModel.html).

## Aggregate roots — model owns logic AND data

When a domain rule depends on multiple persistence rows (e.g., a `Position`'s realised P&L depends on its `Execution` rows), the rich-domain rule is *not* satisfied by passing the dependent rows in as method arguments:

```kotlin
// Anemic-data: model has the logic but doesn't own the data.
// Service still pulls executions and hands them to the model.
position.realizedPnl(executions: List<Execution>): BigDecimal
position.withClosed(closeDate, executions, fxRate): Position
```

The aggregate root pattern from Eric Evans — [Domain-Driven Design](https://martinfowler.com/bliki/DDD_Aggregate.html) — promotes the multi-row bundle to a first-class type that owns both the logic and the data:

```kotlin
data class PositionWithExecutions(
  val position: Position,
  val executions: List<Execution>,
) {
  val realizedPnl: BigDecimal              // no args — aggregate has everything it needs
  fun realizedPnlBase(fxRate: BigDecimal?): BigDecimal
  fun withClosed(closeDate: LocalDate, fxRate: BigDecimal?): PositionWithExecutions
  fun withExecutionAdded(execution: Execution): PositionWithExecutions
  fun recalculated(): PositionWithExecutions
}
```

The service layer then composes between aggregates without ever performing domain logic itself:

```kotlin
fun closePosition(id: Long, closeDate: LocalDate, fxRate: BigDecimal?): Position {
  val aggregate = positionRepo.findWithExecutionsById(id)
    ?: throw NoSuchElementException("Position $id not found")
  val closed = aggregate.withClosed(closeDate, fxRate)
  val updatedPortfolio = portfolio.withRealizedPnlApplied(closed.realizedPnl, closed.totalCommissions)
  positionRepo.save(closed.position)
  portfolioRepo.save(updatedPortfolio)
  return closed.position
}
```

Test for the right shape: **the service asks the aggregate questions; it does not pull state, branch on it, and compute results.** If the service pulls a list out of the aggregate (or out of a second repository) and runs a `fold` / `sumOf` / `filter` over it before persisting, that fold belongs on the aggregate.

Cross-aggregate effects (e.g., closing a `Position` updates the parent `Portfolio`'s `currentBalance`) are correctly orchestrated at the service: the service asks `aggregate.realizedPnl`, then asks `portfolio.withRealizedPnlApplied(...)` — composing two aggregates, never reaching inside either to do the work itself.

The bare row data class (`Position`) stays as the persistence shape for queries that don't need the full aggregate (list views, summaries). The aggregate type (`PositionWithExecutions`) is the domain shape for operations that mutate state. Repositories expose both: `findById(id): Position?` for the row, `findWithExecutionsById(id): PositionWithExecutions?` for the aggregate. Callers pick the load shape that matches their need; no N+1 risk because the choice is explicit.
