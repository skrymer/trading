package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.controller.dto.ConditionEvaluationResult
import com.skrymer.udgaard.controller.dto.ConditionMetadata
import com.skrymer.udgaard.controller.dto.ParameterMetadata
import com.skrymer.udgaard.domain.OrderBlockDomain
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
 * Use cases:
 * - consecutiveDays=1: Price just broke above resistance
 * - consecutiveDays=3: Confirming breakout held for 3 days (default)
 * - consecutiveDays=5: Strong confirmation of sustained breakout
 *
 * @param consecutiveDays Number of consecutive days price must be above order block (default: 3)
 * @param ageInDays Minimum age of order block to consider (default: 30 days)
 */
@Component
class AboveBearishOrderBlockCondition(
  private val consecutiveDays: Int = 3,
  private val ageInDays: Int = 30,
) : EntryCondition {
  override fun evaluate(
    stock: StockDomain,
    quote: StockQuoteDomain,
  ): Boolean {
    // TradingView's obCooldownBars logic (cooldown tracking only):
    // 1. Count bars since price was last INSIDE an OB
    // 2. Allow entry if barsSinceBlocked >= consecutiveDays (default 3)
    //
    // Note: This condition ONLY checks if sufficient cooldown time has passed since last inside.

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
   * For cooldown purposes, "blocked" means price is INSIDE any OB range [low, high].
   */
  private fun isOrderBlockBlocked(
    orderBlocks: List<OrderBlockDomain>,
    quote: StockQuoteDomain,
  ): Boolean {
    val close = quote.closePrice

    // Check bearInside: is price inside any OB?
    return orderBlocks.any { close >= it.low && close <= it.high }
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
        return count
      }

      count++
      currentQuote = prevQuote
    }

    // If we looked back 100 days without finding a blocked day, consider it "never blocked"
    return Int.MAX_VALUE
  }

  override fun description(): String = "Price above bearish order block for $consecutiveDays consecutive days (age >= ${ageInDays}d)"

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

    // If current bar is inside an OB, always block
    if (isOrderBlockBlocked(relevantOrderBlocks, quote)) {
      return ConditionEvaluationResult(
        conditionType = "AboveBearishOrderBlockCondition",
        description = description(),
        passed = false,
        actualValue = "Currently inside",
        threshold = ">= $consecutiveDays bars",
        message = "Currently inside OB [${"%.2f".format(highestBlock.low)}-${"%.2f".format(highestBlock.high)}] at ${"%.2f".format(close)} (${blockAge}d old) ✗",
      )
    }

    // Count bars since last inside an OB
    val barsSinceBlocked = countBarsSinceBlocked(stock, quote, relevantOrderBlocks)

    val passed = barsSinceBlocked >= consecutiveDays

    val message =
      if (passed) {
        if (barsSinceBlocked == Int.MAX_VALUE) {
          "Never inside OB [${"%.2f".format(highestBlock.low)}-${
            "%.2f".format(
              highestBlock.high,
            )
          }] - cooldown not applicable (${blockAge}d old) ✓"
        } else {
          "Cooldown OK: $barsSinceBlocked bars since last inside OB [${"%.2f".format(highestBlock.low)}-${
            "%.2f".format(
              highestBlock.high,
            )
          }] (${blockAge}d old) ✓"
        }
      } else {
        "Cooldown insufficient: only $barsSinceBlocked bars since last inside OB [${"%.2f".format(highestBlock.low)}-${
          "%.2f".format(
            highestBlock.high,
          )
        }], need $consecutiveDays (${blockAge}d old) ✗"
      }

    val actualValue =
      if (barsSinceBlocked == Int.MAX_VALUE) {
        "Never inside"
      } else {
        "$barsSinceBlocked bars since inside"
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
