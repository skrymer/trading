package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.backtesting.model.ATRDrawdownStats
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.backtesting.model.BacktestReport
import com.skrymer.udgaard.backtesting.model.DrawdownBucket
import com.skrymer.udgaard.backtesting.model.EntryDecisionContext
import com.skrymer.udgaard.backtesting.model.ExcursionMetrics
import com.skrymer.udgaard.backtesting.model.ExitReasonAnalysis
import com.skrymer.udgaard.backtesting.model.ExitStats
import com.skrymer.udgaard.backtesting.model.LosingTradesATRStats
import com.skrymer.udgaard.backtesting.model.MarketConditionSnapshot
import com.skrymer.udgaard.backtesting.model.PeriodStats
import com.skrymer.udgaard.backtesting.model.PositionSizingConfig
import com.skrymer.udgaard.backtesting.model.PotentialEntry
import com.skrymer.udgaard.backtesting.model.RankedEntry
import com.skrymer.udgaard.backtesting.model.SectorPerformance
import com.skrymer.udgaard.backtesting.model.StockPair
import com.skrymer.udgaard.backtesting.model.StockPerformance
import com.skrymer.udgaard.backtesting.model.TimeBasedStats
import com.skrymer.udgaard.backtesting.model.Trade
import com.skrymer.udgaard.backtesting.model.calculateEdgeConsistency
import com.skrymer.udgaard.backtesting.service.sizer.SizingContext
import com.skrymer.udgaard.backtesting.service.sizer.applyLeverageCap
import com.skrymer.udgaard.backtesting.strategy.CompositeRanker
import com.skrymer.udgaard.backtesting.strategy.EntryStrategy
import com.skrymer.udgaard.backtesting.strategy.ExitStrategy
import com.skrymer.udgaard.backtesting.strategy.StockRanker
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import com.skrymer.udgaard.data.repository.MarketBreadthRepository
import com.skrymer.udgaard.data.repository.SectorBreadthRepository
import com.skrymer.udgaard.data.repository.StockJooqRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.Collections
import java.util.IdentityHashMap
import java.util.TreeMap
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
 * - StockJooqRepository: Fetches SPY stock, underlying assets, and market breadth data
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Service
class BacktestService(
  private val stockRepository: StockJooqRepository,
  private val sectorBreadthRepository: SectorBreadthRepository,
  private val marketBreadthRepository: MarketBreadthRepository,
) {
  /**
   * Holds indexed quotes for fast O(1) date lookups.
   */
  private data class StockQuoteIndex(
    val stock: Stock,
    val quotesByDate: Map<LocalDate, StockQuote>,
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
    stocks: List<Stock>,
    useUnderlyingAssets: Boolean,
    customUnderlyingMap: Map<String, String>?,
    allStocksMap: Map<String, Stock>,
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
  private fun buildQuoteIndexes(stocks: List<Stock>): Map<Stock, StockQuoteIndex> =
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
    stocks: List<Stock>,
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
   * @param tradingDateIndex - map of date to index for O(1) lookups
   * @param cooldownDays - number of trading days to wait after exit before allowing new entry
   * @return true if in cooldown period, false otherwise
   */
  private fun isInCooldown(
    currentDate: LocalDate,
    lastExitDate: LocalDate?,
    tradingDateIndex: Map<LocalDate, Int>,
    cooldownDays: Int,
  ): Boolean {
    if (cooldownDays <= 0 || lastExitDate == null) {
      return false
    }

    val exitDateIdx = tradingDateIndex[lastExitDate] ?: return false
    val currentDateIdx = tradingDateIndex[currentDate] ?: return false
    val tradingDaysSinceExit = currentDateIdx - exitDateIdx
    return tradingDaysSinceExit <= cooldownDays
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
    context: BacktestContext = BacktestContext.EMPTY,
  ): Trade? {
    // Test exit strategy on STRATEGY stock - convert to domain models
    val strategyStockDomain = entry.stockPair.strategyStock
    val strategyEntryQuoteDomain = entry.strategyEntryQuote
    val exitReport = strategyStockDomain.testExitStrategy(strategyEntryQuoteDomain, exitStrategy, context)

    if (exitReport.exitReason.isNotBlank()) {
      // Get the exit quote from the strategy stock's exit report
      val strategyExitQuote = exitReport.quotes.lastOrNull()

      if (strategyExitQuote != null) {
        // Get corresponding exit date quote from TRADING stock for P/L
        val exitDate = strategyExitQuote.date
        val tradingExitQuote = quoteIndexes[entry.stockPair.tradingStock]?.getQuote(exitDate)

        return if (tradingExitQuote != null) {
          buildTradeWithStrategyExit(entry, exitStrategy, exitReport, tradingExitQuote, exitDate)
        } else {
          // Trading stock has no quote on the strategy's exit date — almost always
          // because it delisted before that date. Force-close on the trading stock's
          // last available bar so the loss surfaces in the backtest instead of
          // vanishing via a silent drop. Returning null here is what caused
          // pre-2010 walk-forwards to under-report drawdown.
          buildForceClosedTradeOnDelisting(entry, exitDate)
        }
      }
    }

    // Exit strategy never matched. If the trading stock is known-delisted, the
    // strategy ran out of bars without ever signalling — force-close on the last
    // available bar with the DELISTED reason instead of dropping. Without this
    // guard, late-cycle losses on delisted symbols vanish from the report.
    return forceCloseIfDelisted(entry)
  }

  private fun forceCloseIfDelisted(entry: PotentialEntry): Trade? {
    val tradingStock = entry.stockPair.tradingStock
    val delistingDate = tradingStock.delistingDate ?: return null
    val entryDate = entry.tradingEntryQuote.date
    if (!delistingDate.isAfter(entryDate)) return null
    return buildForceClosedTradeOnDelisting(entry, delistingDate)
  }

  private fun buildTradeWithStrategyExit(
    entry: PotentialEntry,
    exitStrategy: ExitStrategy,
    exitReport: com.skrymer.udgaard.data.model.ExitReport,
    tradingExitQuote: StockQuote,
    exitDate: LocalDate,
  ): Trade {
    val exitPrice = exitStrategy.exitPrice(entry.stockPair.tradingStock, entry.tradingEntryQuote, tradingExitQuote)
    val profit = exitPrice - entry.tradingEntryQuote.closePrice
    val entryDate = entry.tradingEntryQuote.date
    val tradingQuotes = entry.stockPair.tradingStock.quotesInRange(entryDate, exitDate)
    return Trade(
      entry.stockPair.tradingStock.symbol,
      entry.stockPair.underlyingSymbol,
      entry.tradingEntryQuote,
      tradingQuotes,
      exitReport.exitReason,
      profit,
      entry.tradingEntryQuote.date,
      entry.stockPair.tradingStock.sectorSymbol ?: "",
    )
  }

  private fun buildForceClosedTradeOnDelisting(
    entry: PotentialEntry,
    strategyExitDate: LocalDate,
  ): Trade? {
    val entryDate = entry.tradingEntryQuote.date
    // Last bar between the day after entry and the strategy's intended exit date.
    // Drops candidates whose trading stock had no bars at all post-entry —
    // shouldn't happen given an entry quote was found, but guard it anyway.
    val lastAvailableBar =
      entry.stockPair.tradingStock
        .quotesInRange(entryDate.plusDays(1), strategyExitDate)
        .lastOrNull()
        ?: return null
    val exitPrice = lastAvailableBar.closePrice
    val profit = exitPrice - entry.tradingEntryQuote.closePrice
    val tradingQuotes = entry.stockPair.tradingStock.quotesInRange(entryDate, lastAvailableBar.date)
    return Trade(
      entry.stockPair.tradingStock.symbol,
      entry.stockPair.underlyingSymbol,
      entry.tradingEntryQuote,
      tradingQuotes,
      Trade.EXIT_REASON_DELISTED,
      profit,
      entryDate,
      entry.stockPair.tradingStock.sectorSymbol ?: "",
    )
  }

  /**
   * Lightweight entry signal captured during Pass 1 of two-pass processing.
   */
  private data class EntrySignal(
    val date: LocalDate,
    val tradingSymbol: String,
    val strategySymbol: String?,
    val rankerScore: Double,
  )

  /**
   * Run backtest for the given [entryStrategy] and [exitStrategy] using the [symbols] given.
   *
   * Stocks are loaded in batches to keep memory bounded. Two processing paths:
   * - Path 1 (unlimited positions): batched parallel — each batch loaded, processed, released
   * - Path 2 (position-limited): two-pass — Pass 1 collects entry signals in batches,
   *   Pass 2 loads only signal stocks and runs sequential processing
   *
   * @param entryStrategy - the entry strategy
   * @param exitStrategy - the exit strategy
   * @param symbols - stock symbols to backtest
   * @param after - start date for backtest
   * @param before - end date for backtest
   * @param maxPositions - maximum simultaneous positions. Null means unlimited (default: null)
   * @param ranker - stock ranker for selection when position limit is reached (default: CompositeRanker)
   * @param useUnderlyingAssets - enable automatic underlying asset detection (default: true)
   * @param customUnderlyingMap - custom symbol → underlying mappings (overrides AssetMapper)
   * @param cooldownDays - global cooldown in trading days after exit (default: 0)
   * @return a backtest report
   */
  fun backtest(
    entryStrategy: EntryStrategy,
    exitStrategy: ExitStrategy,
    symbols: List<String>,
    after: LocalDate,
    before: LocalDate,
    maxPositions: Int? = null,
    ranker: StockRanker = CompositeRanker(),
    useUnderlyingAssets: Boolean = true,
    customUnderlyingMap: Map<String, String>? = null,
    cooldownDays: Int = 0,
    entryDelayDays: Int = 0,
    sharedContext: BacktestContext? = null,
    sharedBreadthByDate: Map<LocalDate, Double>? = null,
    randomSeed: Long? = null,
    positionSizingConfig: PositionSizingConfig? = null,
  ): BacktestReport {
    val logger = LoggerFactory.getLogger("Backtest")
    val backtestStartTime = System.currentTimeMillis()

    // Load SPY for market condition tracking (with date filtering)
    val spyStock = stockRepository.findBySymbol("SPY", quotesAfter = after)
    if (spyStock == null) {
      logger.warn("SPY stock not found in database - market conditions will not be tracked")
    }

    val breadthByDate = sharedBreadthByDate ?: marketBreadthRepository.calculateBreadthByDate()
    if (sharedBreadthByDate == null) {
      logger.info("Calculated market breadth for ${breadthByDate.size} trading days")
    }

    val backtestContext = if (sharedContext != null) {
      // Re-use shared context but update SPY quotes for this window's date range
      val spyQuoteMap = spyStock?.quotes?.associateBy { it.date } ?: emptyMap()
      sharedContext.copy(spyQuoteMap = spyQuoteMap)
    } else {
      val sectorBreadthMap = sectorBreadthRepository.findAllAsMap()
      val marketBreadthMap = marketBreadthRepository.findAllAsMap()
      val spyQuoteMap = spyStock?.quotes?.associateBy { it.date } ?: emptyMap()
      BacktestContext(sectorBreadthMap, marketBreadthMap, spyQuoteMap).also {
        logger.info(
          "Loaded backtest context: ${sectorBreadthMap.size} sectors, " +
            "${marketBreadthMap.size} market breadth days, ${spyQuoteMap.size} SPY quotes",
        )
      }
    }

    require(symbols.isNotEmpty()) { "No symbols provided for backtest" }

    val positionInfo = maxPositions?.let { "max $it positions" } ?: "unlimited positions"
    val cooldownInfo = if (cooldownDays > 0) ", ${cooldownDays}d cooldown" else ""
    val delayInfo = if (entryDelayDays > 0) ", ${entryDelayDays}d entry delay" else ""
    logger.info("Backtest: ${symbols.size} symbols, $positionInfo$cooldownInfo$delayInfo")

    // Dispatch to batched-parallel (Path 1) or two-pass (Path 2) based on constraints
    val (trades, missedTrades) =
      if (maxPositions == null && cooldownDays == 0) {
        backtestBatchedParallel(
          symbols,
          after,
          before,
          entryStrategy,
          exitStrategy,
          useUnderlyingAssets,
          customUnderlyingMap,
          backtestContext,
          entryDelayDays,
          logger,
        )
      } else {
        backtestTwoPass(
          symbols,
          after,
          before,
          entryStrategy,
          exitStrategy,
          maxPositions,
          ranker,
          useUnderlyingAssets,
          customUnderlyingMap,
          cooldownDays,
          entryDelayDays,
          backtestContext,
          logger,
          randomSeed,
          positionSizingConfig,
        )
      }

    val mainLoopDuration = System.currentTimeMillis() - backtestStartTime

    if (trades.isEmpty() && missedTrades.isEmpty()) {
      // Check if stocks actually had quote data — if not, the DB is empty
      val sampleStocks = stockRepository.findBySymbols(symbols.take(5), quotesAfter = after)
      if (sampleStocks.isEmpty() || sampleStocks.all { it.quotes.isEmpty() }) {
        throw IllegalArgumentException(
          "No stock data found in database. Use Data Manager to refresh stocks first.",
        )
      }
    }

    logger.info("Backtest complete: ${trades.size} trades, ${missedTrades.size} missed in ${mainLoopDuration}ms")

    return buildReport(trades, missedTrades, spyStock, breadthByDate, backtestStartTime, logger)
  }

  /**
   * Path 1: Batched parallel processing for unlimited positions (no maxPositions, no cooldown).
   * Loads stocks in batches, processes each batch independently, then releases memory.
   */
  private fun backtestBatchedParallel(
    symbols: List<String>,
    after: LocalDate,
    before: LocalDate,
    entryStrategy: EntryStrategy,
    exitStrategy: ExitStrategy,
    useUnderlyingAssets: Boolean,
    customUnderlyingMap: Map<String, String>?,
    context: BacktestContext,
    entryDelayDays: Int,
    logger: org.slf4j.Logger,
  ): Pair<List<Trade>, List<Trade>> {
    val allTrades = mutableListOf<Trade>()
    val batches = symbols.chunked(BATCH_SIZE)

    batches.forEachIndexed { batchIndex, batch ->
      logger.info("Processing batch ${batchIndex + 1}/${batches.size}: ${batch.size} symbols")

      val (tradingStocks, allStocksMap) = loadBatchWithUnderlying(
        batch,
        after,
        useUnderlyingAssets,
        customUnderlyingMap,
      )
      if (tradingStocks.isEmpty()) return@forEachIndexed

      validateUnderlyingAssets(tradingStocks, useUnderlyingAssets, customUnderlyingMap, allStocksMap)
      val stockPairs = createStockPairs(tradingStocks, useUnderlyingAssets, customUnderlyingMap, allStocksMap)

      val stocksForIndexing = stockPairs.flatMap { listOf(it.tradingStock, it.strategyStock) }.distinct()
      val quoteIndexes = buildQuoteIndexes(stocksForIndexing)
      val allTradingDates = buildTradingDateRange(tradingStocks, after, before)

      val (batchTrades, _) = backtestParallel(
        entryStrategy,
        exitStrategy,
        stockPairs,
        quoteIndexes,
        allTradingDates,
        context,
        entryDelayDays,
        logger,
      )
      allTrades.addAll(batchTrades)
    }

    return Pair(allTrades, emptyList())
  }

  /**
   * Path 2: Two-pass processing for position-limited backtests.
   * Pass 1: Batched signal collection (entry conditions only, low memory).
   * Pass 2: Load only stocks that triggered signals, run sequential backtest.
   */
  private fun backtestTwoPass(
    symbols: List<String>,
    after: LocalDate,
    before: LocalDate,
    entryStrategy: EntryStrategy,
    exitStrategy: ExitStrategy,
    maxPositions: Int?,
    ranker: StockRanker,
    useUnderlyingAssets: Boolean,
    customUnderlyingMap: Map<String, String>?,
    cooldownDays: Int,
    entryDelayDays: Int,
    context: BacktestContext,
    logger: org.slf4j.Logger,
    randomSeed: Long? = null,
    positionSizingConfig: PositionSizingConfig? = null,
  ): Pair<List<Trade>, List<Trade>> {
    val effectiveMaxPositions = maxPositions ?: Int.MAX_VALUE

    // --- Pass 1: Collect entry signals (batched, low memory) ---
    logger.info("Pass 1: Collecting entry signals from ${symbols.size} symbols...")
    val entrySignals = collectEntrySignals(
      symbols,
      after,
      before,
      entryStrategy,
      ranker,
      useUnderlyingAssets,
      customUnderlyingMap,
      context,
      logger,
    )
    val signalStockCount = entrySignals.map { it.tradingSymbol }.distinct().size
    logger.info("Pass 1 complete: ${entrySignals.size} entry signals from $signalStockCount stocks")

    if (entrySignals.isEmpty()) {
      return Pair(emptyList(), emptyList())
    }

    // --- Pass 2: Load signal stocks + run sequential ---
    val signalTradingSymbols = entrySignals.map { it.tradingSymbol }.distinct()
    val signalStrategySymbols = entrySignals.mapNotNull { it.strategySymbol }.distinct()
    val allSignalSymbols = (signalTradingSymbols + signalStrategySymbols).distinct()

    logger.info("Pass 2: Loading ${allSignalSymbols.size} signal stocks...")
    val stocks = stockRepository.findBySymbols(allSignalSymbols, after)
    val allStocksMap = stocks.associateBy { it.symbol }

    val tradingStocks = signalTradingSymbols.mapNotNull { allStocksMap[it] }
    validateUnderlyingAssets(tradingStocks, useUnderlyingAssets, customUnderlyingMap, allStocksMap)
    val stockPairs = createStockPairs(tradingStocks, useUnderlyingAssets, customUnderlyingMap, allStocksMap)

    val stocksForIndexing = stockPairs.flatMap { listOf(it.tradingStock, it.strategyStock) }.distinct()
    val quoteIndexes = buildQuoteIndexes(stocksForIndexing)
    val allTradingDates = buildTradingDateRange(tradingStocks, after, before)

    logger.info("Pass 2: Running sequential backtest with ${tradingStocks.size} stocks, ${allTradingDates.size} trading days")

    val tieBreakRandom = if (randomSeed != null) {
      kotlin.random.Random(randomSeed)
    } else {
      kotlin.random.Random(System.nanoTime())
    }

    return backtestSequential(
      entryStrategy,
      exitStrategy,
      stockPairs,
      quoteIndexes,
      allTradingDates,
      maxPositions,
      effectiveMaxPositions,
      ranker,
      cooldownDays,
      entryDelayDays,
      context,
      logger,
      tieBreakRandom,
      positionSizingConfig,
    )
  }

  /**
   * Collects entry signals in batches without keeping full stock data in memory.
   * Each batch is loaded, scanned for entry conditions, and released.
   */
  private fun collectEntrySignals(
    symbols: List<String>,
    after: LocalDate,
    before: LocalDate,
    entryStrategy: EntryStrategy,
    ranker: StockRanker,
    useUnderlyingAssets: Boolean,
    customUnderlyingMap: Map<String, String>?,
    context: BacktestContext,
    logger: org.slf4j.Logger,
  ): List<EntrySignal> {
    val allSignals = mutableListOf<EntrySignal>()
    val batches = symbols.chunked(BATCH_SIZE)

    batches.forEachIndexed { batchIndex, batch ->
      logger.info("Signal scan batch ${batchIndex + 1}/${batches.size}: ${batch.size} symbols")

      val (tradingStocks, allStocksMap) = loadBatchWithUnderlying(
        batch,
        after,
        useUnderlyingAssets,
        customUnderlyingMap,
      )
      if (tradingStocks.isEmpty()) return@forEachIndexed

      val stockPairs = createStockPairs(tradingStocks, useUnderlyingAssets, customUnderlyingMap, allStocksMap)
      val stocksForIndexing = stockPairs.flatMap { listOf(it.tradingStock, it.strategyStock) }.distinct()
      val quoteIndexes = buildQuoteIndexes(stocksForIndexing)

      // Scan each stock pair for entry signals
      for (stockPair in stockPairs) {
        val strategyIndex = quoteIndexes[stockPair.strategyStock] ?: continue
        val tradingIndex = quoteIndexes[stockPair.tradingStock] ?: continue

        for ((date, strategyQuote) in strategyIndex.quotesByDate) {
          if (date.isBefore(after) || date.isAfter(before)) continue
          // Skip if stock not yet listed or already delisted (survivorship bias filter)
          if (stockPair.tradingStock.listingDate?.isAfter(date) == true) continue
          if (stockPair.tradingStock.delistingDate?.isBefore(date) == true) continue
          tradingIndex.getQuote(date) ?: continue

          if (entryStrategy.test(stockPair.strategyStock, strategyQuote, context)) {
            val score = ranker.score(stockPair.strategyStock, strategyQuote, context)
            allSignals.add(
              EntrySignal(
                date = date,
                tradingSymbol = stockPair.tradingStock.symbol,
                strategySymbol = stockPair.underlyingSymbol,
                rankerScore = score,
              ),
            )
          }
        }
      }
    }

    return allSignals
  }

  /**
   * Loads a batch of trading stocks plus any needed underlying assets from the DB.
   * @return pair of (tradingStocks, allStocksMap including underlyings)
   */
  private fun loadBatchWithUnderlying(
    batch: List<String>,
    quotesAfter: LocalDate,
    useUnderlyingAssets: Boolean,
    customUnderlyingMap: Map<String, String>?,
  ): Pair<List<Stock>, Map<String, Stock>> {
    val underlyingSymbols = if (useUnderlyingAssets) {
      batch
        .map { getStrategySymbol(it, true, customUnderlyingMap) }
        .filter { it !in batch }
        .distinct()
    } else {
      emptyList()
    }

    val symbolsToLoad = (batch + underlyingSymbols).distinct()
    val stocks = stockRepository.findBySymbols(symbolsToLoad, quotesAfter)
    val allStocksMap = stocks.associateBy { it.symbol }
    val tradingStocks = batch.mapNotNull { allStocksMap[it] }

    return Pair(tradingStocks, allStocksMap)
  }

  /**
   * Builds the backtest report from completed trades. Enriches trades with excursion metrics
   * and market conditions, then calculates aggregate statistics.
   */
  private fun buildReport(
    trades: List<Trade>,
    missedTrades: List<Trade>,
    spyStock: Stock?,
    breadthByDate: Map<LocalDate, Double>,
    backtestStartTime: Long,
    logger: org.slf4j.Logger,
  ): BacktestReport {
    logger.info("Calculating trade excursion metrics and market conditions...")

    val spyQuotesByDate = spyStock?.quotes?.associateBy { it.date }

    // Enrich each trade with excursion metrics and market conditions (parallel)
    runBlocking {
      val dispatcher = Dispatchers.Default.limitedParallelism(Runtime.getRuntime().availableProcessors().coerceAtMost(5))
      trades
        .map { trade ->
          async(dispatcher) {
            trade.excursionMetrics = calculateExcursionMetrics(trade, trade.entryQuote)
            trade.marketConditionAtEntry =
              captureMarketConditionIndexed(trade.entryQuote.date, spyQuotesByDate, breadthByDate)
          }
        }.awaitAll()
    }

    logger.info("Calculating aggregate diagnostic statistics...")

    val (winningTrades, losingTrades) = trades.partition { it.profit > 0 }

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

    val edgeConsistency = calculateEdgeConsistency(timeStats.byYear)

    val marketAvgs =
      trades
        .mapNotNull { it.marketConditionAtEntry }
        .let { conditions ->
          if (conditions.isEmpty()) {
            null
          } else {
            mapOf(
              "avgMarketBreadth" to conditions.mapNotNull { it.marketBreadthBullPercent }.average(),
              "spyUptrendPercent" to (conditions.count { it.spyInUptrend }.toDouble() / conditions.size) * 100,
            )
          }
        }

    val totalDuration = System.currentTimeMillis() - backtestStartTime
    logger.info("Backtest analysis complete in ${totalDuration}ms total")

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
      edgeConsistencyScore = edgeConsistency,
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
    quoteIndexes: Map<Stock, StockQuoteIndex>,
    allTradingDates: List<LocalDate>,
    context: BacktestContext,
    entryDelayDays: Int,
    logger: org.slf4j.Logger,
  ): Pair<List<Trade>, List<Trade>> {
    val parallelism = Runtime.getRuntime().availableProcessors().coerceAtMost(5)
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
              val result = processStockTrades(
                stockPair,
                entryStrategy,
                exitStrategy,
                quoteIndexes,
                allTradingDates,
                context,
                entryDelayDays,
              )
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
    quoteIndexes: Map<Stock, StockQuoteIndex>,
    allTradingDates: List<LocalDate>,
    context: BacktestContext,
    entryDelayDays: Int = 0,
  ): List<Trade> {
    val trades = mutableListOf<Trade>()
    val usedTradeQuotes = Collections.newSetFromMap<StockQuote>(IdentityHashMap())

    for ((index, currentDate) in allTradingDates.withIndex()) {
      // Skip if stock not yet listed or already delisted (survivorship bias filter)
      if (stockPair.tradingStock.listingDate?.isAfter(currentDate) == true) continue
      if (stockPair.tradingStock.delistingDate?.isBefore(currentDate) == true) continue

      val strategyQuote = quoteIndexes[stockPair.strategyStock]?.getQuote(currentDate) ?: continue
      val tradingQuote = quoteIndexes[stockPair.tradingStock]?.getQuote(currentDate) ?: continue

      if (!entryStrategy.test(stockPair.strategyStock, strategyQuote, context)) continue

      // Resolve entry quotes: use delayed date if entryDelayDays > 0
      val (entryStrategyQuote, entryTradingQuote) =
        if (entryDelayDays > 0) {
          val delayedIndex = index + entryDelayDays
          if (delayedIndex >= allTradingDates.size) continue
          val delayedDate = allTradingDates[delayedIndex]
          val delayedStrategy = quoteIndexes[stockPair.strategyStock]?.getQuote(delayedDate) ?: continue
          val delayedTrading = quoteIndexes[stockPair.tradingStock]?.getQuote(delayedDate) ?: continue
          Pair(delayedStrategy, delayedTrading)
        } else {
          Pair(strategyQuote, tradingQuote)
        }

      if (entryTradingQuote in usedTradeQuotes) continue

      val entry = PotentialEntry(stockPair, entryStrategyQuote, entryTradingQuote)
      val trade = createTradeFromEntry(entry, exitStrategy, quoteIndexes, context) ?: continue

      trades.add(trade)
      usedTradeQuotes.addAll(trade.quotes)
    }

    return trades
  }

  /**
   * Holds the result of selecting entries for a single date, splitting into
   * entries that will be traded and entries that were skipped.
   */
  private data class SelectionResult(
    val selected: List<RankedEntry>,
    val notSelected: List<RankedEntry>,
    val reservedSharesMap: Map<RankedEntry, Int>,
    val capitalSkips: Int,
    val contextMap: Map<RankedEntry, EntryDecisionContext>,
  )

  private fun backtestSequential(
    entryStrategy: EntryStrategy,
    exitStrategy: ExitStrategy,
    stockPairs: List<StockPair>,
    quoteIndexes: Map<Stock, StockQuoteIndex>,
    allTradingDates: List<LocalDate>,
    maxPositions: Int?,
    effectiveMaxPositions: Int,
    ranker: StockRanker,
    cooldownDays: Int,
    entryDelayDays: Int,
    context: BacktestContext,
    logger: org.slf4j.Logger,
    tieBreakRandom: kotlin.random.Random = kotlin.random.Random(System.nanoTime()),
    positionSizingConfig: PositionSizingConfig? = null,
  ): Pair<List<Trade>, List<Trade>> {
    val trades = mutableListOf<Trade>()
    val missedTrades = mutableListOf<Trade>()
    val usedTradeQuotes = Collections.newSetFromMap<StockQuote>(IdentityHashMap())
    val usedMissedQuotes = Collections.newSetFromMap<StockQuote>(IdentityHashMap())
    var lastExitDate: LocalDate? = null

    // O(1) open position tracking instead of scanning all trades each date
    var openPositionCount = 0
    val scheduledExits = TreeMap<LocalDate, MutableList<Trade>>()
    val budget = positionSizingConfig?.let { SelectionBudget(it) }
    var capitalSkips = 0

    logger.info(
      "Sequential processing: ${stockPairs.size} stocks evaluated per date" +
        if (budget != null) " (capital-aware)" else "",
    )

    val tradingDateIndex = allTradingDates.withIndex().associate { (i, date) -> date to i }
    val totalDays = allTradingDates.size
    var lastLoggedPercent = 0

    allTradingDates.forEachIndexed { index, currentDate ->
      // Drain positions that exited before today
      while (scheduledExits.isNotEmpty() && scheduledExits.firstKey() < currentDate) {
        val exiting = scheduledExits.pollFirstEntry().value
        openPositionCount -= exiting.size
        if (budget != null) exiting.forEach { budget.releasePosition(it) }
      }

      // Log progress at 5% intervals
      val currentPercent = ((index + 1) * 100) / totalDays
      if (currentPercent >= lastLoggedPercent + 5 && currentPercent < 100) {
        logger.info("Backtest progress: $currentPercent% (${index + 1}/$totalDays days, ${trades.size} trades)")
        lastLoggedPercent = currentPercent
      }

      val entriesForThisDate = findEntriesForDate(
        stockPairs,
        quoteIndexes,
        currentDate,
        lastExitDate,
        tradingDateIndex,
        cooldownDays,
        entryStrategy,
        context,
      )

      if (entriesForThisDate.isNotEmpty()) {
        val resolvedEntries = resolveDelayedEntries(
          entriesForThisDate,
          index,
          entryDelayDays,
          allTradingDates,
          quoteIndexes,
        )

        val rankedEntries =
          resolvedEntries
            .map { entry ->
              val score = ranker.score(entry.stockPair.strategyStock, entry.strategyEntryQuote, context)
              val jitteredScore = score + tieBreakRandom.nextDouble() * StockRanker.TIE_BREAK_JITTER
              RankedEntry(entry, jitteredScore)
            }.sortedByDescending { it.score }

        val availableSlots = (effectiveMaxPositions - openPositionCount).coerceAtLeast(0)

        val selection = selectEntries(rankedEntries, availableSlots, maxPositions, budget, openPositionCount)
        capitalSkips += selection.capitalSkips

        selection.selected.forEach { rankedEntry ->
          val result = processSelectedEntry(
            rankedEntry,
            selection.reservedSharesMap,
            usedTradeQuotes,
            exitStrategy,
            quoteIndexes,
            context,
            budget,
          )
          if (result != null) {
            result.trade.entryContext = selection.contextMap[rankedEntry]
            trades.add(result.trade)
            usedTradeQuotes.addAll(result.trade.quotes)
            if (result.exitDate != null) {
              openPositionCount++
              scheduledExits.getOrPut(result.exitDate) { mutableListOf() }.add(result.trade)
            }
            if (cooldownDays > 0) lastExitDate = result.exitDate
          }
        }

        processMissedEntries(
          selection.notSelected,
          usedMissedQuotes,
          usedTradeQuotes,
          exitStrategy,
          quoteIndexes,
          context,
          budget,
          selection.contextMap,
          missedTrades,
        )
      }
    }

    if (budget != null) {
      logger.info(
        "Capital-aware selection: $capitalSkips capital-skipped, " +
          "${trades.size} selected, ${budget.summary()}",
      )
    }

    return Pair(trades, missedTrades)
  }

  /** Scans stock pairs for entry signals on a given date, respecting cooldown. */
  private fun findEntriesForDate(
    stockPairs: List<StockPair>,
    quoteIndexes: Map<Stock, StockQuoteIndex>,
    currentDate: LocalDate,
    lastExitDate: LocalDate?,
    tradingDateIndex: Map<LocalDate, Int>,
    cooldownDays: Int,
    entryStrategy: EntryStrategy,
    context: BacktestContext,
  ): List<PotentialEntry> =
    stockPairs.mapNotNull { stockPair ->
      val strategyQuote = quoteIndexes[stockPair.strategyStock]?.getQuote(currentDate) ?: return@mapNotNull null
      val tradingQuote = quoteIndexes[stockPair.tradingStock]?.getQuote(currentDate) ?: return@mapNotNull null

      if (isInCooldown(currentDate, lastExitDate, tradingDateIndex, cooldownDays)) return@mapNotNull null
      if (!entryStrategy.test(stockPair.strategyStock, strategyQuote, context)) return@mapNotNull null

      PotentialEntry(stockPair, strategyQuote, tradingQuote)
    }

  /** Splits ranked entries into selected (will trade) and not-selected (missed), respecting budget. */
  private fun selectEntries(
    rankedEntries: List<RankedEntry>,
    availableSlots: Int,
    maxPositions: Int?,
    budget: SelectionBudget?,
    openPositionCount: Int,
  ): SelectionResult {
    if (budget == null) {
      return SelectionResult(
        selected = rankedEntries.take(availableSlots),
        notSelected = if (maxPositions != null) rankedEntries.drop(availableSlots) else emptyList(),
        reservedSharesMap = emptyMap(),
        capitalSkips = 0,
        contextMap = emptyMap(),
      )
    }

    val selected = mutableListOf<RankedEntry>()
    val notSelected = mutableListOf<RankedEntry>()
    val reservedSharesMap = mutableMapOf<RankedEntry, Int>()
    val contextMap = mutableMapOf<RankedEntry, EntryDecisionContext>()
    val cohortSize = rankedEntries.size
    var slotsRemaining = availableSlots
    var skips = 0

    rankedEntries.forEachIndexed { idx, rankedEntry ->
      val cashBefore = budget.cash()
      val notionalBefore = budget.openNotional()
      val rank = idx + 1
      val slotsBefore = slotsRemaining

      val shares: Int
      if (slotsRemaining <= 0) {
        if (maxPositions != null) notSelected.add(rankedEntry)
        shares = 0
      } else {
        val reservedShares = budget.tryReserve(rankedEntry.entry)
        if (reservedShares <= 0) {
          notSelected.add(rankedEntry)
          skips++
          shares = 0
        } else {
          selected.add(rankedEntry)
          reservedSharesMap[rankedEntry] = reservedShares
          slotsRemaining--
          shares = reservedShares
        }
      }

      contextMap[rankedEntry] = EntryDecisionContext(
        cashAtDecision = cashBefore,
        openNotionalAtDecision = notionalBefore,
        openPositionCount = openPositionCount,
        cohortSize = cohortSize,
        rankInCohort = rank,
        availableSlots = slotsBefore,
        sharesReserved = shares,
      )
    }

    return SelectionResult(selected, notSelected, reservedSharesMap, skips, contextMap)
  }

  /** Result of processing a single selected entry into a trade. */
  private data class SelectedTradeResult(
    val trade: Trade,
    val exitDate: LocalDate?,
  )

  /** Processes a single selected entry: creates the trade and manages budget reservations. */
  private fun processSelectedEntry(
    rankedEntry: RankedEntry,
    reservedSharesMap: Map<RankedEntry, Int>,
    usedTradeQuotes: MutableSet<StockQuote>,
    exitStrategy: ExitStrategy,
    quoteIndexes: Map<Stock, StockQuoteIndex>,
    context: BacktestContext,
    budget: SelectionBudget?,
  ): SelectedTradeResult? {
    val entry = rankedEntry.entry
    val reserved = reservedSharesMap[rankedEntry] ?: 0

    if (entry.tradingEntryQuote in usedTradeQuotes) {
      if (budget != null && reserved > 0) budget.cancelReservation(reserved, entry.tradingEntryQuote.closePrice)
      return null
    }

    val trade = createTradeFromEntry(entry, exitStrategy, quoteIndexes, context)
    if (trade == null) {
      if (budget != null && reserved > 0) budget.cancelReservation(reserved, entry.tradingEntryQuote.closePrice)
      return null
    }

    val exitDate = trade.quotes.lastOrNull()?.date
    if (exitDate != null) {
      budget?.confirmEntry(trade, reserved, entry.tradingEntryQuote.closePrice)
    }

    return SelectedTradeResult(trade, exitDate)
  }

  /** Processes not-selected entries as missed trades, tagging capital-skipped ones. */
  private fun processMissedEntries(
    notSelectedEntries: List<RankedEntry>,
    usedMissedQuotes: MutableSet<StockQuote>,
    usedTradeQuotes: Set<StockQuote>,
    exitStrategy: ExitStrategy,
    quoteIndexes: Map<Stock, StockQuoteIndex>,
    context: BacktestContext,
    budget: SelectionBudget?,
    contextMap: Map<RankedEntry, EntryDecisionContext>,
    missedTrades: MutableList<Trade>,
  ) {
    notSelectedEntries.forEach { rankedEntry ->
      val entry = rankedEntry.entry

      if (entry.tradingEntryQuote !in usedMissedQuotes && entry.tradingEntryQuote !in usedTradeQuotes) {
        val trade = createTradeFromEntry(entry, exitStrategy, quoteIndexes, context) ?: return@forEach
        val ctx = contextMap[rankedEntry]
        if (budget != null && ctx?.isCapitalSkip() == true) {
          trade.missedReason = Trade.MISSED_INSUFFICIENT_CAPITAL
        }
        trade.entryContext = ctx
        missedTrades.add(trade)
        usedMissedQuotes.addAll(trade.quotes)
      }
    }
  }

  /**
   * Resolves delayed entry quotes for entries that fired on a given date.
   * If entryDelayDays > 0, remaps each entry to use quotes from the delayed trading date.
   * Entries where the delayed date has no data are dropped.
   */
  private fun resolveDelayedEntries(
    entries: List<PotentialEntry>,
    dateIndex: Int,
    entryDelayDays: Int,
    allTradingDates: List<LocalDate>,
    quoteIndexes: Map<Stock, StockQuoteIndex>,
  ): List<PotentialEntry> {
    if (entryDelayDays <= 0) return entries

    val delayedIndex = dateIndex + entryDelayDays
    if (delayedIndex >= allTradingDates.size) return emptyList()

    val delayedDate = allTradingDates[delayedIndex]
    return entries.mapNotNull { entry ->
      val delayedStrategy = quoteIndexes[entry.stockPair.strategyStock]?.getQuote(delayedDate)
      val delayedTrading = quoteIndexes[entry.stockPair.tradingStock]?.getQuote(delayedDate)
      if (delayedStrategy != null && delayedTrading != null) {
        PotentialEntry(entry.stockPair, delayedStrategy, delayedTrading)
      } else {
        null
      }
    }
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
    return com.skrymer.udgaard.data.util.AssetMapper
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
    stocks: List<Stock>,
    useUnderlying: Boolean,
    customMap: Map<String, String>?,
    allStocksMap: Map<String, Stock>,
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

  /**
   * Calculate Maximum Favorable Excursion (MFE) and Maximum Adverse Excursion (MAE)
   * for a trade, both in percentage and ATR units.
   */
  private fun calculateExcursionMetrics(
    trade: Trade,
    entryQuote: StockQuote,
  ): ExcursionMetrics {
    val entryPrice = entryQuote.closePrice
    val entryATR = if (entryQuote.atr > 0.0) entryQuote.atr else 1.0

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
   * SPY uptrend uses EMA-based trend, not Ovtlyr's spyInUptrend field.
   * Market breadth is calculated from all stocks in the DB (% in uptrend per date).
   */
  private fun captureMarketConditionIndexed(
    date: LocalDate,
    spyQuotesByDate: Map<LocalDate, StockQuote>?,
    breadthByDate: Map<LocalDate, Double>?,
  ): MarketConditionSnapshot? {
    val spyQuote = spyQuotesByDate?.get(date) ?: return null

    return MarketConditionSnapshot(
      spyClose = spyQuote.closePrice,
      spyInUptrend = spyQuote.isInUptrend(),
      marketBreadthBullPercent = breadthByDate?.get(date),
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
    val winners = trades.filter { it.profit > 0 }
    val losers = trades.filter { it.profit <= 0 }
    val exitReasons = trades.groupingBy { it.exitReason }.eachCount()

    val winRate = if (trades.isNotEmpty()) winners.size.toDouble() / trades.size else 0.0
    val avgWinPercent = if (winners.isNotEmpty()) winners.map { it.profitPercentage }.average() else 0.0
    val avgLossPercent = if (losers.isNotEmpty()) kotlin.math.abs(losers.map { it.profitPercentage }.average()) else 0.0
    val edge = (avgWinPercent * winRate) - ((1.0 - winRate) * avgLossPercent)

    return PeriodStats(
      trades = trades.size,
      winRate = if (trades.isNotEmpty()) winRate * 100 else 0.0,
      avgProfit = if (trades.isNotEmpty()) trades.map { it.profitPercentage }.average() else 0.0,
      avgHoldingDays = if (trades.isNotEmpty()) trades.map { it.tradingDays }.average() else 0.0,
      exitReasons = exitReasons,
      edge = edge,
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

  /**
   * Capital gate for trade selection. Tracks estimated cash + open notional per bar to answer
   * "can this candidate be funded?" without duplicating the M2M/equity-curve logic owned by
   * [PositionSizingService]. Sizing is delegated to the configured [PositionSizer].
   */
  private class SelectionBudget(
    private val config: PositionSizingConfig,
  ) {
    private var estimatedCash: Double = config.startingCapital
    private var estimatedOpenNotional: Double = 0.0
    private var peakCash: Double = config.startingCapital
    private val openPositions = mutableMapOf<Trade, BudgetPosition>()

    private fun buildContext(entry: PotentialEntry): SizingContext {
      val quote = entry.tradingEntryQuote
      val stock = entry.stockPair.tradingStock
      return SizingContext(
        portfolioValue = estimatedCash,
        entryPrice = quote.closePrice,
        atr = quote.atr,
        symbol = stock.symbol,
        sector = stock.sectorSymbol.orEmpty(),
      )
    }

    private fun estimateShares(ctx: SizingContext): Int {
      val pv = ctx.portfolioValue
      if (pv <= 0.0) return 0
      val sizer = PositionSizingService.scaledSizer(config, peakCash, pv)
      val shares = sizer.calculateShares(ctx)
      if (shares <= 0) return 0
      return applyLeverageCap(shares, ctx.entryPrice, pv, estimatedOpenNotional, config.leverageRatio)
    }

    /**
     * Combined fundability check + reservation. Returns shares > 0 if funded, 0 if not.
     * Merged so separate canFund/reserve calls cannot drift — the budget mutates on success only.
     */
    fun tryReserve(entry: PotentialEntry): Int {
      val shares = estimateShares(buildContext(entry))
      if (shares <= 0) return 0
      estimatedOpenNotional += shares * entry.tradingEntryQuote.closePrice
      return shares
    }

    fun cash(): Double = estimatedCash

    fun openNotional(): Double = estimatedOpenNotional

    fun confirmEntry(trade: Trade, shares: Int, entryPrice: Double) {
      if (shares <= 0) return
      openPositions[trade] = BudgetPosition(shares, entryPrice)
    }

    fun cancelReservation(shares: Int, entryPrice: Double) {
      if (shares <= 0) return
      estimatedOpenNotional = (estimatedOpenNotional - shares * entryPrice).coerceAtLeast(0.0)
    }

    fun releasePosition(trade: Trade) {
      val pos = openPositions.remove(trade) ?: return
      estimatedOpenNotional = (estimatedOpenNotional - pos.shares * pos.entryPrice).coerceAtLeast(0.0)
      estimatedCash += pos.shares * trade.profit
      if (estimatedCash > peakCash) peakCash = estimatedCash
    }

    fun summary(): String = "estimatedCash=${String.format("%.2f", estimatedCash)}"

    private data class BudgetPosition(
      val shares: Int,
      val entryPrice: Double
    )
  }

  companion object {
    private const val BATCH_SIZE = 150
  }
}
