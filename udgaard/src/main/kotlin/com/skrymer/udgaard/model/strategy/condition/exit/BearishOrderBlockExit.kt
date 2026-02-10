package com.skrymer.udgaard.model.strategy.condition.exit

import com.skrymer.udgaard.controller.dto.ConditionEvaluationResult
import com.skrymer.udgaard.controller.dto.ConditionMetadata
import com.skrymer.udgaard.controller.dto.ParameterMetadata
import com.skrymer.udgaard.domain.OrderBlockSensitivity
import com.skrymer.udgaard.domain.OrderBlockType
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
 * @param sensitivity If set, only consider order blocks with this sensitivity level
 */
@Component
class BearishOrderBlockExit(
  private val orderBlockAgeInDays: Int = 120,
  private val useHighPrice: Boolean = false,
  private val sensitivity: OrderBlockSensitivity? = null,
) : ExitCondition {
  override fun shouldExit(
    stock: StockDomain,
    entryQuote: StockQuoteDomain?,
    quote: StockQuoteDomain,
  ): Boolean = stock.withinOrderBlock(quote, orderBlockAgeInDays, useHighPrice, sensitivity)

  override fun evaluateWithDetails(
    stock: StockDomain,
    entryQuote: StockQuoteDomain?,
    quote: StockQuoteDomain,
  ): ConditionEvaluationResult {
    val candleTop = if (useHighPrice) quote.high else maxOf(quote.openPrice, quote.closePrice)
    val candleBottom = minOf(quote.openPrice, quote.closePrice)
    val passed = stock.withinOrderBlock(quote, orderBlockAgeInDays, useHighPrice, sensitivity)

    val matchingBlocks =
      stock.orderBlocks
        .filter { it.orderBlockType == OrderBlockType.BEARISH }
        .filter { sensitivity == null || it.sensitivity == sensitivity }
        .filter { it.startsBefore(quote.date) }
        .filter { it.endsAfter(quote.date) }
        .filter { stock.countTradingDaysBetween(it.startDate, quote.date) >= orderBlockAgeInDays }
        .filter { candleTop >= it.low && candleBottom <= it.high }

    val topLabel = if (useHighPrice) "high" else "bodyTop"
    val message =
      if (matchingBlocks.isNotEmpty()) {
        val ob = matchingBlocks.first()
        val age = stock.countTradingDaysBetween(ob.startDate, quote.date)
        "Candle ($topLabel=${"%.2f".format(candleTop)}, bodyBottom=${"%.2f".format(candleBottom)}) overlaps bearish OB [${"%.2f".format(ob.low)}-${"%.2f".format(ob.high)}] (age $age days) ✓"
      } else {
        val allBearish =
          stock.orderBlocks
            .filter { it.orderBlockType == OrderBlockType.BEARISH }
            .filter { sensitivity == null || it.sensitivity == sensitivity }
            .filter { it.startsBefore(quote.date) }
            .filter { it.endsAfter(quote.date) }
        if (allBearish.isEmpty()) {
          "No active bearish order blocks on ${quote.date} ✗"
        } else {
          "Candle ($topLabel=${"%.2f".format(candleTop)}, bodyBottom=${"%.2f".format(candleBottom)}) not within any qualifying bearish OB ✗"
        }
      }

    return ConditionEvaluationResult(
      conditionType = "BearishOrderBlockExit",
      description = description(),
      passed = passed,
      actualValue = "$topLabel=${"%.2f".format(candleTop)}, bodyBottom=${"%.2f".format(candleBottom)}, withinOB=$passed",
      threshold = "Price within bearish OB (age >= $orderBlockAgeInDays days)",
      message = message,
    )
  }

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
