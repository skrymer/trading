package com.skrymer.udgaard.data.integration.midgaard.dto

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MidgaardQuoteDtoTest {
  private fun dto(
    sma200: BigDecimal? = null,
    high52Week: BigDecimal? = null,
    low52Week: BigDecimal? = null,
  ) = MidgaardQuoteDto(
    symbol = "AAPL",
    date = LocalDate.of(2020, 1, 2),
    open = BigDecimal("100"),
    high = BigDecimal("110"),
    low = BigDecimal("95"),
    close = BigDecimal("105"),
    volume = 1000L,
    atr = null,
    adx = null,
    ema5 = null,
    ema10 = null,
    ema20 = null,
    ema50 = null,
    ema100 = null,
    ema200 = null,
    donchianUpper5 = null,
    sma50 = null,
    sma150 = null,
    sma200 = sma200,
    high52Week = high52Week,
    low52Week = low52Week,
  )

  @Test
  fun `toStockQuote preserves a null SMA or 52-week value as null rather than zero`() {
    // Given a Midgaard quote whose SMA/52-week values are undefined (insufficient history)
    val quote = dto(sma200 = null, high52Week = null, low52Week = null)

    // When mapped to the domain StockQuote
    val stockQuote = quote.toStockQuote()

    // Then the undefined indicators stay null — not silently coerced to 0.0 like the legacy EMA fields
    assertNull(stockQuote.sma200)
    assertNull(stockQuote.high52Week)
    assertNull(stockQuote.low52Week)
  }

  @Test
  fun `toStockQuote carries through a populated SMA and 52-week value`() {
    // Given a Midgaard quote with computed SMA/52-week values
    val quote = dto(sma200 = BigDecimal("101.5"), high52Week = BigDecimal("120.0"), low52Week = BigDecimal("80.0"))

    // When mapped to the domain StockQuote
    val stockQuote = quote.toStockQuote()

    // Then the values are converted to Double
    assertEquals(101.5, stockQuote.sma200)
    assertEquals(120.0, stockQuote.high52Week)
    assertEquals(80.0, stockQuote.low52Week)
  }
}
