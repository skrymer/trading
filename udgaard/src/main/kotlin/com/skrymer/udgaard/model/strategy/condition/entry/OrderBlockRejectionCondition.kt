package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.controller.dto.ConditionEvaluationResult
import com.skrymer.udgaard.controller.dto.ConditionMetadata
import com.skrymer.udgaard.controller.dto.ParameterMetadata
import com.skrymer.udgaard.domain.OrderBlockType
import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.domain.StockQuoteDomain
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Entry condition that checks if price has been rejected by an order block N times.
 * A rejection occurs when price approaches the order block (within threshold) but fails to break through.
 * This indicates a strong resistance level that may eventually break, providing a good entry opportunity.
 *
 * @param minRejections Minimum number of rejections required (default 2)
 * @param ageInDays Minimum age of order block in days (default 30)
 * @param rejectionThreshold Percentage within order block to count as approach (default 2.0%)
 */
@Component
class OrderBlockRejectionCondition(
  private val minRejections: Int = 2,
  private val ageInDays: Int = 30,
  private val rejectionThreshold: Double = 2.0,
) : EntryCondition {
  override fun evaluate(
    stock: StockDomain,
    quote: StockQuoteDomain,
  ): Boolean {
    // Find bearish order blocks that are old enough
    val relevantOrderBlocks =
      stock.orderBlocks
        .filter { it.orderBlockType == OrderBlockType.BEARISH }
        .filter { ChronoUnit.DAYS.between(it.startDate, quote.date) >= ageInDays }
        .filter { it.startDate.isBefore(quote.date) }
        .filter {
          val endDate = it.endDate
          endDate == null || endDate.isAfter(quote.date)
        }

    if (relevantOrderBlocks.isEmpty()) {
      return false
    }

    // Check each order block for rejections
    return relevantOrderBlocks.any { orderBlock ->
      val rejectionCount = countRejections(stock, orderBlock.startDate, quote.date, orderBlock.high)
      rejectionCount >= minRejections
    }
  }

  /**
   * Count how many times price was rejected by the order block.
   * A rejection is when price approaches within threshold but doesn't break through.
   */
  private fun countRejections(
    stock: StockDomain,
    startDate: LocalDate,
    endDate: LocalDate,
    resistanceLevel: Double,
  ): Int {
    // Get all quotes between order block start and current date
    val quotes =
      stock.quotes
        .filter { !it.date.isBefore(startDate) && it.date.isBefore(endDate) }
        .sortedBy { it.date }

    if (quotes.isEmpty()) return 0

    // Calculate rejection zone (within threshold % of resistance)
    val rejectionZoneLow = resistanceLevel * (1.0 - rejectionThreshold / 100.0)

    var rejectionCount = 0
    var inRejectionZone = false

    for (q in quotes) {
      val high = q.high
      val close = q.closePrice

      // Check if price is in rejection zone (close to resistance but not through)
      val isApproaching = high >= rejectionZoneLow && high <= resistanceLevel

      if (isApproaching && !inRejectionZone) {
        // Entering rejection zone - potential rejection
        inRejectionZone = true
      } else if (!isApproaching && inRejectionZone) {
        // Left rejection zone without breaking through - count as rejection
        rejectionCount++
        inRejectionZone = false
      } else if (high > resistanceLevel) {
        // Broke through resistance - reset
        inRejectionZone = false
      }
    }

    return rejectionCount
  }

  override fun description(): String =
    "Order block rejected price $minRejections+ times (age >= ${ageInDays}d)"

  override fun getMetadata() =
    ConditionMetadata(
      type = "orderBlockRejection",
      displayName = "Order Block Rejection",
      description = "Price has been rejected by order block N times",
      parameters =
        listOf(
          ParameterMetadata(
            name = "minRejections",
            displayName = "Min Rejections",
            type = "number",
            defaultValue = 2,
            min = 1,
            max = 10,
          ),
          ParameterMetadata(
            name = "ageInDays",
            displayName = "Age in Days",
            type = "number",
            defaultValue = 30,
            min = 1,
            max = 365,
          ),
          ParameterMetadata(
            name = "rejectionThreshold",
            displayName = "Rejection Threshold %",
            type = "number",
            defaultValue = 2.0,
            min = 0.5,
            max = 5.0,
          ),
        ),
      category = "OrderBlock",
    )

  override fun evaluateWithDetails(
    stock: StockDomain,
    quote: StockQuoteDomain,
  ): ConditionEvaluationResult {
    val relevantOrderBlocks =
      stock.orderBlocks
        .filter { it.orderBlockType == OrderBlockType.BEARISH }
        .filter { ChronoUnit.DAYS.between(it.startDate, quote.date) >= ageInDays }
        .filter { it.startDate.isBefore(quote.date) }
        .filter {
          val endDate = it.endDate
          endDate == null || endDate.isAfter(quote.date)
        }

    val message: String
    val actualValue: String
    val threshold: String
    val passed: Boolean

    if (relevantOrderBlocks.isEmpty()) {
      passed = false
      message = "No relevant order blocks found (age >= ${ageInDays}d) ✗"
      actualValue = "No blocks"
      threshold = ">= $minRejections rejections"
    } else {
      // Find block with most rejections
      val blockRejections =
        relevantOrderBlocks.map { block ->
          val count = countRejections(stock, block.startDate, quote.date, block.high)
          val age = ChronoUnit.DAYS.between(block.startDate, quote.date)
          Triple(block, count, age)
        }

      val bestBlock = blockRejections.maxByOrNull { it.second }

      if (bestBlock != null) {
        val (block, rejCount, age) = bestBlock
        passed = rejCount >= minRejections

        message =
          if (passed) {
            "Order block at ${"%.2f".format(block.high)} rejected $rejCount times (${age}d old) ✓"
          } else {
            "Best block at ${"%.2f".format(block.high)} rejected only $rejCount times (need $minRejections) ✗"
          }

        actualValue = "$rejCount rejections"
        threshold = ">= $minRejections"
      } else {
        passed = false
        message = "No rejections found ✗"
        actualValue = "0 rejections"
        threshold = ">= $minRejections"
      }
    }

    return ConditionEvaluationResult(
      conditionType = "OrderBlockRejectionCondition",
      description = description(),
      passed = passed,
      actualValue = actualValue,
      threshold = threshold,
      message = message,
    )
  }
}
