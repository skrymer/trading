package com.skrymer.udgaard.data.service

import com.skrymer.udgaard.data.model.AssetType
import com.skrymer.udgaard.data.model.Fundamental
import com.skrymer.udgaard.data.model.LiquidityFilterParams
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TradableUniverseFilterTest {
  private val filter = TradableUniverseFilter()

  /**
   * A stock of [bars] consecutive daily quotes ending at [lastDate], every bar at [close] and [volume].
   * Day spacing is irrelevant to the filter (it counts bars, not calendar days). When [rawClose] and
   * [sharesOutstanding] are both given the stock carries a point-in-time market cap (one fundamental
   * filed a month before [lastDate]); leaving either null leaves the cap unmeasurable.
   */
  private fun stockWith(
    symbol: String = "TEST",
    assetType: AssetType = AssetType.STOCK,
    bars: Int,
    close: Double,
    volume: Long,
    lastDate: LocalDate = LocalDate.of(2011, 1, 14),
    rawClose: Double? = null,
    sharesOutstanding: Long? = null,
  ): Stock {
    val quotes =
      (0 until bars).map { i ->
        StockQuote(
          symbol = symbol,
          date = lastDate.minusDays((bars - 1 - i).toLong()),
          closePrice = close,
          volume = volume,
          rawClose = rawClose,
        )
      }
    val fundamentals =
      sharesOutstanding?.let {
        listOf(
          Fundamental(
            symbol = symbol,
            fiscalDateEnding = lastDate.minusMonths(3),
            filingDate = lastDate.minusMonths(1),
            sharesOutstanding = it,
          ),
        )
      } ?: emptyList()
    return Stock(symbol = symbol, assetType = assetType, quotes = quotes, fundamentals = fundamentals)
  }

  @Test
  fun `a name whose trailing 20-bar median dollar-volume is below the floor as of the bar is not tradable`() {
    // Given: a STOCK with ample history and price above the floor, but thin liquidity —
    // close $10 x 50,000 shares = $500k/day dollar-volume, well below the $1M floor.
    val asOf = LocalDate.of(2011, 1, 14)
    val stock = stockWith(bars = 260, close = 10.0, volume = 50_000, lastDate = asOf)

    // When: eligibility is checked as of the latest bar
    val eligible = filter.isEligible(stock, asOf)

    // Then: the liquidity floor rejects it
    assertFalse(eligible)
  }

  @Test
  fun `a liquid, priced name with ample history is tradable`() {
    // Given: a STOCK clearing every floor — close $20, 200,000 shares = $4M/day dollar-volume, 260 bars.
    val asOf = LocalDate.of(2011, 1, 14)
    val stock = stockWith(bars = 260, close = 20.0, volume = 200_000, lastDate = asOf)

    // When: eligibility is checked as of the latest bar
    val eligible = filter.isEligible(stock, asOf)

    // Then: it is in the tradable universe
    assertTrue(eligible)
  }

  @Test
  fun `a liquid name trading below the price floor as of the bar is not tradable`() {
    // Given: a STOCK with ample history and clearing the liquidity floor — close $3 x 500,000 shares
    // = $1.5M/day dollar-volume (>= $1M) — but the $3 close is below the $5 price floor.
    val asOf = LocalDate.of(2011, 1, 14)
    val stock = stockWith(bars = 260, close = 3.0, volume = 500_000, lastDate = asOf)

    // When: eligibility is checked as of the latest bar
    val eligible = filter.isEligible(stock, asOf)

    // Then: the price floor rejects it despite ample liquidity
    assertFalse(eligible)
  }

  @Test
  fun `a liquid, priced name with fewer than the minimum bars of history is not tradable`() {
    // Given: a STOCK clearing price and liquidity (close $20 x 200,000 = $4M/day) but only 100 bars
    // of history as of the bar — short of the 252-bar warmup floor.
    val asOf = LocalDate.of(2011, 1, 14)
    val stock = stockWith(bars = 100, close = 20.0, volume = 200_000, lastDate = asOf)

    // When: eligibility is checked as of the latest bar
    val eligible = filter.isEligible(stock, asOf)

    // Then: insufficient history rejects it
    assertFalse(eligible)
  }

  @Test
  fun `tradability is judged point-in-time — only bars on or before the decision bar count`() {
    // Given: a $20 STOCK with 300 bars, illiquid for most of its life (40k shares = $800k/day) and
    // liquid only across its final 20 bars (500k shares = $10M/day).
    val lastDate = LocalDate.of(2011, 1, 14)
    val bars = 300
    val quotes =
      (0 until bars).map { i ->
        StockQuote(
          symbol = "DRIFT",
          date = lastDate.minusDays((bars - 1 - i).toLong()),
          closePrice = 20.0,
          volume = if (i >= bars - 20) 500_000 else 40_000,
        )
      }
    val stock = Stock(symbol = "DRIFT", assetType = AssetType.STOCK, quotes = quotes)
    val beforeLiquidityArrived = quotes[bars - 21].date // 280 bars exist, all still illiquid
    val afterLiquidityArrived = quotes.last().date // the trailing 20 bars are now the liquid ones

    // When/Then: illiquid when judged before the liquidity arrived (no peeking at future bars),
    // tradable once the liquid window lies in the past.
    assertFalse(filter.isEligible(stock, beforeLiquidityArrived))
    assertTrue(filter.isEligible(stock, afterLiquidityArrived))
  }

  @Test
  fun `the liquidity floor uses the median, robust to a bad-print outlier the mean would fail on`() {
    // Given: a $20 STOCK whose trailing 20-bar window is 9 zero-volume bad prints + 11 bars at
    // $1.5M/day. Median = $1.5M (>= $1M, tradable); the mean would be 11x1.5M/20 = $825k (< $1M).
    val lastDate = LocalDate.of(2011, 1, 14)
    val bars = 260
    val quotes =
      (0 until bars).map { i ->
        val badPrint = i in 240..248 // 9 zero-volume bars inside the trailing window
        StockQuote(
          symbol = "MED",
          date = lastDate.minusDays((bars - 1 - i).toLong()),
          closePrice = 20.0,
          volume = if (badPrint) 0L else 75_000,
        )
      }
    val stock = Stock(symbol = "MED", assetType = AssetType.STOCK, quotes = quotes)

    // When/Then: the median clears the floor — a mean-based gate would wrongly reject it.
    assertTrue(filter.isEligible(stock, lastDate))
  }

  @Test
  fun `the history floor is exact — 251 bars is rejected, 252 bars is tradable`() {
    // Given: two liquid, priced STOCKs differing only in history length, straddling the 252-bar floor.
    val asOf = LocalDate.of(2011, 1, 14)
    val justUnder = stockWith(bars = 251, close = 20.0, volume = 200_000, lastDate = asOf)
    val exactly = stockWith(bars = 252, close = 20.0, volume = 200_000, lastDate = asOf)

    // When/Then: one bar short is out; exactly at the floor is in.
    assertFalse(filter.isEligible(justUnder, asOf))
    assertTrue(filter.isEligible(exactly, asOf))
  }

  @Test
  fun `a name whose point-in-time market cap is below the floor as of the bar is not tradable`() {
    // Given: a liquid, priced STOCK with ample history whose cap is a provable $200M — raw close $20 x
    // 10,000,000 shares (no splits, so k(t) = 1) — below the $300M micro/small floor.
    val asOf = LocalDate.of(2011, 1, 14)
    val stock =
      stockWith(
        bars = 260,
        close = 20.0,
        volume = 200_000,
        lastDate = asOf,
        rawClose = 20.0,
        sharesOutstanding = 10_000_000,
      )

    // When: eligibility is checked as of the latest bar
    val eligible = filter.isEligible(stock, asOf)

    // Then: the cap floor rejects it despite clearing price, liquidity and history
    assertFalse(eligible)
  }

  @Test
  fun `a liquid, priced name whose market cap clears the floor is tradable`() {
    // Given: a STOCK clearing every floor incl. cap — raw close $20 x 20,000,000 shares = $400M (>= $300M).
    val asOf = LocalDate.of(2011, 1, 14)
    val stock =
      stockWith(
        bars = 260,
        close = 20.0,
        volume = 200_000,
        lastDate = asOf,
        rawClose = 20.0,
        sharesOutstanding = 20_000_000,
      )

    // When: eligibility is checked as of the latest bar
    val eligible = filter.isEligible(stock, asOf)

    // Then: it is in the tradable universe — the cap floor does not over-reject above the boundary
    assertTrue(eligible)
  }

  @Test
  fun `a liquid, priced name with no visible shares fundamental passes the cap floor — fail-open`() {
    // Given: a liquid, priced STOCK with ample history and a raw close, but no shares fundamental — so
    // its point-in-time cap is unmeasurable (the early-era / delisted vendor coverage gap, e.g. 2003
    // Alcoa). Fail-open: an unknown cap must NOT be treated as a sub-threshold cap.
    val asOf = LocalDate.of(2011, 1, 14)
    val stock =
      stockWith(
        bars = 260,
        close = 20.0,
        volume = 200_000,
        lastDate = asOf,
        rawClose = 20.0,
        sharesOutstanding = null,
      )

    // When: eligibility is checked as of the latest bar
    val eligible = filter.isEligible(stock, asOf)

    // Then: the cap floor does not exclude it — fillability is carried by the price/volume/age legs
    assertTrue(eligible)
  }

  @Test
  fun `the cap floor is inclusive — a name exactly at the boundary is tradable`() {
    // Given: a liquid, priced STOCK whose cap sits exactly on the $300M boundary — raw close $30 x
    // 10,000,000 shares.
    val asOf = LocalDate.of(2011, 1, 14)
    val stock =
      stockWith(
        bars = 260,
        close = 30.0,
        volume = 200_000,
        lastDate = asOf,
        rawClose = 30.0,
        sharesOutstanding = 10_000_000,
      )

    // When/Then: exactly at the floor is in (the gate is `>=`, not `>`)
    assertTrue(filter.isEligible(stock, asOf))
  }

  @Test
  fun `degenerate params with no history are rejected cleanly, never crash`() {
    // Given: a future "universe epoch" could set a zero minimum-bars; an empty-history stock must
    // still be rejected without an index-out-of-bounds, even with that degenerate config.
    val degenerate = TradableUniverseFilter(LiquidityFilterParams(minBars = 0))
    val emptyStock = Stock(symbol = "EMPTY", assetType = AssetType.STOCK, quotes = emptyList())

    // When/Then: untradable, no exception.
    assertFalse(degenerate.isEligible(emptyStock, LocalDate.of(2011, 1, 14)))
  }
}
