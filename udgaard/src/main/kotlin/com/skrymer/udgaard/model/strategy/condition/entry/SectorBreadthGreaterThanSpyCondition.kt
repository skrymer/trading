package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.controller.dto.ConditionEvaluationResult
import com.skrymer.udgaard.controller.dto.ConditionMetadata
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Entry condition that checks if sector breadth is greater than market (SPY) breadth.
 *
 * This condition identifies stocks in sectors that are outperforming the overall market
 * in terms of breadth/participation. When sector breadth > market breadth, it suggests
 * stronger participation and momentum within that sector compared to the broader market.
 *
 * Use case: Enter positions when the stock's sector shows stronger breadth than the market,
 * indicating relative sector strength and better odds of sustained moves.
 */
@Component
class SectorBreadthGreaterThanSpyCondition : EntryCondition {
  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
  ): Boolean = quote.sectorBreadth > quote.marketAdvancingPercent

  override fun description(): String = "Sector breadth > Market breadth"

  override fun getMetadata() =
    ConditionMetadata(
      type = "sectorBreadthGreaterThanSpy",
      displayName = "Sector Breadth > Market Breadth",
      description = "Sector breadth is greater than market (SPY) breadth",
      parameters = emptyList(),
      category = "Sector",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
  ): ConditionEvaluationResult {
    val sectorBreadth = quote.sectorBreadth
    val marketBreadth = quote.marketAdvancingPercent
    val passed = sectorBreadth > marketBreadth
    val difference = sectorBreadth - marketBreadth

    val message =
      if (passed) {
        "Sector breadth %.1f%% > Market breadth %.1f%% (diff: %+.1f%%) ✓".format(
          sectorBreadth,
          marketBreadth,
          difference,
        )
      } else {
        "Sector breadth %.1f%% ≤ Market breadth %.1f%% (diff: %+.1f%%) ✗".format(
          sectorBreadth,
          marketBreadth,
          difference,
        )
      }

    return ConditionEvaluationResult(
      conditionType = "SectorBreadthGreaterThanSpyCondition",
      description = description(),
      passed = passed,
      actualValue = "Sector: %.1f%%, Market: %.1f%%".format(sectorBreadth, marketBreadth),
      threshold = "Sector > Market",
      message = message,
    )
  }
}
