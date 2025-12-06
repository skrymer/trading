package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.controller.dto.ConditionEvaluationResult
import com.skrymer.udgaard.model.OrderBlockType
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.condition.TradingCondition
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Entry condition that checks if the price is below a bearish order block by a specified percentage.
 * This is useful for entering positions when price pulls back to support areas.
 *
 * @param percentBelow Percentage below order block required (e.g., 2.0 for 2%)
 * @param ageInDays Minimum age of order block to consider (default 30 days)
 */
class BelowOrderBlockCondition(
    private val percentBelow: Double = 2.0,
    private val ageInDays: Int = 30
) : TradingCondition {
    override fun evaluate(stock: Stock, quote: StockQuote): Boolean {
        // Find bearish order blocks older than specified age
        val relevantOrderBlocks = stock.orderBlocks
            .filter {
                // Must be a bearish order block (resistance)
                it.orderBlockType == OrderBlockType.BEARISH
            }
            .filter {
                // Must be older than specified age
                ChronoUnit.DAYS.between(
                    it.startDate,
                    quote.date
                ) >= ageInDays
            }
            .filter {
                // Order block must have started before current quote
                it.startDate.isBefore(quote.date)
            }
            .filter {
                // Order block must still be active (endDate is null or in the future)
                it.endDate == null || it.endDate.isAfter(quote.date)
            }
            .filter {
                // Order block must be above current price (we're below it)
                it.low > quote.closePrice
            }

        // If no relevant order blocks exist, allow entry
        if (relevantOrderBlocks.isEmpty()) {
            return true
        }

        // Check if price is at least percentBelow% below any relevant order block's low
        return relevantOrderBlocks.any { orderBlock ->
            val requiredPrice = orderBlock.low * (1.0 - percentBelow / 100.0)
            quote.closePrice <= requiredPrice
        }
    }

    override fun description(): String =
        "Price at least ${percentBelow}% below order block (age > ${ageInDays}d)"

    override fun getMetadata() = com.skrymer.udgaard.model.strategy.condition.ConditionMetadata(
        type = "belowOrderBlock",
        description = description()
    )

    override fun evaluateWithDetails(stock: Stock, quote: StockQuote): ConditionEvaluationResult {
        val price = quote.closePrice

        // Find relevant order blocks
        val relevantOrderBlocks = stock.orderBlocks
            .filter { it.orderBlockType == OrderBlockType.BEARISH }
            .filter { ChronoUnit.DAYS.between(it.startDate, quote.date) >= ageInDays }
            .filter { it.startDate.isBefore(quote.date) }
            .filter { it.endDate == null || it.endDate.isAfter(quote.date) }
            .filter { it.low > price }

        val message: String
        val actualValue: String
        val threshold: String
        val passed: Boolean

        if (relevantOrderBlocks.isEmpty()) {
            passed = true
            message = "No relevant order blocks found (age > ${ageInDays}d) ✓"
            actualValue = "No blocks"
            threshold = ">= ${ageInDays}d old"
        } else {
            // Find the closest order block
            val closestBlock = relevantOrderBlocks.minByOrNull { it.low }!!
            val blockAge = ChronoUnit.DAYS.between(closestBlock.startDate, quote.date)
            val requiredPrice = closestBlock.low * (1.0 - percentBelow / 100.0)
            val actualPercentBelow = ((closestBlock.low - price) / closestBlock.low) * 100.0

            passed = price <= requiredPrice

            message = if (passed) {
                "Price ${"%.2f".format(price)} is ${"%.1f".format(actualPercentBelow)}% below block at ${"%.2f".format(closestBlock.low)} (${blockAge}d old) ✓"
            } else {
                "Price ${"%.2f".format(price)} is ${"%.1f".format(actualPercentBelow)}% below block at ${"%.2f".format(closestBlock.low)} (requires >= ${percentBelow}%) ✗"
            }

            actualValue = "%.1f%%".format(actualPercentBelow)
            threshold = ">= ${percentBelow}%"
        }

        return ConditionEvaluationResult(
            conditionType = "BelowOrderBlockCondition",
            description = description(),
            passed = passed,
            actualValue = actualValue,
            threshold = threshold,
            message = message
        )
    }
}
