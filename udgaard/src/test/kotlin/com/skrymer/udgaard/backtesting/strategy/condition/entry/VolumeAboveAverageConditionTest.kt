package com.skrymer.udgaard.backtesting.strategy.condition.entry
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class VolumeAboveAverageConditionTest {
  @Test
  fun `should return true when volume is above average by multiplier`() {
    val condition = VolumeAboveAverageCondition(multiplier = 1.3, lookbackDays = 10)

    val stock =
      Stock(
        quotes =
          mutableListOf(
            // Historical quotes with 1M average volume
            StockQuote(date = LocalDate.of(2024, 1, 1), volume = 1_000_000),
            StockQuote(date = LocalDate.of(2024, 1, 2), volume = 1_000_000),
            StockQuote(date = LocalDate.of(2024, 1, 3), volume = 1_000_000),
            StockQuote(date = LocalDate.of(2024, 1, 4), volume = 1_000_000),
            StockQuote(date = LocalDate.of(2024, 1, 5), volume = 1_000_000),
            StockQuote(date = LocalDate.of(2024, 1, 6), volume = 1_000_000),
            StockQuote(date = LocalDate.of(2024, 1, 7), volume = 1_000_000),
            StockQuote(date = LocalDate.of(2024, 1, 8), volume = 1_000_000),
            StockQuote(date = LocalDate.of(2024, 1, 9), volume = 1_000_000),
            StockQuote(date = LocalDate.of(2024, 1, 10), volume = 1_000_000),
          )
      )

    // Current quote with 1.5M volume (1.5x average, exceeds 1.3x requirement)
    val quote = StockQuote(date = LocalDate.of(2024, 1, 11), volume = 1_500_000)

    assertTrue(
      condition.evaluate(stock, quote),
      "Condition should be true when volume is 1.5x average (exceeds 1.3x requirement)",
    )
  }

  @Test
  fun `should return false when volume is below average by multiplier`() {
    val condition = VolumeAboveAverageCondition(multiplier = 1.3, lookbackDays = 10)

    val stock =
      Stock(
        quotes =
          mutableListOf(
            StockQuote(date = LocalDate.of(2024, 1, 1), volume = 1_000_000),
            StockQuote(date = LocalDate.of(2024, 1, 2), volume = 1_000_000),
            StockQuote(date = LocalDate.of(2024, 1, 3), volume = 1_000_000),
            StockQuote(date = LocalDate.of(2024, 1, 4), volume = 1_000_000),
            StockQuote(date = LocalDate.of(2024, 1, 5), volume = 1_000_000),
            StockQuote(date = LocalDate.of(2024, 1, 6), volume = 1_000_000),
            StockQuote(date = LocalDate.of(2024, 1, 7), volume = 1_000_000),
            StockQuote(date = LocalDate.of(2024, 1, 8), volume = 1_000_000),
            StockQuote(date = LocalDate.of(2024, 1, 9), volume = 1_000_000),
            StockQuote(date = LocalDate.of(2024, 1, 10), volume = 1_000_000),
          )
      )

    // Current quote with 1.2M volume (1.2x average, below 1.3x requirement)
    val quote = StockQuote(date = LocalDate.of(2024, 1, 11), volume = 1_200_000)

    assertFalse(
      condition.evaluate(stock, quote),
      "Condition should be false when volume is 1.2x average (below 1.3x requirement)",
    )
  }

  @Test
  fun `should return true when volume exactly equals multiplier threshold`() {
    val condition = VolumeAboveAverageCondition(multiplier = 1.5, lookbackDays = 5)

    val stock =
      Stock(
        quotes =
          mutableListOf(
            StockQuote(date = LocalDate.of(2024, 1, 1), volume = 1_000_000),
            StockQuote(date = LocalDate.of(2024, 1, 2), volume = 1_000_000),
            StockQuote(date = LocalDate.of(2024, 1, 3), volume = 1_000_000),
            StockQuote(date = LocalDate.of(2024, 1, 4), volume = 1_000_000),
            StockQuote(date = LocalDate.of(2024, 1, 5), volume = 1_000_000),
          )
      )

    // Current quote with exactly 1.5M volume (1.5x average, exactly at threshold)
    val quote = StockQuote(date = LocalDate.of(2024, 1, 6), volume = 1_500_000)

    assertTrue(
      condition.evaluate(stock, quote),
      "Condition should be true when volume exactly equals multiplier threshold",
    )
  }

  @Test
  fun `should return false when insufficient historical data`() {
    val condition = VolumeAboveAverageCondition(multiplier = 1.3, lookbackDays = 20)

    val stock =
      Stock(
        quotes =
          mutableListOf(
            // Only 5 quotes, less than 50% of 20-day lookback
            StockQuote(date = LocalDate.of(2024, 1, 1), volume = 1_000_000),
            StockQuote(date = LocalDate.of(2024, 1, 2), volume = 1_000_000),
            StockQuote(date = LocalDate.of(2024, 1, 3), volume = 1_000_000),
            StockQuote(date = LocalDate.of(2024, 1, 4), volume = 1_000_000),
            StockQuote(date = LocalDate.of(2024, 1, 5), volume = 1_000_000),
          )
      )

    val quote = StockQuote(date = LocalDate.of(2024, 1, 6), volume = 2_000_000)

    assertFalse(
      condition.evaluate(stock, quote),
      "Condition should be false when insufficient historical data (need at least 50% of lookback)",
    )
  }

  @Test
  fun `should calculate average from correct lookback period`() {
    val condition = VolumeAboveAverageCondition(multiplier = 1.3, lookbackDays = 5)

    val stock =
      Stock(
        quotes =
          mutableListOf(
            // Older quotes outside lookback period (high volume)
            StockQuote(date = LocalDate.of(2024, 1, 1), volume = 5_000_000),
            StockQuote(date = LocalDate.of(2024, 1, 2), volume = 5_000_000),
            StockQuote(date = LocalDate.of(2024, 1, 3), volume = 5_000_000),
            // Recent quotes in lookback period (low volume)
            StockQuote(date = LocalDate.of(2024, 1, 11), volume = 1_000_000),
            StockQuote(date = LocalDate.of(2024, 1, 12), volume = 1_000_000),
            StockQuote(date = LocalDate.of(2024, 1, 13), volume = 1_000_000),
            StockQuote(date = LocalDate.of(2024, 1, 14), volume = 1_000_000),
            StockQuote(date = LocalDate.of(2024, 1, 15), volume = 1_000_000),
          )
      )

    // Current quote should only compare to recent 5-day average (1M), not older quotes (5M)
    val quote = StockQuote(date = LocalDate.of(2024, 1, 16), volume = 1_400_000)

    assertTrue(
      condition.evaluate(stock, quote),
      "Condition should only use recent 5-day average, ignoring older high-volume quotes",
    )
  }

  @Test
  fun `should handle varying volume levels correctly`() {
    val condition = VolumeAboveAverageCondition(multiplier = 1.5, lookbackDays = 10)

    val stock =
      Stock(
        quotes =
          mutableListOf(
            // Varying volumes: 500K, 1M, 1.5M, 2M, etc.
            StockQuote(date = LocalDate.of(2024, 1, 1), volume = 500_000),
            StockQuote(date = LocalDate.of(2024, 1, 2), volume = 1_000_000),
            StockQuote(date = LocalDate.of(2024, 1, 3), volume = 1_500_000),
            StockQuote(date = LocalDate.of(2024, 1, 4), volume = 2_000_000),
            StockQuote(date = LocalDate.of(2024, 1, 5), volume = 800_000),
            StockQuote(date = LocalDate.of(2024, 1, 6), volume = 1_200_000),
            StockQuote(date = LocalDate.of(2024, 1, 7), volume = 900_000),
            StockQuote(date = LocalDate.of(2024, 1, 8), volume = 1_100_000),
            StockQuote(date = LocalDate.of(2024, 1, 9), volume = 1_000_000),
            StockQuote(date = LocalDate.of(2024, 1, 10), volume = 1_000_000),
          )
      )

    // Average is 1.1M, 1.5x = 1.65M
    val quote = StockQuote(date = LocalDate.of(2024, 1, 11), volume = 1_700_000)

    assertTrue(
      condition.evaluate(stock, quote),
      "Condition should correctly calculate average from varying volume levels",
    )
  }

  @Test
  fun `should provide correct description with default parameters`() {
    val condition = VolumeAboveAverageCondition()
    assertEquals("Volume ≥ 1.3× avg (20 days)", condition.description())
  }

  @Test
  fun `should provide correct description with custom parameters`() {
    val condition = VolumeAboveAverageCondition(multiplier = 2.0, lookbackDays = 50)
    assertEquals("Volume ≥ 2.0× avg (50 days)", condition.description())
  }

  @Test
  fun `should provide correct metadata`() {
    val condition = VolumeAboveAverageCondition()
    val metadata = condition.getMetadata()

    assertEquals("volumeAboveAverage", metadata.type)
    assertEquals("Volume Above Average", metadata.displayName)
    assertEquals("Volume", metadata.category)
    assertEquals(2, metadata.parameters.size)

    val multiplierParam = metadata.parameters.find { it.name == "multiplier" }
    assertNotNull(multiplierParam)
    assertEquals("number", multiplierParam?.type)
    assertEquals(1.3, multiplierParam?.defaultValue)
    assertEquals(1.0, multiplierParam?.min)
    assertEquals(5.0, multiplierParam?.max)

    val lookbackParam = metadata.parameters.find { it.name == "lookbackDays" }
    assertNotNull(lookbackParam)
    assertEquals("number", lookbackParam?.type)
    assertEquals(20, lookbackParam?.defaultValue)
    assertEquals(5, lookbackParam?.min)
    assertEquals(100, lookbackParam?.max)
  }

  @Test
  fun `should provide detailed evaluation result when passed`() {
    val condition = VolumeAboveAverageCondition(multiplier = 1.3, lookbackDays = 5)

    val stock =
      Stock(
        quotes =
          mutableListOf(
            StockQuote(date = LocalDate.of(2024, 1, 1), volume = 1_000_000),
            StockQuote(date = LocalDate.of(2024, 1, 2), volume = 1_000_000),
            StockQuote(date = LocalDate.of(2024, 1, 3), volume = 1_000_000),
            StockQuote(date = LocalDate.of(2024, 1, 4), volume = 1_000_000),
            StockQuote(date = LocalDate.of(2024, 1, 5), volume = 1_000_000),
          )
      )

    val quote = StockQuote(date = LocalDate.of(2024, 1, 6), volume = 1_500_000)

    val result = condition.evaluateWithDetails(stock, quote)

    assertTrue(result.passed)
    assertEquals("VolumeAboveAverageCondition", result.conditionType)
    assertEquals("1.50×", result.actualValue)
    assertEquals("1.3×", result.threshold)
    assertTrue(result.message!!.contains("1.5M"))
    assertTrue(result.message!!.contains("✓"))
  }

  @Test
  fun `should provide detailed evaluation result when failed`() {
    val condition = VolumeAboveAverageCondition(multiplier = 1.5, lookbackDays = 5)

    val stock =
      Stock(
        quotes =
          mutableListOf(
            StockQuote(date = LocalDate.of(2024, 1, 1), volume = 1_000_000),
            StockQuote(date = LocalDate.of(2024, 1, 2), volume = 1_000_000),
            StockQuote(date = LocalDate.of(2024, 1, 3), volume = 1_000_000),
            StockQuote(date = LocalDate.of(2024, 1, 4), volume = 1_000_000),
            StockQuote(date = LocalDate.of(2024, 1, 5), volume = 1_000_000),
          )
      )

    val quote = StockQuote(date = LocalDate.of(2024, 1, 6), volume = 1_200_000)

    val result = condition.evaluateWithDetails(stock, quote)

    assertFalse(result.passed)
    assertEquals("VolumeAboveAverageCondition", result.conditionType)
    assertEquals("1.20×", result.actualValue)
    assertEquals("1.5×", result.threshold)
    assertTrue(result.message!!.contains("1.2M"))
    assertTrue(result.message.contains("needs 1.5×"))
    assertTrue(result.message!!.contains("✗"))
  }
}
