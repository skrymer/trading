package com.skrymer.udgaard.portfolio.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ExecutionTest {
  @Test
  fun `closingFor builds an execution that liquidates the current quantity at the given exit price`() {
    // Given: an open stock position with 100 shares
    val position = stockPosition(symbol = "AAPL", currentQuantity = 100)

    // When
    val closing = Execution.closingFor(
      position = position,
      exitPrice = 60.0,
      exitDate = LocalDate.of(2026, 1, 20),
    )

    // Then: the closing exec sells out the current quantity, on the exit date, at the exit price
    assertEquals(-100, closing.quantity, "Liquidates currentQuantity (sign-flipped)")
    assertEquals(60.0, closing.price)
    assertEquals(LocalDate.of(2026, 1, 20), closing.executionDate)
    assertEquals(position.id, closing.positionId)
    assertNull(closing.id, "New execution — no id yet")
  }

  @Test
  fun `closingFor for an option position uses the contracts count not the share count`() {
    // Given: option position with 5 contracts (currentContracts=5, currentQuantity=5 in shares-equivalent)
    val position = optionPosition(currentContracts = 5)

    // When
    val closing = Execution.closingFor(
      position = position,
      exitPrice = 2.50,
      exitDate = LocalDate.of(2026, 1, 20),
    )

    // Then: option close sells 5 contracts (signed), price is per-contract
    assertEquals(-5, closing.quantity, "Sells the current contract count")
    assertEquals(2.50, closing.price)
  }

  @Test
  fun `closingFor refuses to build a closing execution for an unsaved position`() {
    // Given: a position without an id (e.g., not yet persisted)
    val unsaved = stockPosition(symbol = "AAPL", currentQuantity = 100).copy(id = null)

    // When / Then: the factory's contract requires a saved position so the closing exec has a
    // valid foreign key — fail-fast rather than persisting a row with a null positionId.
    assertThrows(IllegalStateException::class.java) {
      Execution.closingFor(unsaved, exitPrice = 60.0, exitDate = LocalDate.of(2026, 1, 20))
    }
  }

  @Test
  fun `closingFor carries the supplied fxRateToBase onto the closing execution`() {
    // Given: open USD-quoted position in an AUD-base portfolio. The caller knows the close-time
    // FX rate (e.g., from the most recent prior execution or a Midgaard fetch).
    val position = stockPosition(symbol = "AAPL", currentQuantity = 100)

    // When
    val closing = Execution.closingFor(
      position = position,
      exitPrice = 60.0,
      exitDate = LocalDate.of(2026, 1, 20),
      fxRateToBase = 1.4,
    )

    // Then: the closing leg carries its rate so realizedPnlBase computes correctly
    assertEquals(1.4, closing.fxRateToBase, "Closing exec must carry the FX rate so the close contributes accurately")
  }

  private fun stockPosition(symbol: String, currentQuantity: Int): Position =
    Position(
      id = 42L,
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
      averageEntryPrice = 50.0,
      totalCost = 5000.0,
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

  private fun optionPosition(currentContracts: Int): Position =
    stockPosition(symbol = "NEM   C100", currentQuantity = currentContracts).copy(
      instrumentType = InstrumentType.OPTION,
      optionType = OptionType.CALL,
      strikePrice = 100.0,
      expirationDate = LocalDate.of(2026, 2, 20),
      multiplier = 100,
      currentContracts = currentContracts,
    )
}
