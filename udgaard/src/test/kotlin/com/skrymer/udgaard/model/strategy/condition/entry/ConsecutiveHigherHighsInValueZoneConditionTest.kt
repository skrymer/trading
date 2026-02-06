package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.domain.StockQuoteDomain
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ConsecutiveHigherHighsInValueZoneConditionTest {
  @Test
  fun `should return true when 3 consecutive higher closes all in value zone`() {
    val condition = ConsecutiveHigherHighsInValueZoneCondition(consecutiveDays = 3, atrMultiplier = 2.0, emaPeriod = 20)

    val stock =
      StockDomain(
        quotes =
          mutableListOf(
            StockQuoteDomain(
              date = LocalDate.of(2023, 12, 31),
              high = 100.0,
              closePrice = 100.0,
              closePriceEMA20 = 100.0,
              atr = 2.0,
            ), // Entry point (not checked for VZ)
            StockQuoteDomain(
              date = LocalDate.of(2024, 1, 1),
              high = 101.0,
              closePrice = 101.0,
              closePriceEMA20 = 100.0,
              atr = 2.0,
            ), // Value zone: 100-104, close: 101.0 ✓
            StockQuoteDomain(
              date = LocalDate.of(2024, 1, 2),
              high = 102.0,
              closePrice = 102.0,
              closePriceEMA20 = 100.0,
              atr = 2.0,
            ), // Value zone: 100-104, close: 102.0 ✓
            StockQuoteDomain(
              date = LocalDate.of(2024, 1, 3),
              high = 103.0,
              closePrice = 103.0,
              closePriceEMA20 = 100.0,
              atr = 2.0,
            ), // Value zone: 100-104, close: 103.0 ✓
          ),
      )

    val quote = stock.quotes[3] // Most recent

    assertTrue(
      condition.evaluate(stock, quote),
      "Condition should be true when 3 consecutive higher closes are all in value zone",
    )
  }

  @Test
  fun `should return false when closes are not consecutively higher`() {
    val condition = ConsecutiveHigherHighsInValueZoneCondition(consecutiveDays = 3, atrMultiplier = 2.0, emaPeriod = 20)

    val stock =
      StockDomain(
        quotes =
          mutableListOf(
            StockQuoteDomain(
              date = LocalDate.of(2023, 12, 31),
              high = 100.0,
              closePrice = 100.0,
              closePriceEMA20 = 100.0,
              atr = 2.0,
            ),
            StockQuoteDomain(
              date = LocalDate.of(2024, 1, 1),
              high = 103.0,
              closePrice = 102.0, // Highest close
              closePriceEMA20 = 100.0,
              atr = 2.0,
            ),
            StockQuoteDomain(
              date = LocalDate.of(2024, 1, 2),
              high = 102.0,
              closePrice = 101.0, // Lower close
              closePriceEMA20 = 100.0,
              atr = 2.0,
            ),
            StockQuoteDomain(
              date = LocalDate.of(2024, 1, 3),
              high = 103.0,
              closePrice = 101.5, // Still lower than first
              closePriceEMA20 = 100.0,
              atr = 2.0,
            ),
          ),
      )

    val quote = stock.quotes[3]

    assertFalse(
      condition.evaluate(stock, quote),
      "Condition should be false when closes are not consecutively increasing",
    )
  }

  @Test
  fun `should return false when one day is outside value zone`() {
    val condition = ConsecutiveHigherHighsInValueZoneCondition(consecutiveDays = 3, atrMultiplier = 2.0, emaPeriod = 20)

    val stock =
      StockDomain(
        quotes =
          mutableListOf(
            StockQuoteDomain(
              date = LocalDate.of(2023, 12, 31),
              high = 100.0,
              closePrice = 100.0,
              closePriceEMA20 = 100.0,
              atr = 2.0,
            ),
            StockQuoteDomain(
              date = LocalDate.of(2024, 1, 1),
              high = 101.0,
              closePrice = 101.0,
              closePriceEMA20 = 100.0,
              atr = 2.0,
            ), // Value zone: 100-104 ✓
            StockQuoteDomain(
              date = LocalDate.of(2024, 1, 2),
              high = 102.0,
              closePrice = 105.0, // ABOVE value zone
              closePriceEMA20 = 100.0,
              atr = 2.0,
            ), // ✗
            StockQuoteDomain(
              date = LocalDate.of(2024, 1, 3),
              high = 103.0,
              closePrice = 103.0,
              closePriceEMA20 = 100.0,
              atr = 2.0,
            ), // Value zone: 100-104 ✓
          ),
      )

    val quote = stock.quotes[3]

    assertFalse(
      condition.evaluate(stock, quote),
      "Condition should be false when one day's close price is outside value zone",
    )
  }

  @Test
  fun `should return false when price is below EMA`() {
    val condition = ConsecutiveHigherHighsInValueZoneCondition(consecutiveDays = 3, atrMultiplier = 2.0, emaPeriod = 20)

    val stock =
      StockDomain(
        quotes =
          mutableListOf(
            StockQuoteDomain(
              date = LocalDate.of(2023, 12, 31),
              high = 100.0,
              closePrice = 98.0,
              closePriceEMA20 = 100.0,
              atr = 2.0,
            ),
            StockQuoteDomain(
              date = LocalDate.of(2024, 1, 1),
              high = 101.0,
              closePrice = 99.0, // BELOW EMA
              closePriceEMA20 = 100.0,
              atr = 2.0,
            ),
            StockQuoteDomain(
              date = LocalDate.of(2024, 1, 2),
              high = 102.0,
              closePrice = 101.0,
              closePriceEMA20 = 100.0,
              atr = 2.0,
            ),
            StockQuoteDomain(
              date = LocalDate.of(2024, 1, 3),
              high = 103.0,
              closePrice = 102.0,
              closePriceEMA20 = 100.0,
              atr = 2.0,
            ),
          ),
      )

    val quote = stock.quotes[3]

    assertFalse(
      condition.evaluate(stock, quote),
      "Condition should be false when one day's close is below EMA",
    )
  }

  @Test
  fun `should return false when insufficient historical data`() {
    val condition = ConsecutiveHigherHighsInValueZoneCondition(consecutiveDays = 3, atrMultiplier = 2.0, emaPeriod = 20)

    val stock =
      StockDomain(
        quotes =
          mutableListOf(
            StockQuoteDomain(
              date = LocalDate.of(2024, 1, 2),
              high = 102.0,
              closePrice = 102.0,
              closePriceEMA20 = 100.0,
              atr = 2.0,
            ),
            StockQuoteDomain(
              date = LocalDate.of(2024, 1, 3),
              high = 103.0,
              closePrice = 103.0,
              closePriceEMA20 = 100.0,
              atr = 2.0,
            ),
          ),
      )

    val quote = stock.quotes[1] // Only 2 quotes, need 4 (N+1)

    assertFalse(
      condition.evaluate(stock, quote),
      "Condition should be false when insufficient historical data",
    )
  }

  @Test
  fun `should work with 2 consecutive days`() {
    val condition = ConsecutiveHigherHighsInValueZoneCondition(consecutiveDays = 2, atrMultiplier = 2.0, emaPeriod = 20)

    val stock =
      StockDomain(
        quotes =
          mutableListOf(
            StockQuoteDomain(
              date = LocalDate.of(2024, 1, 1),
              high = 101.0,
              closePrice = 101.0,
              closePriceEMA20 = 100.0,
              atr = 2.0,
            ), // Entry point
            StockQuoteDomain(
              date = LocalDate.of(2024, 1, 2),
              high = 102.0,
              closePrice = 102.0,
              closePriceEMA20 = 100.0,
              atr = 2.0,
            ),
            StockQuoteDomain(
              date = LocalDate.of(2024, 1, 3),
              high = 103.0,
              closePrice = 103.0,
              closePriceEMA20 = 100.0,
              atr = 2.0,
            ),
          ),
      )

    val quote = stock.quotes[2]

    assertTrue(
      condition.evaluate(stock, quote),
      "Condition should work with 2 consecutive days",
    )
  }

  @Test
  fun `should work with 5 consecutive days`() {
    val condition = ConsecutiveHigherHighsInValueZoneCondition(consecutiveDays = 5, atrMultiplier = 2.0, emaPeriod = 20)

    val stock =
      StockDomain(
        quotes =
          mutableListOf(
            StockQuoteDomain(date = LocalDate.of(2023, 12, 31), high = 100.0, closePrice = 100.0, closePriceEMA20 = 100.0, atr = 2.0),
            StockQuoteDomain(date = LocalDate.of(2024, 1, 1), high = 101.0, closePrice = 101.0, closePriceEMA20 = 100.0, atr = 2.0),
            StockQuoteDomain(date = LocalDate.of(2024, 1, 2), high = 101.5, closePrice = 101.5, closePriceEMA20 = 100.0, atr = 2.0),
            StockQuoteDomain(date = LocalDate.of(2024, 1, 3), high = 102.0, closePrice = 102.0, closePriceEMA20 = 100.0, atr = 2.0),
            StockQuoteDomain(date = LocalDate.of(2024, 1, 4), high = 102.5, closePrice = 102.5, closePriceEMA20 = 100.0, atr = 2.0),
            StockQuoteDomain(date = LocalDate.of(2024, 1, 5), high = 103.0, closePrice = 103.0, closePriceEMA20 = 100.0, atr = 2.0),
          ),
      )

    val quote = stock.quotes[5]

    assertTrue(
      condition.evaluate(stock, quote),
      "Condition should work with 5 consecutive days",
    )
  }

  @Test
  fun `should work with different ATR multiplier`() {
    val condition = ConsecutiveHigherHighsInValueZoneCondition(consecutiveDays = 3, atrMultiplier = 3.0, emaPeriod = 20)

    val stock =
      StockDomain(
        quotes =
          mutableListOf(
            StockQuoteDomain(
              date = LocalDate.of(2023, 12, 31),
              high = 100.0,
              closePrice = 104.0,
              closePriceEMA20 = 100.0,
              atr = 2.0,
            ), // Entry point
            StockQuoteDomain(
              date = LocalDate.of(2024, 1, 1),
              high = 101.0,
              closePrice = 105.0, // Would be outside with 2.0 multiplier
              closePriceEMA20 = 100.0,
              atr = 2.0,
            ), // Value zone: 100-106 with 3.0 multiplier ✓
            StockQuoteDomain(
              date = LocalDate.of(2024, 1, 2),
              high = 102.0,
              closePrice = 105.5,
              closePriceEMA20 = 100.0,
              atr = 2.0,
            ),
            StockQuoteDomain(
              date = LocalDate.of(2024, 1, 3),
              high = 103.0,
              closePrice = 105.9,
              closePriceEMA20 = 100.0,
              atr = 2.0,
            ),
          ),
      )

    val quote = stock.quotes[3]

    assertTrue(
      condition.evaluate(stock, quote),
      "Condition should work with different ATR multiplier (wider value zone)",
    )
  }

  @Test
  fun `should work with different EMA period`() {
    val condition = ConsecutiveHigherHighsInValueZoneCondition(consecutiveDays = 3, atrMultiplier = 2.0, emaPeriod = 10)

    val stock =
      StockDomain(
        quotes =
          mutableListOf(
            StockQuoteDomain(
              date = LocalDate.of(2023, 12, 31),
              high = 100.0,
              closePrice = 100.0,
              closePriceEMA10 = 100.0,
              atr = 2.0,
            ), // Entry point
            StockQuoteDomain(
              date = LocalDate.of(2024, 1, 1),
              high = 101.0,
              closePrice = 101.0,
              closePriceEMA10 = 100.0,
              atr = 2.0,
            ),
            StockQuoteDomain(
              date = LocalDate.of(2024, 1, 2),
              high = 102.0,
              closePrice = 102.0,
              closePriceEMA10 = 100.0,
              atr = 2.0,
            ),
            StockQuoteDomain(
              date = LocalDate.of(2024, 1, 3),
              high = 103.0,
              closePrice = 103.0,
              closePriceEMA10 = 100.0,
              atr = 2.0,
            ),
          ),
      )

    val quote = stock.quotes[3]

    assertTrue(
      condition.evaluate(stock, quote),
      "Condition should work with different EMA period",
    )
  }

  @Test
  fun `should return true when closes are equal (matching Pine Script behavior)`() {
    val condition = ConsecutiveHigherHighsInValueZoneCondition(consecutiveDays = 3, atrMultiplier = 2.0, emaPeriod = 20)

    val stock =
      StockDomain(
        quotes =
          mutableListOf(
            StockQuoteDomain(
              date = LocalDate.of(2023, 12, 31),
              high = 101.0,
              closePrice = 101.0,
              closePriceEMA20 = 100.0,
              atr = 2.0,
            ), // Entry point
            StockQuoteDomain(
              date = LocalDate.of(2024, 1, 1),
              high = 102.0,
              closePrice = 102.0,
              closePriceEMA20 = 100.0,
              atr = 2.0,
            ),
            StockQuoteDomain(
              date = LocalDate.of(2024, 1, 2),
              high = 101.0,
              closePrice = 102.0, // EQUAL close (allowed with >=)
              closePriceEMA20 = 100.0,
              atr = 2.0,
            ),
            StockQuoteDomain(
              date = LocalDate.of(2024, 1, 3),
              high = 103.0,
              closePrice = 103.0,
              closePriceEMA20 = 100.0,
              atr = 2.0,
            ),
          ),
      )

    val quote = stock.quotes[3]

    assertTrue(
      condition.evaluate(stock, quote),
      "Condition should be true when closes are equal (>= allows consolidation)",
    )
  }

  @Test
  fun `should provide correct description with default parameters`() {
    val condition = ConsecutiveHigherHighsInValueZoneCondition()
    assertEquals("3 consecutive higher closes in value zone (20EMA to 20EMA + 2.0ATR)", condition.description())
  }

  @Test
  fun `should provide correct description with custom parameters`() {
    val condition = ConsecutiveHigherHighsInValueZoneCondition(consecutiveDays = 5, atrMultiplier = 3.0, emaPeriod = 10)
    assertEquals("5 consecutive higher closes in value zone (10EMA to 10EMA + 3.0ATR)", condition.description())
  }

  @Test
  fun `should provide correct metadata`() {
    val condition = ConsecutiveHigherHighsInValueZoneCondition()
    val metadata = condition.getMetadata()

    assertEquals("consecutiveHigherHighsInValueZone", metadata.type)
    assertEquals("Consecutive Higher Closes in Value Zone", metadata.displayName)
    assertEquals("Price Action", metadata.category)
    assertEquals(3, metadata.parameters.size)

    val consecutiveDaysParam = metadata.parameters.find { it.name == "consecutiveDays" }
    assertNotNull(consecutiveDaysParam)
    assertEquals("number", consecutiveDaysParam?.type)
    assertEquals(3, consecutiveDaysParam?.defaultValue)
    assertEquals(2, consecutiveDaysParam?.min)
    assertEquals(10, consecutiveDaysParam?.max)

    val emaPeriodParam = metadata.parameters.find { it.name == "emaPeriod" }
    assertNotNull(emaPeriodParam)
    assertEquals("number", emaPeriodParam?.type)
    assertEquals(20, emaPeriodParam?.defaultValue)

    val atrMultiplierParam = metadata.parameters.find { it.name == "atrMultiplier" }
    assertNotNull(atrMultiplierParam)
    assertEquals("number", atrMultiplierParam?.type)
    assertEquals(2.0, atrMultiplierParam?.defaultValue)
  }

  @Test
  fun `should provide detailed evaluation result when passed`() {
    val condition = ConsecutiveHigherHighsInValueZoneCondition(consecutiveDays = 3, atrMultiplier = 2.0, emaPeriod = 20)

    val stock =
      StockDomain(
        quotes =
          mutableListOf(
            StockQuoteDomain(date = LocalDate.of(2023, 12, 31), high = 100.0, closePrice = 100.0, closePriceEMA20 = 100.0, atr = 2.0),
            StockQuoteDomain(date = LocalDate.of(2024, 1, 1), high = 101.0, closePrice = 101.0, closePriceEMA20 = 100.0, atr = 2.0),
            StockQuoteDomain(date = LocalDate.of(2024, 1, 2), high = 102.0, closePrice = 102.0, closePriceEMA20 = 100.0, atr = 2.0),
            StockQuoteDomain(date = LocalDate.of(2024, 1, 3), high = 103.0, closePrice = 103.0, closePriceEMA20 = 100.0, atr = 2.0),
          ),
      )

    val quote = stock.quotes[3]

    val result = condition.evaluateWithDetails(stock, quote)

    assertTrue(result.passed)
    assertEquals("ConsecutiveHigherHighsInValueZoneCondition", result.conditionType)
    assertTrue(result.message!!.contains("103.00 >= 102.00 >= 101.00"))
    assertTrue(result.message!!.contains("✓"))
    assertTrue(result.message!!.contains("100.00 - 104.00"))
  }

  @Test
  fun `should provide detailed evaluation result when failed due to insufficient data`() {
    val condition = ConsecutiveHigherHighsInValueZoneCondition(consecutiveDays = 3, atrMultiplier = 2.0, emaPeriod = 20)

    val stock =
      StockDomain(
        quotes =
          mutableListOf(
            StockQuoteDomain(date = LocalDate.of(2024, 1, 2), high = 102.0, closePrice = 102.0, closePriceEMA20 = 100.0, atr = 2.0),
          ),
      )

    val quote = stock.quotes[0]

    val result = condition.evaluateWithDetails(stock, quote)

    assertFalse(result.passed)
    assertEquals("ConsecutiveHigherHighsInValueZoneCondition", result.conditionType)
    assertTrue(result.message!!.contains("Insufficient historical data"))
    assertTrue(result.message!!.contains("✗"))
  }

  @Test
  fun `should provide detailed evaluation result when failed due to not higher closes`() {
    val condition = ConsecutiveHigherHighsInValueZoneCondition(consecutiveDays = 3, atrMultiplier = 2.0, emaPeriod = 20)

    val stock =
      StockDomain(
        quotes =
          mutableListOf(
            StockQuoteDomain(date = LocalDate.of(2023, 12, 31), high = 100.0, closePrice = 100.0, closePriceEMA20 = 100.0, atr = 2.0),
            StockQuoteDomain(date = LocalDate.of(2024, 1, 1), high = 103.0, closePrice = 103.0, closePriceEMA20 = 100.0, atr = 2.0),
            StockQuoteDomain(date = LocalDate.of(2024, 1, 2), high = 102.0, closePrice = 101.0, closePriceEMA20 = 100.0, atr = 2.0),
            StockQuoteDomain(date = LocalDate.of(2024, 1, 3), high = 103.0, closePrice = 102.0, closePriceEMA20 = 100.0, atr = 2.0),
          ),
      )

    val quote = stock.quotes[3]

    val result = condition.evaluateWithDetails(stock, quote)

    assertFalse(result.passed)
    assertTrue(result.message!!.contains("✗"))
  }
}
