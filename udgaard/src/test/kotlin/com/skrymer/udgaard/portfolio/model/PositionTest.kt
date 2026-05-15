package com.skrymer.udgaard.portfolio.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PositionTest {
  @Test
  fun `strategyGroupKey collapses the Broker Import placeholder to Unassigned`() {
    // Given: a position imported from a broker — entryStrategy is the "Broker Import" placeholder,
    // which means no strategy was actually chosen
    val position = stockPosition(entryStrategy = "Broker Import")

    // When / Then: it groups under "(Unassigned)", not as a strategy named "Broker Import"
    assertEquals("(Unassigned)", position.strategyGroupKey)
  }

  @Test
  fun `strategyGroupKey collapses a null entry strategy to Unassigned`() {
    // Given: a position with no strategy assigned at all
    val position = stockPosition(entryStrategy = null)

    // When / Then
    assertEquals("(Unassigned)", position.strategyGroupKey)
  }

  @Test
  fun `strategyGroupKey collapses a blank entry strategy to Unassigned`() {
    // Given: a position whose entry strategy is present but blank
    val position = stockPosition(entryStrategy = "  ")

    // When / Then: a blank string is not a real strategy name
    assertEquals("(Unassigned)", position.strategyGroupKey)
  }

  private fun stockPosition(entryStrategy: String?): Position =
    Position(
      id = 1L,
      portfolioId = 1L,
      symbol = "AAPL",
      underlyingSymbol = null,
      instrumentType = InstrumentType.STOCK,
      optionType = null,
      strikePrice = null,
      expirationDate = null,
      multiplier = 1,
      currentQuantity = 0,
      currentContracts = null,
      averageEntryPrice = 0.0,
      totalCost = 0.0,
      status = PositionStatus.CLOSED,
      openedDate = LocalDate.of(2026, 1, 5),
      closedDate = LocalDate.of(2026, 1, 20),
      realizedPnl = 0.0,
      rolledToPositionId = null,
      parentPositionId = null,
      entryStrategy = entryStrategy,
      exitStrategy = null,
      notes = null,
    )
}
