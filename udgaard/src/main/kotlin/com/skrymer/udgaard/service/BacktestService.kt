package com.skrymer.udgaard.service

import com.skrymer.udgaard.model.BacktestReport
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.Trade
import com.skrymer.udgaard.model.backtest.PotentialEntry
import com.skrymer.udgaard.model.backtest.RankedEntry
import com.skrymer.udgaard.model.backtest.StockPair
import com.skrymer.udgaard.model.strategy.EntryStrategy
import com.skrymer.udgaard.model.strategy.ExitStrategy
import com.skrymer.udgaard.model.strategy.HeatmapRanker
import com.skrymer.udgaard.model.strategy.StockRanker
import com.skrymer.udgaard.repository.StockRepository
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
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
    private val stockRepository: StockRepository
) {

    /**
     * Holds indexed quotes for fast O(1) date lookups.
     */
    private data class StockQuoteIndex(
        val stock: Stock,
        val quotesByDate: Map<LocalDate, com.skrymer.udgaard.model.StockQuote>
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
        customUnderlyingMap: Map<String, String>?
    ): List<StockPair> {
        return stocks.map { tradingStock ->
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
    }

    /**
     * Builds quote indexes for all stocks to enable O(1) date lookups.
     *
     * @param stocks - stocks to index
     * @return map of stock to quote index
     */
    private fun buildQuoteIndexes(stocks: List<Stock>): Map<Stock, StockQuoteIndex> {
        return stocks.associate { stock ->
            val quotesByDate = stock.quotes
                .filter { it.date != null }
                .associateBy { it.date!! }
            stock to StockQuoteIndex(stock, quotesByDate)
        }
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
        before: LocalDate
    ): List<LocalDate> {
        return stocks.flatMap { stock ->
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
    }

    /**
     * Checks if the current date is within the global cooldown period.
     *
     * @param currentDate - the date to check
     * @param lastExitDate - the most recent exit date, or null if no exits yet
     * @param allTradingDates - sorted list of all trading dates
     * @param cooldownDays - number of trading days for cooldown
     * @return true if in cooldown period, false otherwise
     */
    private fun isInCooldown(
        currentDate: LocalDate,
        lastExitDate: LocalDate?,
        allTradingDates: List<LocalDate>,
        cooldownDays: Int
    ): Boolean {
        if (cooldownDays <= 0 || lastExitDate == null) {
            return false
        }

        val exitDateIndex = allTradingDates.indexOf(lastExitDate)
        val currentDateIndex = allTradingDates.indexOf(currentDate)

        return if (exitDateIndex >= 0 && currentDateIndex >= 0) {
            val tradingDaysSinceExit = currentDateIndex - exitDateIndex
            tradingDaysSinceExit < cooldownDays
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
        quoteIndexes: Map<Stock, StockQuoteIndex>
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
                    val tradingQuotes = entry.stockPair.tradingStock.quotes.filter { quote ->
                        val qDate = quote.date
                        qDate != null && !qDate.isBefore(entryDate) && !qDate.isAfter(exitDate)
                    }

                    return Trade(
                        entry.stockPair.tradingStock.symbol!!,
                        entry.stockPair.underlyingSymbol,  // Store which underlying was used
                        entry.tradingEntryQuote,  // Use trading stock prices
                        tradingQuotes,
                        exitReport.exitReason,
                        profit,
                        entry.tradingEntryQuote.date,
                        entry.stockPair.tradingStock.sectorSymbol ?: ""
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
            val entriesForThisDate = stockPairs.mapNotNull { stockPair ->
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
                val rankedEntries = entriesForThisDate
                    .map { entry ->
                        val score = ranker.score(entry.stockPair.strategyStock, entry.strategyEntryQuote)
                        RankedEntry(entry, score)
                    }
                    .sortedByDescending { it.score } // Higher score = better

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
                        trades.find { it.containsQuote(entry.tradingEntryQuote) } == null) {
                        val trade = createTradeFromEntry(entry, exitStrategy, quoteIndexes)

                        if (trade != null) {
                            missedTrades.add(trade)
                        }
                    }
                }
            }
        }

        logger.info("Backtest complete: ${trades.size} trades, ${missedTrades.size} missed")

        val (winningTrades, losingTrades) = trades.partition { it.profit > 0 }
        return BacktestReport(winningTrades, losingTrades, missedTrades)
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
                "Please load this data before running the backtest."
            )
        }
    }

    // Helper extension for formatting doubles
    private fun Double.format(decimals: Int) = "%.${decimals}f".format(this)
}
