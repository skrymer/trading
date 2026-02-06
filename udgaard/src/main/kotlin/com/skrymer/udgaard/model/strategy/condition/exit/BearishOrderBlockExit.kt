package com.skrymer.udgaard.model.strategy.condition.exit

import com.skrymer.udgaard.controller.dto.ConditionMetadata
import com.skrymer.udgaard.controller.dto.ParameterMetadata
import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.domain.StockQuoteDomain
import org.springframework.stereotype.Component

/**
 * Exit condition that triggers when price enters a bearish order block (resistance zone).
 * Bearish order blocks act as resistance where price may struggle to break through.
 * All order blocks are calculated using ROC (Rate of Change) analysis.
 *
 * @param orderBlockAgeInDays Minimum age of order block in days (default 120)
 * @param useHighPrice If true, checks if high touches order block; if false, checks close price (default: false)
 */
@Component
class BearishOrderBlockExit(
  private val orderBlockAgeInDays: Int = 120,
  private val useHighPrice: Boolean = false,
) : ExitCondition {
  override fun shouldExit(
    stock: StockDomain,
    entryQuote: StockQuoteDomain?,
    quote: StockQuoteDomain,
  ): Boolean = stock.withinOrderBlock(quote, orderBlockAgeInDays, useHighPrice)

  override fun exitReason(): String = "Price entered bearish order block (age > $orderBlockAgeInDays days)"

  override fun description(): String = "Bearish order block (age > ${orderBlockAgeInDays}d)"

  override fun getMetadata() =
    ConditionMetadata(
      type = "bearishOrderBlock",
      displayName = "Bearish Order Block",
      description = "Exit when price enters a bearish order block (resistance zone)",
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
          ParameterMetadata(
            name = "useHighPrice",
            displayName = "Use High Price",
            type = "boolean",
            defaultValue = false,
          ),
        ),
      category = "ProfitTaking",
    )
}
