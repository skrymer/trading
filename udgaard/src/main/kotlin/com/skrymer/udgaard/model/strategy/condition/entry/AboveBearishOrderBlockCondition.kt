package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.controller.dto.ConditionEvaluationResult
import com.skrymer.udgaard.controller.dto.ConditionMetadata
import com.skrymer.udgaard.controller.dto.ParameterMetadata
import com.skrymer.udgaard.domain.OrderBlockDomain
import com.skrymer.udgaard.domain.OrderBlockSensitivity
import com.skrymer.udgaard.domain.OrderBlockType
import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.domain.StockQuoteDomain
import org.springframework.stereotype.Component

/**
 * Entry condition that checks if price has been above a bearish order block for X consecutive days.
 *
 * This validates a successful breakout above resistance that has held for multiple days,
 * indicating strong momentum and reduced likelihood of a failed breakout.
 *
 * The "blocked" state matches TradingView's obEntryBlocked logic:
 * - Inside: price is within [low, high] of any bearish OB
 * - Near: price is within proximityPercent% below an OB's bottom
 *
 * Use cases:
 * - consecutiveDays=1: Price just broke above resistance
 * - consecutiveDays=3: Confirming breakout held for 3 days (default)
 * - consecutiveDays=5: Strong confirmation of sustained breakout
 *
 * @param consecutiveDays Number of consecutive days price must be above order block (default: 3)
 * @param ageInDays Minimum age of order block to consider (default: 30 days)
 * @param proximityPercent Percentage distance below OB bottom that still counts as blocked (default: 2.0%)
 * @param sensitivity If set, only consider order blocks with this sensitivity level
 */
@Component
class AboveBearishOrderBlockCondition(
  private val consecutiveDays: Int = 3,
  private val ageInDays: Int = 30,
  private val proximityPercent: Double = 2.0,
  private val sensitivity: OrderBlockSensitivity? = null,
) : EntryCondition {
  override fun evaluate(
    stock: StockDomain,
    quote: StockQuoteDomain,
  ): Boolean {
    // TradingView's obCooldownBars logic (cooldown tracking only):
    // 1. Count bars since price was last INSIDE or NEAR an OB
    //    (inside = [low, high]; near = within proximityPercent% below OB bottom)
    // 2. Allow entry if barsSinceBlocked >= consecutiveDays (default 3)

    val relevantOrderBlocks = getRelevantOrderBlocks(stock, quote)

    // No relevant order blocks → safe to enter
    if (relevantOrderBlocks.isEmpty()) {
      return true
    }

    // If current bar is inside an OB, always block
    if (isOrderBlockBlocked(relevantOrderBlocks, quote)) {
      return false
    }

    // Count bars since last inside an OB
    val barsSinceBlocked = countBarsSinceBlocked(stock, quote, relevantOrderBlocks)

    // Apply cooldown: need at least consecutiveDays bars since last inside
    return barsSinceBlocked >= consecutiveDays
  }

  /**
   * Get relevant bearish order blocks that should be considered for blocking.
   * OBs must be:
   * - Bearish (resistance)
   * - At least ageInDays old
   * - Active (not ended, or ended recently)
   */
  private fun getRelevantOrderBlocks(
    stock: StockDomain,
    quote: StockQuoteDomain,
  ): List<OrderBlockDomain> =
    stock.orderBlocks
      .filter { it.orderBlockType == OrderBlockType.BEARISH }
      .filter { sensitivity == null || it.sensitivity == sensitivity }
      .filter {
        stock.countTradingDaysBetween(
          it.startDate,
          quote.date,
        ) >= ageInDays
      }.filter {
        // OB must be active (either no end date, or ended very recently)
        val endDate = it.endDate
        endDate == null || endDate.isAfter(quote.date) || endDate.isEqual(quote.date)
      }

  /**
   * Check if quote is "blocked" by order blocks.
   * For cooldown purposes, "blocked" means price is INSIDE any OB range [low, high],
   * OR within proximityPercent% below any OB's bottom (matching TV's bearNear logic).
   */
  private fun isOrderBlockBlocked(
    orderBlocks: List<OrderBlockDomain>,
    quote: StockQuoteDomain,
  ): Boolean {
    val close = quote.closePrice

    return orderBlocks.any { ob ->
      val inside = close >= ob.low && close <= ob.high
      val near = close < ob.low && ((ob.low - close) / close) * 100.0 <= proximityPercent
      inside || near
    }
  }

  /**
   * Count how many bars have passed since price was last "blocked".
   * Implements TradingView's barssince(obEntryBlocked) logic.
   * Returns Int.MAX_VALUE if never blocked in lookback window.
   */
  private fun countBarsSinceBlocked(
    stock: StockDomain,
    quote: StockQuoteDomain,
    relevantOrderBlocks: List<OrderBlockDomain>,
  ): Int {
    var count = 0
    var currentQuote = quote

    // Walk backwards until we find a blocked day (or hit limit)
    while (count < 100) { // Safety limit: don't look back more than 100 days
      val prevQuote = stock.getPreviousQuote(currentQuote) ?: return Int.MAX_VALUE

      // Get relevant OBs for this historical date
      val historicalOBs = getRelevantOrderBlocks(stock, prevQuote)

      if (isOrderBlockBlocked(historicalOBs, prevQuote)) {
        return count + 1
      }

      count++
      currentQuote = prevQuote
    }

    // If we looked back 100 days without finding a blocked day, consider it "never blocked"
    return Int.MAX_VALUE
  }

  override fun description(): String = "Price above bearish order block for $consecutiveDays consecutive days (age >= ${ageInDays}d, proximity $proximityPercent%)"

  override fun getMetadata() =
    ConditionMetadata(
      type = "aboveBearishOrderBlock",
      displayName = "Above Bearish Order Block",
      description = "Price has been above a bearish order block (resistance) for X consecutive days",
      parameters =
        listOf(
          ParameterMetadata(
            name = "consecutiveDays",
            displayName = "Consecutive Days",
            type = "number",
            defaultValue = 3,
            min = 1,
            max = 10,
            options = listOf("1", "2", "3", "5", "7"),
          ),
          ParameterMetadata(
            name = "ageInDays",
            displayName = "Order Block Age (Days)",
            type = "number",
            defaultValue = 30,
            min = 1,
            max = 365,
          ),
          ParameterMetadata(
            name = "proximityPercent",
            displayName = "Proximity (%)",
            type = "number",
            defaultValue = 2.0,
            min = 0.0,
            max = 10.0,
          ),
        ),
      category = "OrderBlock",
    )

  override fun evaluateWithDetails(
    stock: StockDomain,
    quote: StockQuoteDomain,
  ): ConditionEvaluationResult {
    val relevantOrderBlocks = getRelevantOrderBlocks(stock, quote)

    if (relevantOrderBlocks.isEmpty()) {
      return ConditionEvaluationResult(
        conditionType = "AboveBearishOrderBlockCondition",
        description = description(),
        passed = true,
        actualValue = "No blocks",
        threshold = ">= ${ageInDays}d old",
        message = "No relevant bearish order blocks found - safe to enter ✓",
      )
    }

    val close = quote.closePrice
    val highestBlock = relevantOrderBlocks.maxByOrNull { it.high }!!
    val blockAge = stock.countTradingDaysBetween(highestBlock.startDate, quote.date!!).toLong()

    // If current bar is inside or near an OB, always block
    if (isOrderBlockBlocked(relevantOrderBlocks, quote)) {
      val isInside = relevantOrderBlocks.any { close >= it.low && close <= it.high }
      val blockingOb =
        if (isInside) {
          relevantOrderBlocks.first { close >= it.low && close <= it.high }
        } else {
          relevantOrderBlocks.filter { close < it.low }.minByOrNull { it.low - close }!!
        }
      val obAge = stock.countTradingDaysBetween(blockingOb.startDate, quote.date).toLong()
      val statusStr = if (isInside) "inside" else "near"
      val nearPct = if (!isInside) " (${"%.1f".format(((blockingOb.low - close) / close) * 100.0)}% below)" else ""
      return ConditionEvaluationResult(
        conditionType = "AboveBearishOrderBlockCondition",
        description = description(),
        passed = false,
        actualValue = if (isInside) "Currently inside" else "Currently near (within $proximityPercent%)",
        threshold = ">= $consecutiveDays bars",
        message = "Currently $statusStr OB [${"%.2f".format(blockingOb.low)}-${"%.2f".format(blockingOb.high)}] at ${"%.2f".format(close)}$nearPct (${obAge}d old) ✗",
      )
    }

    // Count bars since last inside an OB
    val barsSinceBlocked = countBarsSinceBlocked(stock, quote, relevantOrderBlocks)

    val passed = barsSinceBlocked >= consecutiveDays

    val message =
      if (passed) {
        if (barsSinceBlocked == Int.MAX_VALUE) {
          "Never inside/near OB [${"%.2f".format(highestBlock.low)}-${
            "%.2f".format(
              highestBlock.high,
            )
          }] - cooldown not applicable (${blockAge}d old) ✓"
        } else {
          "Cooldown OK: $barsSinceBlocked bars since last inside/near OB [${"%.2f".format(highestBlock.low)}-${
            "%.2f".format(
              highestBlock.high,
            )
          }] (${blockAge}d old) ✓"
        }
      } else {
        "Cooldown insufficient: only $barsSinceBlocked bars since last inside/near OB [${"%.2f".format(highestBlock.low)}-${
          "%.2f".format(
            highestBlock.high,
          )
        }], need $consecutiveDays (${blockAge}d old) ✗"
      }

    val actualValue =
      if (barsSinceBlocked == Int.MAX_VALUE) {
        "Never inside/near"
      } else {
        "$barsSinceBlocked bars since inside/near"
      }

    return ConditionEvaluationResult(
      conditionType = "AboveBearishOrderBlockCondition",
      description = description(),
      passed = passed,
      actualValue = actualValue,
      threshold = ">= $consecutiveDays bars",
      message = message,
    )
  }
}
