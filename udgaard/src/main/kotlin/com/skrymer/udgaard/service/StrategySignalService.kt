package com.skrymer.udgaard.service

import com.skrymer.udgaard.controller.dto.QuoteWithSignal
import com.skrymer.udgaard.controller.dto.StockWithSignals
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.EntryStrategy
import com.skrymer.udgaard.model.strategy.ExitStrategy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Service for evaluating strategies on stock data and generating entry/exit signals.
 */
@Service
class StrategySignalService(
    private val strategyRegistry: StrategyRegistry
) {
    private val logger = LoggerFactory.getLogger(StrategySignalService::class.java)

    /**
     * Evaluate strategies on stock data and annotate each quote with entry/exit signals.
     *
     * @param stock The stock to evaluate
     * @param entryStrategyName The name of the entry strategy to use
     * @param exitStrategyName The name of the exit strategy to use
     * @param cooldownDays Number of trading days to wait after exit before allowing new entry (default: 0)
     * @return Stock data with entry/exit signals for each quote
     */
    fun evaluateStrategies(
        stock: Stock,
        entryStrategyName: String,
        exitStrategyName: String,
        cooldownDays: Int = 0
    ): StockWithSignals? {
        logger.info("Evaluating strategies entry=$entryStrategyName, exit=$exitStrategyName, cooldown=$cooldownDays on stock ${stock.symbol}")

        val entryStrategy = strategyRegistry.createEntryStrategy(entryStrategyName)
        val exitStrategy = strategyRegistry.createExitStrategy(exitStrategyName)

        if (entryStrategy == null) {
            logger.error("Entry strategy $entryStrategyName not found")
            return null
        }

        if (exitStrategy == null) {
            logger.error("Exit strategy $exitStrategyName not found")
            return null
        }

        val quotesWithSignals = evaluateQuotes(stock, entryStrategy, exitStrategy, cooldownDays)

        logger.info("Evaluated ${quotesWithSignals.size} quotes for ${stock.symbol} with entry=$entryStrategyName, exit=$exitStrategyName, cooldown=$cooldownDays")
        return StockWithSignals(
            stock = stock,
            entryStrategyName = entryStrategyName,
            exitStrategyName = exitStrategyName,
            quotesWithSignals = quotesWithSignals
        )
    }

    /**
     * Evaluate each quote for entry/exit signals.
     *
     * After an exit, no new entries are allowed for cooldownDays trading days.
     * For example, if cooldownDays = 5 and exit on Day 0, entries are blocked on Days 1-5
     * and allowed starting on Day 6.
     *
     * @param stock The stock to evaluate
     * @param entryStrategy Entry strategy to use
     * @param exitStrategy Exit strategy to use
     * @param cooldownDays Number of trading days to wait after exit before allowing new entry
     */
    private fun evaluateQuotes(
        stock: Stock,
        entryStrategy: EntryStrategy,
        exitStrategy: ExitStrategy,
        cooldownDays: Int
    ): List<QuoteWithSignal> {
        val sortedQuotes = stock.quotes.sortedBy { it.date }
        var entryQuote: StockQuote? = null
        var cooldownRemaining = 0

        return sortedQuotes.map { quote ->
            var entrySignal = false
            var exitSignal = false
            var exitReason: String? = null

            // Decrement cooldown counter if in cooldown
            if (cooldownRemaining > 0) {
                cooldownRemaining--
            }

            // Check for entry signal if not currently in a position and not in cooldown
            if (entryQuote == null && cooldownRemaining == 0) {
                entrySignal = entryStrategy.test(stock, quote)
                if (entrySignal) {
                    entryQuote = quote
                }
            }
            // Check for exit signal if currently in a position
            else if (entryQuote != null) {
                exitSignal = exitStrategy.match(stock, entryQuote, quote)
                if (exitSignal) {
                    exitReason = exitStrategy.reason(stock, entryQuote, quote)
                    entryQuote = null  // Exit position
                    cooldownRemaining = cooldownDays + 1  // Start cooldown period (cooldownDays + exit day)
                }
            }

            QuoteWithSignal(
                quote = quote,
                entrySignal = entrySignal,
                exitSignal = exitSignal,
                exitReason = exitReason
            )
        }
    }
}
