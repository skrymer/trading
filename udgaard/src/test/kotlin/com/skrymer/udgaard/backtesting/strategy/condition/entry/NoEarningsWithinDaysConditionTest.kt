package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.data.model.Earning
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

class NoEarningsWithinDaysConditionTest {
  @Test
  fun `should return true when no earnings within next 7 days`() {
    val condition = NoEarningsWithinDaysCondition(days = 7)

    val stock =
      Stock(
        earnings =
          mutableListOf(
            Earning(
              reportedDate = LocalDate.of(2024, 1, 20), // 15 days away
            )
          )
      )

    val quote = StockQuote(date = LocalDate.of(2024, 1, 5))

    assertTrue(
      condition.evaluate(stock, quote),
      "Condition should be true when earnings are 15 days away (outside 7-day window)",
    )
  }

  @Test
  fun `should return false when earnings within next 7 days`() {
    val condition = NoEarningsWithinDaysCondition(days = 7)

    val stock =
      Stock(
        earnings =
          mutableListOf(
            Earning(
              reportedDate = LocalDate.of(2024, 1, 10), // 5 days away
            )
          )
      )

    val quote = StockQuote(date = LocalDate.of(2024, 1, 5))

    assertFalse(
      condition.evaluate(stock, quote),
      "Condition should be false when earnings are 5 days away (within 7-day window)",
    )
  }

  @Test
  fun `should return false when earnings tomorrow (days equals 1)`() {
    val condition = NoEarningsWithinDaysCondition(days = 1)

    val stock =
      Stock(
        earnings =
          mutableListOf(
            Earning(
              reportedDate = LocalDate.of(2024, 1, 6), // Tomorrow
            )
          )
      )

    val quote = StockQuote(date = LocalDate.of(2024, 1, 5))

    assertFalse(
      condition.evaluate(stock, quote),
      "Condition should be false when earnings are tomorrow",
    )
  }

  @Test
  fun `should return false when earnings are on same day as quote (days equals 0)`() {
    val condition = NoEarningsWithinDaysCondition(days = 0)

    val stock =
      Stock(
        earnings =
          mutableListOf(
            Earning(
              reportedDate = LocalDate.of(2024, 1, 5), // Same day
            )
          )
      )

    val quote = StockQuote(date = LocalDate.of(2024, 1, 5))

    assertFalse(
      condition.evaluate(stock, quote),
      "Condition should be false when earnings are on the same day (within 0 days)",
    )
  }

  @Test
  fun `should return false when earnings exactly at boundary`() {
    val condition = NoEarningsWithinDaysCondition(days = 7)

    val stock =
      Stock(
        earnings =
          mutableListOf(
            Earning(
              reportedDate = LocalDate.of(2024, 1, 12), // Exactly 7 days away
            )
          )
      )

    val quote = StockQuote(date = LocalDate.of(2024, 1, 5))

    assertFalse(
      condition.evaluate(stock, quote),
      "Condition should be false when earnings are exactly at 7-day boundary",
    )
  }

  @Test
  fun `should return true when earnings are past (before quote date)`() {
    val condition = NoEarningsWithinDaysCondition(days = 7)

    val stock =
      Stock(
        earnings =
          mutableListOf(
            Earning(
              reportedDate = LocalDate.of(2024, 1, 1), // In the past
            )
          )
      )

    val quote = StockQuote(date = LocalDate.of(2024, 1, 5))

    assertTrue(
      condition.evaluate(stock, quote),
      "Condition should be true when earnings are in the past",
    )
  }

  @Test
  fun `should return true when no earnings data available`() {
    val condition = NoEarningsWithinDaysCondition(days = 7)

    val stock = Stock(earnings = mutableListOf())

    val quote = StockQuote(date = LocalDate.of(2024, 1, 5))

    assertTrue(
      condition.evaluate(stock, quote),
      "Condition should be true when no earnings data is available",
    )
  }

  @Test
  fun `should work with longer lookforward period`() {
    val condition = NoEarningsWithinDaysCondition(days = 30)

    val stock =
      Stock(
        earnings =
          mutableListOf(
            Earning(
              reportedDate = LocalDate.of(2024, 2, 1), // 27 days away
            )
          )
      )

    val quote = StockQuote(date = LocalDate.of(2024, 1, 5))

    assertFalse(
      condition.evaluate(stock, quote),
      "Condition should be false when earnings are within 30-day window",
    )
  }

  @Test
  fun `should handle multiple earnings and check only next one`() {
    val condition = NoEarningsWithinDaysCondition(days = 7)

    val stock =
      Stock(
        earnings =
          mutableListOf(
            Earning(
              reportedDate = LocalDate.of(2024, 1, 1), // Past
            ),
            Earning(
              reportedDate = LocalDate.of(2024, 1, 20), // Future, far away
            ),
            Earning(
              reportedDate = LocalDate.of(2024, 1, 10), // Future, close - THIS ONE
            )
          )
      )

    val quote = StockQuote(date = LocalDate.of(2024, 1, 5))

    assertFalse(
      condition.evaluate(stock, quote),
      "Condition should check the next earnings date (Jan 10), which is within 7 days",
    )
  }

  @Test
  fun `should provide correct description for default days`() {
    val condition = NoEarningsWithinDaysCondition()
    assertEquals("No earnings within 7 days", condition.description())
  }

  @Test
  fun `should provide correct description for 1 day`() {
    val condition = NoEarningsWithinDaysCondition(days = 1)
    assertEquals("No earnings tomorrow", condition.description())
  }

  @Test
  fun `should provide correct description for custom days`() {
    val condition = NoEarningsWithinDaysCondition(days = 14)
    assertEquals("No earnings within 14 days", condition.description())
  }

  @Test
  fun `should provide correct metadata`() {
    val condition = NoEarningsWithinDaysCondition()
    val metadata = condition.getMetadata()

    assertEquals("noEarningsWithinDays", metadata.type)
    assertEquals("No Earnings Within Days", metadata.displayName)
    assertEquals("Earnings", metadata.category)
    assertEquals(1, metadata.parameters.size)

    val daysParam = metadata.parameters.find { it.name == "days" }
    assertNotNull(daysParam)
    assertEquals("number", daysParam?.type)
    assertEquals(7, daysParam?.defaultValue)
    assertEquals(0, daysParam?.min)
    assertEquals(30, daysParam?.max)
  }

  @Test
  fun `should provide detailed evaluation result when passed with no upcoming earnings`() {
    val condition = NoEarningsWithinDaysCondition(days = 7)

    val stock = Stock(earnings = mutableListOf())

    val quote = StockQuote(date = LocalDate.of(2024, 1, 5))

    val result = condition.evaluateWithDetails(stock, quote)

    assertTrue(result.passed)
    assertEquals("NoEarningsWithinDaysCondition", result.conditionType)
    assertEquals("None", result.actualValue)
    assertEquals("7 days", result.threshold)
    assertTrue(result.message!!.contains("No upcoming earnings found"))
    assertTrue(result.message!!.contains("✓"))
  }

  @Test
  fun `should provide detailed evaluation result when passed with distant earnings`() {
    val condition = NoEarningsWithinDaysCondition(days = 7)

    val stock =
      Stock(
        earnings =
          mutableListOf(
            Earning(
              reportedDate = LocalDate.of(2024, 1, 20),
            )
          )
      )

    val quote = StockQuote(date = LocalDate.of(2024, 1, 5))

    val result = condition.evaluateWithDetails(stock, quote)

    assertTrue(result.passed)
    assertEquals("NoEarningsWithinDaysCondition", result.conditionType)
    assertEquals("2024-01-20", result.actualValue)
    assertEquals("7 days", result.threshold)
    assertTrue(result.message!!.contains("Next earnings in 15 days"))
    assertTrue(result.message!!.contains("✓"))
  }

  @Test
  fun `should provide detailed evaluation result when failed with near earnings`() {
    val condition = NoEarningsWithinDaysCondition(days = 7)

    val stock =
      Stock(
        earnings =
          mutableListOf(
            Earning(
              reportedDate = LocalDate.of(2024, 1, 8),
            )
          )
      )

    val quote = StockQuote(date = LocalDate.of(2024, 1, 5))

    val result = condition.evaluateWithDetails(stock, quote)

    assertFalse(result.passed)
    assertEquals("NoEarningsWithinDaysCondition", result.conditionType)
    assertEquals("2024-01-08", result.actualValue)
    assertEquals("7 days", result.threshold)
    assertTrue(result.message!!.contains("Earnings in 3 days"))
    assertTrue(result.message!!.contains("too soon"))
    assertTrue(result.message!!.contains("✗"))
  }
}
