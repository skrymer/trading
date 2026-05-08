package com.skrymer.udgaard.scanner.model

import com.skrymer.udgaard.portfolio.model.InstrumentType
import com.skrymer.udgaard.portfolio.model.OptionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ScannerTradeTest {
  @Test
  fun `computeRealizedPnl for STOCK uses (exit - entry) * quantity`() {
    // Given
    val trade = stockTrade(entryPrice = 100.0, quantity = 10)

    // When
    val pnl = trade.computeRealizedPnl(exitPrice = 110.0)

    // Then
    assertEquals(100.0, pnl)
  }

  @Test
  fun `computeRealizedPnl for OPTION uses (exit - optionPrice) * qty * multiplier plus rolledCredits`() {
    // Given
    val trade = optionTrade(
      entryPrice = 100.0,
      optionPrice = 5.00,
      quantity = 2,
      multiplier = 100,
      rolledCredits = 50.0,
    )

    // When: option closed at $7.50 (premium gained $2.50)
    val pnl = trade.computeRealizedPnl(exitPrice = 7.50)

    // Then: (7.50 - 5.00) * 2 * 100 + 50 = 550
    assertEquals(550.0, pnl)
  }

  @Test
  fun `computeRealizedPnl for OPTION falls back to entryPrice when optionPrice is null`() {
    // Given: legacy trade where optionPrice was never recorded
    val trade = optionTrade(
      entryPrice = 4.50,
      optionPrice = null,
      quantity = 1,
      multiplier = 100,
      rolledCredits = 0.0,
    )

    // When
    val pnl = trade.computeRealizedPnl(exitPrice = 6.00)

    // Then: (6.00 - 4.50) * 1 * 100 = 150
    assertEquals(150.0, pnl)
  }

  @Test
  fun `withClosed stamps status, prices, dates, realizedPnl, and closedAt`() {
    // Given
    val trade = stockTrade(entryPrice = 100.0, quantity = 10)
    val exitDate = LocalDate.of(2026, 5, 15)
    val closedAt = LocalDateTime.of(2026, 5, 15, 16, 0)

    // When
    val closed = trade.withClosed(exitDate = exitDate, exitPrice = 105.0, closedAt = closedAt)

    // Then
    assertEquals(TradeStatus.CLOSED, closed.status)
    assertEquals(105.0, closed.exitPrice)
    assertEquals(exitDate, closed.exitDate)
    assertEquals(50.0, closed.realizedPnl)
    assertEquals(closedAt, closed.closedAt)
    // unrelated fields preserved
    assertEquals(trade.entryPrice, closed.entryPrice)
    assertEquals(trade.symbol, closed.symbol)
  }

  @Test
  fun `withNotes returns a copy with the new notes value`() {
    // Given
    val trade = stockTrade(entryPrice = 100.0, quantity = 10).copy(notes = "old")

    // When
    val updated = trade.withNotes("new")

    // Then
    assertEquals("new", updated.notes)
    assertEquals(trade.symbol, updated.symbol)
    assertEquals(trade.entryPrice, updated.entryPrice)
  }

  private fun stockTrade(
    entryPrice: Double,
    quantity: Int,
  ) = ScannerTrade(
    id = 1L,
    symbol = "AAPL",
    sectorSymbol = "XLK",
    instrumentType = InstrumentType.STOCK,
    entryPrice = entryPrice,
    entryDate = LocalDate.of(2026, 5, 1),
    quantity = quantity,
    optionType = null,
    strikePrice = null,
    expirationDate = null,
    entryStrategyName = "TestEntry",
    exitStrategyName = "TestExit",
    notes = null,
  )

  private fun optionTrade(
    entryPrice: Double,
    optionPrice: Double?,
    quantity: Int,
    multiplier: Int,
    rolledCredits: Double,
  ) = ScannerTrade(
    id = 1L,
    symbol = "AAPL",
    sectorSymbol = "XLK",
    instrumentType = InstrumentType.OPTION,
    entryPrice = entryPrice,
    entryDate = LocalDate.of(2026, 5, 1),
    quantity = quantity,
    optionType = OptionType.CALL,
    strikePrice = 100.0,
    expirationDate = LocalDate.of(2026, 6, 19),
    multiplier = multiplier,
    optionPrice = optionPrice,
    rolledCredits = rolledCredits,
    entryStrategyName = "TestEntry",
    exitStrategyName = "TestExit",
    notes = null,
  )
}
