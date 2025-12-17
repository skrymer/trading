package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.controller.dto.ConditionEvaluationResult
import com.skrymer.udgaard.controller.dto.ConditionMetadata
import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.domain.StockQuoteDomain
import com.skrymer.udgaard.model.strategy.condition.entry.EntryCondition
import org.springframework.stereotype.Component

/**
 * Entry condition that checks if the sector is in an uptrend.
 * Sector is considered in uptrend when sector bull percentage is over 10 EMA.
 */
@Component
class SectorUptrendCondition : EntryCondition {
  override fun evaluate(
    stock: StockDomain,
    quote: StockQuoteDomain,
  ): Boolean = quote.sectorIsInUptrend()

  override fun description(): String = "Sector in uptrend"

  override fun getMetadata() =
    ConditionMetadata(
      type = "sectorUptrend",
      displayName = "Sector in Uptrend",
      description = "Stock's sector is in uptrend",
      parameters = emptyList(),
      category = "Sector",
    )

  override fun evaluateWithDetails(
    stock: StockDomain,
    quote: StockQuoteDomain,
  ): ConditionEvaluationResult {
    val passed = evaluate(stock, quote)
    val message = if (passed) description() + " ✓" else description() + " ✗"

    return ConditionEvaluationResult(
      conditionType = "SectorUptrendCondition",
      description = description(),
      passed = passed,
      actualValue = null,
      threshold = null,
      message = message,
    )
  }
}
