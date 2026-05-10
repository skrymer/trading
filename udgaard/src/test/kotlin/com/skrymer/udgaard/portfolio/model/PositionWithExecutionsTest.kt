package com.skrymer.udgaard.portfolio.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PositionWithExecutionsTest {
  @Test
  fun `realizedPnl for a stock with one buy and one sell is the price difference times shares`() {
    // Given: AAPL, bought 100 @ $50, sold 100 @ $60. Stocks have multiplier = 1.
    val position = stockPosition(symbol = "AAPL", currentQuantity = 0)
    val executions = listOf(
      execution(quantity = 100, price = 50.0, date = LocalDate.of(2026, 1, 5)),
      execution(quantity = -100, price = 60.0, date = LocalDate.of(2026, 1, 20)),
    )
    val aggregate = PositionWithExecutions(position = position, executions = executions)

    // When / Then
    assertEquals(1000.0, aggregate.realizedPnl, "(60 − 50) × 100 shares × 1 multiplier")
  }

  @Test
  fun `totalCommissions sums execution commissions and treats null as zero`() {
    // Given: three executions — two with commissions, one with null
    val executions = listOf(
      execution(quantity = 100, price = 50.0, date = LocalDate.of(2026, 1, 5), commission = -1.5),
      execution(quantity = -40, price = 55.0, date = LocalDate.of(2026, 1, 10), commission = null),
      execution(quantity = -60, price = 60.0, date = LocalDate.of(2026, 1, 20), commission = -2.25),
    )
    val aggregate = PositionWithExecutions(
      position = stockPosition(symbol = "AAPL", currentQuantity = 0),
      executions = executions,
    )

    // When / Then: -1.5 + 0 + -2.25 = -3.75; the null-commission row contributes nothing.
    assertEquals(-3.75, aggregate.totalCommissions, "Commissions sum across executions; null treated as 0")
  }

  @Test
  fun `realizedPnlBase weights each execution by its own fxRateToBase`() {
    // Given: AUD-base portfolio holding USD-quoted stock. The buy and sell happen at different
    // FX rates, so the base-currency P&L isn't just realizedPnl × an average rate — each leg
    // is weighted by the rate AT THAT TRADE.
    val executions = listOf(
      execution(quantity = 100, price = 50.0, date = LocalDate.of(2026, 1, 5), fxRateToBase = 1.50),
      execution(quantity = -100, price = 60.0, date = LocalDate.of(2026, 1, 20), fxRateToBase = 1.40),
    )
    val aggregate = PositionWithExecutions(
      position = stockPosition(symbol = "AAPL", currentQuantity = 0),
      executions = executions,
    )

    // When / Then:
    //   totalBoughtBase = 100 × 50 × 1.50 = 7500 AUD
    //   totalSoldBase   = 100 × 60 × 1.40 = 8400 AUD
    //   realizedPnlBase = (8400 − 7500) × 1 = 900 AUD
    // Note: contrast with realizedPnl in trade currency (USD) = (60−50) × 100 = 1000 USD,
    // which at a flat 1.45 rate would yield 1450 AUD. Per-execution weighting matters.
    assertEquals(900.0, aggregate.realizedPnlBase, "Each leg weighted by its trade-time fxRateToBase")
  }

  @Test
  fun `realizedPnl for an option position applies the contract multiplier`() {
    // Given: 2 contracts of NEM C100, bought @ $1.50, sold @ $2.00. Standard contract = 100 shares.
    val position = optionPosition(symbol = "NEM   C100", multiplier = 100)
    val executions = listOf(
      execution(quantity = 2, price = 1.50, date = LocalDate.of(2026, 1, 5)),
      execution(quantity = -2, price = 2.00, date = LocalDate.of(2026, 1, 20)),
    )
    val aggregate = PositionWithExecutions(position = position, executions = executions)

    // When / Then: (2.00 − 1.50) × 2 contracts × 100 multiplier = $100 P&L on the underlying.
    assertEquals(100.0, aggregate.realizedPnl, "Option P&L = price diff × contracts × multiplier")
  }

  @Test
  fun `withClosed sets status CLOSED, closedDate, currentQuantity 0, and writes realizedPnl onto the position`() {
    // Given: open stock position with realised executions but realizedPnl not yet stored
    val open = stockPosition(symbol = "AAPL", currentQuantity = 100)
    val executions = listOf(
      execution(quantity = 100, price = 50.0, date = LocalDate.of(2026, 1, 5)),
      execution(quantity = -100, price = 60.0, date = LocalDate.of(2026, 1, 20)),
    )
    val aggregate = PositionWithExecutions(position = open, executions = executions)

    // When
    val closeDate = LocalDate.of(2026, 1, 20)
    val closed = aggregate.withClosed(closeDate)

    // Then: position transitions atomically — status, closedDate, currentQuantity, realizedPnl all updated
    assertEquals(PositionStatus.CLOSED, closed.position.status)
    assertEquals(closeDate, closed.position.closedDate)
    assertEquals(0, closed.position.currentQuantity)
    assertEquals(1000.0, closed.position.realizedPnl)
  }

  @Test
  fun `withClosed returns a new aggregate without mutating the original`() {
    // Given: open aggregate
    val open = stockPosition(symbol = "AAPL", currentQuantity = 100)
    val executions = listOf(
      execution(quantity = 100, price = 50.0, date = LocalDate.of(2026, 1, 5)),
      execution(quantity = -100, price = 60.0, date = LocalDate.of(2026, 1, 20)),
    )
    val original = PositionWithExecutions(position = open, executions = executions)

    // When
    val closed = original.withClosed(LocalDate.of(2026, 1, 20))

    // Then: original unchanged, closed is a different instance
    assertEquals(PositionStatus.OPEN, original.position.status, "Original aggregate's position must not mutate")
    assertEquals(100, original.position.currentQuantity)
    assertEquals(null, original.position.realizedPnl)
    assertEquals(PositionStatus.CLOSED, closed.position.status)
  }

  @Test
  fun `withExecutionAdded appends the execution and recomputes derived values`() {
    // Given: aggregate with one buy
    val buy = execution(quantity = 100, price = 50.0, date = LocalDate.of(2026, 1, 5))
    val original = PositionWithExecutions(
      position = stockPosition(symbol = "AAPL", currentQuantity = 100),
      executions = listOf(buy),
    )
    assertEquals(0.0, original.realizedPnl, "Pre-add: only a buy → no realised P&L yet")

    // When: append a sell
    val sell = execution(quantity = -100, price = 60.0, date = LocalDate.of(2026, 1, 20))
    val updated = original.withExecutionAdded(sell)

    // Then: executions list grew, realised P&L derives from the new contents
    assertEquals(2, updated.executions.size)
    assertEquals(sell, updated.executions.last())
    assertEquals(1000.0, updated.realizedPnl)
  }

  @Test
  fun `recalculated resets the running average on a zero-crossing so avgEntryPrice reflects the latest leg`() {
    // Given: roll-shape executions — buy fully out, then re-open at a different price.
    // Without the reset, avgEntryPrice would blend $50 and $80; with the reset, it picks up
    // only the latest leg ($80).
    val executions = listOf(
      execution(quantity = 100, price = 50.0, date = LocalDate.of(2026, 1, 5)),
      execution(quantity = -100, price = 55.0, date = LocalDate.of(2026, 1, 10)),
      execution(quantity = 50, price = 80.0, date = LocalDate.of(2026, 1, 15)),
    )
    val aggregate = PositionWithExecutions(
      position = stockPosition(symbol = "AAPL", currentQuantity = 0).copy(averageEntryPrice = 0.0),
      executions = executions,
    )

    // When
    val recalculated = aggregate.recalculated()

    // Then
    assertEquals(50, recalculated.position.currentQuantity, "Latest leg quantity")
    assertEquals(80.0, recalculated.position.averageEntryPrice, "Reset on zero-crossing → only latest buy contributes")
    // totalCost is sum of all buys × multiplier (for stats display): 100×50 + 50×80 = 9000
    assertEquals(9000.0, recalculated.position.totalCost)
  }

  @Test
  fun `recalculated syncs currentContracts to currentQuantity for option positions`() {
    // Given: option position. After buy 5 → sell 2, the aggregate's currentContracts must mirror
    // currentQuantity (3), not stay null or stale.
    val position = optionPosition(symbol = "NEM   C100", multiplier = 100)
    val executions = listOf(
      execution(quantity = 5, price = 1.50, date = LocalDate.of(2026, 1, 5)),
      execution(quantity = -2, price = 2.00, date = LocalDate.of(2026, 1, 10)),
    )
    val aggregate = PositionWithExecutions(position = position, executions = executions)

    // When
    val recalculated = aggregate.recalculated()

    // Then
    assertEquals(3, recalculated.position.currentQuantity)
    assertEquals(3, recalculated.position.currentContracts, "Options track currentContracts mirroring currentQuantity")
  }

  @Test
  fun `realizedPnl on a partially closed position only includes the matched shares not the open ones`() {
    // Given: bought 100, sold only 40. The 60 still-open shares must NOT show up as a $3000 loss
    // (their cost basis hasn't been settled). Only the 40 matched shares contribute.
    val executions = listOf(
      execution(quantity = 100, price = 50.0, date = LocalDate.of(2026, 1, 5)),
      execution(quantity = -40, price = 60.0, date = LocalDate.of(2026, 1, 20)),
    )
    val aggregate = PositionWithExecutions(
      position = stockPosition(symbol = "AAPL", currentQuantity = 60),
      executions = executions,
    )

    // When / Then: 40 × (60 − 50) × 1 = $400 — the 60 unmatched shares contribute $0.
    assertEquals(400.0, aggregate.realizedPnl, "Partial close: only matched shares contribute")
  }

  @Test
  fun `derived values are zero for an aggregate with no executions`() {
    // Given: position with no executions yet (e.g., manually created, never traded)
    val aggregate = PositionWithExecutions(
      position = stockPosition(symbol = "AAPL", currentQuantity = 0),
      executions = emptyList(),
    )

    // When / Then
    assertEquals(0.0, aggregate.realizedPnl)
    assertEquals(0.0, aggregate.totalCommissions)
    assertEquals(null, aggregate.realizedPnlBase, "No executions → no FX-rate signal at all → null")
  }

  private fun optionPosition(symbol: String, multiplier: Int): Position =
    stockPosition(symbol = symbol, currentQuantity = 0).copy(
      instrumentType = InstrumentType.OPTION,
      optionType = OptionType.CALL,
      strikePrice = 100.0,
      expirationDate = LocalDate.of(2026, 2, 20),
      multiplier = multiplier,
      currentContracts = 0,
    )

  private fun stockPosition(symbol: String, currentQuantity: Int): Position =
    Position(
      id = 1L,
      portfolioId = 1L,
      symbol = symbol,
      underlyingSymbol = null,
      instrumentType = InstrumentType.STOCK,
      optionType = null,
      strikePrice = null,
      expirationDate = null,
      multiplier = 1,
      currentQuantity = currentQuantity,
      currentContracts = null,
      averageEntryPrice = 0.0,
      totalCost = 0.0,
      status = PositionStatus.OPEN,
      openedDate = LocalDate.of(2026, 1, 5),
      closedDate = null,
      realizedPnl = null,
      rolledToPositionId = null,
      parentPositionId = null,
      entryStrategy = null,
      exitStrategy = null,
      notes = null,
    )

  private fun execution(
    quantity: Int,
    price: Double,
    date: LocalDate,
    commission: Double? = null,
    fxRateToBase: Double? = null,
  ): Execution =
    Execution(
      id = null,
      positionId = 1L,
      brokerTradeId = null,
      linkedBrokerTradeId = null,
      quantity = quantity,
      price = price,
      executionDate = date,
      executionTime = null,
      commission = commission,
      fxRateToBase = fxRateToBase,
      notes = null,
    )
}
