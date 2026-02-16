package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.data.model.Earning
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DaysSinceEarningsConditionTest {
  @Test
  fun `should return true when enough days since last earnings`() {
    val condition = DaysSinceEarningsCondition(days = 5)

    val stock =
      Stock(
        earnings =
          listOf(
            Earning(
              reportedDate = LocalDate.of(2024, 1, 1),
            ),
          ),
      )

    val quote = StockQuote(date = LocalDate.of(2024, 1, 10))

    assertTrue(
      condition.evaluate(stock, quote),
      "Should pass when 9 days have passed since earnings (threshold is 5)",
    )
  }

  @Test
  fun `should return false when earnings too recent`() {
    val condition = DaysSinceEarningsCondition(days = 5)

    val stock =
      Stock(
        earnings =
          listOf(
            Earning(
              reportedDate = LocalDate.of(2024, 1, 8),
            ),
          ),
      )

    val quote = StockQuote(date = LocalDate.of(2024, 1, 10))

    assertFalse(
      condition.evaluate(stock, quote),
      "Should fail when only 2 days since earnings (threshold is 5)",
    )
  }

  @Test
  fun `should return true when exactly at boundary`() {
    val condition = DaysSinceEarningsCondition(days = 5)

    val stock =
      Stock(
        earnings =
          listOf(
            Earning(
              reportedDate = LocalDate.of(2024, 1, 5),
            ),
          ),
      )

    val quote = StockQuote(date = LocalDate.of(2024, 1, 10))

    assertTrue(
      condition.evaluate(stock, quote),
      "Should pass when exactly 5 days since earnings (threshold is 5)",
    )
  }

  @Test
  fun `should return false when one day before boundary`() {
    val condition = DaysSinceEarningsCondition(days = 5)

    val stock =
      Stock(
        earnings =
          listOf(
            Earning(
              reportedDate = LocalDate.of(2024, 1, 6),
            ),
          ),
      )

    val quote = StockQuote(date = LocalDate.of(2024, 1, 10))

    assertFalse(
      condition.evaluate(stock, quote),
      "Should fail when only 4 days since earnings (threshold is 5)",
    )
  }

  @Test
  fun `should use most recent past earnings when multiple exist`() {
    val condition = DaysSinceEarningsCondition(days = 5)

    val stock =
      Stock(
        earnings =
          listOf(
            Earning(
              reportedDate = LocalDate.of(2023, 10, 15),
            ),
            Earning(
              reportedDate = LocalDate.of(2024, 1, 8),
            ),
            Earning(
              reportedDate = LocalDate.of(2023, 7, 20),
            ),
          ),
      )

    val quote = StockQuote(date = LocalDate.of(2024, 1, 10))

    assertFalse(
      condition.evaluate(stock, quote),
      "Should check most recent past earnings (Jan 8), which is only 2 days ago",
    )
  }

  @Test
  fun `should return true when no past earnings exist`() {
    val condition = DaysSinceEarningsCondition(days = 5)

    val stock = Stock(earnings = emptyList())

    val quote = StockQuote(date = LocalDate.of(2024, 1, 10))

    assertTrue(
      condition.evaluate(stock, quote),
      "Should pass when no earnings data exists",
    )
  }

  @Test
  fun `should return true when only future earnings exist`() {
    val condition = DaysSinceEarningsCondition(days = 5)

    val stock =
      Stock(
        earnings =
          listOf(
            Earning(
              reportedDate = LocalDate.of(2024, 2, 15),
            ),
          ),
      )

    val quote = StockQuote(date = LocalDate.of(2024, 1, 10))

    assertTrue(
      condition.evaluate(stock, quote),
      "Should pass when only future earnings exist (no past earnings)",
    )
  }

  @Test
  fun `should ignore future earnings and check only past`() {
    val condition = DaysSinceEarningsCondition(days = 5)

    val stock =
      Stock(
        earnings =
          listOf(
            Earning(
              reportedDate = LocalDate.of(2024, 1, 9),
            ),
            Earning(
              reportedDate = LocalDate.of(2024, 2, 15),
            ),
          ),
      )

    val quote = StockQuote(date = LocalDate.of(2024, 1, 10))

    assertFalse(
      condition.evaluate(stock, quote),
      "Should fail based on past earnings (Jan 9 = 1 day ago), ignoring future earnings",
    )
  }

  @Test
  fun `should return false when earnings on same day as quote`() {
    val condition = DaysSinceEarningsCondition(days = 5)

    val stock =
      Stock(
        earnings =
          listOf(
            Earning(
              reportedDate = LocalDate.of(2024, 1, 10),
            ),
          ),
      )

    val quote = StockQuote(date = LocalDate.of(2024, 1, 10))

    assertFalse(
      condition.evaluate(stock, quote),
      "Should fail when earnings are on same day (0 days since)",
    )
  }

  @Test
  fun `should handle earnings with null reportedDate`() {
    val condition = DaysSinceEarningsCondition(days = 5)

    val stock =
      Stock(
        earnings =
          listOf(
            Earning(
              reportedDate = null,
            ),
          ),
      )

    val quote = StockQuote(date = LocalDate.of(2024, 1, 10))

    assertTrue(
      condition.evaluate(stock, quote),
      "Should pass when earnings have null reportedDate",
    )
  }

  @Test
  fun `should provide correct description for default days`() {
    val condition = DaysSinceEarningsCondition()
    assertEquals("At least 5 days since earnings", condition.description())
  }

  @Test
  fun `should provide correct description for custom days`() {
    val condition = DaysSinceEarningsCondition(days = 10)
    assertEquals("At least 10 days since earnings", condition.description())
  }

  @Test
  fun `should provide correct metadata`() {
    val condition = DaysSinceEarningsCondition()
    val metadata = condition.getMetadata()

    assertEquals("daysSinceEarnings", metadata.type)
    assertEquals("Days Since Earnings", metadata.displayName)
    assertEquals("Earnings", metadata.category)
    assertEquals(1, metadata.parameters.size)

    val daysParam = metadata.parameters.find { it.name == "days" }
    assertNotNull(daysParam)
    assertEquals("number", daysParam?.type)
    assertEquals(5, daysParam?.defaultValue)
    assertEquals(0, daysParam?.min)
    assertEquals(30, daysParam?.max)
  }

  @Test
  fun `should provide detailed evaluation result when passed with no past earnings`() {
    val condition = DaysSinceEarningsCondition(days = 5)

    val stock = Stock(earnings = emptyList())

    val quote = StockQuote(date = LocalDate.of(2024, 1, 10))

    val result = condition.evaluateWithDetails(stock, quote)

    assertTrue(result.passed)
    assertEquals("DaysSinceEarningsCondition", result.conditionType)
    assertEquals("None", result.actualValue)
    assertEquals("5 days", result.threshold)
    assertTrue(result.message!!.contains("No past earnings found"))
    assertTrue(result.message!!.contains("✓"))
  }

  @Test
  fun `should provide detailed evaluation result when passed with distant past earnings`() {
    val condition = DaysSinceEarningsCondition(days = 5)

    val stock =
      Stock(
        earnings =
          listOf(
            Earning(
              reportedDate = LocalDate.of(2024, 1, 1),
            ),
          ),
      )

    val quote = StockQuote(date = LocalDate.of(2024, 1, 10))

    val result = condition.evaluateWithDetails(stock, quote)

    assertTrue(result.passed)
    assertEquals("DaysSinceEarningsCondition", result.conditionType)
    assertEquals("2024-01-01", result.actualValue)
    assertEquals("5 days", result.threshold)
    assertTrue(result.message!!.contains("9 days since earnings"))
    assertTrue(result.message!!.contains("✓"))
  }

  @Test
  fun `should provide detailed evaluation result when failed with recent earnings`() {
    val condition = DaysSinceEarningsCondition(days = 5)

    val stock =
      Stock(
        earnings =
          listOf(
            Earning(
              reportedDate = LocalDate.of(2024, 1, 8),
            ),
          ),
      )

    val quote = StockQuote(date = LocalDate.of(2024, 1, 10))

    val result = condition.evaluateWithDetails(stock, quote)

    assertFalse(result.passed)
    assertEquals("DaysSinceEarningsCondition", result.conditionType)
    assertEquals("2024-01-08", result.actualValue)
    assertEquals("5 days", result.threshold)
    assertTrue(result.message!!.contains("2 days since earnings"))
    assertTrue(result.message!!.contains("too recent"))
    assertTrue(result.message!!.contains("✗"))
  }
}
