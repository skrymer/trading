package com.skrymer.udgaard.backtesting.strategy.condition.exit

import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Exit condition that triggers when heatmap is declining (getting more fearful).
 * This helps lock in profits before sentiment shifts too negative.
 */
@Component
class HeatmapDecliningExit : ExitCondition {
  override fun shouldExit(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
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
