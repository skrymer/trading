package com.skrymer.udgaard.backtesting.strategy.condition.exit

import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.dto.ParameterMetadata
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Exit condition that triggers the day before an earnings announcement.
 *
 * This helps avoid earnings-related volatility by exiting positions one day
 * before the earnings are reported.
 *
 * @param daysBeforeEarnings Number of days before earnings to exit (default: 1)
 */
@Component
class BeforeEarningsExit(
  private val daysBeforeEarnings: Int = 1,
) : ExitCondition {
  override fun shouldExit(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
  ): Boolean {
    val quoteDate = quote.date ?: return false
    return stock.hasEarningsWithinDays(quoteDate, daysBeforeEarnings)
  }

  override fun exitReason(): String =
    if (daysBeforeEarnings == 1) {
      "Exit before earnings"
    } else {
      "Exit $daysBeforeEarnings days before earnings"
    }

  override fun description(): String = exitReason()

  override fun getMetadata() =
    ConditionMetadata(
      type = "beforeEarnings",
      displayName = "Exit Before Earnings",
      description = "Exit X days before earnings announcement",
      parameters =
        listOf(
          ParameterMetadata(
            name = "daysBeforeEarnings",
            displayName = "Days Before Earnings",
            type = "number",
            defaultValue = 1,
            min = 0,
            max = 10,
            options = listOf("0", "1", "2", "3", "5", "7"),
          ),
        ),
      category = "Earnings",
    )
}
