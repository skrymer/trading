package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.controller.dto.ConditionEvaluationResult
import com.skrymer.udgaard.controller.dto.ConditionMetadata
import com.skrymer.udgaard.controller.dto.ParameterMetadata
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Entry condition that checks if a faster EMA is above a slower EMA.
 *
 * This condition verifies EMA alignment, which indicates trend direction.
 * When a faster EMA is above a slower EMA, it suggests an uptrend.
 *
 * Available EMAs: 5, 10, 20, 50
 *
 * Common EMA alignments:
 * - 5 EMA > 10 EMA: Very short-term uptrend
 * - 10 EMA > 20 EMA: Short-term uptrend
 * - 20 EMA > 50 EMA: Medium-term uptrend
 *
 * Use case: Filter for stocks where shorter-term momentum is above longer-term,
 * indicating the trend is up and price is likely to continue rising.
 *
 * @param fastEmaPeriod The faster EMA period (default: 10, options: 5, 10, 20, 50)
 * @param slowEmaPeriod The slower EMA period (default: 20, options: 5, 10, 20, 50)
 */
@Component
class EmaAlignmentCondition(
  private val fastEmaPeriod: Int = 10,
  private val slowEmaPeriod: Int = 20,
) : EntryCondition {
  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
  ): Boolean {
    val fastEma = getEma(quote, fastEmaPeriod) ?: return false
    val slowEma = getEma(quote, slowEmaPeriod) ?: return false
    return fastEma > slowEma
  }

  override fun description(): String = "EMA$fastEmaPeriod > EMA$slowEmaPeriod"

  override fun getMetadata() =
    ConditionMetadata(
      type = "emaAlignment",
      displayName = "EMA Alignment",
      description = "Fast EMA is above slow EMA (uptrend alignment)",
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
            min = 5,
            max = 50,
          ),
        ),
      category = "Trend",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
  ): ConditionEvaluationResult {
    val fastEma = getEma(quote, fastEmaPeriod)
    val slowEma = getEma(quote, slowEmaPeriod)

    if (fastEma == null || slowEma == null) {
      return ConditionEvaluationResult(
        conditionType = "EmaAlignmentCondition",
        description = description(),
        passed = false,
        actualValue = null,
        threshold = null,
        message = "EMA values not available ✗",
      )
    }

    val passed = fastEma > slowEma
    val difference = fastEma - slowEma
    val percentDiff = (difference / slowEma) * 100

    val message =
      if (passed) {
        "EMA$fastEmaPeriod (${"%.2f".format(fastEma)}) > EMA$slowEmaPeriod (${"%.2f".format(slowEma)}) [%+.2f%%] ✓".format(
          percentDiff,
        )
      } else {
        "EMA$fastEmaPeriod (${"%.2f".format(fastEma)}) ≤ EMA$slowEmaPeriod (${"%.2f".format(slowEma)}) [%+.2f%%] ✗".format(
          percentDiff,
        )
      }

    return ConditionEvaluationResult(
      conditionType = "EmaAlignmentCondition",
      description = description(),
      passed = passed,
      actualValue = "%.2f vs %.2f".format(fastEma, slowEma),
      threshold = "Fast > Slow",
      message = message,
    )
  }

  private fun getEma(
    quote: StockQuote,
    period: Int,
  ): Double? =
    when (period) {
      5 -> quote.closePriceEMA5
      10 -> quote.closePriceEMA10
      20 -> quote.closePriceEMA20
      50 -> quote.closePriceEMA50
      else -> null
    }
}
