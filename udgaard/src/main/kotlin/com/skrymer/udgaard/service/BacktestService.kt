package com.skrymer.udgaard.service

import com.skrymer.udgaard.domain.BreadthQuoteDomain
import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.domain.StockQuoteDomain
import com.skrymer.udgaard.model.ATRDrawdownStats
import com.skrymer.udgaard.model.BacktestReport
import com.skrymer.udgaard.model.DrawdownBucket
import com.skrymer.udgaard.model.ExcursionMetrics
import com.skrymer.udgaard.model.ExitReasonAnalysis
import com.skrymer.udgaard.model.ExitStats
import com.skrymer.udgaard.model.LosingTradesATRStats
import com.skrymer.udgaard.model.MarketConditionSnapshot
import com.skrymer.udgaard.model.PeriodStats
import com.skrymer.udgaard.model.SectorPerformance
import com.skrymer.udgaard.model.StockPerformance
import com.skrymer.udgaard.model.TimeBasedStats
import com.skrymer.udgaard.model.Trade
import com.skrymer.udgaard.model.backtest.PotentialEntry
import com.skrymer.udgaard.model.backtest.RankedEntry
import com.skrymer.udgaard.model.backtest.StockPair
import com.skrymer.udgaard.model.strategy.EntryStrategy
import com.skrymer.udgaard.model.strategy.ExitStrategy
import com.skrymer.udgaard.model.strategy.HeatmapRanker
import com.skrymer.udgaard.model.strategy.StockRanker
import com.skrymer.udgaard.repository.jooq.BreadthJooqRepository
import com.skrymer.udgaard.repository.jooq.StockJooqRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

/**
 * Service for running backtests on trading strategies.
 *
 * This service handles the core backtesting logic, including:
 * - Date-by-date chronological processing to prevent look-ahead bias
 * - Position limits with stock ranking
 * - Underlying asset support (e.g., trade TQQQ using QQQ signals)
 * - Global cooldown periods to prevent overtrading
 * - Missed trade tracking for opportunity cost analysis
 *
 * Dependencies:
 * - StockJooqRepository: Fetches SPY stock and underlying assets as needed
 * - BreadthJooqRepository: Fetches market breadth data (FULLSTOCK) as needed
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Service
class BacktestService(
  private val stockRepository: StockJooqRepository,
  private val breadthRepository: BreadthJooqRepository,
) {
  /**
   * Holds indexed quotes for fast O(1) date lookups.
   */
  private data class StockQuoteIndex(
    val stock: StockDomain,
    val quotesByDate: Map<LocalDate, StockQuoteDomain>,
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
   * @param allStocksMap - map of symbol to Stock for looking up underlying assets
   * @return list of stock pairs
   */
  private fun createStockPairs(
    stocks: List<StockDomain>,
    useUnderlyingAssets: Boolean,
    customUnderlyingMap: Map<String, String>?,
    allStocksMap: Map<String, StockDomain>,
  ): List<StockPair> =
    stocks.map { tradingStock ->
      val strategySymbol = getStrategySymbol(tradingStock.symbol, useUnderlyingAssets, customUnderlyingMap)

      val (strategyStock, underlying) =
        if (strategySymbol != tradingStock.symbol) {
          val underlyingStock =
            allStocksMap[strategySymbol]
              ?: throw IllegalStateException("Underlying asset $strategySymbol not found - ensure it's loaded before backtest")
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
  private fun buildQuoteIndexes(stocks: List<StockDomain>): Map<StockDomain, StockQuoteIndex> =
    stocks.associateWith { stock ->
      val quotesByDate =
        stock.quotes.associateBy { it.date }

      StockQuoteIndex(stock, quotesByDate)
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
    stocks: List<StockDomain>,
    after: LocalDate,
    before: LocalDate,
  ): List<LocalDate> =
    stocks
      .flatMap { stock ->
        stock.quotes
          .mapNotNull { quote ->
            val quoteDate = quote.date
            if (!quoteDate.isBefore(after) && !quoteDate.isAfter(before)) {
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
    quoteIndexes: Map<StockDomain, StockQuoteIndex>,
  ): Trade? {
    // Test exit strategy on STRATEGY stock - convert to domain models
    val strategyStockDomain = entry.stockPair.strategyStock
    val strategyEntryQuoteDomain = entry.strategyEntryQuote
    val exitReport = strategyStockDomain.testExitStrategy(strategyEntryQuoteDomain, exitStrategy)

    if (exitReport.exitReason.isNotBlank()) {
      // Get the exit quote from the strategy stock's exit report
      val strategyExitQuote = exitReport.quotes.lastOrNull()

      if (strategyExitQuote != null) {
        // Get corresponding exit date quote from TRADING stock for P/L
        val exitDate = strategyExitQuote.date
        val tradingExitQuote = quoteIndexes[entry.stockPair.tradingStock]?.getQuote(exitDate)

        if (tradingExitQuote != null) {
          val exitPrice = exitStrategy.exitPrice(entry.stockPair.tradingStock, entry.tradingEntryQuote, tradingExitQuote)
          val profit = exitPrice - entry.tradingEntryQuote.closePrice

          // Get all trading quotes between entry and exit
          val entryDate = entry.tradingEntryQuote.date
          val tradingQuotes =
            entry.stockPair.tradingStock.quotes.filter { quote ->
              val qDate = quote.date
              !qDate.isBefore(entryDate) && !qDate.isAfter(exitDate)
            }

          return Trade(
            entry.stockPair.tradingStock.symbol,
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
   * NOTE: This method does NOT use @Transactional because:
   * 1. Backtests can take 5+ minutes with many stocks
   * 2. Holding a transaction/connection open for that long triggers connection leak detection
   * 3. All Stock data is already loaded in memory (via getAllStocks) before this method runs
   * 4. This method only reads data, performs no writes
   *
   * @param entryStrategy - the entry strategy
   * @param exitStrategy - the exit strategy
   * @param stocks - the stocks to generate the report for (MUST be fully loaded with quotes/orderBlocks/earnings)
   * @param after - start date for backtest
   * @param before - end date for backtest
   * @param maxPositions - maximum number of positions to enter per day. Null means unlimited (default: null)
   * @param ranker - the stock ranker to use for selecting best stocks when position limit is reached (default: HeatmapRanker)
   * @param useUnderlyingAssets - enable automatic underlying asset detection for strategy evaluation (default: true)
   * @param customUnderlyingMap - custom symbol → underlying mappings (overrides AssetMapper)
   * @param cooldownDays - global cooldown period in trading days after exit before allowing new entries (default: 0)
   * @return a backtest report
   */
  fun backtest(
    entryStrategy: EntryStrategy,
    exitStrategy: ExitStrategy,
    stocks: List<StockDomain>,
    after: LocalDate,
    before: LocalDate,
    maxPositions: Int? = null,
    ranker: StockRanker = HeatmapRanker(),
    useUnderlyingAssets: Boolean = true,
    customUnderlyingMap: Map<String, String>? = null,
    cooldownDays: Int = 0,
  ): BacktestReport {
    val logger = LoggerFactory.getLogger("Backtest")
    val backtestStartTime = System.currentTimeMillis()
    val effectiveMaxPositions = maxPositions ?: Int.MAX_VALUE

    // Fetch SPY stock for market condition tracking
    val spyStock = stockRepository.findBySymbol("SPY")
    if (spyStock == null) {
      logger.warn("SPY stock not found in database - market conditions will not be tracked")
    }

    // Fetch market breadth data (FULLSTOCK) for breadth analysis
    val marketBreadth = breadthRepository.findBySymbol("FULLSTOCK")
    if (marketBreadth == null) {
      logger.warn("FULLSTOCK breadth data not found in database - breadth analysis will not be available")
    }

    // Fetch underlying assets if needed
    val underlyingAssets = mutableListOf<StockDomain>()
    if (useUnderlyingAssets) {
      val underlyingSymbols =
        stocks
          .map { getStrategySymbol(it.symbol, useUnderlyingAssets, customUnderlyingMap) }
          .filter { it != null && it !in stocks.map { s -> s.symbol } }
          .distinct()

      logger.info("Fetching ${underlyingSymbols.size} underlying assets: ${underlyingSymbols.joinToString(", ")}")

      underlyingSymbols.forEach { symbol ->
        val underlying = stockRepository.findBySymbol(symbol)
        if (underlying != null) {
          underlyingAssets.add(underlying)
        } else {
          logger.error("Underlying asset $symbol not found in database")
          throw IllegalArgumentException("Underlying asset $symbol not found in database. Please load this data before running the backtest.")
        }
      }
    }

    // Combine trading stocks and underlying assets for processing
    val allStocks = stocks + underlyingAssets
    val allStocksMap = allStocks.associateBy { it.symbol }

    logger.info("Backtest starting with ${stocks.size} trading stocks and ${underlyingAssets.size} underlying assets")

    // Validate that all underlying assets exist in the loaded stocks
    validateUnderlyingAssets(stocks, useUnderlyingAssets, customUnderlyingMap, allStocksMap)

    // Create stock pairs: trading stock + strategy stock (might be the same)
    val stockPairs = createStockPairs(stocks, useUnderlyingAssets, customUnderlyingMap, allStocksMap)

    // Build quote indexes for O(1) date lookups
    val stocksForIndexing = stockPairs.flatMap { listOf(it.tradingStock, it.strategyStock) }.distinct()
    val quoteIndexes = buildQuoteIndexes(stocksForIndexing)

    // Step 1: Build date range - collect all unique trading dates from all stocks
    val allTradingDates = buildTradingDateRange(stocks, after, before)

    val positionInfo = maxPositions?.let { "max $it positions" } ?: "unlimited positions"
    val cooldownInfo = if (cooldownDays > 0) ", ${cooldownDays}d cooldown" else ""
    logger.info("Backtest: ${allTradingDates.size} trading days, $positionInfo$cooldownInfo")

    // Step 2: Choose parallel or sequential processing path
    val (trades, missedTrades) =
      if (maxPositions == null && cooldownDays == 0) {
        backtestParallel(entryStrategy, exitStrategy, stockPairs, quoteIndexes, allTradingDates, logger)
      } else {
        backtestSequential(
          entryStrategy,
          exitStrategy,
          stockPairs,
          quoteIndexes,
          allTradingDates,
          maxPositions,
          effectiveMaxPositions,
          ranker,
          cooldownDays,
          logger,
        )
      }

    val mainLoopDuration = System.currentTimeMillis() - backtestStartTime
    logger.info("Backtest complete: ${trades.size} trades, ${missedTrades.size} missed in ${mainLoopDuration}ms")

    logger.info("Calculating trade excursion metrics and market conditions...")

    // Build indexed lookups for SPY and breadth to avoid O(n) scans per trade
    val spyQuotesByDate = spyStock?.quotes?.associateBy { it.date }
    val breadthQuotesByDate = marketBreadth?.quotes?.associateBy { it.quoteDate }

    // Enrich each trade with excursion metrics and market conditions (parallel)
    runBlocking {
      val dispatcher = Dispatchers.Default.limitedParallelism(Runtime.getRuntime().availableProcessors().coerceAtMost(16))
      trades
        .map { trade ->
          async(dispatcher) {
            trade.excursionMetrics = calculateExcursionMetrics(trade, trade.entryQuote)
            trade.marketConditionAtEntry =
              captureMarketConditionIndexed(trade.entryQuote.date, spyQuotesByDate, breadthQuotesByDate)
          }
        }.awaitAll()
    }

    logger.info("Calculating aggregate diagnostic statistics...")

    val (winningTrades, losingTrades) = trades.partition { it.profit > 0 }

    // Calculate aggregate diagnostic statistics (parallel)
    val (timeStats, exitAnalysis, sectorPerf, stockPerf, atrDrawdown) =
      runBlocking {
        val d = Dispatchers.Default
        val t1 = async(d) { calculateTimeBasedStats(trades) }
        val t2 = async(d) { calculateExitReasonAnalysis(trades) }
        val t3 = async(d) { calculateSectorPerformance(trades) }
        val t4 = async(d) { calculateStockPerformance(trades) }
        val t5 = async(d) { calculateATRDrawdownStats(winningTrades, losingTrades) }
        AnalyticsResult(t1.await(), t2.await(), t3.await(), t4.await(), t5.await())
      }

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

    val totalDuration = System.currentTimeMillis() - backtestStartTime
    logger.info("Backtest analysis complete with enhanced metrics in ${totalDuration}ms total")

    return BacktestReport(
      winningTrades,
      losingTrades,
      missedTrades,
      timeBasedStats = timeStats,
      exitReasonAnalysis = exitAnalysis,
      sectorPerformance = sectorPerf,
      stockPerformance = stockPerf,
      atrDrawdownStats = atrDrawdown,
      marketConditionAverages = marketAvgs,
    )
  }

  /**
   * Parallel backtest path: processes each stock independently using coroutines.
   * Used when there are no position limits or cooldown (each stock is independent).
   */
  private fun backtestParallel(
    entryStrategy: EntryStrategy,
    exitStrategy: ExitStrategy,
    stockPairs: List<StockPair>,
    quoteIndexes: Map<StockDomain, StockQuoteIndex>,
    allTradingDates: List<LocalDate>,
    logger: org.slf4j.Logger,
  ): Pair<List<Trade>, List<Trade>> {
    val parallelism = Runtime.getRuntime().availableProcessors().coerceAtMost(16)
    val totalStocks = stockPairs.size
    logger.info("Parallel processing: $totalStocks stocks across $parallelism threads")

    val completed = AtomicInteger(0)
    val lastLoggedPercent = AtomicInteger(0)

    val trades =
      runBlocking {
        val dispatcher = Dispatchers.Default.limitedParallelism(parallelism)
        stockPairs
          .map { stockPair ->
            async(dispatcher) {
              val result = processStockTrades(stockPair, entryStrategy, exitStrategy, quoteIndexes, allTradingDates)
              val done = completed.incrementAndGet()
              val pct = (done * 100) / totalStocks
              val lastPct = lastLoggedPercent.get()
              if (pct >= lastPct + 10 && pct < 100 && lastLoggedPercent.compareAndSet(lastPct, pct)) {
                logger.info("Backtest progress: $pct% ($done/$totalStocks stocks)")
              }
              result
            }
          }.awaitAll()
          .flatten()
      }

    return Pair(trades, emptyList())
  }

  /**
   * Process trades for a single stock pair. Each stock is independent — no shared mutable state.
   */
  private fun processStockTrades(
    stockPair: StockPair,
    entryStrategy: EntryStrategy,
    exitStrategy: ExitStrategy,
    quoteIndexes: Map<StockDomain, StockQuoteIndex>,
    allTradingDates: List<LocalDate>,
  ): List<Trade> {
    val trades = mutableListOf<Trade>()
    val usedTradeQuotes = HashSet<StockQuoteDomain>()

    for (currentDate in allTradingDates) {
      val strategyQuote = quoteIndexes[stockPair.strategyStock]?.getQuote(currentDate) ?: continue
      val tradingQuote = quoteIndexes[stockPair.tradingStock]?.getQuote(currentDate) ?: continue

      if (!entryStrategy.test(stockPair.strategyStock, strategyQuote)) continue
      if (tradingQuote in usedTradeQuotes) continue

      val entry = PotentialEntry(stockPair, strategyQuote, tradingQuote)
      val trade = createTradeFromEntry(entry, exitStrategy, quoteIndexes) ?: continue

      trades.add(trade)
      usedTradeQuotes.addAll(trade.quotes)
    }

    return trades
  }

  /**
   * Sequential backtest path: date-by-date processing with position limits and cooldown.
   * Required when cross-stock dependencies exist (maxPositions or cooldown).
   */
  private fun backtestSequential(
    entryStrategy: EntryStrategy,
    exitStrategy: ExitStrategy,
    stockPairs: List<StockPair>,
    quoteIndexes: Map<StockDomain, StockQuoteIndex>,
    allTradingDates: List<LocalDate>,
    maxPositions: Int?,
    effectiveMaxPositions: Int,
    ranker: StockRanker,
    cooldownDays: Int,
    logger: org.slf4j.Logger,
  ): Pair<List<Trade>, List<Trade>> {
    val trades = mutableListOf<Trade>()
    val missedTrades = mutableListOf<Trade>()
    val usedTradeQuotes = HashSet<StockQuoteDomain>()
    val usedMissedQuotes = HashSet<StockQuoteDomain>()
    var lastExitDate: LocalDate? = null

    logger.info("Sequential processing: ${stockPairs.size} stocks evaluated per date")

    val totalDays = allTradingDates.size
    var lastLoggedPercent = 0

    allTradingDates.forEachIndexed { index, currentDate ->
      // Log progress at 5% intervals
      val currentPercent = ((index + 1) * 100) / totalDays
      if (currentPercent >= lastLoggedPercent + 5 && currentPercent < 100) {
        logger.info("Backtest progress: $currentPercent% (${index + 1}/$totalDays days, ${trades.size} trades)")
        lastLoggedPercent = currentPercent
      }

      val entriesForThisDate =
        stockPairs
          .mapNotNull { stockPair ->
            val strategyQuote = quoteIndexes[stockPair.strategyStock]?.getQuote(currentDate)
            val tradingQuote = quoteIndexes[stockPair.tradingStock]?.getQuote(currentDate)

            if (strategyQuote != null && tradingQuote != null) {
              val inCooldown = isInCooldown(currentDate, lastExitDate, allTradingDates, cooldownDays)

              if (inCooldown) {
                null
              } else {
                if (entryStrategy.test(stockPair.strategyStock, strategyQuote)) {
                  PotentialEntry(stockPair, strategyQuote, tradingQuote)
                } else {
                  null
                }
              }
            } else {
              null
            }
          }

      if (entriesForThisDate.isNotEmpty()) {
        val rankedEntries =
          entriesForThisDate
            .map { entry ->
              val score = ranker.score(entry.stockPair.strategyStock, entry.strategyEntryQuote)
              RankedEntry(entry, score)
            }.sortedByDescending { it.score }

        val openPositionCount =
          if (maxPositions != null) {
            trades.count { trade ->
              trade.entryQuote.date <= currentDate &&
                (trade.quotes.lastOrNull()?.date ?: currentDate) >= currentDate
            }
          } else {
            0
          }
        val availableSlots = (effectiveMaxPositions - openPositionCount).coerceAtLeast(0)
        val selectedEntries = rankedEntries.take(availableSlots)
        val notSelectedEntries = if (maxPositions != null) rankedEntries.drop(availableSlots) else emptyList()

        selectedEntries.forEach { rankedEntry ->
          val entry = rankedEntry.entry

          if (entry.tradingEntryQuote !in usedTradeQuotes) {
            val trade = createTradeFromEntry(entry, exitStrategy, quoteIndexes)

            if (trade != null) {
              trades.add(trade)
              usedTradeQuotes.addAll(trade.quotes)

              if (cooldownDays > 0) {
                lastExitDate = trade.quotes.lastOrNull()?.date
              }
            }
          }
        }

        notSelectedEntries.forEach { rankedEntry ->
          val entry = rankedEntry.entry

          if (entry.tradingEntryQuote !in usedMissedQuotes &&
            entry.tradingEntryQuote !in usedTradeQuotes
          ) {
            val trade = createTradeFromEntry(entry, exitStrategy, quoteIndexes)

            if (trade != null) {
              missedTrades.add(trade)
              usedMissedQuotes.addAll(trade.quotes)
            }
          }
        }
      }
    }

    return Pair(trades, missedTrades)
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
   * Validates that all underlying symbols exist in the loaded stocks map.
   * Throws IllegalArgumentException if any are missing.
   *
   * @param stocks - the trading stocks
   * @param useUnderlying - whether underlying asset detection is enabled
   * @param customMap - custom overrides map
   * @param allStocksMap - map of symbol to Stock for validation
   */
  private fun validateUnderlyingAssets(
    stocks: List<StockDomain>,
    useUnderlying: Boolean,
    customMap: Map<String, String>?,
    allStocksMap: Map<String, StockDomain>,
  ) {
    if (!useUnderlying) return

    val logger = LoggerFactory.getLogger("Backtest")
    val missingSymbols = mutableListOf<String>()

    stocks.forEach { stock ->
      val strategySymbol = getStrategySymbol(stock.symbol, true, customMap)

      if (strategySymbol != stock.symbol) {
        // Check if this underlying asset exists in the loaded stocks
        if (!allStocksMap.containsKey(strategySymbol)) {
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
    entryQuote: StockQuoteDomain,
  ): ExcursionMetrics {
    val entryPrice = entryQuote.closePrice
    val entryATR = entryQuote.atr // Fallback to 1.0 if ATR missing

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
        val count = values.count { it in previousThreshold..<threshold }
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
   * Capture market conditions at trade entry date using pre-indexed lookups for O(1) access.
   */
  private fun captureMarketConditionIndexed(
    date: LocalDate,
    spyQuotesByDate: Map<LocalDate, StockQuoteDomain>?,
    breadthQuotesByDate: Map<LocalDate, BreadthQuoteDomain>?,
  ): MarketConditionSnapshot? {
    val spyQuote = spyQuotesByDate?.get(date) ?: return null
    val breadthQuote = breadthQuotesByDate?.get(date)

    return MarketConditionSnapshot(
      spyClose = spyQuote.closePrice,
      spyHeatmap = spyQuote.heatmap,
      spyInUptrend = spyQuote.spyInUptrend,
      marketBreadthBullPercent = breadthQuote?.ema_10,
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
      avgHoldingDays = if (trades.isNotEmpty()) trades.map { it.tradingDays }.average() else 0.0,
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
            avgHoldingDays = if (reasonTrades.isNotEmpty()) reasonTrades.map { it.tradingDays }.average() else 0.0,
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
          sector = sector,
          trades = sectorTrades.size,
          winRate = if (sectorTrades.isNotEmpty()) (winners.toDouble() / sectorTrades.size) * 100 else 0.0,
          avgProfit = if (sectorTrades.isNotEmpty()) sectorTrades.map { it.profitPercentage }.average() else 0.0,
          avgHoldingDays = if (sectorTrades.isNotEmpty()) sectorTrades.map { it.tradingDays }.average() else 0.0,
        )
      }.sortedByDescending { it.trades }

  /**
   * Calculate performance statistics grouped by stock symbol.
   * Returns list sorted by edge (descending), showing top performing stocks first.
   */
  private fun calculateStockPerformance(trades: List<Trade>): List<StockPerformance> =
    trades
      .groupBy { it.stockSymbol }
      .map { (symbol, stockTrades) ->
        val winners = stockTrades.count { it.profit > 0 }
        val winRate = if (stockTrades.isNotEmpty()) (winners.toDouble() / stockTrades.size) else 0.0

        val avgWinPercent =
          if (winners > 0) {
            stockTrades.filter { it.profit > 0 }.map { it.profitPercentage }.average()
          } else {
            0.0
          }

        val losers = stockTrades.size - winners
        val avgLossPercent =
          if (losers > 0) {
            kotlin.math.abs(stockTrades.filter { it.profit <= 0 }.map { it.profitPercentage }.average())
          } else {
            0.0
          }

        val edge = (avgWinPercent * winRate) - ((1.0 - winRate) * avgLossPercent)

        // Calculate profit factor for this stock
        val profitFactor =
          if (losers > 0) {
            val grossProfit = stockTrades.filter { it.profit > 0 }.sumOf { it.profit }
            val grossLoss = kotlin.math.abs(stockTrades.filter { it.profit <= 0 }.sumOf { it.profit })
            if (grossLoss == 0.0) 0.0 else grossProfit / grossLoss
          } else {
            null // No losing trades means infinite profit factor
          }

        // Calculate maximum drawdown for this stock
        val maxDrawdown = calculateMaxDrawdownForTrades(stockTrades)

        StockPerformance(
          symbol = symbol,
          trades = stockTrades.size,
          winRate = winRate * 100, // Convert to percentage
          avgProfit = if (stockTrades.isNotEmpty()) stockTrades.map { it.profitPercentage }.average() else 0.0,
          avgHoldingDays = if (stockTrades.isNotEmpty()) stockTrades.map { it.tradingDays.toDouble() }.average() else 0.0,
          totalProfitPercentage = stockTrades.sumOf { it.profitPercentage },
          edge = edge,
          profitFactor = profitFactor,
          maxDrawdown = maxDrawdown,
        )
      }.sortedByDescending { it.edge }

  /**
   * Calculate maximum drawdown for a list of trades.
   * Drawdown is the peak-to-trough decline in cumulative returns.
   */
  private fun calculateMaxDrawdownForTrades(trades: List<Trade>): Double {
    if (trades.isEmpty()) return 0.0

    val sortedTrades = trades.sortedBy { it.entryQuote.date }
    var peak = 0.0
    var maxDrawdown = 0.0
    var cumulativeReturn = 0.0

    sortedTrades.forEach { trade ->
      cumulativeReturn += trade.profitPercentage

      if (cumulativeReturn > peak) {
        peak = cumulativeReturn
      }

      val drawdown = peak - cumulativeReturn
      if (drawdown > maxDrawdown) {
        maxDrawdown = drawdown
      }
    }

    return maxDrawdown
  }

  /**
   * Container for parallel analytics results to enable destructuring.
   */
  private data class AnalyticsResult(
    val timeStats: TimeBasedStats,
    val exitAnalysis: ExitReasonAnalysis,
    val sectorPerf: List<SectorPerformance>,
    val stockPerf: List<StockPerformance>,
    val atrDrawdown: ATRDrawdownStats?,
  )
}
