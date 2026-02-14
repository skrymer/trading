package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.dto.ParameterMetadata
import com.skrymer.udgaard.backtesting.strategy.condition.entry.EntryCondition
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Entry condition that filters stocks by minimum price.
 *
 * This helps avoid penny stocks and ensure sufficient liquidity
 * by only entering positions when the stock price is above a
 * specified dollar threshold.
 *
 * @param minimumPrice Minimum close price in dollars (default: 10.0)
 */
@Component
class MinimumPriceCondition(
  private val minimumPrice: Double = 10.0,
) : EntryCondition {
  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
  ): Boolean = quote.closePrice >= minimumPrice

  override fun description(): String = "Price >= $${"%.2f".format(minimumPrice)}"

  override fun getMetadata() =
    ConditionMetadata(
      type = "minimumPrice",
      displayName = "Minimum Price",
      description = "Stock price is above minimum threshold (avoids penny stocks)",
      parameters =
        listOf(
          ParameterMetadata(
            name = "minimumPrice",
            displayName = "Minimum Price",
            type = "number",
            defaultValue = 10.0,
            min = 0.01,
            max = 1000,
          ),
        ),
      category = "Stock",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
  ): ConditionEvaluationResult {
    val passed = evaluate(stock, quote)
    val message = if (passed) description() + " ✓" else description() + " ✗"

    return ConditionEvaluationResult(
      conditionType = "MinimumPriceCondition",
      description = description(),
      passed = passed,
      actualValue = null,
      threshold = null,
      message = message,
    )
  }
}
