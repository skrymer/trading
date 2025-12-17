package com.skrymer.udgaard.model.strategy.condition.exit

import com.skrymer.udgaard.controller.dto.ConditionMetadata
import com.skrymer.udgaard.controller.dto.ParameterMetadata
import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.domain.StockQuoteDomain
import org.springframework.stereotype.Component

/**
 * Exit condition that triggers when price extends beyond a profit target.
 * Target is defined as a number of ATRs above a specified EMA.
 */
@Component
class ProfitTargetExit(
  private val atrMultiplier: Double = 3.0,
  private val emaPeriod: Int = 20,
) : ExitCondition {
  override fun shouldExit(
    stock: StockDomain,
    entryQuote: StockQuoteDomain?,
    quote: StockQuoteDomain,
  ): Boolean {
    val emaValue = getEmaValue(quote, emaPeriod)
    return quote.closePrice > (emaValue + (atrMultiplier * quote.atr))
  }

  override fun exitReason(): String = "Price is $atrMultiplier ATR above $emaPeriod EMA"

  override fun description(): String = "Price > ${emaPeriod}EMA + ${atrMultiplier}ATR"

  override fun getMetadata() =
    ConditionMetadata(
      type = "profitTarget",
      displayName = "Profit Target",
      description = "Exit when price extends above EMA + ATR multiplier",
      parameters =
        listOf(
          ParameterMetadata(
            name = "atrMultiplier",
            displayName = "ATR Multiplier",
            type = "number",
            defaultValue = 3.0,
            min = 1.0,
            max = 10.0,
          ),
          ParameterMetadata(
            name = "emaPeriod",
            displayName = "EMA Period",
            type = "number",
            defaultValue = 20,
            options = listOf("10", "20", "50"),
          ),
        ),
      category = "ProfitTaking",
    )

  private fun getEmaValue(
    quote: StockQuoteDomain,
    period: Int,
  ): Double =
    when (period) {
      5 -> quote.closePriceEMA5
      10 -> quote.closePriceEMA10
      20 -> quote.closePriceEMA20
      50 -> quote.closePriceEMA50
      else -> 0.0
    }
}
