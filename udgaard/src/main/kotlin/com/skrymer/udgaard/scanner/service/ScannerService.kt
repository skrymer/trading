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
import com.skrymer.udgaard.data.integration.LatestQuote
import com.skrymer.udgaard.data.integration.StockProvider
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
import com.skrymer.udgaard.scanner.dto.ClosedTradeStatsResponse
import com.skrymer.udgaard.scanner.dto.DrawdownStatsResponse
import com.skrymer.udgaard.scanner.dto.RollScannerTradeRequest
import com.skrymer.udgaard.scanner.dto.ScanRequest
import com.skrymer.udgaard.scanner.dto.StrategyClosedStats
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
import java.time.temporal.ChronoUnit
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
  private val stockProvider: StockProvider,
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
      latestDataDate = currentMarketDate,
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

    val liveQuotesBySymbol = stockProvider.getLatestQuotes(uniqueSymbols)
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

    val liveQuotesBySymbol = stockProvider.getLatestQuotes(symbols)
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
    liveQuotesBySymbol: Map<String, LatestQuote>,
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
    liveQuotesBySymbol: Map<String, LatestQuote>,
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
    val previousDayQuote = stock.quotes.getOrNull(stock.quotes.size - 2)

    val liveQuote = liveQuotesBySymbol[trade.symbol]
    val usedLiveData = liveQuote != null
    val syntheticQuote = if (liveQuote != null) {
      createSyntheticQuote(lastDbQuote, liveQuote, stock)
    } else {
      lastDbQuote
    }

    // When the synthetic quote represents a later bar than anything stored, append it so
    // the strategy's date-arithmetic (Stock.countTradingDaysBetween) and entryQuote lookup
    // both count today. Without this, a user clicking "check exits" mid-session on the
    // final day of a since-entry window condition would see it NOT fire until today's EOD
    // ingestion runs — defeating the whole point of live exit checks. Only appends when
    // the synthetic bar is genuinely newer than the stored tail (preserves the
    // sorted-ascending invariant on Stock.quotes).
    val stockForEvaluation = if (syntheticQuote.date.isAfter(lastDbQuote.date)) {
      stock.copy(quotes = stock.quotes + syntheticQuote)
    } else {
      stock
    }
    val entryQuote = stockForEvaluation.quotes.find { it.date == trade.entryDate }

    val exitReport = exitStrategy.test(stockForEvaluation, entryQuote, syntheticQuote, backtestContext)
    val currentPrice = syntheticQuote.closePrice
    val priorClose = if (usedLiveData) lastDbQuote.closePrice else previousDayQuote?.closePrice ?: lastDbQuote.closePrice
    val pnlPercent = if (trade.entryPrice != 0.0) {
      ((currentPrice - trade.entryPrice) / trade.entryPrice) * 100
    } else {
      0.0
    }
    val pnlDollars = calculatePnlDollars(trade, currentPrice - trade.entryPrice, includeRolledCredits = true)
    val dailyPnlDollars = calculatePnlDollars(trade, currentPrice - priorClose)

    return ExitCheckResult(
      tradeId = trade.id ?: 0,
      symbol = trade.symbol,
      exitTriggered = exitReport.match,
      exitReason = exitReport.exitReason,
      currentPrice = currentPrice,
      priorClose = priorClose,
      unrealizedPnlPercent = pnlPercent,
      unrealizedPnlDollars = pnlDollars,
      dailyPnlDollars = dailyPnlDollars,
      usedLiveData = usedLiveData,
    )
  }

  private fun createSyntheticQuote(
    lastDbQuote: StockQuote,
    liveQuote: LatestQuote,
    stock: Stock,
  ): StockQuote {
    // When the live quote describes the same bar already stored — price matches the last
    // stored close AND previousClose matches the prior stored close — there's no new data
    // to project. Applying the EMA recurrence anyway advances indicators by a phantom
    // forward step and can produce false exit signals (e.g., a marginal EMA10/EMA20 cross
    // that doesn't exist at the actual bar). Common whenever checkExits runs after EOD:
    // the DB has today's close and the live-quote provider reports the same close.
    val previousDbQuote = stock.getPreviousQuote(lastDbQuote)
    val priceMatchesStoredClose = abs(liveQuote.price - lastDbQuote.closePrice) < QUOTE_MATCH_TOLERANCE
    val previousCloseMatches = previousDbQuote == null ||
      abs(liveQuote.previousClose - previousDbQuote.closePrice) < QUOTE_MATCH_TOLERANCE
    if (priceMatchesStoredClose && previousCloseMatches) {
      return lastDbQuote
    }

    // Anchor the synthetic bar's date to the provider's market-clock timestamp when
    // available; fall back to server wall-clock only if the provider didn't supply one.
    // Using LocalDate.now() unconditionally would pick the server's calendar day, which
    // can diverge from the US market day by +/-1 around midnight or in non-US timezones —
    // re-introducing the same timezone bug the LatestQuote.date field was added to solve.
    val syntheticDate = liveQuote.date ?: LocalDate.now()
    val price = liveQuote.price
    if (price <= 0.0) return lastDbQuote.copy(date = syntheticDate, volume = liveQuote.volume)

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
      date = syntheticDate,
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
      entryDate = resolveEntryDate(request.symbol, LocalDate.parse(request.entryDate)),
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
    logger.info(
      "Added scanner trade ${saved.id} for ${saved.symbol} with entryDate=${saved.entryDate} " +
        "(client sent ${request.entryDate})",
    )
    return saved
  }

  /**
   * Authoritative entry-date resolution. Browser-supplied dates are unreliable — a user in
   * a timezone ahead of the US market (e.g. Australia) can stamp a date one calendar day
   * after the real US trading day. Instead anchor to:
   *   1. The live quote's date (from the provider's market timestamp — always the real
   *      trading day), preferring the stored date when both refer to the same bar so the
   *      entryQuote lookup in evaluateTradeExit is guaranteed against stored data.
   *   2. The latest stored bar for the symbol.
   *   3. The client-supplied date as a last-resort fallback (no live quote, no stored data).
   */
  private fun resolveEntryDate(symbol: String, clientDate: LocalDate): LocalDate {
    val liveDate = runCatching { stockProvider.getLatestQuote(symbol)?.date }
      .onFailure { logger.warn("Live quote fetch failed while resolving entryDate for $symbol: ${it.message}") }
      .getOrNull()
    // Scalar query rather than loading the full quote history — addTrade is a write path
    // and loading years of rows just to compute the max date was a latency hit.
    val latestStored = stockRepository.getLatestQuoteDate(symbol)

    // Observability: if the market-authoritative signals disagree with the client's date
    // by more than a few days, something is likely stale (provider cache, ingestion stuck)
    // or the client clock is badly wrong. Log so a future anomaly is investigable.
    val chosen = when {
      liveDate != null && latestStored != null && liveDate == latestStored -> latestStored
      liveDate != null && latestStored != null && liveDate > latestStored -> liveDate
      latestStored != null -> latestStored
      liveDate != null -> liveDate
      else -> clientDate
    }
    if (chosen != clientDate && abs(ChronoUnit.DAYS.between(chosen, clientDate)) > STALE_DATE_WARN_DAYS) {
      logger.warn(
        "entryDate divergence for $symbol: resolved=$chosen (live=$liveDate, stored=$latestStored) " +
          "vs client=$clientDate — possible provider/ingestion staleness or client clock drift",
      )
    }
    return chosen
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

  @Suppress("ForbiddenComment") // Tracked follow-up: inject a Clock — see TODO below.
  fun getTrades(): List<ScannerTrade> {
    val trades = scannerTradeRepository.findOpen()
    if (trades.isEmpty()) return trades

    // Count trading days via the stored quote series. All US-listed symbols share the same
    // trading calendar, so Stock.countTradingDaysBetween on the per-symbol quote index gives
    // an accurate business-day count including market holidays — something client-side
    // calendar-day math can't produce.
    // TODO: inject a Clock across ScannerService (also used by checkExits) so wall-clock
    // reads become deterministic — removes a class of midnight-boundary test flakiness.
    val today = LocalDate.now()
    val earliestEntry = trades.minOf { it.entryDate }
    val stocksBySymbol = stockRepository
      .findBySymbols(trades.map { it.symbol }.distinct(), quotesAfter = earliestEntry)
      .associateBy { it.symbol }

    return trades.map { trade ->
      // Preserve null when the stock has no stored quotes — the frontend renders "-",
      // which is more informative than collapsing "data missing" and "0 days elapsed"
      // into the same display value.
      val days = stocksBySymbol[trade.symbol]?.countTradingDaysBetween(trade.entryDate, today)
      trade.copy(tradingDaysHeld = days)
    }
  }

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

  fun getClosedTradeStats(): ClosedTradeStatsResponse {
    val closedTrades = scannerTradeRepository.findClosed()
    if (closedTrades.isEmpty()) {
      return ClosedTradeStatsResponse(overall = null, byStrategy = emptyList())
    }

    val overall = computeClosedStats("Overall", closedTrades)

    val byStrategy = closedTrades
      .groupBy { it.entryStrategyName }
      .map { (strategy, trades) -> computeClosedStats(strategy, trades) }
      .sortedByDescending { it.trades }

    return ClosedTradeStatsResponse(overall = overall, byStrategy = byStrategy)
  }

  private fun computeClosedStats(strategy: String, trades: List<ScannerTrade>): StrategyClosedStats {
    val wins = trades.filter { (it.realizedPnl ?: 0.0) > 0.0 }
    val losses = trades.filter { (it.realizedPnl ?: 0.0) < 0.0 }
    val winRate = if (trades.isNotEmpty()) (wins.size.toDouble() / trades.size) * 100 else 0.0

    val avgWinDollars = if (wins.isNotEmpty()) wins.sumOf { it.realizedPnl ?: 0.0 } / wins.size else 0.0
    val avgLossDollars = if (losses.isNotEmpty()) kotlin.math.abs(losses.sumOf { it.realizedPnl ?: 0.0 } / losses.size) else 0.0

    val avgWinPct = if (wins.isNotEmpty()) wins.sumOf { tradePnlPercent(it) } / wins.size else 0.0
    val avgLossPct = if (losses.isNotEmpty()) kotlin.math.abs(losses.sumOf { tradePnlPercent(it) } / losses.size) else 0.0

    val grossProfit = wins.sumOf { it.realizedPnl ?: 0.0 }
    val grossLoss = kotlin.math.abs(losses.sumOf { it.realizedPnl ?: 0.0 })

    val wr = winRate / 100
    val edge = (avgWinPct * wr) - (avgLossPct * (1 - wr))

    return StrategyClosedStats(
      strategy = strategy,
      trades = trades.size,
      wins = wins.size,
      losses = losses.size,
      winRate = winRate,
      edge = edge,
      profitFactor = if (grossLoss > 0) {
        grossProfit / grossLoss
      } else if (grossProfit > 0) {
        null
      } else {
        0.0
      },
      avgWinPct = avgWinPct,
      avgLossPct = avgLossPct,
      avgWinDollars = avgWinDollars,
      avgLossDollars = avgLossDollars,
      totalPnl = trades.sumOf { it.realizedPnl ?: 0.0 },
    )
  }

  private fun tradePnlPercent(trade: ScannerTrade): Double {
    val pnl = trade.realizedPnl ?: 0.0
    val costBasis = if (trade.instrumentType == InstrumentType.OPTION) {
      (trade.optionPrice ?: trade.entryPrice) * trade.quantity * trade.multiplier
    } else {
      trade.entryPrice * trade.quantity
    }
    return if (costBasis > 0) (pnl / costBasis) * 100 else 0.0
  }

  private fun computeOpenTradeUnrealizedPnl(): Double {
    val openTrades = scannerTradeRepository.findOpen()
    if (openTrades.isEmpty()) return 0.0

    val symbols = openTrades.map { it.symbol }.distinct()
    val liveQuotesBySymbol = stockProvider.getLatestQuotes(symbols)
    val quotesAfter = LocalDate.now().minusDays(90)
    val stocksBySymbol = stockRepository.findBySymbols(symbols, quotesAfter = quotesAfter).associateBy { it.symbol }

    return openTrades.sumOf { trade ->
      val currentPrice = liveQuotesBySymbol[trade.symbol]?.price
        ?: stocksBySymbol[trade.symbol]?.quotes?.lastOrNull()?.closePrice
        ?: return@sumOf 0.0
      calculatePnlDollars(trade, currentPrice - trade.entryPrice, includeRolledCredits = true)
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

    // Stored prices round to 4 decimals; anything tighter than this is rounding noise, not a real price move.
    private const val QUOTE_MATCH_TOLERANCE = 1e-4

    // Log a warning when the market-authoritative entry date differs from the client's
    // by more than this many days — suggests provider staleness, ingestion lag, or a
    // client with a badly wrong clock.
    private const val STALE_DATE_WARN_DAYS = 7L

    private fun calculatePnlDollars(trade: ScannerTrade, priceChange: Double, includeRolledCredits: Boolean = false): Double =
      if (trade.instrumentType == InstrumentType.OPTION) {
        val delta = trade.delta ?: DEFAULT_OPTION_DELTA
        priceChange * delta * trade.quantity * trade.multiplier + if (includeRolledCredits) trade.rolledCredits else 0.0
      } else {
        priceChange * trade.quantity
      }

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
