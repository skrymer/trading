package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.dto.ParameterMetadata
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Entry condition that requires a minimum spread between fast and slow EMAs
 * as a percentage of price.
 *
 * Winners tend to have wider EMA10-EMA20 spreads (1.30%) compared to
 * never-green losers (0.99%), indicating stronger trend momentum.
 */
@Component
class EmaSpreadCondition(
  private val fastEmaPeriod: Int = 10,
  private val slowEmaPeriod: Int = 20,
  private val minSpreadPercent: Double = 1.0,
) : EntryCondition {
  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
  ): Boolean {
    val fastEma = getEma(quote, fastEmaPeriod) ?: return false
    val slowEma = getEma(quote, slowEmaPeriod) ?: return false
    if (quote.closePrice <= 0) return false
    val spread = (fastEma - slowEma) / quote.closePrice * 100
    return spread >= minSpreadPercent
  }

  override fun description(): String =
    "EMA spread (EMA$fastEmaPeriod - EMA$slowEmaPeriod ≥ ${"%.1f".format(minSpreadPercent)}% of price)"

  override fun getMetadata() =
    ConditionMetadata(
      type = "emaSpread",
      displayName = "EMA Spread",
      description = "Minimum spread between fast and slow EMAs as percentage of price",
      parameters =
        listOf(
          ParameterMetadata(
            name = "fastEmaPeriod",
            displayName = "Fast EMA Period",
            type = "number",
            defaultValue = 10,
            min = 5,
            max = 50,
          ),
          ParameterMetadata(
            name = "slowEmaPeriod",
            displayName = "Slow EMA Period",
            type = "number",
            defaultValue = 20,
            min = 10,
            max = 200,
          ),
          ParameterMetadata(
            name = "minSpreadPercent",
            displayName = "Min Spread %",
            type = "number",
            defaultValue = 1.0,
            min = 0,
            max = 10,
          ),
        ),
      category = "Trend",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
  ): ConditionEvaluationResult {
    val passed = evaluate(stock, quote)
    val fastEma = getEma(quote, fastEmaPeriod)
    val slowEma = getEma(quote, slowEmaPeriod)
    val spread =
      if (fastEma != null && slowEma != null && quote.closePrice > 0) {
        (fastEma - slowEma) / quote.closePrice * 100
      } else {
        0.0
      }

    return ConditionEvaluationResult(
      conditionType = "EmaSpreadCondition",
      description = description(),
      passed = passed,
      actualValue = "${"%.2f".format(spread)}%",
      threshold = "≥${"%.1f".format(minSpreadPercent)}%",
      message =
        if (passed) {
          "EMA spread ${"%.2f".format(spread)}% ≥ ${"%.1f".format(minSpreadPercent)}%"
        } else {
          "EMA spread ${"%.2f".format(spread)}% < ${"%.1f".format(minSpreadPercent)}%"
        },
    )
  }

  companion object {
    fun getEma(
      quote: StockQuote,
      period: Int,
    ): Double? =
      when (period) {
        5 -> quote.closePriceEMA5
        10 -> quote.closePriceEMA10
        20 -> quote.closePriceEMA20
        50 -> quote.closePriceEMA50
        100 -> quote.closePriceEMA100
        200 -> quote.ema200
        else -> null
      }
  }
}
