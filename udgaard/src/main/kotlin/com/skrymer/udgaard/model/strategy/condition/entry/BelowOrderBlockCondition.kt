package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.controller.dto.ConditionEvaluationResult
import com.skrymer.udgaard.controller.dto.ConditionMetadata
import com.skrymer.udgaard.controller.dto.ParameterMetadata
import com.skrymer.udgaard.domain.OrderBlockType
import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.domain.StockQuoteDomain
import com.skrymer.udgaard.model.strategy.condition.entry.EntryCondition
import java.time.temporal.ChronoUnit

/**
 * Entry condition that checks if the price is sufficiently below any bearish order blocks.
 * Returns true (allows entry) only when price is at least X% below all relevant order blocks.
 * Returns false (blocks entry) when price is within or too close to any order block.
 *
 * @param percentBelow Percentage below order block required (e.g., 2.0 for 2%)
 * @param ageInDays Minimum age of order block to consider (default 30 days)
 */
class BelowOrderBlockCondition(
  private val percentBelow: Double = 2.0,
  private val ageInDays: Int = 30,
) : EntryCondition {
  override fun evaluate(
    stock: StockDomain,
    quote: StockQuoteDomain,
  ): Boolean {
    // Find bearish order blocks at least as old as specified age
    val relevantOrderBlocks =
      stock.orderBlocks
        .filter {
          // Must be a bearish order block (resistance)
          it.orderBlockType == OrderBlockType.BEARISH
        }.filter {
          // Must be at least ageInDays old (>= ageInDays)
          ChronoUnit.DAYS.between(
            it.startDate,
            quote.date,
          ) >= ageInDays
        }.filter {
          // Order block must have started before current quote
          it.startDate.isBefore(quote.date)
        }.filter {
          // Order block must still be active (endDate is null or in the future)
          val endDate = it.endDate
          endDate == null || endDate.isAfter(quote.date)
        }.filter {
          // Price must be at or below the order block's high (below or within)
          quote.closePrice <= it.high
        }

    // If no relevant order blocks exist, allow entry
    if (relevantOrderBlocks.isEmpty()) {
      return true
    }

    // Check if price is sufficiently below ALL relevant order blocks
    // Return false (block entry) if price is within OR too close to ANY order block
    return relevantOrderBlocks.all { orderBlock ->
      val requiredPrice = orderBlock.low * (1.0 - percentBelow / 100.0)
      // Allow entry only if price is at least percentBelow% below the order block's low
      quote.closePrice <= requiredPrice
    }
  }

  override fun description(): String = "Price at least $percentBelow% below order block (age >= ${ageInDays}d)"

  override fun getMetadata() =
    ConditionMetadata(
      type = "belowOrderBlock",
      displayName = "Below Order Block",
      description = "Price is below an order block by specified percentage",
      parameters =
        listOf(
          ParameterMetadata(
            name = "percentBelow",
            displayName = "Percent Below",
            type = "number",
            defaultValue = 2.0,
            min = 0.5,
            max = 10.0,
          ),
          ParameterMetadata(
            name = "ageInDays",
            displayName = "Age in Days",
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
    val price = quote.closePrice

    // Find relevant order blocks
    val relevantOrderBlocks =
      stock.orderBlocks
        .filter { it.orderBlockType == OrderBlockType.BEARISH }
        .filter { ChronoUnit.DAYS.between(it.startDate, quote.date) >= ageInDays }
        .filter { it.startDate.isBefore(quote.date) }
        .filter {
          val endDate = it.endDate
          endDate == null || endDate.isAfter(quote.date)
        }.filter { price <= it.high }

    val message: String
    val actualValue: String
    val threshold: String
    val passed: Boolean

    if (relevantOrderBlocks.isEmpty()) {
      passed = true
      message = "No relevant order blocks found (age >= ${ageInDays}d) ✓"
      actualValue = "No blocks"
      threshold = ">= ${ageInDays}d old"
    } else {
      // Find the closest order block (lowest low)
      val closestBlock = relevantOrderBlocks.minByOrNull { it.low }!!
      val blockAge = ChronoUnit.DAYS.between(closestBlock.startDate, quote.date)
      val requiredPrice = closestBlock.low * (1.0 - percentBelow / 100.0)
      val actualPercentBelow = ((closestBlock.low - price) / closestBlock.low) * 100.0

      val isWithinBlock = price >= closestBlock.low && price <= closestBlock.high
      val isSufficientlyBelow = price <= requiredPrice

      passed = isSufficientlyBelow

      message =
        if (isWithinBlock) {
          "Price ${"%.2f".format(
            price,
          )} is WITHIN block [${"%.2f".format(closestBlock.low)}-${"%.2f".format(closestBlock.high)}] (${blockAge}d old) ✗"
        } else if (isSufficientlyBelow) {
          "Price ${"%.2f".format(price)} is ${"%.1f".format(actualPercentBelow)}% below block at ${
            "%.2f".format(
              closestBlock.low,
            )
          } (${blockAge}d old) ✓"
        } else {
          "Price ${"%.2f".format(price)} is ${"%.1f".format(actualPercentBelow)}% below block at ${
            "%.2f".format(
              closestBlock.low,
            )
          } (requires >= $percentBelow%) ✗"
        }

      actualValue = "%.1f%%".format(actualPercentBelow)
      threshold = ">= $percentBelow%"
    }

    return ConditionEvaluationResult(
      conditionType = "BelowOrderBlockCondition",
      description = description(),
      passed = passed,
      actualValue = actualValue,
      threshold = threshold,
      message = message,
    )
  }
}
