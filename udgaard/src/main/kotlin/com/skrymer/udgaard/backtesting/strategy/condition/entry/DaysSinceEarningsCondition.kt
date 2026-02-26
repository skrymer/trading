package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.dto.ParameterMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component
import java.time.temporal.ChronoUnit

/**
 * Entry condition that ensures enough days have passed since the most recent earnings report.
 *
 * This helps avoid entering positions during the volatile post-earnings period.
 * Common use cases:
 * - days=3: Avoid the immediate post-earnings reaction
 * - days=5: Give a full trading week after earnings
 * - days=10: Wait for post-earnings volatility to settle
 *
 * @param days Minimum number of days since last earnings (default: 5)
 */
@Component
class DaysSinceEarningsCondition(
  private val days: Int = 5,
) : EntryCondition {
  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean {
    val quoteDate = quote.date

    val mostRecentEarnings =
      stock.earnings
        .filter { it.reportedDate != null && !it.reportedDate.isAfter(quoteDate) }
        .maxByOrNull { it.reportedDate!! }
        ?: return true

    val daysSince = ChronoUnit.DAYS.between(mostRecentEarnings.reportedDate, quoteDate)
    return daysSince >= days
  }

  override fun description(): String = "At least $days days since earnings"

  override fun getMetadata() =
    ConditionMetadata(
      type = "daysSinceEarnings",
      displayName = "Days Since Earnings",
      description = "Ensure enough days have passed since last earnings report",
      parameters =
        listOf(
          ParameterMetadata(
            name = "days",
            displayName = "Days Since Earnings",
            type = "number",
            defaultValue = 5,
            min = 0,
            max = 30,
            options = listOf("0", "1", "3", "5", "7", "10", "14", "21", "30"),
          ),
        ),
      category = "Earnings",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): ConditionEvaluationResult {
    val quoteDate = quote.date

    val mostRecentEarnings =
      stock.earnings
        .filter { it.reportedDate != null && !it.reportedDate.isAfter(quoteDate) }
        .maxByOrNull { it.reportedDate!! }

    val passed = evaluate(stock, quote, context)

    val message =
      if (mostRecentEarnings != null && mostRecentEarnings.reportedDate != null) {
        val daysSince = ChronoUnit.DAYS.between(mostRecentEarnings.reportedDate, quoteDate)
        if (passed) {
          "$daysSince days since earnings (${mostRecentEarnings.reportedDate}) ✓"
        } else {
          "$daysSince days since earnings (${mostRecentEarnings.reportedDate}) - too recent ✗"
        }
      } else {
        "No past earnings found ✓"
      }

    return ConditionEvaluationResult(
      conditionType = "DaysSinceEarningsCondition",
      description = description(),
      passed = passed,
      actualValue = mostRecentEarnings?.reportedDate?.toString() ?: "None",
      threshold = "$days days",
      message = message,
    )
  }
}
