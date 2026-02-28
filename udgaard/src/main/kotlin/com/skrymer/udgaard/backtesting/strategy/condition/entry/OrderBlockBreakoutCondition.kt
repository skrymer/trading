package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.dto.ParameterMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.OrderBlock
import com.skrymer.udgaard.data.model.OrderBlockType
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Entry condition for order block breakouts: price is above the nearest recently mitigated
 * bearish OB's high, confirming a breakout above former resistance.
 *
 * Logic:
 * 1. Guard: FAIL if close price is inside any active (non-mitigated) bearish OB zone
 * 2. Find the nearest (most recently mitigated) bearish OB within maxDaysSinceBreakout days
 * 3. Check price is ABOVE that OB's high
 * 4. Verify price has been above for at least consecutiveDays
 *
 * @param consecutiveDays Number of consecutive days price must be above OB high (default: 1)
 * @param maxDaysSinceBreakout Maximum trading days since OB was mitigated (default: 3)
 * @param ageInDays Minimum age of order block in trading days from startDate (default: 0)
 */
@Component
class OrderBlockBreakoutCondition(
  private val consecutiveDays: Int = 1,
  private val maxDaysSinceBreakout: Int = 3,
  private val ageInDays: Int = 0,
) : EntryCondition {
  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean {
    if (isInsideActiveOB(stock, quote)) return false

    val nearestOB = getNearestMitigatedOB(stock, quote) ?: return false

    if (quote.closePrice <= nearestOB.high) return false

    return consecutiveDays <= 1 || countConsecutiveDaysAbove(stock, quote, nearestOB.high) >= consecutiveDays
  }

  /**
   * Check if the close price is inside any active (non-mitigated) bearish OB zone.
   */
  private fun isInsideActiveOB(
    stock: Stock,
    quote: StockQuote,
  ): Boolean =
    stock.orderBlocks.any {
      it.orderBlockType == OrderBlockType.BEARISH &&
        it.startsBefore(quote.date) &&
        (it.endDate == null || it.endDate!!.isAfter(quote.date)) &&
        quote.closePrice >= it.low &&
        quote.closePrice <= it.high
    }

  /**
   * Get the nearest (most recently mitigated) bearish order block.
   * A mitigated OB has endDate set and endDate is on or before the quote date.
   */
  private fun getNearestMitigatedOB(
    stock: Stock,
    quote: StockQuote,
  ): OrderBlock? =
    stock.orderBlocks
      .filter { it.orderBlockType == OrderBlockType.BEARISH }
      .filter { stock.countTradingDaysBetween(it.startDate, quote.date) >= ageInDays }
      .filter { it.endDate != null && !it.endDate!!.isAfter(quote.date) }
      .filter {
        stock.countTradingDaysBetween(it.endDate!!, quote.date) <= maxDaysSinceBreakout
      }.maxByOrNull { it.endDate!! }

  /**
   * Count how many consecutive days (including current) price has been above the level.
   */
  private fun countConsecutiveDaysAbove(
    stock: Stock,
    quote: StockQuote,
    level: Double,
  ): Int {
    var count = 1
    var prevQuote = stock.getPreviousQuote(quote)

    while (count < 100 && prevQuote != null && prevQuote.closePrice > level) {
      count++
      prevQuote = stock.getPreviousQuote(prevQuote)
    }

    return count
  }

  override fun description(): String =
    "OB breakout (above mitigated OB high, breakout within $maxDaysSinceBreakout days, held $consecutiveDays days)"

  override fun getMetadata() =
    ConditionMetadata(
      type = "orderBlockBreakout",
      displayName = "Order Block Breakout",
      description =
        "Price is above a recently mitigated bearish order block high, " +
          "confirming a breakout above former resistance",
      parameters =
        listOf(
          ParameterMetadata(
            name = "consecutiveDays",
            displayName = "Consecutive Days Above",
            type = "number",
            defaultValue = 1,
            min = 1,
            max = 10,
          ),
          ParameterMetadata(
            name = "maxDaysSinceBreakout",
            displayName = "Max Days Since Breakout",
            type = "number",
            defaultValue = 3,
            min = 1,
            max = 30,
          ),
          ParameterMetadata(
            name = "ageInDays",
            displayName = "Min OB Age (Days)",
            type = "number",
            defaultValue = 0,
            min = 0,
            max = 365,
          ),
        ),
      category = "OrderBlock",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): ConditionEvaluationResult {
    val close = quote.closePrice

    if (isInsideActiveOB(stock, quote)) {
      return ConditionEvaluationResult(
        conditionType = "OrderBlockBreakoutCondition",
        description = description(),
        passed = false,
        actualValue = "Inside active OB",
        threshold = "Price outside all active bearish OB zones",
        message = "Price (${"%.2f".format(close)}) is inside an active bearish order block zone \u2717",
      )
    }

    val nearestOB = getNearestMitigatedOB(stock, quote)

    if (nearestOB == null) {
      return ConditionEvaluationResult(
        conditionType = "OrderBlockBreakoutCondition",
        description = description(),
        passed = false,
        actualValue = "No mitigated OBs",
        threshold = "Mitigated within $maxDaysSinceBreakout days",
        message = "No recently mitigated bearish order blocks found \u2717",
      )
    }

    val daysSinceBreakout = stock.countTradingDaysBetween(nearestOB.endDate!!, quote.date)
    val obRange = "${"%.2f".format(nearestOB.low)}-${"%.2f".format(nearestOB.high)}"

    if (close <= nearestOB.high) {
      return ConditionEvaluationResult(
        conditionType = "OrderBlockBreakoutCondition",
        description = description(),
        passed = false,
        actualValue = "Below OB high",
        threshold = "Above OB high",
        message = "Price (${"%.2f".format(close)}) below mitigated OB high " +
          "[$obRange] (broken ${daysSinceBreakout}d ago) \u2717",
      )
    }

    val daysAbove = countConsecutiveDaysAbove(stock, quote, nearestOB.high)
    val passed = daysAbove >= consecutiveDays

    return ConditionEvaluationResult(
      conditionType = "OrderBlockBreakoutCondition",
      description = description(),
      passed = passed,
      actualValue = "$daysAbove days above, broken ${daysSinceBreakout}d ago",
      threshold = ">= $consecutiveDays days above",
      message =
        if (passed) {
          "Breakout confirmed: above mitigated OB [$obRange] for $daysAbove days " +
            "(broken ${daysSinceBreakout}d ago) \u2713"
        } else {
          "Breakout too recent: only $daysAbove/$consecutiveDays days above OB " +
            "[$obRange] (broken ${daysSinceBreakout}d ago) \u2717"
        },
    )
  }
}
