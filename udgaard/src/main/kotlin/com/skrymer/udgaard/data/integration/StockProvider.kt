package com.skrymer.udgaard.data.integration

import com.skrymer.udgaard.data.model.Earning
import com.skrymer.udgaard.data.model.Fundamental
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.boot.actuate.health.HealthIndicator
import java.time.LocalDate

data class LatestQuote(
  val symbol: String,
  val price: Double,
  val previousClose: Double = 0.0,
  val change: Double = 0.0,
  val changePercent: Double = 0.0,
  val volume: Long = 0,
  val high: Double = 0.0,
  val low: Double = 0.0,
  // Trading day the quote belongs to, in America/New_York (US market calendar).
  // Nullable because legacy call sites and providers that don't carry a timestamp
  // will omit it — consumers should fall back to stored data when null.
  val date: LocalDate? = null,
)

/**
 * Interface for stock price and volume data providers
 *
 * Implementations provide OHLCV (Open, High, Low, Close, Volume) data for stocks, preferably
 * adjusted for corporate actions like splits and dividends. Extends Spring's `HealthIndicator`
 * so each provider exposes its own liveness probe to `/actuator/health` — Spring auto-registers
 * any `HealthIndicator` bean.
 */
interface StockProvider : HealthIndicator {
  /**
   * Get daily time series data for a stock symbol
   *
   * @param symbol Stock symbol (e.g., "AAPL", "MSFT")
   * @return List of stock quotes with price and volume data, or null if unavailable
   */
  fun getDailyAdjustedTimeSeries(symbol: String): List<StockQuote>?

  /**
   * Get the latest live quote for a symbol
   */
  fun getLatestQuote(symbol: String): LatestQuote?

  /**
   * Get latest live quotes for multiple symbols in parallel
   */
  fun getLatestQuotes(symbols: List<String>): Map<String, LatestQuote>

  /**
   * Get the full history of earnings reports for a symbol (past and projected).
   *
   * Returns an empty list when the symbol has no earnings data (e.g. a newly listed
   * stock that hasn't reported yet). Returns `null` only when the call failed — outage,
   * timeout, parse error. Consumers must treat `null` as "we don't know" and may fall
   * back to last-known-good data; treating `null` as "no earnings" silently inverts
   * filters like `noEarningsWithinDays`.
   */
  fun getEarnings(symbol: String): List<Earning>?

  /**
   * Get the full history of point-in-time quarterly fundamentals for a symbol (ADR 0019 L1).
   *
   * Returns an empty list when the symbol has no statements (e.g. an ETF). Returns `null` only when
   * the call failed — outage, timeout, parse error. As with [getEarnings], consumers must treat `null`
   * as "we don't know" and fall back to last-known-good data rather than as "no fundamentals".
   */
  fun getFundamentals(symbol: String): List<Fundamental>?
}
