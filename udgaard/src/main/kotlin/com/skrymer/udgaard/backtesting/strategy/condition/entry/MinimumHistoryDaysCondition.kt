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
 * Filters out trades on names that don't have enough trading history before
 * the entry date. Targets IPO-recency overfit — VCP-style breakouts on
 * freshly-listed names with thin price history are statistically over-fit
 * to the visible window. The 2018 weakness in survivorship-corrected
 * backtests is the fingerprint of this bias (lots of recent IPOs that
 * subsequently delisted).
 *
 * Uses `stock.listingDate` when set (populated by `StockIngestionService`
 * from the first bar EODHD returns); falls back to the first bar in
 * `stock.quotes` for stocks ingested before the listing-date metadata
 * existed.
 *
 * Counts calendar days, not trading days — easier to specify and reason
 * about, and the rounding error vs trading days is at most ~30% which is
 * smaller than the granularity of the parameter sweeps.
 */
@Component
class MinimumHistoryDaysCondition(
  private val days: Int = 180,
) : EntryCondition {
  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean {
    val firstBarDate = stock.listingDate ?: stock.quotes.firstOrNull()?.date ?: return false
    val daysSinceListing = ChronoUnit.DAYS.between(firstBarDate, quote.date)
    return daysSinceListing >= days
  }

  override fun description(): String = "History >= $days days since listing"

  override fun getMetadata() =
    ConditionMetadata(
      type = "minimumHistoryDays",
      displayName = "Minimum History Days",
      description = "Stock has been listed for at least N calendar days (filters IPO-recency overfit)",
      parameters =
        listOf(
          ParameterMetadata(
            name = "days",
            displayName = "Minimum Days",
            type = "number",
            defaultValue = 180,
            min = 1,
            max = 3650,
          ),
        ),
      category = "Stock",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): ConditionEvaluationResult {
    val firstBarDate = stock.listingDate ?: stock.quotes.firstOrNull()?.date
    val daysSinceListing = firstBarDate?.let { ChronoUnit.DAYS.between(it, quote.date) }
    val passed = daysSinceListing != null && daysSinceListing >= days
    val message =
      when {
        firstBarDate == null -> description() + " ✗ (no listing date)"
        passed -> description() + " ✓ ($daysSinceListing days)"
        else -> description() + " ✗ ($daysSinceListing days)"
      }
    return ConditionEvaluationResult(
      conditionType = "MinimumHistoryDaysCondition",
      description = description(),
      passed = passed,
      actualValue = daysSinceListing?.toString(),
      threshold = days.toString(),
      message = message,
    )
  }
}
