package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.controller.dto.ConditionEvaluationResult
import com.skrymer.udgaard.controller.dto.ConditionMetadata
import com.skrymer.udgaard.controller.dto.ParameterMetadata
import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.domain.StockQuoteDomain
import com.skrymer.udgaard.model.strategy.condition.entry.EntryCondition
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
    stock: StockDomain,
    quote: StockQuoteDomain,
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
    stock: StockDomain,
    quote: StockQuoteDomain,
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
