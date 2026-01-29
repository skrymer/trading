package com.skrymer.udgaard.model.strategy.condition.exit

import com.skrymer.udgaard.controller.dto.ConditionMetadata
import com.skrymer.udgaard.controller.dto.ParameterMetadata
import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.domain.StockQuoteDomain
import org.springframework.stereotype.Component

/**
 * Exit condition that triggers when price is within an order block older than specified age.
 * All order blocks are calculated using ROC (Rate of Change) analysis.
 * @param orderBlockAgeInDays Minimum age of order block in days (default 120)
 */
@Component
class OrderBlockExit(
  private val orderBlockAgeInDays: Int = 120,
) : ExitCondition {
  override fun shouldExit(
    stock: StockDomain,
    entryQuote: StockQuoteDomain?,
    quote: StockQuoteDomain,
  ): Boolean = stock.withinOrderBlock(quote, orderBlockAgeInDays)

  override fun exitReason(): String = "Quote is within an order block older than $orderBlockAgeInDays days"

  override fun description(): String = "Within order block (age > ${orderBlockAgeInDays}d)"

  override fun getMetadata() =
    ConditionMetadata(
      type = "orderBlock",
      displayName = "Order Block",
      description = "Exit when price enters an order block",
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
      category = "ProfitTaking",
    )
}
