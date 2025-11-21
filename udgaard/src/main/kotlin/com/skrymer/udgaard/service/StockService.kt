package com.skrymer.udgaard.service

import com.skrymer.udgaard.integration.ovtlyr.OvtlyrClient
import com.skrymer.udgaard.integration.ovtlyr.dto.OvtlyrStockInformation
import com.skrymer.udgaard.model.BacktestReport
import com.skrymer.udgaard.model.MarketBreadth
import com.skrymer.udgaard.model.MarketSymbol
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.Trade
import com.skrymer.udgaard.model.strategy.EntryStrategy
import com.skrymer.udgaard.model.strategy.ExitStrategy
import com.skrymer.udgaard.model.strategy.StockRanker
import com.skrymer.udgaard.model.strategy.HeatmapRanker
import com.skrymer.udgaard.model.valueOf
import com.skrymer.udgaard.repository.MarketBreadthRepository
import com.skrymer.udgaard.repository.StockRepository
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope


@Service
open class StockService(
  val stockRepository: StockRepository,
  val ovtlyrClient: OvtlyrClient,
  val marketBreadthRepository: MarketBreadthRepository,
  val orderBlockCalculator: OrderBlockCalculator,
  val alphaVantageClient: com.skrymer.udgaard.integration.alphavantage.AlphaVantageClient
) {

  /**
   * Loads the stock from DB if exists, else load it from Ovtlyr and save it.
   * @param symbol - the [symbol] of the stock to get
   * @param forceFetch - force fetch the stock from the ovtlyr API
   */
  @Cacheable(value = ["stocks"], key = "#symbol", unless = "#forceFetch")
  @org.springframework.cache.annotation.CacheEvict(value = ["stocks"], key = "#symbol", condition = "#forceFetch")
  open fun getStock(symbol: String, forceFetch: Boolean = false): Stock? {
    if(forceFetch){
      val spy: OvtlyrStockInformation? = ovtlyrClient.getStockInformation("SPY")
      checkNotNull(spy)
      return fetchStock(symbol, spy)
    }

    return stockRepository.findById(symbol).orElseGet {
      val spy: OvtlyrStockInformation? = ovtlyrClient.getStockInformation("SPY")
      checkNotNull(spy)
      fetchStock(symbol, spy)
    }
  }

  /**
   * Loads the stocks by symbol from DB if exists, else load it from Ovtlyr and save it.
   *
   * @param symbols - the stocks to load
   * @param forceFetch - force fetching stocks from ovtlyr api
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  suspend fun getStocks(symbols: List<String>, forceFetch: Boolean = false): List<Stock> = supervisorScope {
    val logger = LoggerFactory.getLogger("StockFetcher")
    val limited = Dispatchers.IO.limitedParallelism(10)

    val spy: OvtlyrStockInformation? = ovtlyrClient.getStockInformation("SPY")
    checkNotNull(spy)

    symbols.map { symbol ->
      async(limited) {
        runCatching {
          if (forceFetch) {
            fetchStock(symbol, spy)
          } else {
            stockRepository.findById(symbol).orElseGet { fetchStock(symbol, spy) }
          }
        }.onFailure { e ->
          logger.warn("Failed to fetch symbol={}: {}", symbol, e.message, e)
        }.getOrNull()
      }
    }
    .awaitAll()
    .filterNotNull()
  }

  /**
   * @return all stocks currently stored in DB
   */
  @Cacheable(value = ["stocks"], key = "'allStocks'")
  open fun getAllStocks(): List<Stock>{
    return stockRepository.findAll()
  }

  /**
   * Get stocks by a list of symbols (efficient repository query)
   * Returns only stocks that exist in the database
   *
   * @param symbols - list of stock symbols to fetch
   * @param forceFetch - force fetching stocks from ovtlyr api (bypasses cache)
   * @return list of stocks matching the provided symbols (only those that exist in DB)
   */
  @Cacheable(value = ["stocks"], key = "'bySymbols:' + #symbols.toString()", unless = "#forceFetch")
  @OptIn(ExperimentalCoroutinesApi::class)
  open fun getStocksBySymbols(symbols: List<String>, forceFetch: Boolean = false): List<Stock> = runBlocking {
    // Sort symbols to ensure consistent cache keys (since symbols may come from a Set with no guaranteed order)
    val sortedSymbols = symbols.sorted()

    val logger = LoggerFactory.getLogger("StockFetcher")
    val limited = Dispatchers.IO.limitedParallelism(10)

    if (forceFetch) {
      // Force fetch all symbols from API
      val spy: OvtlyrStockInformation? = ovtlyrClient.getStockInformation("SPY")
      checkNotNull(spy) { "Failed to fetch SPY reference data" }

      return@runBlocking sortedSymbols.map { symbol ->
        async(limited) {
          runCatching {
            fetchStock(symbol, spy)
          }.onFailure { e ->
            logger.warn("Failed to force fetch symbol={}: {}", symbol, e.message, e)
          }.getOrNull()
        }
      }
      .awaitAll()
      .filterNotNull()
    }

    // Get existing stocks from repository
    val existingStocks = stockRepository.findBySymbolIn(sortedSymbols)

    return@runBlocking existingStocks
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
    customMap: Map<String, String>?
  ): String {
    if (!useUnderlying) return tradingSymbol

    // Check custom map first
    customMap?.get(tradingSymbol)?.let { return it }

    // Fall back to AssetMapper
    return com.skrymer.udgaard.util.AssetMapper.getUnderlyingSymbol(tradingSymbol)
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
    customMap: Map<String, String>?
  ) {
    if (!useUnderlying) return

    val logger = LoggerFactory.getLogger("Backtest")
    val missingSymbols = mutableListOf<String>()

    stocks.forEach { stock ->
      val strategySymbol = getStrategySymbol(stock.symbol!!, useUnderlying, customMap)

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
        "Please load this data before running the backtest."
      )
    }
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
   * @param cooldownDays - global cooldown period in trading days after any exit before allowing new entries (default: 0)
   * @return a backtest report
   */
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
    cooldownDays: Int = 0
  ): BacktestReport {
    val logger = LoggerFactory.getLogger("Backtest")
    val effectiveMaxPositions = maxPositions ?: Int.MAX_VALUE

    // Validate that all underlying assets exist in the database
    validateUnderlyingAssets(stocks, useUnderlyingAssets, customUnderlyingMap)

    // Track most recent exit date for global cooldown enforcement
    var lastExitDate: LocalDate? = null

    // Create stock pairs: trading stock + strategy stock (might be the same)
    data class StockPair(
      val tradingStock: Stock,
      val strategyStock: Stock,
      val underlyingSymbol: String?
    )

    val stockPairs = stocks.map { tradingStock ->
      val strategySymbol = getStrategySymbol(tradingStock.symbol!!, useUnderlyingAssets, customUnderlyingMap)

      val (strategyStock, underlying) = if (strategySymbol != tradingStock.symbol) {
        val underlyingStock = stockRepository.findByIdOrNull(strategySymbol)
          ?: throw IllegalStateException("Underlying asset $strategySymbol not found")
        Pair(underlyingStock, strategySymbol)
      } else {
        Pair(tradingStock, null)
      }

      StockPair(tradingStock, strategyStock, underlying)
    }

    val trades = ArrayList<Trade>()
    val missedTrades = ArrayList<Trade>()

    // Step 1: Build date range - collect all unique trading dates from all stocks
    val allTradingDates = stocks.flatMap { stock ->
      stock.quotes
        .mapNotNull { quote ->
          val quoteDate = quote.date
          if (quoteDate != null && !quoteDate.isBefore(after) && !quoteDate.isAfter(before)) {
            quoteDate
          } else {
            null
          }
        }
    }.distinct().sorted()

    val cooldownInfo = if (cooldownDays > 0) ", cooldown: $cooldownDays trading days" else ""
    if (maxPositions != null) {
      logger.info("Backtest with position limit: ${allTradingDates.size} trading days, max $maxPositions positions per day, using ${ranker.description()}$cooldownInfo")
    } else {
      logger.info("Backtest: ${allTradingDates.size} trading days, unlimited positions per day$cooldownInfo")
    }

    // Step 2: Process each date chronologically
    allTradingDates.forEach { currentDate ->
      data class PotentialEntry(
        val stockPair: StockPair,
        val strategyEntryQuote: com.skrymer.udgaard.model.StockQuote,
        val tradingEntryQuote: com.skrymer.udgaard.model.StockQuote
      )

      // Step 2a: For each stock pair, check if entry strategy matches on this date
      val entriesForThisDate = stockPairs.mapNotNull { stockPair ->
        // Get quote for this specific date from STRATEGY stock (for entry evaluation)
        val strategyQuote = stockPair.strategyStock.quotes.find { it.date == currentDate }

        // Also get quote from TRADING stock (for actual price/P&L)
        val tradingQuote = stockPair.tradingStock.quotes.find { it.date == currentDate }

        if (strategyQuote != null && tradingQuote != null) {
          // Check global cooldown first if enabled (counting trading days, not calendar days)
          val isInCooldown = if (cooldownDays > 0 && lastExitDate != null) {
            val exitDateIndex = allTradingDates.indexOf(lastExitDate)
            val currentDateIndex = allTradingDates.indexOf(currentDate)

            if (exitDateIndex >= 0 && currentDateIndex >= 0) {
              val tradingDaysSinceExit = currentDateIndex - exitDateIndex
              tradingDaysSinceExit < cooldownDays
            } else {
              false
            }
          } else {
            false
          }

          if (isInCooldown) {
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
        logger.debug("$currentDate: ${entriesForThisDate.size} potential entries")

        // Step 2b: Rank entries by score (using strategy stock for ranking)
        val rankedEntries = entriesForThisDate
          .map { entry ->
            val score = ranker.score(entry.stockPair.strategyStock, entry.strategyEntryQuote)
            entry to score
          }
          .sortedByDescending { it.second } // Higher score = better

        // Step 2c: Take top N based on position limit (or all if unlimited)
        val selectedEntries = rankedEntries.take(effectiveMaxPositions)
        val notSelectedEntries = if (maxPositions != null) rankedEntries.drop(effectiveMaxPositions) else emptyList()

        if (maxPositions != null) {
          logger.debug("$currentDate: Selected ${selectedEntries.size} stocks (scores: ${selectedEntries.map { it.second.format(2) }})")
          if (notSelectedEntries.isNotEmpty()) {
            logger.debug("$currentDate: Missed ${notSelectedEntries.size} opportunities due to position limit")
          }
        }

        // Step 2d: Create trades for selected stocks
        selectedEntries.forEach { (entry, score) ->
          // Check if we're not already in this trade (using trading quote)
          if (trades.find { it.containsQuote(entry.tradingEntryQuote) } == null) {
            // Test exit strategy on STRATEGY stock
            val exitReport = entry.stockPair.strategyStock.testExitStrategy(entry.strategyEntryQuote, exitStrategy)

            if (exitReport.exitReason.isNotBlank()) {
              // Get the exit quote from the strategy stock's exit report
              val strategyExitQuote = exitReport.quotes.lastOrNull()

              if (strategyExitQuote?.date != null) {
                // Get corresponding exit date quote from TRADING stock for P/L
                val exitDate = strategyExitQuote.date!!
                val tradingExitQuote = entry.stockPair.tradingStock.getQuoteByDate(exitDate)

                if (tradingExitQuote != null) {
                  val profit = tradingExitQuote.closePrice - entry.tradingEntryQuote.closePrice

                  // Get all trading quotes between entry and exit
                  val entryDate = entry.tradingEntryQuote.date!!
                  val tradingQuotes = entry.stockPair.tradingStock.quotes.filter { quote ->
                    val qDate = quote.date
                    qDate != null && !qDate.isBefore(entryDate) && !qDate.isAfter(exitDate)
                  }

                val trade = Trade(
                  entry.stockPair.tradingStock.symbol!!,
                  entry.stockPair.underlyingSymbol,  // Store which underlying was used
                  entry.tradingEntryQuote,  // Use trading stock prices
                  tradingQuotes,
                  exitReport.exitReason,
                  profit,
                  entry.tradingEntryQuote.date,
                  entry.stockPair.tradingStock.sectorSymbol ?: ""
                )
                trades.add(trade)

                  // Record exit date for global cooldown tracking
                  if (cooldownDays > 0) {
                    lastExitDate = exitDate
                  }

                  if (maxPositions != null) {
                    val symbolInfo = if (entry.stockPair.underlyingSymbol != null) {
                      "${entry.stockPair.tradingStock.symbol} (${entry.stockPair.underlyingSymbol} signals)"
                    } else {
                      entry.stockPair.tradingStock.symbol!!
                    }
                    logger.debug("  ✓ $symbolInfo (score: ${score.format(2)}, profit: ${trade.profitPercentage.format(2)}%)")
                  }
                }
              }
            }
          }
        }

        // Step 2e: Track missed trades (not selected due to position limit)
        notSelectedEntries.forEach { (entry, score) ->
          // Check if we're not already tracking this trade
          if (missedTrades.find { it.containsQuote(entry.tradingEntryQuote) } == null &&
              trades.find { it.containsQuote(entry.tradingEntryQuote) } == null) {
            val exitReport = entry.stockPair.strategyStock.testExitStrategy(entry.strategyEntryQuote, exitStrategy)

            if (exitReport.exitReason.isNotBlank()) {
              // Get the exit quote from the strategy stock's exit report
              val strategyExitQuote = exitReport.quotes.lastOrNull()

              if (strategyExitQuote?.date != null) {
                // Get corresponding exit date quote from TRADING stock for P/L
                val exitDate = strategyExitQuote.date!!
                val tradingExitQuote = entry.stockPair.tradingStock.getQuoteByDate(exitDate)

                if (tradingExitQuote != null) {
                  val profit = tradingExitQuote.closePrice - entry.tradingEntryQuote.closePrice

                  val entryDate = entry.tradingEntryQuote.date!!
                  val tradingQuotes = entry.stockPair.tradingStock.quotes.filter { quote ->
                    val qDate = quote.date
                    qDate != null && !qDate.isBefore(entryDate) && !qDate.isAfter(exitDate)
                  }

                  val trade = Trade(
                    entry.stockPair.tradingStock.symbol!!,
                    entry.stockPair.underlyingSymbol,
                    entry.tradingEntryQuote,
                    tradingQuotes,
                    exitReport.exitReason,
                    profit,
                    entry.tradingEntryQuote.date,
                    entry.stockPair.tradingStock.sectorSymbol ?: ""
                  )
                  missedTrades.add(trade)

                  if (maxPositions != null) {
                    val symbolInfo = if (entry.stockPair.underlyingSymbol != null) {
                      "${entry.stockPair.tradingStock.symbol} (${entry.stockPair.underlyingSymbol} signals)"
                    } else {
                      entry.stockPair.tradingStock.symbol!!
                    }
                    logger.debug("  ✗ $symbolInfo (score: ${score.format(2)}, missed profit: ${trade.profitPercentage.format(2)}%)")
                  }
                }
              }
            }
          }
        }
      }
    }

    logger.info("Backtest complete: ${trades.size} trades executed, ${missedTrades.size} opportunities missed due to position limits")

    val (winningTrades, losingTrades) = trades.partition { it.profit > 0 }
    return BacktestReport(winningTrades, losingTrades, missedTrades)
  }

  // Helper extension for formatting doubles
  private fun Double.format(decimals: Int) = "%.${decimals}f".format(this)

  /**
   * Fetches stock data from Ovtlyr API and saves it to the database.
   *
   * @param symbol - the stock symbol to fetch
   * @param spy - SPY reference data for enriching stock information
   * @return the fetched and saved stock, or null if fetch or save failed
   */
  private fun fetchStock(symbol: String, spy: OvtlyrStockInformation): Stock? {
    val logger = LoggerFactory.getLogger("StockService")
    val stockInformation = ovtlyrClient.getStockInformation(symbol) ?: return null

    return runCatching {
      val marketBreadth = marketBreadthRepository.findByIdOrNull(MarketSymbol.FULLSTOCK)
      val marketSymbol = MarketSymbol.valueOf(stockInformation.sectorSymbol)
      val sectorMarketBreadth = marketBreadthRepository.findByIdOrNull(marketSymbol)
      val stock = stockInformation.toModel(marketBreadth, sectorMarketBreadth, spy)

      // Enrich quotes with volume data from Alpha Vantage
      logger.info("Starting volume enrichment for $symbol (${stock.quotes.size} quotes to enrich)")
      val alphaQuotes = alphaVantageClient.getDailyTimeSeriesCompact(symbol)
      if (alphaQuotes != null) {
        logger.info("Enriching $symbol with volume data from Alpha Vantage (${alphaQuotes.size} Alpha quotes available)")
        logger.debug("Ovtlyr date range: ${stock.quotes.firstOrNull()?.date} to ${stock.quotes.lastOrNull()?.date}")
        logger.debug("Alpha Vantage date range: ${alphaQuotes.firstOrNull()?.date} to ${alphaQuotes.lastOrNull()?.date}")

        var matchedCount = 0
        var unmatchedCount = 0
        stock.quotes.forEach { quote ->
          val matchingAlphaQuote = alphaQuotes.find { it.date == quote.date }
          if (matchingAlphaQuote != null) {
            quote.volume = matchingAlphaQuote.volume
            matchedCount++
          } else {
            unmatchedCount++
          }
        }
        logger.info("Volume enrichment complete for $symbol: $matchedCount quotes matched, $unmatchedCount unmatched")

        // Log sample of enriched quotes
        val quotesWithVolume = stock.quotes.filter { it.volume > 0 }
        logger.info("Quotes with volume > 0: ${quotesWithVolume.size} out of ${stock.quotes.size}")
      } else {
        logger.warn("Could not fetch volume data from Alpha Vantage for $symbol, using default values")
      }

      // Calculate order blocks using ROC algorithm and add them to the stock
      val calculatedOrderBlocks = orderBlockCalculator.calculateOrderBlocks(
        quotes = stock.quotes,
        sensitivity = 28.0
      )

      // Combine Ovtlyr order blocks with calculated ones
      val allOrderBlocks = stock.orderBlocks + calculatedOrderBlocks
      val enrichedStock = Stock(
        symbol = stock.symbol,
        sectorSymbol = stock.sectorSymbol,
        quotes = stock.quotes,
        orderBlocks = allOrderBlocks,
        ovtlyrPerformance = stock.ovtlyrPerformance
      )

      return stockRepository.save(enrichedStock)
    }.getOrNull()
  }
}
