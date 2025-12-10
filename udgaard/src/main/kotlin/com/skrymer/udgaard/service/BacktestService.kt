package com.skrymer.udgaard.service

import com.skrymer.udgaard.model.*
import com.skrymer.udgaard.model.backtest.PotentialEntry
import com.skrymer.udgaard.model.backtest.RankedEntry
import com.skrymer.udgaard.model.backtest.StockPair
import com.skrymer.udgaard.model.strategy.EntryStrategy
import com.skrymer.udgaard.model.strategy.ExitStrategy
import com.skrymer.udgaard.model.strategy.HeatmapRanker
import com.skrymer.udgaard.model.strategy.StockRanker
import com.skrymer.udgaard.repository.BreadthRepository
import com.skrymer.udgaard.repository.StockRepository
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * Service for running backtests on trading strategies.
 *
 * This service handles the core backtesting logic, including:
 * - Date-by-date chronological processing to prevent look-ahead bias
 * - Position limits with stock ranking
 * - Underlying asset support (e.g., trade TQQQ using QQQ signals)
 * - Global cooldown periods to prevent overtrading
 * - Missed trade tracking for opportunity cost analysis
 */
@Service
class BacktestService(
  private val stockRepository: StockRepository,
  private val breadthRepository: BreadthRepository,
) {
  /**
   * Holds indexed quotes for fast O(1) date lookups.
   */
  private data class StockQuoteIndex(
    val stock: Stock,
    val quotesByDate: Map<LocalDate, com.skrymer.udgaard.model.StockQuote>,
  ) {
    fun getQuote(date: LocalDate) = quotesByDate[date]
  }

  /**
   * Creates stock pairs for backtesting.
   * Each pair contains the trading stock (what we buy/sell) and the strategy stock
   * (what signals we use for entry/exit decisions).
   *
   * @param stocks - the stocks to trade
   * @param useUnderlyingAssets - whether to use underlying assets for strategy signals
   * @param customUnderlyingMap - custom symbol mappings
   * @return list of stock pairs
   */
  private fun createStockPairs(
    stocks: List<Stock>,
    useUnderlyingAssets: Boolean,
    customUnderlyingMap: Map<String, String>?,
  ): List<StockPair> =
    stocks.map { tradingStock ->
      val strategySymbol = getStrategySymbol(tradingStock.symbol!!, useUnderlyingAssets, customUnderlyingMap)

      val (strategyStock, underlying) =
        if (strategySymbol != tradingStock.symbol) {
          val underlyingStock =
            stockRepository.findByIdOrNull(strategySymbol)
              ?: throw IllegalStateException("Underlying asset $strategySymbol not found")
          Pair(underlyingStock, strategySymbol)
        } else {
          Pair(tradingStock, null)
        }

      StockPair(tradingStock, strategyStock, underlying)
    }

  /**
   * Builds quote indexes for all stocks to enable O(1) date lookups.
   *
   * @param stocks - stocks to index
   * @return map of stock to quote index
   */
  private fun buildQuoteIndexes(stocks: List<Stock>): Map<Stock, StockQuoteIndex> =
    stocks.associate { stock ->
      val quotesByDate =
        stock.quotes
          .filter { it.date != null }
          .associateBy { it.date!! }
      stock to StockQuoteIndex(stock, quotesByDate)
    }

  /**
   * Builds a sorted list of all unique trading dates from all stocks within the date range.
   *
   * @param stocks - the stocks to extract trading dates from
   * @param after - start date for backtest (inclusive)
   * @param before - end date for backtest (inclusive)
   * @return sorted list of trading dates
   */
  private fun buildTradingDateRange(
    stocks: List<Stock>,
    after: LocalDate,
    before: LocalDate,
  ): List<LocalDate> =
    stocks
      .flatMap { stock ->
        stock.quotes
          .mapNotNull { quote ->
            val quoteDate = quote.date
            if (quoteDate != null && !quoteDate.isBefore(after) && !quoteDate.isAfter(before)) {
              quoteDate
            } else {
              null
            }
          }
      }.distinct()
      .sorted()

  /**
   * Checks if the current date is within the global cooldown period.
   *
   * After an exit, no new entries are allowed for cooldownDays trading days.
   * For example, if cooldownDays = 5 and exit on Day 0, entries are blocked on Days 1-5
   * and allowed starting on Day 6.
   *
   * @param currentDate - the date to check
   * @param lastExitDate - the most recent exit date, or null if no exits yet
   * @param allTradingDates - sorted list of all trading dates
   * @param cooldownDays - number of trading days to wait after exit before allowing new entry
   * @return true if in cooldown period, false otherwise
   */
  private fun isInCooldown(
    currentDate: LocalDate,
    lastExitDate: LocalDate?,
    allTradingDates: List<LocalDate>,
    cooldownDays: Int,
  ): Boolean {
    if (cooldownDays <= 0 || lastExitDate == null) {
      return false
    }

    val exitDateIndex = allTradingDates.indexOf(lastExitDate)
    val currentDateIndex = allTradingDates.indexOf(currentDate)

    return if (exitDateIndex >= 0 && currentDateIndex >= 0) {
      val tradingDaysSinceExit = currentDateIndex - exitDateIndex
      tradingDaysSinceExit <= cooldownDays
    } else {
      false
    }
  }

  /**
   * Creates a trade from a potential entry by evaluating the exit strategy.
   *
   * @param entry - the potential entry to create a trade from
   * @param exitStrategy - the exit strategy to use
   * @param quoteIndexes - indexed quotes for fast lookup
   * @return a Trade if entry conditions are met and a valid exit is found, null otherwise
   */
  private fun createTradeFromEntry(
    entry: PotentialEntry,
    exitStrategy: ExitStrategy,
    quoteIndexes: Map<Stock, StockQuoteIndex>,
  ): Trade? {
    // Test exit strategy on STRATEGY stock
    val exitReport = entry.stockPair.strategyStock.testExitStrategy(entry.strategyEntryQuote, exitStrategy)

    if (exitReport.exitReason.isNotBlank()) {
      // Get the exit quote from the strategy stock's exit report
      val strategyExitQuote = exitReport.quotes.lastOrNull()

      if (strategyExitQuote?.date != null) {
        // Get corresponding exit date quote from TRADING stock for P/L
        val exitDate = strategyExitQuote.date!!
        val tradingExitQuote = quoteIndexes[entry.stockPair.tradingStock]?.getQuote(exitDate)

        if (tradingExitQuote != null) {
          val profit = tradingExitQuote.closePrice - entry.tradingEntryQuote.closePrice

          // Get all trading quotes between entry and exit
          val entryDate = entry.tradingEntryQuote.date!!
          val tradingQuotes =
            entry.stockPair.tradingStock.quotes.filter { quote ->
              val qDate = quote.date
              qDate != null && !qDate.isBefore(entryDate) && !qDate.isAfter(exitDate)
            }

          return Trade(
            entry.stockPair.tradingStock.symbol!!,
            entry.stockPair.underlyingSymbol, // Store which underlying was used
            entry.tradingEntryQuote, // Use trading stock prices
            tradingQuotes,
            exitReport.exitReason,
            profit,
            entry.tradingEntryQuote.date,
            entry.stockPair.tradingStock.sectorSymbol ?: "",
          )
        }
      }
    }

    return null
  }

  /**
   * Run backtest for the given [entryStrategy] and [exitStrategy] using the [stocks] given.
   *
   * This method uses date-by-date processing which ensures proper chronological evaluation
   * of all trading conditions as the backtest progresses through time.
   *
   * @param entryStrategy - the entry strategy
   * @param exitStrategy - the exit strategy
   * @param stocks - the stocks to generate the report for
   * @param after - start date for backtest
   * @param before - end date for backtest
   * @param maxPositions - maximum number of positions to enter per day. Null means unlimited (default: null)
   * @param ranker - the stock ranker to use for selecting best stocks when position limit is reached (default: HeatmapRanker)
   * @param useUnderlyingAssets - enable automatic underlying asset detection for strategy evaluation (default: true)
   * @param customUnderlyingMap - custom symbol → underlying mappings (overrides AssetMapper)
   * @param cooldownDays - global cooldown period in trading days after exit before allowing new entries (default: 0)
   * @return a backtest report
   */
  @Transactional(readOnly = true)
  fun backtest(
    entryStrategy: EntryStrategy,
    exitStrategy: ExitStrategy,
    stocks: List<Stock>,
    after: LocalDate,
    before: LocalDate,
    maxPositions: Int? = null,
    ranker: StockRanker = HeatmapRanker(),
    useUnderlyingAssets: Boolean = true,
    customUnderlyingMap: Map<String, String>? = null,
    cooldownDays: Int = 0,
  ): BacktestReport {
    val logger = LoggerFactory.getLogger("Backtest")
    val effectiveMaxPositions = maxPositions ?: Int.MAX_VALUE

    // Validate that all underlying assets exist in the database
    validateUnderlyingAssets(stocks, useUnderlyingAssets, customUnderlyingMap)

    // Track most recent exit date for global cooldown enforcement
    var lastExitDate: LocalDate? = null

    // Create stock pairs: trading stock + strategy stock (might be the same)
    val stockPairs = createStockPairs(stocks, useUnderlyingAssets, customUnderlyingMap)

    // Build quote indexes for O(1) date lookups
    val allStocks = stockPairs.flatMap { listOf(it.tradingStock, it.strategyStock) }.distinct()
    val quoteIndexes = buildQuoteIndexes(allStocks)

    val trades = ArrayList<Trade>()
    val missedTrades = ArrayList<Trade>()

    // Step 1: Build date range - collect all unique trading dates from all stocks
    val allTradingDates = buildTradingDateRange(stocks, after, before)

    val positionInfo = maxPositions?.let { "max $it positions" } ?: "unlimited positions"
    val cooldownInfo = if (cooldownDays > 0) ", ${cooldownDays}d cooldown" else ""
    logger.info("Backtest: ${allTradingDates.size} trading days, $positionInfo$cooldownInfo")

    // Step 2: Process each date chronologically
    val totalDays = allTradingDates.size
    var lastLoggedPercent = 0

    allTradingDates.forEachIndexed { index, currentDate ->
      // Log progress at 5% intervals
      val currentPercent = ((index + 1) * 100) / totalDays
      if (currentPercent >= lastLoggedPercent + 5 && currentPercent < 100) {
        logger.info("Backtest progress: $currentPercent% (${index + 1}/$totalDays days, ${trades.size} trades)")
        lastLoggedPercent = currentPercent
      }
      // Step 2a: For each stock pair, check if entry strategy matches on this date
      val entriesForThisDate =
        stockPairs.mapNotNull { stockPair ->
          // Get quote for this specific date from STRATEGY stock (for entry evaluation)
          val strategyQuote = quoteIndexes[stockPair.strategyStock]?.getQuote(currentDate)

          // Also get quote from TRADING stock (for actual price/P&L)
          val tradingQuote = quoteIndexes[stockPair.tradingStock]?.getQuote(currentDate)

          if (strategyQuote != null && tradingQuote != null) {
            // Check global cooldown first if enabled (counting trading days, not calendar days)
            val inCooldown = isInCooldown(currentDate, lastExitDate, allTradingDates, cooldownDays)

            if (inCooldown) {
              // Skip this entry due to global cooldown
              null
            } else if (entryStrategy.test(stockPair.strategyStock, strategyQuote)) {
              // Test strategy against the underlying/strategy stock
              PotentialEntry(stockPair, strategyQuote, tradingQuote)
            } else {
              null
            }
          } else {
            null
          }
        }

      if (entriesForThisDate.isNotEmpty()) {
        // Step 2b: Rank entries by score (using strategy stock for ranking)
        val rankedEntries =
          entriesForThisDate
            .map { entry ->
              val score = ranker.score(entry.stockPair.strategyStock, entry.strategyEntryQuote)
              RankedEntry(entry, score)
            }.sortedByDescending { it.score } // Higher score = better

        // Step 2c: Take top N based on position limit (or all if unlimited)
        val selectedEntries = rankedEntries.take(effectiveMaxPositions)
        val notSelectedEntries = if (maxPositions != null) rankedEntries.drop(effectiveMaxPositions) else emptyList()

        // Step 2d: Create trades for selected stocks
        selectedEntries.forEach { rankedEntry ->
          val entry = rankedEntry.entry

          // Check if we're not already in this trade (using trading quote)
          if (trades.find { it.containsQuote(entry.tradingEntryQuote) } == null) {
            val trade = createTradeFromEntry(entry, exitStrategy, quoteIndexes)

            if (trade != null) {
              trades.add(trade)

              // Record exit date for global cooldown tracking
              if (cooldownDays > 0) {
                lastExitDate = trade.quotes.lastOrNull()?.date
              }
            }
          }
        }

        // Step 2e: Track missed trades (not selected due to position limit)
        notSelectedEntries.forEach { rankedEntry ->
          val entry = rankedEntry.entry

          // Check if we're not already tracking this trade
          if (missedTrades.find { it.containsQuote(entry.tradingEntryQuote) } == null &&
            trades.find { it.containsQuote(entry.tradingEntryQuote) } == null
          ) {
            val trade = createTradeFromEntry(entry, exitStrategy, quoteIndexes)

            if (trade != null) {
              missedTrades.add(trade)
            }
          }
        }
      }
    }

    logger.info("Backtest complete: ${trades.size} trades, ${missedTrades.size} missed")

    // Get SPY and market breadth for market condition tracking
    val spyStock = stockRepository.findByIdOrNull("SPY")
    val marketBreadth = breadthRepository.findBySymbol(BreadthSymbol.Market().toIdentifier())

    logger.info("Calculating trade excursion metrics and market conditions...")

    // Enrich each trade with excursion metrics and market conditions
    trades.forEach { trade ->
      trade.excursionMetrics = calculateExcursionMetrics(trade, trade.entryQuote)
      trade.entryQuote.date?.let { entryDate ->
        trade.marketConditionAtEntry =
          captureMarketCondition(
            entryDate,
            spyStock,
            marketBreadth,
          )
      }
    }

    logger.info("Calculating aggregate diagnostic statistics...")

    val (winningTrades, losingTrades) = trades.partition { it.profit > 0 }

    // Calculate aggregate diagnostic statistics
    val timeStats = calculateTimeBasedStats(trades)
    val exitAnalysis = calculateExitReasonAnalysis(trades)
    val sectorPerf = calculateSectorPerformance(trades)
    val atrDrawdown = calculateATRDrawdownStats(winningTrades, losingTrades)

    // Calculate market condition averages
    val marketAvgs =
      trades
        .mapNotNull { it.marketConditionAtEntry }
        .let { conditions ->
          if (conditions.isEmpty()) {
            null
          } else {
            mapOf(
              "avgSpyHeatmap" to conditions.mapNotNull { it.spyHeatmap }.average(),
              "avgMarketBreadth" to conditions.mapNotNull { it.marketBreadthBullPercent }.average(),
              "spyUptrendPercent" to (conditions.count { it.spyInUptrend }.toDouble() / conditions.size) * 100,
            )
          }
        }

    logger.info("Backtest analysis complete with enhanced metrics")

    return BacktestReport(
      winningTrades,
      losingTrades,
      missedTrades,
      timeBasedStats = timeStats,
      exitReasonAnalysis = exitAnalysis,
      sectorPerformance = sectorPerf,
      atrDrawdownStats = atrDrawdown,
      marketConditionAverages = marketAvgs,
    )
  }

  /**
   * Determines which symbol to use for strategy evaluation.
   * Priority: customMap > AssetMapper > original symbol
   *
   * @param tradingSymbol - the symbol being traded
   * @param useUnderlying - whether to enable underlying asset detection
   * @param customMap - custom overrides map
   * @return the symbol to use for strategy evaluation
   */
  private fun getStrategySymbol(
    tradingSymbol: String,
    useUnderlying: Boolean,
    customMap: Map<String, String>?,
  ): String {
    if (!useUnderlying) return tradingSymbol

    // Check custom map first
    customMap?.get(tradingSymbol)?.let { return it }

    // Fall back to AssetMapper
    return com.skrymer.udgaard.util.AssetMapper
      .getUnderlyingSymbol(tradingSymbol)
  }

  /**
   * Validates that all underlying symbols exist in the database.
   * Throws IllegalArgumentException if any are missing.
   *
   * @param stocks - the trading stocks
   * @param useUnderlying - whether underlying asset detection is enabled
   * @param customMap - custom overrides map
   */
  private fun validateUnderlyingAssets(
    stocks: List<Stock>,
    useUnderlying: Boolean,
    customMap: Map<String, String>?,
  ) {
    if (!useUnderlying) return

    val logger = LoggerFactory.getLogger("Backtest")
    val missingSymbols = mutableListOf<String>()

    stocks.forEach { stock ->
      val strategySymbol = getStrategySymbol(stock.symbol!!, true, customMap)

      if (strategySymbol != stock.symbol) {
        // Check if this underlying asset exists
        val underlyingStock = stockRepository.findByIdOrNull(strategySymbol)
        if (underlyingStock == null) {
          missingSymbols.add(strategySymbol)
          logger.warn("Missing underlying asset: $strategySymbol (for ${stock.symbol})")
        }
      }
    }

    if (missingSymbols.isNotEmpty()) {
      throw IllegalArgumentException(
        "Missing underlying asset data for: ${missingSymbols.distinct().joinToString(", ")}. " +
          "Please load this data before running the backtest.",
      )
    }
  }

  // Helper extension for formatting doubles
  private fun Double.format(decimals: Int) = "%.${decimals}f".format(this)

  /**
   * Calculate Maximum Favorable Excursion (MFE) and Maximum Adverse Excursion (MAE)
   * for a trade, both in percentage and ATR units.
   */
  private fun calculateExcursionMetrics(
    trade: Trade,
    entryQuote: StockQuote,
  ): ExcursionMetrics {
    val entryPrice = entryQuote.closePrice
    val entryATR = entryQuote.atr ?: 1.0 // Fallback to 1.0 if ATR missing

    var maxProfit = 0.0
    var maxDrawdown = 0.0
    var maxProfitATR = 0.0
    var maxDrawdownATR = 0.0

    trade.quotes.forEach { quote ->
      val profit = ((quote.closePrice - entryPrice) / entryPrice) * 100
      val profitATR = (quote.closePrice - entryPrice) / entryATR

      if (profit > maxProfit) {
        maxProfit = profit
        maxProfitATR = profitATR
      }
      if (profit < maxDrawdown) {
        maxDrawdown = profit
        maxDrawdownATR = profitATR
      }
    }

    return ExcursionMetrics(
      maxFavorableExcursion = maxProfit,
      maxFavorableExcursionATR = maxProfitATR,
      maxAdverseExcursion = maxDrawdown,
      maxAdverseExcursionATR = kotlin.math.abs(maxDrawdownATR), // Positive value
      mfeReached = maxProfit > 0,
    )
  }

  /**
   * Calculate ATR drawdown statistics for winning trades and ATR loss statistics for losing trades.
   * Measures how much adverse movement winners endured before becoming profitable,
   * and how deep losses went for comparison.
   * Strategy-agnostic - provides percentiles and distribution for user interpretation.
   */
  private fun calculateATRDrawdownStats(
    winningTrades: List<Trade>,
    losingTrades: List<Trade>,
  ): ATRDrawdownStats? {
    val drawdowns =
      winningTrades
        .mapNotNull { it.excursionMetrics?.maxAdverseExcursionATR }
        .sorted()

    if (drawdowns.isEmpty()) return null

    val size = drawdowns.size
    val median = drawdowns[size / 2]
    val mean = drawdowns.average()

    // Calculate percentiles
    fun percentile(
      p: Double,
      values: List<Double>,
    ) = values[((values.size - 1) * p).toInt().coerceIn(0, values.size - 1)]

    val p25 = percentile(0.25, drawdowns)
    val p50 = median
    val p75 = percentile(0.75, drawdowns)
    val p90 = percentile(0.90, drawdowns)
    val p95 = percentile(0.95, drawdowns)
    val p99 = percentile(0.99, drawdowns)

    // Build distribution with cumulative percentages
    val buckets =
      listOf(
        "0.0-0.5" to 0.5,
        "0.5-1.0" to 1.0,
        "1.0-1.5" to 1.5,
        "1.5-2.0" to 2.0,
        "2.0-2.5" to 2.5,
        "2.5-3.0" to 3.0,
        "3.0+" to Double.MAX_VALUE,
      )

    fun buildDistribution(values: List<Double>): Map<String, DrawdownBucket> {
      val distribution = mutableMapOf<String, DrawdownBucket>()
      var cumulativeCount = 0
      var previousThreshold = 0.0

      buckets.forEach { (range, threshold) ->
        val count = values.count { it >= previousThreshold && it < threshold }
        cumulativeCount += count
        val percentage = (count.toDouble() / values.size) * 100
        val cumulativePercentage = (cumulativeCount.toDouble() / values.size) * 100

        distribution[range] =
          DrawdownBucket(
            range = range,
            count = count,
            percentage = percentage,
            cumulativePercentage = cumulativePercentage,
          )

        previousThreshold = threshold
      }

      return distribution
    }

    val distribution = buildDistribution(drawdowns)

    // Calculate losing trades ATR stats
    val losingTradesStats =
      run {
        val losses =
          losingTrades
            .mapNotNull { it.excursionMetrics?.maxAdverseExcursionATR }
            .sorted()

        if (losses.isEmpty()) return@run null

        val lossSize = losses.size
        val lossMedian = losses[lossSize / 2]
        val lossMean = losses.average()

        LosingTradesATRStats(
          medianLoss = lossMedian,
          meanLoss = lossMean,
          percentile25 = percentile(0.25, losses),
          percentile50 = lossMedian,
          percentile75 = percentile(0.75, losses),
          percentile90 = percentile(0.90, losses),
          percentile95 = percentile(0.95, losses),
          percentile99 = percentile(0.99, losses),
          minLoss = losses.first(),
          maxLoss = losses.last(),
          distribution = buildDistribution(losses),
          totalLosingTrades = lossSize,
        )
      }

    return ATRDrawdownStats(
      medianDrawdown = median,
      meanDrawdown = mean,
      percentile25 = p25,
      percentile50 = p50,
      percentile75 = p75,
      percentile90 = p90,
      percentile95 = p95,
      percentile99 = p99,
      minDrawdown = drawdowns.first(),
      maxDrawdown = drawdowns.last(),
      distribution = distribution,
      totalWinningTrades = size,
      losingTradesStats = losingTradesStats,
    )
  }

  /**
   * Capture market conditions at trade entry date.
   * Requires SPY stock data and optionally market breadth.
   */
  private fun captureMarketCondition(
    date: LocalDate,
    spyStock: Stock?,
    marketBreadth: Breadth?,
  ): MarketConditionSnapshot? {
    if (spyStock == null) return null

    val spyQuote =
      spyStock.quotes.firstOrNull { it.date == date }
        ?: return null

    val breadthQuote = marketBreadth?.quotes?.firstOrNull { it.quoteDate == date }

    return MarketConditionSnapshot(
      spyClose = spyQuote.closePrice,
      spyHeatmap = spyQuote.heatmap,
      spyInUptrend = spyQuote.spyInUptrend,
      marketBreadthBullPercent = breadthQuote?.ema_10, // Use EMA as proxy for bull percentage
      entryDate = date,
    )
  }

  /**
   * Calculate performance statistics grouped by year, quarter, and month.
   */
  private fun calculateTimeBasedStats(trades: List<Trade>): TimeBasedStats {
    val byYear =
      trades
        .groupBy { it.startDate?.year }
        .mapNotNull { (year, periodTrades) ->
          year?.let { it to calculatePeriodStats(periodTrades) }
        }.toMap()

    val byQuarter =
      trades
        .groupBy { trade ->
          trade.startDate?.let { "${it.year}-Q${(it.monthValue - 1) / 3 + 1}" }
        }.mapNotNull { (quarter, periodTrades) ->
          quarter?.let { it to calculatePeriodStats(periodTrades) }
        }.toMap()

    val byMonth =
      trades
        .groupBy { trade ->
          trade.startDate?.let { "${it.year}-${it.monthValue.toString().padStart(2, '0')}" }
        }.mapNotNull { (month, periodTrades) ->
          month?.let { it to calculatePeriodStats(periodTrades) }
        }.toMap()

    return TimeBasedStats(byYear, byQuarter, byMonth)
  }

  /**
   * Calculate statistics for a specific time period.
   */
  private fun calculatePeriodStats(trades: List<Trade>): PeriodStats {
    val winners = trades.count { it.profit > 0 }
    val exitReasons = trades.groupingBy { it.exitReason }.eachCount()

    return PeriodStats(
      trades = trades.size,
      winRate = if (trades.isNotEmpty()) (winners.toDouble() / trades.size) * 100 else 0.0,
      avgProfit = if (trades.isNotEmpty()) trades.map { it.profitPercentage }.average() else 0.0,
      avgHoldingDays = if (trades.isNotEmpty()) trades.mapNotNull { it.tradingDays }.average() else 0.0,
      exitReasons = exitReasons,
    )
  }

  /**
   * Analyze exit reasons with statistics per reason and per year.
   */
  private fun calculateExitReasonAnalysis(trades: List<Trade>): ExitReasonAnalysis {
    val byReason =
      trades
        .groupBy { it.exitReason }
        .mapValues { (_, reasonTrades) ->
          val winners = reasonTrades.count { it.profit > 0 }
          ExitStats(
            count = reasonTrades.size,
            avgProfit = if (reasonTrades.isNotEmpty()) reasonTrades.map { it.profitPercentage }.average() else 0.0,
            avgHoldingDays = if (reasonTrades.isNotEmpty()) reasonTrades.mapNotNull { it.tradingDays }.average() else 0.0,
            winRate = if (reasonTrades.isNotEmpty()) (winners.toDouble() / reasonTrades.size) * 100 else 0.0,
          )
        }

    val byYearAndReason =
      trades
        .groupBy { it.startDate?.year }
        .mapNotNull { (year, yearTrades) ->
          year?.let {
            it to yearTrades.groupingBy { trade -> trade.exitReason }.eachCount()
          }
        }.toMap()

    return ExitReasonAnalysis(byReason, byYearAndReason)
  }

  /**
   * Calculate performance statistics grouped by sector.
   */
  private fun calculateSectorPerformance(trades: List<Trade>): List<SectorPerformance> =
    trades
      .groupBy { it.sector }
      .map { (sector, sectorTrades) ->
        val winners = sectorTrades.count { it.profit > 0 }
        SectorPerformance(
          sector = sector ?: "Unknown",
          trades = sectorTrades.size,
          winRate = if (sectorTrades.isNotEmpty()) (winners.toDouble() / sectorTrades.size) * 100 else 0.0,
          avgProfit = if (sectorTrades.isNotEmpty()) sectorTrades.map { it.profitPercentage }.average() else 0.0,
          avgHoldingDays = if (sectorTrades.isNotEmpty()) sectorTrades.mapNotNull { it.tradingDays }.average() else 0.0,
        )
      }.sortedByDescending { it.trades }
}
