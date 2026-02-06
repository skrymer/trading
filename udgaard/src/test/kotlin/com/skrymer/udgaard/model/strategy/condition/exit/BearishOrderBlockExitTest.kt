package com.skrymer.udgaard.model.strategy.condition.exit

import com.skrymer.udgaard.domain.OrderBlockDomain
import com.skrymer.udgaard.domain.OrderBlockType
import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.domain.StockQuoteDomain
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

class BearishOrderBlockExitTest {
  // Helper to create quotes between two dates (trading days only - Mon-Fri)
  private fun createTradingDayQuotes(
    startDate: LocalDate,
    endDate: LocalDate,
  ): List<StockQuoteDomain> {
    val quotes = mutableListOf<StockQuoteDomain>()
    var currentDate = startDate.plusDays(1)
    while (!currentDate.isAfter(endDate)) {
      if (currentDate.dayOfWeek.value < 6) {
        quotes.add(StockQuoteDomain(date = currentDate, closePrice = 100.0))
      }
      currentDate = currentDate.plusDays(1)
    }
    return quotes
  }

  @Test
  fun `should exit when within bearish order block older than threshold`() {
    val condition = BearishOrderBlockExit(orderBlockAgeInDays = 120)

    val orderBlock =
      OrderBlockDomain(
        low = 95.0,
        high = 105.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    val quoteDate = LocalDate.of(2024, 7, 1)
    val quotes = createTradingDayQuotes(LocalDate.of(2024, 1, 1), quoteDate)

    val stock =
      StockDomain(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes = quotes,
        orderBlocks = listOf(orderBlock),
      )

    val quote =
      StockQuoteDomain(
        date = quoteDate,
        closePrice = 100.0,
      )

    assertTrue(
      condition.shouldExit(stock, null, quote),
      "Should exit when within bearish order block older than threshold",
    )
  }

  @Test
  fun `should not exit when not within any bearish order block`() {
    val condition = BearishOrderBlockExit(orderBlockAgeInDays = 120)

    val orderBlock =
      OrderBlockDomain(
        low = 95.0,
        high = 105.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = LocalDate.of(2024, 5, 30),
        orderBlockType = OrderBlockType.BEARISH,
      )

    val quoteDate = LocalDate.of(2024, 3, 1)
    val quotes = createTradingDayQuotes(LocalDate.of(2024, 1, 1), quoteDate)

    val stock =
      StockDomain(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes = quotes,
        orderBlocks = mutableListOf(orderBlock),
      )

    val quote =
      StockQuoteDomain(
        date = quoteDate,
        closePrice = 110.0, // Outside order block range (> high)
      )

    assertFalse(
      condition.shouldExit(stock, null, quote),
      "Should not exit when not within bearish order block",
    )
  }

  @Test
  fun `should not exit when stock has no order blocks`() {
    val condition = BearishOrderBlockExit(orderBlockAgeInDays = 120)

    val stock =
      StockDomain(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes = mutableListOf(),
        orderBlocks = mutableListOf(),
      )

    val quote =
      StockQuoteDomain(
        date = LocalDate.of(2024, 6, 1),
        closePrice = 100.0,
      )

    assertFalse(
      condition.shouldExit(stock, null, quote),
      "Should not exit when stock has no order blocks",
    )
  }

  @Test
  fun `should work with different age thresholds`() {
    val condition = BearishOrderBlockExit(orderBlockAgeInDays = 30)

    val orderBlock =
      OrderBlockDomain(
        low = 95.0,
        high = 105.0,
        startDate = LocalDate.of(2024, 4, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    val quoteDate = LocalDate.of(2024, 5, 15)
    val quotes = createTradingDayQuotes(LocalDate.of(2024, 4, 1), quoteDate)

    val stock =
      StockDomain(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes = quotes,
        orderBlocks = mutableListOf(orderBlock),
      )

    val quote =
      StockQuoteDomain(
        date = quoteDate,
        closePrice = 100.0,
      )

    assertTrue(
      condition.shouldExit(stock, null, quote),
      "Should work with custom age threshold",
    )
  }

  @Test
  fun `should not exit when within bullish order block`() {
    val condition = BearishOrderBlockExit(orderBlockAgeInDays = 120)

    val orderBlock =
      OrderBlockDomain(
        low = 95.0,
        high = 105.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = LocalDate.of(2024, 5, 30),
        orderBlockType = OrderBlockType.BULLISH,
      )

    val quoteDate = LocalDate.of(2024, 3, 1)
    val quotes = createTradingDayQuotes(LocalDate.of(2024, 1, 1), quoteDate)

    val stock =
      StockDomain(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes = quotes,
        orderBlocks = mutableListOf(orderBlock),
      )

    val quote =
      StockQuoteDomain(
        date = quoteDate,
        closePrice = 100.0,
      )

    assertFalse(
      condition.shouldExit(stock, null, quote),
      "Should not exit when within bullish order block (only bearish blocks trigger exit)",
    )
  }

  @Test
  fun `should provide correct exit reason`() {
    val condition = BearishOrderBlockExit(orderBlockAgeInDays = 120)
    assertEquals("Price entered bearish order block (age > 120 days)", condition.exitReason())
  }

  @Test
  fun `should provide correct description`() {
    val condition = BearishOrderBlockExit(orderBlockAgeInDays = 120)
    assertEquals("Bearish order block (age > 120d)", condition.description())
  }

  @Test
  fun `should use default age of 120 days`() {
    val condition = BearishOrderBlockExit()
    assertTrue(condition.exitReason().contains("120 days"))
  }

  @Test
  fun `should provide correct metadata`() {
    val condition = BearishOrderBlockExit()
    val metadata = condition.getMetadata()

    assertEquals("bearishOrderBlock", metadata.type)
    assertEquals("Bearish Order Block", metadata.displayName)
    assertEquals("Exit when price enters a bearish order block (resistance zone)", metadata.description)
    assertEquals("ProfitTaking", metadata.category)
    assertEquals(2, metadata.parameters.size)

    val ageParam = metadata.parameters.find { it.name == "ageInDays" }
    assertNotNull(ageParam)
    assertEquals("number", ageParam?.type)
    assertEquals(120, ageParam?.defaultValue)
    assertEquals(1, ageParam?.min)
    assertEquals(365, ageParam?.max)
  }

  @Test
  fun `should NOT exit when order block is too young based on quote date`() {
    val condition = BearishOrderBlockExit(orderBlockAgeInDays = 30)

    val orderBlock =
      OrderBlockDomain(
        low = 95.0,
        high = 105.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    // Only provide a few quotes (< 30 trading days)
    val quoteDate = LocalDate.of(2024, 1, 16)
    val quotes = createTradingDayQuotes(LocalDate.of(2024, 1, 1), quoteDate)

    val stock =
      StockDomain(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes = quotes,
        orderBlocks = mutableListOf(orderBlock),
      )

    val quote =
      StockQuoteDomain(
        date = quoteDate,
        closePrice = 100.0,
      )

    assertFalse(
      condition.shouldExit(stock, null, quote),
      "Should NOT exit when order block is too young (< 30 day threshold)",
    )
  }

  @Test
  fun `should calculate age based on quote date not current date - regression test`() {
    val condition = BearishOrderBlockExit(orderBlockAgeInDays = 30)

    val orderBlock =
      OrderBlockDomain(
        low = 95.0,
        high = 105.0,
        startDate = LocalDate.of(2021, 10, 29),
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    // Testing day 10 (too young)
    val quoteDate10 = LocalDate.of(2021, 11, 8)
    val quotes10 = createTradingDayQuotes(LocalDate.of(2021, 10, 29), quoteDate10)

    val stock10 =
      StockDomain(
        symbol = "TSLA",
        sectorSymbol = "XLK",
        quotes = quotes10,
        orderBlocks = mutableListOf(orderBlock),
      )

    val quoteDay10 =
      StockQuoteDomain(
        date = quoteDate10,
        closePrice = 100.0,
      )

    assertFalse(
      condition.shouldExit(stock10, null, quoteDay10),
      "Should NOT exit on day 10 - order block is only ~7 trading days old (< 30 day threshold)",
    )

    // Testing day 35 (old enough) - provide enough quotes
    val quoteDate35 = LocalDate.of(2021, 12, 20)
    val quotes35 = createTradingDayQuotes(LocalDate.of(2021, 10, 29), quoteDate35)

    val stock35 =
      StockDomain(
        symbol = "TSLA",
        sectorSymbol = "XLK",
        quotes = quotes35,
        orderBlocks = mutableListOf(orderBlock),
      )

    val quoteDay35 =
      StockQuoteDomain(
        date = quoteDate35,
        closePrice = 100.0,
      )

    assertTrue(
      condition.shouldExit(stock35, null, quoteDay35),
      "Should exit when order block is old enough (>= 30 trading days)",
    )
  }

  @Test
  fun `should work correctly with mitigated order blocks`() {
    val condition = BearishOrderBlockExit(orderBlockAgeInDays = 30)

    val orderBlock =
      OrderBlockDomain(
        low = 95.0,
        high = 105.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = LocalDate.of(2024, 3, 1),
        orderBlockType = OrderBlockType.BEARISH,
      )

    // Quote from when block was active (Feb 15 = ~33 trading days old)
    val quoteDate = LocalDate.of(2024, 2, 15)
    val quotes = createTradingDayQuotes(LocalDate.of(2024, 1, 1), quoteDate)

    val stock =
      StockDomain(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes = quotes,
        orderBlocks = mutableListOf(orderBlock),
      )

    val quoteWhileActive =
      StockQuoteDomain(
        date = quoteDate,
        closePrice = 100.0,
      )

    assertTrue(
      condition.shouldExit(stock, null, quoteWhileActive),
      "Should exit when quote is within active period of order block",
    )

    // Quote after block was mitigated (March 15)
    val quoteAfterDate = LocalDate.of(2024, 3, 15)
    val quotesAfter = createTradingDayQuotes(LocalDate.of(2024, 1, 1), quoteAfterDate)

    val stockAfter =
      StockDomain(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes = quotesAfter,
        orderBlocks = mutableListOf(orderBlock),
      )

    val quoteAfterMitigation =
      StockQuoteDomain(
        date = quoteAfterDate,
        closePrice = 100.0,
      )

    assertFalse(
      condition.shouldExit(stockAfter, null, quoteAfterMitigation),
      "Should NOT exit when quote is after order block mitigation date",
    )
  }

  @Test
  fun `should exit when high touches order block with useHighPrice=true`() {
    val condition = BearishOrderBlockExit(orderBlockAgeInDays = 30, useHighPrice = true)

    val orderBlock =
      OrderBlockDomain(
        low = 95.0,
        high = 105.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    val quoteDate = LocalDate.of(2024, 2, 15)
    val quotes = createTradingDayQuotes(LocalDate.of(2024, 1, 1), quoteDate)

    val stock =
      StockDomain(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes = quotes,
        orderBlocks = mutableListOf(orderBlock),
      )

    val quote =
      StockQuoteDomain(
        date = quoteDate,
        closePrice = 92.0,
        high = 100.0, // Inside order block (95 < 100 < 105)
      )

    assertTrue(
      condition.shouldExit(stock, null, quote),
      "Should exit when high touches order block with useHighPrice=true",
    )
  }

  @Test
  fun `should NOT exit when high touches but useHighPrice=false (default)`() {
    val condition = BearishOrderBlockExit(orderBlockAgeInDays = 30, useHighPrice = false)

    val orderBlock =
      OrderBlockDomain(
        low = 95.0,
        high = 105.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    val quoteDate = LocalDate.of(2024, 2, 15)
    val quotes = createTradingDayQuotes(LocalDate.of(2024, 1, 1), quoteDate)

    val stock =
      StockDomain(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes = quotes,
        orderBlocks = mutableListOf(orderBlock),
      )

    val quote =
      StockQuoteDomain(
        date = quoteDate,
        closePrice = 92.0, // Below order block
        high = 100.0, // Inside order block
      )

    assertFalse(
      condition.shouldExit(stock, null, quote),
      "Should NOT exit when only high touches but useHighPrice=false (checks close price)",
    )
  }

  @Test
  fun `should exit with both high and close when both are inside order block`() {
    val orderBlock =
      OrderBlockDomain(
        low = 95.0,
        high = 105.0,
        startDate = LocalDate.of(2024, 1, 1),
        endDate = null,
        orderBlockType = OrderBlockType.BEARISH,
      )

    val quoteDate = LocalDate.of(2024, 2, 15)
    val quotes = createTradingDayQuotes(LocalDate.of(2024, 1, 1), quoteDate)

    val stock =
      StockDomain(
        symbol = "TEST",
        sectorSymbol = "XLK",
        quotes = quotes,
        orderBlocks = mutableListOf(orderBlock),
      )

    val quote =
      StockQuoteDomain(
        date = quoteDate,
        closePrice = 100.0,
        high = 103.0,
      )

    // Should exit with useHighPrice=true
    val conditionHigh = BearishOrderBlockExit(orderBlockAgeInDays = 30, useHighPrice = true)
    assertTrue(
      conditionHigh.shouldExit(stock, null, quote),
      "Should exit with useHighPrice=true when both high and close are inside",
    )

    // Should also exit with useHighPrice=false
    val conditionClose = BearishOrderBlockExit(orderBlockAgeInDays = 30, useHighPrice = false)
    assertTrue(
      conditionClose.shouldExit(stock, null, quote),
      "Should exit with useHighPrice=false when both high and close are inside",
    )
  }

  @Test
  fun `should have correct metadata with useHighPrice parameter`() {
    val condition = BearishOrderBlockExit()
    val metadata = condition.getMetadata()

    assertEquals(2, metadata.parameters.size, "Should have 2 parameters")

    val ageParam = metadata.parameters.find { it.name == "ageInDays" }
    assertNotNull(ageParam, "Should have ageInDays parameter")

    val highPriceParam = metadata.parameters.find { it.name == "useHighPrice" }
    assertNotNull(highPriceParam, "Should have useHighPrice parameter")
    assertEquals("boolean", highPriceParam?.type, "useHighPrice should be boolean type")
    assertEquals(false, highPriceParam?.defaultValue, "useHighPrice should default to false")
  }
}
