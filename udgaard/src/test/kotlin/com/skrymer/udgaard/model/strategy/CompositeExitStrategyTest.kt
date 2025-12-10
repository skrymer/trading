package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.condition.LogicalOperator
import com.skrymer.udgaard.model.strategy.condition.exit.EmaCrossExit
import com.skrymer.udgaard.model.strategy.condition.exit.ProfitTargetExit
import com.skrymer.udgaard.model.strategy.condition.exit.SellSignalExit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CompositeExitStrategyTest {
  private val stock = Stock()

  @Test
  fun `should exit when any OR condition is met`() {
    val strategy =
      CompositeExitStrategy(
        exitConditions =
          listOf(
            SellSignalExit(),
            EmaCrossExit(10, 20),
            ProfitTargetExit(3.0, 20),
          ),
        operator = LogicalOperator.OR,
      )

    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        signal = "Sell",
        closePriceEMA10 = 105.0,
        closePriceEMA20 = 100.0,
        closePrice = 100.0,
        atr = 2.0,
      )

    assertTrue(
      strategy.match(stock, null, quote),
      "Should exit when any OR condition is met",
    )
  }

  @Test
  fun `should not exit when all OR conditions fail`() {
    val strategy =
      CompositeExitStrategy(
        exitConditions =
          listOf(
            SellSignalExit(),
            EmaCrossExit(10, 20),
            ProfitTargetExit(3.0, 20),
          ),
        operator = LogicalOperator.OR,
      )

    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        closePriceEMA10 = 105.0, // Above 20 EMA (no cross)
        closePriceEMA20 = 100.0,
        closePrice = 104.0, // Below profit target
        atr = 2.0,
      )

    assertFalse(
      strategy.match(stock, null, quote),
      "Should not exit when all OR conditions fail",
    )
  }

  @Test
  fun `should exit when all AND conditions are met`() {
    val strategy =
      CompositeExitStrategy(
        exitConditions =
          listOf(
            SellSignalExit(),
            EmaCrossExit(10, 20),
          ),
        operator = LogicalOperator.AND,
      )

    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        signal = "Sell",
        closePriceEMA10 = 95.0, // Below 20 EMA (crossed under)
        closePriceEMA20 = 100.0,
      )

    assertTrue(
      strategy.match(stock, null, quote),
      "Should exit when all AND conditions are met",
    )
  }

  @Test
  fun `should not exit when one AND condition fails`() {
    val strategy =
      CompositeExitStrategy(
        exitConditions =
          listOf(
            SellSignalExit(),
            EmaCrossExit(10, 20),
          ),
        operator = LogicalOperator.AND,
      )

    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        signal = "Sell",
        closePriceEMA10 = 105.0, // Above 20 EMA (not crossed)
        closePriceEMA20 = 100.0,
      )

    assertFalse(
      strategy.match(stock, null, quote),
      "Should not exit when one AND condition fails",
    )
  }

  @Test
  fun `should provide exit reason for first matching condition`() {
    val strategy =
      CompositeExitStrategy(
        exitConditions =
          listOf(
            SellSignalExit(),
            EmaCrossExit(10, 20),
            ProfitTargetExit(3.0, 20),
          ),
        operator = LogicalOperator.OR,
      )

    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        closePriceEMA10 = 95.0, // This triggers exit
        closePriceEMA20 = 100.0,
        closePrice = 100.0,
        atr = 2.0,
      )

    val reason = strategy.reason(stock, null, quote)
    assertEquals("10 ema has crossed under the 20 ema", reason)
  }

  @Test
  fun `should return null reason when no condition matches`() {
    val strategy =
      CompositeExitStrategy(
        exitConditions =
          listOf(
            SellSignalExit(),
            EmaCrossExit(10, 20),
          ),
        operator = LogicalOperator.OR,
      )

    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        closePriceEMA10 = 105.0,
        closePriceEMA20 = 100.0,
      )

    val reason = strategy.reason(stock, null, quote)
    assertNull(reason, "Should return null when no condition matches")
  }

  @Test
  fun `should work with DSL builder`() {
    val strategy =
      exitStrategy {
        sellSignal()
        emaCross(10, 20)
        profitTarget(3.0, 20)
      }

    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
        closePriceEMA10 = 105.0,
        closePriceEMA20 = 100.0,
        closePrice = 110.0, // This triggers profit target exit
        atr = 2.0,
      )

    assertTrue(
      strategy.match(stock, null, quote),
      "DSL builder should create working strategy",
    )
  }

  @Test
  fun `should provide custom description when specified`() {
    val strategy =
      CompositeExitStrategy(
        exitConditions = listOf(SellSignalExit()),
        operator = LogicalOperator.OR,
        strategyDescription = "Custom exit strategy",
      )

    assertEquals("Custom exit strategy", strategy.description())
  }

  @Test
  fun `should generate description from conditions when not specified`() {
    val strategy =
      CompositeExitStrategy(
        exitConditions =
          listOf(
            SellSignalExit(),
            EmaCrossExit(10, 20),
          ),
        operator = LogicalOperator.OR,
      )

    val description = strategy.description()
    assertTrue(
      description.contains("Sell signal"),
      "Description should include first condition",
    )
    assertTrue(
      description.contains("10EMA crosses under 20EMA"),
      "Description should include second condition",
    )
  }

  @Test
  fun `should return false when no conditions provided`() {
    val strategy =
      CompositeExitStrategy(
        exitConditions = emptyList(),
        operator = LogicalOperator.OR,
      )

    val quote =
      StockQuote(
        date = LocalDate.of(2024, 1, 15),
      )

    assertFalse(
      strategy.match(stock, null, quote),
      "Strategy should return false when no conditions provided",
    )
  }
}
