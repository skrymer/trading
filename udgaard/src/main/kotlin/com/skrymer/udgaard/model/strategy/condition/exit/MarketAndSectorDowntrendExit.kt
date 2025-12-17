package com.skrymer.udgaard.model.strategy.condition.exit

import com.skrymer.udgaard.controller.dto.ConditionMetadata
import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.domain.StockQuoteDomain
import org.springframework.stereotype.Component

/**
 * Exit condition that triggers when BOTH market and sector breadth reverse to downtrend.
 * This provides defense against broad market weakness.
 */
@Component
class MarketAndSectorDowntrendExit : ExitCondition {
  override fun shouldExit(
    stock: StockDomain,
    entryQuote: StockQuoteDomain?,
    quote: StockQuoteDomain,
  ): Boolean = !quote.sectorIsInUptrend && !quote.marketIsInUptrend

  override fun exitReason(): String = "Market and sector breadth turned bearish"

  override fun description(): String = "Market & sector downtrend"

  override fun getMetadata() =
    ConditionMetadata(
      type = "marketAndSectorDowntrend",
      displayName = "Market & Sector Downtrend",
      description = "Exit when both market and sector are in downtrend",
      parameters = emptyList(),
      category = "Trend",
    )
}
