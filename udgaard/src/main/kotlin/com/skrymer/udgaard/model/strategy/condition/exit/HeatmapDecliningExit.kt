package com.skrymer.udgaard.model.strategy.condition.exit

import com.skrymer.udgaard.controller.dto.ConditionMetadata
import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.domain.StockQuoteDomain
import org.springframework.stereotype.Component

/**
 * Exit condition that triggers when heatmap is declining (getting more fearful).
 * This helps lock in profits before sentiment shifts too negative.
 */
@Component
class HeatmapDecliningExit : ExitCondition {
  override fun shouldExit(
    stock: StockDomain,
    entryQuote: StockQuoteDomain?,
    quote: StockQuoteDomain,
  ): Boolean = quote.isGettingMoreFearful()

  override fun exitReason(): String = "Heatmap is declining (buyers getting fearful)"

  override fun description(): String = "Heatmap declining"

  override fun getMetadata() =
    ConditionMetadata(
      type = "heatmapDeclining",
      displayName = "Heatmap Declining",
      description = "Exit when heatmap is declining",
      parameters = emptyList(),
      category = "Signal",
    )
}
