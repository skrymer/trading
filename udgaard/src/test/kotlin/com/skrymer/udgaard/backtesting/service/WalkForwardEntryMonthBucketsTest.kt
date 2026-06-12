package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.backtesting.model.Trade
import com.skrymer.udgaard.backtesting.model.TradeStatsSummary
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.time.LocalDate

class WalkForwardEntryMonthBucketsTest {
  private val service = WalkForwardService(
    backtestService = mock(),
    sectorBreadthRepository = mock(),
    marketBreadthRepository = mock(),
    positionSizingService = mock(),
    riskMetricsService = mock(),
    stockRepository = mock(),
    riskFreeRateService = mock(),
    leadershipRegimeService = mock(),
    regimeReadoutService = mock(),
  )

  @Test
  fun `buckets OOS trades by entry-date month keyed yyyy-MM`() {
    // Given trades entered across two months — three in March, one in July 2020
    val trades = listOf(
      trade(entryDate = LocalDate.of(2020, 3, 2), entryPrice = 100.0, profit = 10.0),
      trade(entryDate = LocalDate.of(2020, 3, 20), entryPrice = 50.0, profit = 5.0),
      trade(entryDate = LocalDate.of(2020, 3, 31), entryPrice = 100.0, profit = -5.0),
      trade(entryDate = LocalDate.of(2020, 7, 15), entryPrice = 100.0, profit = 8.0),
    )

    // When the OOS trades are bucketed by entry month
    val buckets = service.bucketByEntryMonth(trades)

    // Then there is one bucket per calendar month present, keyed "yyyy-MM"
    assertEquals(setOf("2020-03", "2020-07"), buckets.keys)
    assertEquals(3, buckets.getValue("2020-03").trades)
    assertEquals(2, buckets.getValue("2020-03").winners)
    assertEquals(1, buckets.getValue("2020-07").trades)
  }

  @Test
  fun `bucket totals reconcile to the whole-set trade count`() {
    // Given a mix of trades across three months
    val trades = listOf(
      trade(entryDate = LocalDate.of(2020, 1, 10), entryPrice = 100.0, profit = 4.0),
      trade(entryDate = LocalDate.of(2020, 4, 10), entryPrice = 100.0, profit = -3.0),
      trade(entryDate = LocalDate.of(2020, 4, 28), entryPrice = 100.0, profit = 6.0),
      trade(entryDate = LocalDate.of(2020, 5, 1), entryPrice = 100.0, profit = 2.0),
    )

    // When bucketed
    val buckets = service.bucketByEntryMonth(trades)

    // Then the sum of per-month trade counts equals the input size — no trade dropped or double-counted
    assertEquals(trades.size, buckets.values.sumOf { it.trades })
  }

  @Test
  fun `re-aggregating month buckets reproduces the whole-set edge`() {
    // Given OOS trades spread across three months with mixed winners and losers at varied prices
    val trades = listOf(
      trade(entryDate = LocalDate.of(2020, 1, 10), entryPrice = 100.0, profit = 12.0),
      trade(entryDate = LocalDate.of(2020, 1, 20), entryPrice = 50.0, profit = -4.0),
      trade(entryDate = LocalDate.of(2020, 4, 10), entryPrice = 200.0, profit = 30.0),
      trade(entryDate = LocalDate.of(2020, 4, 28), entryPrice = 100.0, profit = -6.0),
      trade(entryDate = LocalDate.of(2020, 9, 1), entryPrice = 80.0, profit = 5.0),
    )
    val wholeSetEdge = TradeStatsSummary.fromTrades(trades).edge

    // When the per-month buckets are summed back into one summary (ADR-0006 reconciliation:
    // Edge is non-linear, so sum the additive raw fields and recompute Edge once)
    val buckets = service.bucketByEntryMonth(trades).values
    val reaggregated = TradeStatsSummary(
      trades = buckets.sumOf { it.trades },
      winners = buckets.sumOf { it.winners },
      sumWinPercent = buckets.sumOf { it.sumWinPercent },
      sumLossPercent = buckets.sumOf { it.sumLossPercent },
      grossWinProfit = buckets.sumOf { it.grossWinProfit },
      grossLossProfit = buckets.sumOf { it.grossLossProfit },
    )

    // Then the re-aggregated edge matches the window-aggregate edge exactly
    assertEquals(wholeSetEdge, reaggregated.edge, 1e-9)
  }

  private fun trade(entryDate: LocalDate, entryPrice: Double, profit: Double): Trade = Trade(
    stockSymbol = "TEST",
    entryQuote = StockQuote(date = entryDate, closePrice = entryPrice),
    quotes = listOf(StockQuote(date = entryDate.plusDays(5), closePrice = entryPrice + profit)),
    exitReason = "Test exit",
    profit = profit,
    startDate = entryDate,
    sector = "Technology",
  )
}
