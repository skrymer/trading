package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.backtesting.dto.ConditionConfig
import com.skrymer.udgaard.backtesting.dto.EntrySignalDetails
import com.skrymer.udgaard.backtesting.dto.ExitSignalDetails
import com.skrymer.udgaard.backtesting.dto.QuoteWithConditions
import com.skrymer.udgaard.backtesting.dto.QuoteWithSignal
import com.skrymer.udgaard.backtesting.dto.StockConditionSignals
import com.skrymer.udgaard.backtesting.dto.StockWithSignals
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.backtesting.strategy.CompositeExitStrategy
import com.skrymer.udgaard.backtesting.strategy.EntryStrategy
import com.skrymer.udgaard.backtesting.strategy.ExitStrategy
import com.skrymer.udgaard.backtesting.strategy.ProjectXExitStrategy
import com.skrymer.udgaard.backtesting.strategy.condition.LogicalOperator
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import com.skrymer.udgaard.data.repository.MarketBreadthRepository
import com.skrymer.udgaard.data.repository.SectorBreadthRepository
import com.skrymer.udgaard.data.repository.StockJooqRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Service for evaluating strategies on stock data and generating entry/exit signals.
 */
@Service
class StrategySignalService(
  private val strategyRegistry: StrategyRegistry,
  private val dynamicStrategyBuilder: DynamicStrategyBuilder,
  private val marketBreadthRepository: MarketBreadthRepository,
  private val sectorBreadthRepository: SectorBreadthRepository,
  private val stockRepository: StockJooqRepository,
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
    cooldownDays: Int = 0,
  ): StockWithSignals? {
    logger.info(
      "Evaluating strategies entry=$entryStrategyName, exit=$exitStrategyName, " +
        "cooldown=$cooldownDays on stock ${stock.symbol}",
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

    val context = buildContext()
    val quotesWithSignals = evaluateQuotes(stock, entryStrategy, entryStrategyName, exitStrategy, cooldownDays, context)

    logger.info(
      "Evaluated ${quotesWithSignals.size} quotes for ${stock.symbol} " +
        "with entry=$entryStrategyName, exit=$exitStrategyName, cooldown=$cooldownDays",
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
    stock: Stock,
    entryStrategy: EntryStrategy,
    entryStrategyName: String,
    exitStrategy: ExitStrategy,
    cooldownDays: Int,
    context: BacktestContext,
  ): List<QuoteWithSignal> {
    val sortedQuotes = stock.quotes.sortedBy { it.date }
    var entryQuote: StockQuote? = null
    var entryQuoteDomain: StockQuote? = null
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
        entrySignal = entryStrategy.test(stock, quote, context)

        // If entry signal triggered and strategy supports detailed evaluation, get condition details
        if (entrySignal && entryStrategy is com.skrymer.udgaard.backtesting.strategy.DetailedEntryStrategy) {
          entryDetails = entryStrategy.testWithDetails(stock, quote, context).copy(strategyName = entryStrategyName)
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
   * Evaluate individual entry conditions on a stock's data.
   * Returns only matching quotes with detailed condition results.
   */
  fun evaluateConditions(
    stock: Stock,
    conditionConfigs: List<ConditionConfig>,
    operator: String = "AND",
  ): StockConditionSignals {
    val context = buildContext()
    val conditions = conditionConfigs.map { dynamicStrategyBuilder.buildEntryCondition(it) }
    val logicalOp = if (operator.uppercase() == "OR") LogicalOperator.OR else LogicalOperator.AND

    val sortedQuotes = stock.quotes.sortedBy { it.date }
    val matchingQuotes =
      sortedQuotes.mapNotNull { quote ->
        val results = conditions.map { it.evaluateWithDetails(stock, quote, context) }
        val allMet =
          when (logicalOp) {
            LogicalOperator.AND -> results.all { it.passed }
            LogicalOperator.OR -> results.any { it.passed }
            else -> results.all { it.passed }
          }
        if (allMet) {
          QuoteWithConditions(
            date = quote.date,
            closePrice = quote.closePrice,
            allConditionsMet = true,
            conditionResults = results,
          )
        } else {
          null
        }
      }

    return StockConditionSignals(
      symbol = stock.symbol,
      operator = operator,
      conditionDescriptions = conditions.map { it.description() },
      totalQuotes = sortedQuotes.size,
      matchingQuotes = matchingQuotes.size,
      quotesWithConditions = matchingQuotes,
    )
  }

  private fun buildContext(): BacktestContext {
    val sectorBreadthMap = sectorBreadthRepository.findAllAsMap()
    val marketBreadthMap = marketBreadthRepository.findAllAsMap()
    val spyStock = stockRepository.findBySymbol("SPY")
    val spyQuoteMap = spyStock?.quotes?.associateBy { it.date } ?: emptyMap()
    logger.info(
      "Built backtest context: ${sectorBreadthMap.size} sectors, " +
        "${marketBreadthMap.size} market breadth days, ${spyQuoteMap.size} SPY quotes",
    )
    return BacktestContext(sectorBreadthMap, marketBreadthMap, spyQuoteMap)
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
    stock: Stock,
    quoteDate: String,
    entryStrategyName: String,
  ): EntrySignalDetails? {
    logger.info("Evaluating entry conditions for ${stock.symbol} on date=$quoteDate with strategy=$entryStrategyName")

    val entryStrategy = strategyRegistry.createEntryStrategy(entryStrategyName)
    if (entryStrategy == null) {
      logger.error("Entry strategy $entryStrategyName not found")
      return null
    }

    if (entryStrategy !is com.skrymer.udgaard.backtesting.strategy.DetailedEntryStrategy) {
      logger.warn("Entry strategy $entryStrategyName does not support detailed evaluation")
      return null
    }

    // Find the quote for the specified date
    val targetDate =
      try {
        java.time.LocalDate.parse(quoteDate)
      } catch (e: Exception) {
        logger.error("Invalid date format: $quoteDate", e)
        return null
      }

    val quote = stock.quotes.find { it.date == targetDate }
    if (quote == null) {
      logger.error("Quote not found for date $quoteDate in stock ${stock.symbol}")
      return null
    }

    // Evaluate with details
    val context = buildContext()
    val details = entryStrategy.testWithDetails(stock, quote, context).copy(strategyName = entryStrategyName)
    logger.info("Evaluated conditions for ${stock.symbol} on $quoteDate: allConditionsMet=${details.allConditionsMet}")
    return details
  }

  /**
   * Evaluate exit strategy conditions for a specific quote/date.
   * Returns condition details regardless of whether the exit signal passes or fails.
   *
   * @param stock The stock to evaluate
   * @param quoteDate The date of the quote to evaluate
   * @param entryDate The date of the entry quote (exit conditions may reference it)
   * @param exitStrategyName The name of the exit strategy to use
   * @return Exit signal details with all condition evaluations, or null if strategy/quote not found
   */
  fun evaluateExitConditionsForDate(
    stock: Stock,
    quoteDate: String,
    entryDate: String,
    exitStrategyName: String,
  ): ExitSignalDetails? {
    logger.info(
      "Evaluating exit conditions for ${stock.symbol} on date=$quoteDate " +
        "(entry=$entryDate) with strategy=$exitStrategyName",
    )

    val exitStrategy = strategyRegistry.createExitStrategy(exitStrategyName)
    if (exitStrategy == null) {
      logger.error("Exit strategy $exitStrategyName not found")
      return null
    }

    val targetDate =
      try {
        java.time.LocalDate.parse(quoteDate)
      } catch (e: Exception) {
        logger.error("Invalid date format: $quoteDate", e)
        return null
      }

    val parsedEntryDate =
      try {
        java.time.LocalDate.parse(entryDate)
      } catch (e: Exception) {
        logger.error("Invalid entry date format: $entryDate", e)
        return null
      }

    val quote = stock.quotes.find { it.date == targetDate }
    if (quote == null) {
      logger.error("Quote not found for date $quoteDate in stock ${stock.symbol}")
      return null
    }

    val entryQuote = stock.quotes.find { it.date == parsedEntryDate }
    if (entryQuote == null) {
      logger.error("Entry quote not found for date $entryDate in stock ${stock.symbol}")
      return null
    }

    // Try to get the composite strategy for detailed evaluation
    val compositeStrategy =
      when (exitStrategy) {
        is ProjectXExitStrategy -> exitStrategy.getCompositeStrategy()
        is CompositeExitStrategy -> exitStrategy
        else -> null
      }

    return if (compositeStrategy != null) {
      val details = compositeStrategy.testWithDetails(stock, entryQuote, quote)
      details.copy(strategyName = exitStrategyName).also {
        logger.info(
          "Evaluated exit conditions for ${stock.symbol} on $quoteDate: " +
            "anyConditionMet=${it.anyConditionMet}",
        )
      }
    } else {
      // Fallback for non-composite strategies
      val passed = exitStrategy.match(stock, entryQuote, quote)
      ExitSignalDetails(
        strategyName = exitStrategyName,
        strategyDescription = exitStrategy.description(),
        conditions = emptyList(),
        anyConditionMet = passed,
      ).also {
        logger.info("Evaluated exit (basic) for ${stock.symbol} on $quoteDate: anyConditionMet=$passed")
      }
    }
  }
}
