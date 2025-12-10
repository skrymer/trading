package com.skrymer.udgaard.model.strategy.condition.exit

import com.skrymer.udgaard.controller.dto.ConditionMetadata
import com.skrymer.udgaard.controller.dto.ParameterMetadata
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Exit condition that triggers when a faster EMA crosses under a slower EMA.
 */
@Component
class EmaCrossExit(
  private val fastEma: Int = 10,
  private val slowEma: Int = 20
) : ExitCondition {

  override fun shouldExit(stock: Stock, entryQuote: StockQuote?, quote: StockQuote): Boolean {
    val fastValue = getEmaValue(quote, fastEma)
    val slowValue = getEmaValue(quote, slowEma)

    return fastValue < slowValue
  }

  override fun exitReason(): String = "${fastEma} ema has crossed under the ${slowEma} ema"

  override fun description(): String = "${fastEma}EMA crosses under ${slowEma}EMA"

  override fun getMetadata() = ConditionMetadata(
    type = "emaCross",
    displayName = "EMA Crossover",
    description = "Exit when fast EMA crosses below slow EMA",
    parameters = listOf(
      ParameterMetadata(
        name = "fastEma",
        displayName = "Fast EMA",
        type = "number",
        defaultValue = 10,
        options = listOf("5", "10", "20")
      ),
      ParameterMetadata(
        name = "slowEma",
        displayName = "Slow EMA",
        type = "number",
        defaultValue = 20,
        options = listOf("10", "20", "50")
      )
    ),
    category = "Trend"
  )

  private fun getEmaValue(quote: StockQuote, period: Int): Double {
    return when (period) {
      5 -> quote.closePriceEMA5
      10 -> quote.closePriceEMA10
      20 -> quote.closePriceEMA20
      50 -> quote.closePriceEMA50
      else -> 0.0
    }
  }
}
