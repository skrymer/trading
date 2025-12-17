package com.skrymer.udgaard.service

import com.skrymer.udgaard.controller.dto.EntrySignalDetails
import com.skrymer.udgaard.controller.dto.QuoteWithSignal
import com.skrymer.udgaard.controller.dto.StockWithSignals
import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.domain.StockQuoteDomain
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
    stock: StockDomain,
    entryStrategyName: String,
    exitStrategyName: String,
    cooldownDays: Int = 0,
  ): StockWithSignals? {
    logger.info(
      "Evaluating strategies entry=$entryStrategyName, exit=$exitStrategyName, cooldown=$cooldownDays on stock ${stock.symbol}",
    )

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

    val quotesWithSignals = evaluateQuotes(stock, entryStrategy, entryStrategyName, exitStrategy, cooldownDays)

    logger.info(
      "Evaluated ${quotesWithSignals.size} quotes for ${stock.symbol} with entry=$entryStrategyName, exit=$exitStrategyName, cooldown=$cooldownDays",
    )
    return StockWithSignals(
      stock = stock,
      entryStrategyName = entryStrategyName,
      exitStrategyName = exitStrategyName,
      quotesWithSignals = quotesWithSignals,
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
   * @param entryStrategyName Registered name of the entry strategy
   * @param exitStrategy Exit strategy to use
   * @param cooldownDays Number of trading days to wait after exit before allowing new entry
   */
  private fun evaluateQuotes(
    stock: StockDomain,
    entryStrategy: EntryStrategy,
    entryStrategyName: String,
    exitStrategy: ExitStrategy,
    cooldownDays: Int,
  ): List<QuoteWithSignal> {
    val sortedQuotes = stock.quotes.sortedBy { it.date }
    var entryQuote: StockQuoteDomain? = null
    var entryQuoteDomain: StockQuoteDomain? = null
    var cooldownRemaining = 0

    return sortedQuotes.map { quote ->
      var entrySignal = false
      var entryDetails: EntrySignalDetails? = null
      var exitSignal = false
      var exitReason: String? = null

      // Decrement cooldown counter if in cooldown
      if (cooldownRemaining > 0) {
        cooldownRemaining--
      }

      // Check for entry signal if not currently in a position and not in cooldown
      if (entryQuote == null && cooldownRemaining == 0) {
        entrySignal = entryStrategy.test(stock, quote)

        // If entry signal triggered and strategy supports detailed evaluation, get condition details
        if (entrySignal && entryStrategy is com.skrymer.udgaard.model.strategy.DetailedEntryStrategy) {
          entryDetails = entryStrategy.testWithDetails(stock, quote).copy(strategyName = entryStrategyName)
        }

        if (entrySignal) {
          entryQuote = quote
          entryQuoteDomain = quote
        }
      }
      // Check for exit signal if currently in a position
      else if (entryQuote != null) {
        exitSignal = exitStrategy.match(stock, entryQuoteDomain, quote)
        if (exitSignal) {
          exitReason = exitStrategy.reason(stock, entryQuoteDomain, quote)
          entryQuote = null // Exit position
          entryQuoteDomain = null
          cooldownRemaining = cooldownDays + 1 // Start cooldown period (cooldownDays + exit day)
        }
      }

      QuoteWithSignal(
        quote = quote,
        entrySignal = entrySignal,
        entryDetails = entryDetails,
        exitSignal = exitSignal,
        exitReason = exitReason,
      )
    }
  }

  /**
   * Evaluate entry strategy conditions for a specific quote/date.
   * This returns condition details regardless of whether the entry signal passes or fails.
   *
   * @param stock The stock to evaluate
   * @param quoteDate The date of the quote to evaluate
   * @param entryStrategyName The name of the entry strategy to use
   * @return Entry signal details with all condition evaluations, or null if strategy/quote not found
   */
  fun evaluateConditionsForDate(
    stock: StockDomain,
    quoteDate: String,
    entryStrategyName: String,
  ): EntrySignalDetails? {
    logger.info("Evaluating entry conditions for ${stock.symbol} on date=$quoteDate with strategy=$entryStrategyName")

    val entryStrategy = strategyRegistry.createEntryStrategy(entryStrategyName)
    if (entryStrategy == null) {
      logger.error("Entry strategy $entryStrategyName not found")
      return null
    }

    if (entryStrategy !is com.skrymer.udgaard.model.strategy.DetailedEntryStrategy) {
      logger.warn("Entry strategy $entryStrategyName does not support detailed evaluation")
      return null
    }

    // Find the quote for the specified date
    val targetDate =
      try {
        java.time.LocalDate.parse(quoteDate)
      } catch (e: Exception) {
        logger.error("Invalid date format: $quoteDate")
        return null
      }

    val quote = stock.quotes.find { it.date == targetDate }
    if (quote == null) {
      logger.error("Quote not found for date $quoteDate in stock ${stock.symbol}")
      return null
    }

    // Evaluate with details
    val details = entryStrategy.testWithDetails(stock, quote).copy(strategyName = entryStrategyName)
    logger.info("Evaluated conditions for ${stock.symbol} on $quoteDate: allConditionsMet=${details.allConditionsMet}")
    return details
  }
}
