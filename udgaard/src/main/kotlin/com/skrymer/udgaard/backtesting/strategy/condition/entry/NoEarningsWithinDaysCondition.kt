package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.dto.ParameterMetadata
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component
import java.time.temporal.ChronoUnit

/**
 * Entry condition that ensures no earnings announcement within the next X days.
 *
 * This helps avoid entering positions right before earnings volatility.
 * Common use cases:
 * - days=1: Don't enter if earnings tomorrow
 * - days=7: Don't enter if earnings within next week
 * - days=14: Give 2-week buffer before earnings
 *
 * @param days Number of days to look ahead for earnings (default: 7)
 */
@Component
class NoEarningsWithinDaysCondition(
  private val days: Int = 7,
) : EntryCondition {
  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
  ): Boolean {
    val quoteDate = quote.date
    return !stock.hasEarningsWithinDays(quoteDate, days)
  }

  override fun description(): String =
    if (days == 1) {
      "No earnings tomorrow"
    } else {
      "No earnings within $days days"
    }

  override fun getMetadata() =
    ConditionMetadata(
      type = "noEarningsWithinDays",
      displayName = "No Earnings Within Days",
      description = "Ensure no earnings announcement within next X days",
      parameters =
        listOf(
          ParameterMetadata(
            name = "days",
            displayName = "Days to Look Ahead",
            type = "number",
            defaultValue = 7,
            min = 0,
            max = 30,
            options = listOf("0", "1", "3", "5", "7", "14", "21", "30"),
          ),
        ),
      category = "Earnings",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
  ): ConditionEvaluationResult {
    val quoteDate = quote.date

    // Find the next earnings date after the quote date
    val nextEarnings =
      stock.earnings
        .filter { it.reportedDate != null && !it.reportedDate.isBefore(quoteDate) }
        .minByOrNull { it.reportedDate!! }

    val hasEarningsWithinDays = stock.hasEarningsWithinDays(quoteDate, days)
    val passed = !hasEarningsWithinDays

    val message =
      if (nextEarnings != null && nextEarnings.reportedDate != null) {
        val daysUntilEarnings = ChronoUnit.DAYS.between(quoteDate, nextEarnings.reportedDate)
        if (passed) {
          "Next earnings in $daysUntilEarnings days (${nextEarnings.reportedDate}) ✓"
        } else {
          "Earnings in $daysUntilEarnings days (${nextEarnings.reportedDate}) - too soon ✗"
        }
      } else {
        if (passed) {
          "No upcoming earnings found ✓"
        } else {
          "No upcoming earnings data ✗"
        }
      }

    return ConditionEvaluationResult(
      conditionType = "NoEarningsWithinDaysCondition",
      description = description(),
      passed = passed,
      actualValue = nextEarnings?.reportedDate?.toString() ?: "None",
      threshold = "$days days",
      message = message,
    )
  }
}
