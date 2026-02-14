package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.dto.ParameterMetadata
import com.skrymer.udgaard.backtesting.strategy.condition.entry.EntryCondition
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Entry condition that checks if the stock is NOT within an order block older than specified age.
 * Order blocks represent institutional supply/demand zones that can act as resistance.
 *
 * @param ageInDays Minimum age of order block to avoid (default 120 days)
 */
@Component
class NotInOrderBlockCondition(
  private val ageInDays: Int = 120,
) : EntryCondition {
  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
  ): Boolean = !stock.withinOrderBlock(quote, ageInDays)

  override fun description(): String = "Not in order block (age > ${ageInDays}d)"

  override fun getMetadata() =
    ConditionMetadata(
      type = "notInOrderBlock",
      displayName = "Not in Order Block",
      description = "Price is not within an order block",
      parameters =
        listOf(
          ParameterMetadata(
            name = "ageInDays",
            displayName = "Age in Days",
            type = "number",
            defaultValue = 120,
            min = 1,
            max = 365,
          ),
        ),
      category = "OrderBlock",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
  ): ConditionEvaluationResult {
    val passed = evaluate(stock, quote)
    val message = if (passed) description() + " ✓" else description() + " ✗"

    return ConditionEvaluationResult(
      conditionType = "NotInOrderBlockCondition",
      description = description(),
      passed = passed,
      actualValue = null,
      threshold = null,
      message = message,
    )
  }
}
