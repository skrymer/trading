package com.skrymer.udgaard.scanner.service

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.EntrySignalDetails
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.backtesting.service.DynamicStrategyBuilder
import com.skrymer.udgaard.backtesting.service.StrategyRegistry
import com.skrymer.udgaard.backtesting.strategy.DetailedEntryStrategy
import com.skrymer.udgaard.backtesting.strategy.EntryStrategy
import com.skrymer.udgaard.backtesting.strategy.ExitStrategy
import com.skrymer.udgaard.backtesting.strategy.RandomRanker
import com.skrymer.udgaard.backtesting.strategy.RankerFactory
import com.skrymer.udgaard.backtesting.strategy.StockRanker
import com.skrymer.udgaard.data.integration.midgaard.MidgaardClient
import com.skrymer.udgaard.data.integration.midgaard.dto.MidgaardLatestQuoteDto
import com.skrymer.udgaard.data.model.AssetType
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import com.skrymer.udgaard.data.repository.MarketBreadthRepository
import com.skrymer.udgaard.data.repository.SectorBreadthRepository
import com.skrymer.udgaard.data.repository.StockJooqRepository
import com.skrymer.udgaard.data.service.StockService
import com.skrymer.udgaard.data.service.SymbolService
import com.skrymer.udgaard.data.service.TechnicalIndicatorService
import com.skrymer.udgaard.portfolio.model.InstrumentType
import com.skrymer.udgaard.portfolio.model.OptionType
import com.skrymer.udgaard.scanner.dto.AddScannerTradeRequest
import com.skrymer.udgaard.scanner.dto.CloseScannerTradeRequest
import com.skrymer.udgaard.scanner.dto.DrawdownStatsResponse
import com.skrymer.udgaard.scanner.dto.RollScannerTradeRequest
import com.skrymer.udgaard.scanner.dto.ScanRequest
import com.skrymer.udgaard.scanner.dto.UpdateScannerTradeRequest
import com.skrymer.udgaard.scanner.dto.ValidateEntriesRequest
import com.skrymer.udgaard.scanner.model.ConditionFailureSummary
import com.skrymer.udgaard.scanner.model.EntryValidationResponse
import com.skrymer.udgaard.scanner.model.EntryValidationResult
import com.skrymer.udgaard.scanner.model.ExitCheckResponse
import com.skrymer.udgaard.scanner.model.ExitCheckResult
import com.skrymer.udgaard.scanner.model.NearMissCandidate
import com.skrymer.udgaard.scanner.model.ScanResponse
import com.skrymer.udgaard.scanner.model.ScanResult
import com.skrymer.udgaard.scanner.model.ScannerTrade
import com.skrymer.udgaard.scanner.model.TradeStatus
import com.skrymer.udgaard.scanner.repository.ScannerTradeJooqRepository
import com.skrymer.udgaard.service.SettingsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.abs

private data class EvalResult(
  val scanResult: ScanResult?,
  val stock: Stock?,
  val entryQuote: StockQuote?,
  val nearMiss: NearMissCandidate?,
  val failedConditions: List<ConditionEvaluationResult>?,
  val evaluated: Boolean = false,
)

private fun buildConditionFailureSummary(
  allFailedConditions: List<List<ConditionEvaluationResult>>,
  totalStocksScanned: Int,
): List<ConditionFailureSummary> {
  if (allFailedConditions.isEmpty()) return emptyList()

  val totalEvaluated = totalStocksScanned
  val failureCountByType = mutableMapOf<String, Int>()
  val descriptionByType = mutableMapOf<String, String>()

  for (conditions in allFailedConditions) {
    for (condition in conditions) {
      descriptionByType.putIfAbsent(condition.conditionType, condition.description)
      if (!condition.passed) {
        failureCountByType.merge(condition.conditionType, 1, Int::plus)
      }
    }
  }

  return failureCountByType
    .map { (conditionType, count) ->
      ConditionFailureSummary(
        conditionType = conditionType,
        description = descriptionByType[conditionType] ?: conditionType,
        stocksBlocked = count,
        totalStocksEvaluated = totalEvaluated,
      )
    }.sortedByDescending { it.stocksBlocked }
}

private fun rankResults(
  matchedEvals: List<EvalResult>,
  rankerName: String?,
  entryStrategy: EntryStrategy,
  backtestContext: BacktestContext,
): Triple<List<ScanResult>, String, StockRanker> {
  val ranker = if (rankerName != null) {
    RankerFactory.create(rankerName) ?: RandomRanker()
  } else {
    entryStrategy.preferredRanker() ?: RandomRanker()
  }
  val resolvedName = ranker::class.simpleName?.removeSuffix("Ranker") ?: "Unknown"

  val ranked = matchedEvals
    .mapNotNull { eval ->
      val stock = eval.stock ?: return@mapNotNull null
      val quote = eval.entryQuote ?: return@mapNotNull null
      val score = ranker.score(stock, quote, backtestContext)
      val jitteredScore = score + kotlin.random.Random.nextDouble() * StockRanker.TIE_BREAK_JITTER
      eval.scanResult?.copy(rankScore = jitteredScore)
    }.sortedByDescending { it.rankScore ?: 0.0 }

  return Triple(ranked, resolvedName, ranker)
}

@Service
class ScannerService(
  private val scannerTradeRepository: ScannerTradeJooqRepository,
  private val stockRepository: StockJooqRepository,
  private val stockService: StockService,
  private val symbolService: SymbolService,
  private val strategyRegistry: StrategyRegistry,
  private val dynamicStrategyBuilder: DynamicStrategyBuilder,
  private val sectorBreadthRepository: SectorBreadthRepository,
  private val marketBreadthRepository: MarketBreadthRepository,
  private val settingsService: SettingsService,
  private val midgaardClient: MidgaardClient,
  private val technicalIndicatorService: TechnicalIndicatorService,
) {
  private val logger = LoggerFactory.getLogger(ScannerService::class.java)

  /**
   * Run a scan for entry signals across all matching stocks.
   */
  fun scan(request: ScanRequest): ScanResponse {
    val startTime = System.currentTimeMillis()
    val scanDate = LocalDate.now()
    val entryStrategy = strategyRegistry.createEntryStrategy(request.entryStrategyName)
      ?: throw IllegalArgumentException("Entry strategy '${request.entryStrategyName}' not found")

    val backtestContext = buildBacktestContext()
    val currentMarketDate = backtestContext.marketBreadthMap.keys.maxOrNull()
    val symbols = resolveSymbols(request)
    logger.info("Scanning ${symbols.size} symbols, market date: $currentMarketDate")

    val evalResult =
      evaluateSymbols(symbols, scanDate, entryStrategy, backtestContext, request.entryStrategyName, currentMarketDate)

    val (rankedResults, resolvedRankerName, ranker) = rankResults(
      evalResult.matched,
      request.rankerName,
      entryStrategy,
      backtestContext,
    )
    val nearMissLimit = request.nearMissLimit ?: DEFAULT_NEAR_MISS_LIMIT
    val topNearMisses = evalResult.nearMisses
      .mapNotNull { eval ->
        val stock = eval.stock ?: return@mapNotNull null
        val quote = eval.entryQuote ?: return@mapNotNull null
        eval.nearMiss?.copy(rankScore = ranker.score(stock, quote, backtestContext))
      }.sortedWith(
        compareByDescending<NearMissCandidate> { it.conditionsPassed }
          .thenByDescending { it.rankScore ?: 0.0 },
      ).take(nearMissLimit)
    val failureSummary = if (entryStrategy is DetailedEntryStrategy) {
      buildConditionFailureSummary(evalResult.failedConditions, evalResult.stocksEvaluated)
    } else {
      emptyList()
    }
    val executionTime = System.currentTimeMillis() - startTime
    logger.info("Scan complete: ${rankedResults.size} matches in ${executionTime}ms (ranked by $resolvedRankerName)")

    return ScanResponse(
      scanDate = scanDate,
      entryStrategyName = request.entryStrategyName,
      exitStrategyName = request.exitStrategyName,
      results = rankedResults,
      totalStocksScanned = evalResult.stocksEvaluated,
      executionTimeMs = executionTime,
      nearMissCandidates = topNearMisses,
      conditionFailureSummary = failureSummary,
      rankerName = resolvedRankerName,
    )
  }

  private data class ScanEvaluation(
    val matched: List<EvalResult>,
    val nearMisses: List<EvalResult>,
    val failedConditions: List<List<ConditionEvaluationResult>>,
    val stocksEvaluated: Int,
  )

  private fun evaluateSymbols(
    symbols: List<String>,
    scanDate: LocalDate,
    entryStrategy: EntryStrategy,
    backtestContext: BacktestContext,
    strategyName: String,
    currentMarketDate: LocalDate?,
  ): ScanEvaluation {
    val quotesAfter = scanDate.minusDays(90)
    val matched = mutableListOf<EvalResult>()
    val nearMisses = mutableListOf<EvalResult>()
    val failed = mutableListOf<List<ConditionEvaluationResult>>()
    var count = 0

    symbols.chunked(BATCH_SIZE).forEach { batch ->
      val stocks = stockRepository.findBySymbols(batch, quotesAfter = quotesAfter)
      runBlocking(Dispatchers.Default) {
        stocks
          .map { stock ->
            async { evaluateStock(stock, entryStrategy, backtestContext, strategyName, currentMarketDate) }
          }.awaitAll()
          .forEach { eval ->
            if (eval.evaluated) count++
            if (eval.scanResult != null) matched.add(eval)
            if (eval.nearMiss != null) nearMisses.add(eval)
            eval.failedConditions?.let { failed.add(it) }
          }
      }
    }
    return ScanEvaluation(matched, nearMisses, failed, count)
  }

  private fun evaluateStock(
    stock: Stock,
    entryStrategy: EntryStrategy,
    backtestContext: BacktestContext,
    strategyName: String,
    currentMarketDate: LocalDate?,
  ): EvalResult {
    val quote = stock.quotes.lastOrNull() ?: return EvalResult(null, null, null, null, null)
    if (currentMarketDate != null && quote.date != currentMarketDate) return EvalResult(null, null, null, null, null)

    if (entryStrategy is DetailedEntryStrategy) {
      val details = entryStrategy
        .testWithDetails(stock, quote, backtestContext)
        .copy(strategyName = strategyName)

      return if (details.allConditionsMet) {
        EvalResult(toScanResult(stock, quote, details), stock, quote, null, details.conditions, true)
      } else {
        val passed = details.conditions.count { it.passed }
        val nearMiss = if (passed > 0) toNearMiss(stock, quote, details, passed) else null
        EvalResult(null, stock, quote, nearMiss, details.conditions, true)
      }
    }

    val matches = entryStrategy.test(stock, quote, backtestContext)
    return if (matches) {
      EvalResult(toScanResult(stock, quote, null), stock, quote, null, null, true)
    } else {
      EvalResult(null, null, null, null, null, evaluated = true)
    }
  }

  /**
   * Check exit signals for all open scanner trades.
   */
  fun checkExits(): ExitCheckResponse {
    val trades = scannerTradeRepository.findOpen()
    if (trades.isEmpty()) {
      return ExitCheckResponse(results = emptyList(), checksPerformed = 0, exitsTriggered = 0)
    }

    val backtestContext = buildBacktestContext()
    val uniqueSymbols = trades.map { it.symbol }.distinct()
    val quotesAfter = LocalDate.now().minusDays(90)
    val stocksBySymbol =
      stockRepository
        .findBySymbols(uniqueSymbols, quotesAfter = quotesAfter)
        .associateBy { it.symbol }

    val liveQuotesBySymbol = fetchLiveQuotes(uniqueSymbols)
    logger.info("Fetched live quotes for ${liveQuotesBySymbol.size}/${uniqueSymbols.size} symbols")

    val results = trades.mapNotNull { trade ->
      evaluateTradeExit(trade, stocksBySymbol, liveQuotesBySymbol, backtestContext)
    }

    val exitsTriggered = results.count { it.exitTriggered }
    logger.info("Exit check: ${results.size} trades checked, $exitsTriggered exits triggered")

    return ExitCheckResponse(
      results = results,
      checksPerformed = results.size,
      exitsTriggered = exitsTriggered,
    )
  }

  /**
   * Validate scan candidates against live quotes.
   * Re-evaluates entry conditions with current price and checks if exit would trigger immediately.
   */
  fun validateEntries(request: ValidateEntriesRequest): EntryValidationResponse {
    val symbols = request.symbols.take(MAX_VALIDATE_SYMBOLS)
    val entryStrategy = strategyRegistry.createEntryStrategy(request.entryStrategyName)
      ?: throw IllegalArgumentException("Entry strategy '${request.entryStrategyName}' not found")
    val exitStrategy = strategyRegistry.createExitStrategy(request.exitStrategyName)
      ?: throw IllegalArgumentException("Exit strategy '${request.exitStrategyName}' not found")

    val backtestContext = buildBacktestContext()
    val quotesAfter = LocalDate.now().minusDays(90)
    val stocksBySymbol = stockRepository
      .findBySymbols(symbols, quotesAfter = quotesAfter)
      .associateBy { it.symbol }

    val liveQuotesBySymbol = fetchLiveQuotes(symbols)
    logger.info("Validate entries: fetched live quotes for ${liveQuotesBySymbol.size}/${symbols.size} symbols")

    val results = symbols.mapNotNull { symbol ->
      validateSingleEntry(
        symbol,
        stocksBySymbol,
        liveQuotesBySymbol,
        entryStrategy,
        exitStrategy,
        backtestContext,
        request.entryStrategyName,
      )
    }

    val validCount = results.count { it.entryStillValid && !it.exitWouldTrigger }
    val invalidCount = results.count { !it.entryStillValid }
    val doaCount = results.count { it.exitWouldTrigger }
    logger.info("Validate entries: ${results.size} checked, $validCount valid, $invalidCount invalid, $doaCount DOA")

    return EntryValidationResponse(
      results = results,
      validCount = validCount,
      invalidCount = invalidCount,
      doaCount = doaCount,
    )
  }

  private fun validateSingleEntry(
    symbol: String,
    stocksBySymbol: Map<String, Stock>,
    liveQuotesBySymbol: Map<String, MidgaardLatestQuoteDto>,
    entryStrategy: EntryStrategy,
    exitStrategy: ExitStrategy,
    backtestContext: BacktestContext,
    entryStrategyName: String,
  ): EntryValidationResult? {
    val stock = stocksBySymbol[symbol] ?: return null
    val lastDbQuote = stock.quotes.lastOrNull() ?: return null

    val liveQuote = liveQuotesBySymbol[symbol]
    val usedLiveData = liveQuote != null
    // Only update price -- keep original date (for breadth lookups) and volume (after-hours volume is meaningless)
    val syntheticQuote = if (liveQuote != null) {
      lastDbQuote.copy(closePrice = liveQuote.price)
    } else {
      lastDbQuote
    }

    val entryStillValid: Boolean
    val entryDetails: EntrySignalDetails?
    if (entryStrategy is DetailedEntryStrategy) {
      val details = entryStrategy
        .testWithDetails(stock, syntheticQuote, backtestContext)
        .copy(strategyName = entryStrategyName)
      entryStillValid = details.allConditionsMet
      entryDetails = details
      if (!entryStillValid) {
        val failed = details.conditions.filter { !it.passed }.joinToString { "${it.conditionType}: ${it.message}" }
        logger.debug("Validate $symbol invalid: $failed")
      }
    } else {
      entryStillValid = entryStrategy.test(stock, syntheticQuote, backtestContext)
      entryDetails = null
    }

    val exitReport = exitStrategy.test(stock, syntheticQuote, syntheticQuote, backtestContext)

    return EntryValidationResult(
      symbol = symbol,
      entryStillValid = entryStillValid,
      exitWouldTrigger = exitReport.match,
      exitReason = exitReport.exitReason,
      currentPrice = syntheticQuote.closePrice,
      usedLiveData = usedLiveData,
      entrySignalDetails = entryDetails,
    )
  }

  private fun evaluateTradeExit(
    trade: ScannerTrade,
    stocksBySymbol: Map<String, Stock>,
    liveQuotesBySymbol: Map<String, MidgaardLatestQuoteDto>,
    backtestContext: BacktestContext,
  ): ExitCheckResult? {
    val exitStrategy = strategyRegistry.createExitStrategy(trade.exitStrategyName)
    if (exitStrategy == null) {
      logger.warn("Exit strategy '${trade.exitStrategyName}' not found for trade ${trade.id}")
      return null
    }

    val stock = stocksBySymbol[trade.symbol]
    if (stock == null) {
      logger.warn("Stock data not found for ${trade.symbol}")
      return null
    }

    val lastDbQuote = stock.quotes.lastOrNull() ?: return null
    val entryQuote = stock.quotes.find { it.date == trade.entryDate }

    val liveQuote = liveQuotesBySymbol[trade.symbol]
    val usedLiveData = liveQuote != null
    val latestQuote = if (liveQuote != null) {
      createSyntheticQuote(lastDbQuote, liveQuote, stock)
    } else {
      lastDbQuote
    }

    val exitReport = exitStrategy.test(stock, entryQuote, latestQuote, backtestContext)
    val currentPrice = latestQuote.closePrice
    val pnlPercent = if (trade.entryPrice != 0.0) {
      ((currentPrice - trade.entryPrice) / trade.entryPrice) * 100
    } else {
      0.0
    }
    // Unrealized P&L for options uses delta-based approximation from stock price change
    // since live option prices are not available. Realized P&L in closeTrade() uses actual option prices.
    val pnlDollars = if (trade.instrumentType == InstrumentType.OPTION) {
      val delta = trade.delta ?: DEFAULT_OPTION_DELTA
      val stockPriceChange = currentPrice - trade.entryPrice
      stockPriceChange * delta * trade.quantity * trade.multiplier + trade.rolledCredits
    } else {
      (currentPrice - trade.entryPrice) * trade.quantity
    }

    return ExitCheckResult(
      tradeId = trade.id ?: 0,
      symbol = trade.symbol,
      exitTriggered = exitReport.match,
      exitReason = exitReport.exitReason,
      currentPrice = currentPrice,
      unrealizedPnlPercent = pnlPercent,
      unrealizedPnlDollars = pnlDollars,
      usedLiveData = usedLiveData,
    )
  }

  private fun fetchLiveQuotes(symbols: List<String>): Map<String, MidgaardLatestQuoteDto> =
    runBlocking(Dispatchers.IO) {
      symbols
        .map { symbol ->
          async {
            try {
              midgaardClient.getLatestQuote(symbol)?.let { symbol to it }
            } catch (e: Exception) {
              logger.warn("Failed to fetch live quote for $symbol: ${e.message}")
              null
            }
          }
        }.awaitAll()
        .filterNotNull()
        .toMap()
    }

  private fun createSyntheticQuote(
    lastDbQuote: StockQuote,
    liveQuote: MidgaardLatestQuoteDto,
    stock: Stock,
  ): StockQuote {
    val price = liveQuote.price
    if (price <= 0.0) return lastDbQuote.copy(date = LocalDate.now(), volume = liveQuote.volume)

    fun incrementalEma(prevEma: Double, period: Int): Double {
      if (prevEma == 0.0) return 0.0
      val k = 2.0 / (period + 1)
      return (price - prevEma) * k + prevEma
    }

    val high = if (liveQuote.high > 0) liveQuote.high else price
    val low = if (liveQuote.low > 0) liveQuote.low else price
    val prevClose = lastDbQuote.closePrice
    val trueRange = maxOf(high - low, abs(high - prevClose), abs(low - prevClose))
    val newAtr = if (lastDbQuote.atr > 0) {
      ((lastDbQuote.atr * (ATR_PERIOD - 1)) + trueRange) / ATR_PERIOD
    } else {
      lastDbQuote.atr
    }

    val donchianUpper = maxOf(
      stock.quotes.takeLast(DONCHIAN_PERIOD - 1).maxOfOrNull { it.high } ?: 0.0,
      high,
    )

    val syntheticQuote = lastDbQuote.copy(
      date = LocalDate.now(),
      closePrice = price,
      high = high,
      low = low,
      volume = liveQuote.volume,
    )
    syntheticQuote.closePriceEMA5 = incrementalEma(lastDbQuote.closePriceEMA5, 5)
    syntheticQuote.closePriceEMA10 = incrementalEma(lastDbQuote.closePriceEMA10, 10)
    syntheticQuote.closePriceEMA20 = incrementalEma(lastDbQuote.closePriceEMA20, 20)
    syntheticQuote.closePriceEMA50 = incrementalEma(lastDbQuote.closePriceEMA50, 50)
    syntheticQuote.closePriceEMA100 = incrementalEma(lastDbQuote.closePriceEMA100, 100)
    syntheticQuote.ema200 = incrementalEma(lastDbQuote.ema200, 200)
    syntheticQuote.atr = newAtr
    syntheticQuote.donchianUpperBand = donchianUpper
    syntheticQuote.trend = technicalIndicatorService.determineTrend(syntheticQuote)

    return syntheticQuote
  }

  /**
   * Roll a scanner trade: delete old, create new with accumulated credits.
   */
  @Transactional
  fun rollTrade(tradeId: Long, request: RollScannerTradeRequest): ScannerTrade {
    val existingTrade = findOpenTrade(tradeId)

    val optionPrice = existingTrade.optionPrice
      ?: throw IllegalArgumentException("Cannot roll trade $tradeId: optionPrice is missing")
    val rollCredit = (request.closePrice - optionPrice) * existingTrade.quantity *
      if (existingTrade.instrumentType == InstrumentType.OPTION) existingTrade.multiplier else 1
    val newRolledCredits = existingTrade.rolledCredits + rollCredit

    scannerTradeRepository.delete(tradeId)

    val newTrade = ScannerTrade(
      id = null,
      symbol = existingTrade.symbol,
      sectorSymbol = existingTrade.sectorSymbol,
      instrumentType = existingTrade.instrumentType,
      entryPrice = request.newEntryPrice,
      entryDate = LocalDate.parse(request.newEntryDate),
      quantity = request.newQuantity,
      optionType = request.newOptionType?.let { OptionType.valueOf(it) } ?: existingTrade.optionType,
      strikePrice = request.newStrikePrice,
      expirationDate = LocalDate.parse(request.newExpirationDate),
      multiplier = existingTrade.multiplier,
      optionPrice = request.newOptionPrice ?: existingTrade.optionPrice,
      delta = request.newDelta ?: existingTrade.delta,
      entryStrategyName = existingTrade.entryStrategyName,
      exitStrategyName = existingTrade.exitStrategyName,
      rolledCredits = newRolledCredits,
      rollCount = existingTrade.rollCount + 1,
      notes = existingTrade.notes,
    )

    val saved = scannerTradeRepository.save(newTrade)
    logger.info(
      "Rolled scanner trade $tradeId → ${saved.id}: " +
        "credit=$rollCredit, totalCredits=$newRolledCredits, rollCount=${saved.rollCount}",
    )
    return saved
  }

  fun addTrade(request: AddScannerTradeRequest): ScannerTrade {
    val trade = ScannerTrade(
      id = null,
      symbol = request.symbol,
      sectorSymbol = request.sectorSymbol,
      instrumentType = InstrumentType.valueOf(request.instrumentType),
      entryPrice = request.entryPrice,
      entryDate = LocalDate.parse(request.entryDate),
      quantity = request.quantity,
      optionType = request.optionType?.let { OptionType.valueOf(it) },
      strikePrice = request.strikePrice,
      expirationDate = request.expirationDate?.let { LocalDate.parse(it) },
      multiplier = request.multiplier ?: 100,
      optionPrice = request.optionPrice,
      delta = request.delta,
      entryStrategyName = request.entryStrategyName,
      exitStrategyName = request.exitStrategyName,
      notes = request.notes,
    )
    val saved = scannerTradeRepository.save(trade)
    logger.info("Added scanner trade ${saved.id} for ${saved.symbol}")
    return saved
  }

  @Transactional
  fun closeTrade(id: Long, request: CloseScannerTradeRequest): ScannerTrade {
    val trade = findOpenTrade(id)

    val exitPrice = request.exitPrice
    val exitDate = parseDate(request.exitDate)
    val realizedPnl = if (trade.instrumentType == InstrumentType.OPTION) {
      (exitPrice - (trade.optionPrice ?: trade.entryPrice)) * trade.quantity * trade.multiplier + trade.rolledCredits
    } else {
      (exitPrice - trade.entryPrice) * trade.quantity
    }

    val closed = trade.copy(
      status = TradeStatus.CLOSED,
      exitPrice = exitPrice,
      exitDate = exitDate,
      realizedPnl = realizedPnl,
      closedAt = LocalDateTime.now(),
    )
    val saved = scannerTradeRepository.save(closed)
    logger.info("Closed scanner trade $id: ${trade.symbol}, P&L=${"%.2f".format(realizedPnl)}")
    return saved
  }

  fun getTrades(): List<ScannerTrade> = scannerTradeRepository.findOpen()

  fun getClosedTrades(): List<ScannerTrade> = scannerTradeRepository.findClosed()

  fun getTrade(id: Long): ScannerTrade? = scannerTradeRepository.findById(id)

  fun updateTrade(id: Long, request: UpdateScannerTradeRequest): ScannerTrade {
    val existing = findOpenTrade(id)
    val updated = existing.copy(notes = request.notes)
    return scannerTradeRepository.save(updated)
  }

  fun deleteTrade(id: Long) {
    scannerTradeRepository.findById(id)
      ?: throw IllegalArgumentException("Scanner trade $id not found")
    scannerTradeRepository.delete(id)
    logger.info("Deleted scanner trade $id")
  }

  @Transactional
  fun deleteAllTrades(): Int {
    val count = scannerTradeRepository.deleteAll()
    logger.info("Deleted all scanner trades ($count trades)")
    return count
  }

  fun getDrawdownStats(): DrawdownStatsResponse {
    val closedTrades = scannerTradeRepository.findClosed()
    val totalRealizedPnl = closedTrades.sumOf { it.realizedPnl ?: 0.0 }
    val winners = closedTrades.count { (it.realizedPnl ?: 0.0) > 0.0 }
    val winRate = if (closedTrades.isNotEmpty()) winners.toDouble() / closedTrades.size else 0.0

    val totalUnrealizedPnl = computeOpenTradeUnrealizedPnl()

    val settings = settingsService.getPositionSizingSettings()
    val portfolioValue = settings.portfolioValue
    val currentEquity = portfolioValue + totalRealizedPnl + totalUnrealizedPnl

    val peakEquity = maxOf(settings.peakEquity ?: portfolioValue, currentEquity)
    val drawdownPct = if (peakEquity > 0) maxOf(0.0, (peakEquity - currentEquity) / peakEquity * 100) else 0.0

    return DrawdownStatsResponse(
      totalRealizedPnl = totalRealizedPnl,
      closedTradeCount = closedTrades.size,
      winRate = winRate,
      totalUnrealizedPnl = totalUnrealizedPnl,
      currentEquity = currentEquity,
      peakEquity = peakEquity,
      currentDrawdownPct = drawdownPct,
    )
  }

  private fun computeOpenTradeUnrealizedPnl(): Double {
    val openTrades = scannerTradeRepository.findOpen()
    if (openTrades.isEmpty()) return 0.0

    val symbols = openTrades.map { it.symbol }.distinct()
    val liveQuotesBySymbol = fetchLiveQuotes(symbols)
    val quotesAfter = LocalDate.now().minusDays(90)
    val stocksBySymbol = stockRepository.findBySymbols(symbols, quotesAfter = quotesAfter).associateBy { it.symbol }

    return openTrades.sumOf { trade ->
      val currentPrice = liveQuotesBySymbol[trade.symbol]?.price
        ?: stocksBySymbol[trade.symbol]?.quotes?.lastOrNull()?.closePrice
        ?: return@sumOf 0.0
      if (trade.instrumentType == InstrumentType.OPTION) {
        val delta = trade.delta ?: DEFAULT_OPTION_DELTA
        val stockPriceChange = currentPrice - trade.entryPrice
        stockPriceChange * delta * trade.quantity * trade.multiplier + trade.rolledCredits
      } else {
        (currentPrice - trade.entryPrice) * trade.quantity
      }
    }
  }

  private fun buildBacktestContext(): BacktestContext {
    val sectorBreadthMap = sectorBreadthRepository.findAllAsMap()
    val marketBreadthMap = marketBreadthRepository.findAllAsMap()
    val spyStock = stockRepository.findBySymbol("SPY", quotesAfter = LocalDate.now().minusDays(90))
    val spyQuoteMap = spyStock?.quotes?.associateBy { it.date } ?: emptyMap()
    return BacktestContext(sectorBreadthMap, marketBreadthMap, spyQuoteMap)
  }

  private fun resolveSymbols(request: ScanRequest): List<String> {
    var symbols: List<String> =
      if (!request.stockSymbols.isNullOrEmpty()) {
        request.stockSymbols.map { it.uppercase() }
      } else if (!request.assetTypes.isNullOrEmpty()) {
        val assetTypeEnums = request.assetTypes.map { AssetType.valueOf(it) }
        symbolService
          .getAll()
          .filter { it.assetType in assetTypeEnums }
          .map { it.symbol }
      } else {
        stockRepository.findAllSymbols()
      }

    if (!request.includeSectors.isNullOrEmpty() || !request.excludeSectors.isNullOrEmpty()) {
      val sectorBySymbol = stockService.getAllStocksSimple().associate { it.symbol to it.sector }

      if (!request.includeSectors.isNullOrEmpty()) {
        val sectors = request.includeSectors.map { it.uppercase() }.toSet()
        symbols = symbols.filter { sectorBySymbol[it]?.uppercase() in sectors }
      }

      if (!request.excludeSectors.isNullOrEmpty()) {
        val sectors = request.excludeSectors.map { it.uppercase() }.toSet()
        symbols = symbols.filter { sectorBySymbol[it]?.uppercase() !in sectors }
      }
    }

    return symbols
  }

  private fun findOpenTrade(id: Long): ScannerTrade {
    val trade = scannerTradeRepository.findById(id)
      ?: throw IllegalArgumentException("Scanner trade $id not found")
    require(trade.status != TradeStatus.CLOSED) { "Scanner trade $id is already closed" }
    return trade
  }

  private fun parseDate(dateStr: String): LocalDate =
    try {
      LocalDate.parse(dateStr)
    } catch (_: java.time.format.DateTimeParseException) {
      throw IllegalArgumentException("Invalid exit date format: $dateStr")
    }

  companion object {
    private const val BATCH_SIZE = 150
    private const val DEFAULT_NEAR_MISS_LIMIT = 10
    private const val DEFAULT_OPTION_DELTA = 0.80
    private const val MAX_VALIDATE_SYMBOLS = 30
    private const val ATR_PERIOD = 14
    private const val DONCHIAN_PERIOD = 5

    private fun toScanResult(stock: Stock, quote: StockQuote, details: EntrySignalDetails?) =
      ScanResult(
        symbol = stock.symbol,
        sectorSymbol = stock.sectorSymbol,
        closePrice = quote.closePrice,
        date = quote.date,
        entrySignalDetails = details,
        atr = quote.atr,
        trend = quote.trend,
      )

    private fun toNearMiss(
      stock: Stock,
      quote: StockQuote,
      details: EntrySignalDetails,
      conditionsPassed: Int,
    ) = NearMissCandidate(
      symbol = stock.symbol,
      sectorSymbol = stock.sectorSymbol,
      closePrice = quote.closePrice,
      date = quote.date,
      entrySignalDetails = details,
      atr = quote.atr,
      trend = quote.trend,
      conditionsPassed = conditionsPassed,
      conditionsTotal = details.conditions.size,
    )
  }
}
