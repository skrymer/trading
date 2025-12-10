package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

class HeatmapConditionTest {
  private val stock = Stock()

  @Test
  fun `should return true when heatmap is below threshold`() {
    val condition = HeatmapCondition(threshold = 70.0)
    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        heatmap = 65.0,
      )

    assertTrue(
      condition.evaluate(stock, quote),
      "Condition should be true when heatmap is below threshold",
    )
  }

  @Test
  fun `should return false when heatmap equals threshold`() {
    val condition = HeatmapCondition(threshold = 70.0)
    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        heatmap = 70.0,
      )

    assertFalse(
      condition.evaluate(stock, quote),
      "Condition should be false when heatmap equals threshold",
    )
  }

  @Test
  fun `should return false when heatmap is above threshold`() {
    val condition = HeatmapCondition(threshold = 70.0)
    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        heatmap = 85.0,
      )

    assertFalse(
      condition.evaluate(stock, quote),
      "Condition should be false when heatmap is above threshold",
    )
  }

  @Test
  fun `should work with different threshold values`() {
    val condition = HeatmapCondition(threshold = 50.0)
    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        heatmap = 45.0,
      )

    assertTrue(
      condition.evaluate(stock, quote),
      "Condition should work with custom threshold",
    )
  }

  @Test
  fun `should provide correct description`() {
    val condition = HeatmapCondition(threshold = 70.0)
    assertEquals("Heatmap < 70.0", condition.description())
  }
}
