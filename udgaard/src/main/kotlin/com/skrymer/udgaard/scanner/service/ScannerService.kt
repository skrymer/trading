package com.skrymer.udgaard.scanner.service

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.EntrySignalDetails
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.backtesting.service.DynamicStrategyBuilder
import com.skrymer.udgaard.backtesting.service.StrategyRegistry
import com.skrymer.udgaard.backtesting.strategy.DetailedEntryStrategy
import com.skrymer.udgaard.backtesting.strategy.EntryStrategy
import com.skrymer.udgaard.data.model.AssetType
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import com.skrymer.udgaard.data.repository.MarketBreadthRepository
import com.skrymer.udgaard.data.repository.SectorBreadthRepository
import com.skrymer.udgaard.data.repository.StockJooqRepository
import com.skrymer.udgaard.data.service.StockService
import com.skrymer.udgaard.data.service.SymbolService
import com.skrymer.udgaard.portfolio.model.InstrumentType
import com.skrymer.udgaard.portfolio.model.OptionType
import com.skrymer.udgaard.scanner.dto.AddScannerTradeRequest
import com.skrymer.udgaard.scanner.dto.RollScannerTradeRequest
import com.skrymer.udgaard.scanner.dto.ScanRequest
import com.skrymer.udgaard.scanner.dto.UpdateScannerTradeRequest
import com.skrymer.udgaard.scanner.model.ConditionFailureSummary
import com.skrymer.udgaard.scanner.model.ExitCheckResponse
import com.skrymer.udgaard.scanner.model.ExitCheckResult
import com.skrymer.udgaard.scanner.model.NearMissCandidate
import com.skrymer.udgaard.scanner.model.ScanResponse
import com.skrymer.udgaard.scanner.model.ScanResult
import com.skrymer.udgaard.scanner.model.ScannerTrade
import com.skrymer.udgaard.scanner.repository.ScannerTradeJooqRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

private data class EvalResult(
  val scanResult: ScanResult?,
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

    logger.info("Starting scan with entry=${request.entryStrategyName}, exit=${request.exitStrategyName}")

    val backtestContext = buildBacktestContext()
    val currentMarketDate = backtestContext.marketBreadthMap.keys.maxOrNull()
    logger.info("Current market date: $currentMarketDate")

    val symbols = resolveSymbols(request)
    logger.info("Scanning ${symbols.size} symbols")

    val quotesAfter = scanDate.minusDays(90)
    val results = mutableListOf<ScanResult>()
    val nearMisses = mutableListOf<NearMissCandidate>()
    val failedConditions = mutableListOf<List<ConditionEvaluationResult>>()
    var stocksEvaluated = 0

    symbols.chunked(BATCH_SIZE).forEachIndexed { batchIndex, batch ->
      val stocks = stockRepository.findBySymbols(batch, quotesAfter = quotesAfter)
      logger.info("Processing batch ${batchIndex + 1}: ${stocks.size} stocks loaded")

      runBlocking(Dispatchers.Default) {
        stocks
          .map { stock ->
            async { evaluateStock(stock, entryStrategy, backtestContext, request.entryStrategyName, currentMarketDate) }
          }.awaitAll()
          .forEach { eval ->
            if (eval.evaluated) stocksEvaluated++
            eval.scanResult?.let { results.add(it) }
            eval.nearMiss?.let { nearMisses.add(it) }
            eval.failedConditions?.let { failedConditions.add(it) }
          }
      }
    }

    val nearMissLimit = request.nearMissLimit ?: DEFAULT_NEAR_MISS_LIMIT
    val topNearMisses = nearMisses.sortedByDescending { it.conditionsPassed }.take(nearMissLimit)
    val failureSummary = if (entryStrategy is DetailedEntryStrategy) {
      buildConditionFailureSummary(failedConditions, stocksEvaluated)
    } else {
      emptyList()
    }

    val skipped = symbols.size - stocksEvaluated
    val executionTime = System.currentTimeMillis() - startTime
    logger.info(
      "Scan complete: ${results.size} matches, ${nearMisses.size} near-misses " +
        "from $stocksEvaluated stocks in ${executionTime}ms ($skipped skipped, no current quotes)",
    )

    return ScanResponse(
      scanDate = scanDate,
      entryStrategyName = request.entryStrategyName,
      exitStrategyName = request.exitStrategyName,
      results = results,
      totalStocksScanned = stocksEvaluated,
      executionTimeMs = executionTime,
      nearMissCandidates = topNearMisses,
      conditionFailureSummary = failureSummary,
    )
  }

  private fun evaluateStock(
    stock: Stock,
    entryStrategy: EntryStrategy,
    backtestContext: BacktestContext,
    strategyName: String,
    currentMarketDate: LocalDate?,
  ): EvalResult {
    val quote = stock.quotes.lastOrNull() ?: return EvalResult(null, null, null)
    if (currentMarketDate != null && quote.date != currentMarketDate) return EvalResult(null, null, null)

    if (entryStrategy is DetailedEntryStrategy) {
      val details = entryStrategy
        .testWithDetails(stock, quote, backtestContext)
        .copy(strategyName = strategyName)

      return if (details.allConditionsMet) {
        EvalResult(toScanResult(stock, quote, details), null, details.conditions, true)
      } else {
        val passed = details.conditions.count { it.passed }
        val nearMiss = if (passed > 0) toNearMiss(stock, quote, details, passed) else null
        EvalResult(null, nearMiss, details.conditions, true)
      }
    }

    val matches = entryStrategy.test(stock, quote, backtestContext)
    return if (matches) {
      EvalResult(toScanResult(stock, quote, null), null, null, true)
    } else {
      EvalResult(null, null, null, evaluated = true)
    }
  }

  /**
   * Check exit signals for all open scanner trades.
   */
  fun checkExits(): ExitCheckResponse {
    val trades = scannerTradeRepository.findAll()
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

    val results = trades.mapNotNull { trade ->
      val exitStrategy = strategyRegistry.createExitStrategy(trade.exitStrategyName)
      if (exitStrategy == null) {
        logger.warn("Exit strategy '${trade.exitStrategyName}' not found for trade ${trade.id}")
        return@mapNotNull null
      }

      val stock = stocksBySymbol[trade.symbol]
      if (stock == null) {
        logger.warn("Stock data not found for ${trade.symbol}")
        return@mapNotNull null
      }

      val latestQuote = stock.quotes.lastOrNull() ?: return@mapNotNull null
      val entryQuote = stock.quotes.find { it.date == trade.entryDate }

      val exitReport = exitStrategy.test(stock, entryQuote, latestQuote, backtestContext)
      val currentPrice = latestQuote.closePrice
      val pnlPercent = if (trade.entryPrice != 0.0) {
        ((currentPrice - trade.entryPrice) / trade.entryPrice) * 100
      } else {
        0.0
      }

      ExitCheckResult(
        tradeId = trade.id ?: 0,
        symbol = trade.symbol,
        exitTriggered = exitReport.match,
        exitReason = exitReport.exitReason,
        currentPrice = currentPrice,
        unrealizedPnlPercent = pnlPercent,
      )
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
   * Roll a scanner trade: delete old, create new with accumulated credits.
   */
  fun rollTrade(tradeId: Long, request: RollScannerTradeRequest): ScannerTrade {
    val existingTrade = scannerTradeRepository.findById(tradeId)
      ?: throw IllegalArgumentException("Scanner trade $tradeId not found")

    val rollCredit = (request.closePrice - existingTrade.entryPrice) * existingTrade.quantity *
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
      entryStrategyName = request.entryStrategyName,
      exitStrategyName = request.exitStrategyName,
      notes = request.notes,
    )
    val saved = scannerTradeRepository.save(trade)
    logger.info("Added scanner trade ${saved.id} for ${saved.symbol}")
    return saved
  }

  fun getTrades(): List<ScannerTrade> = scannerTradeRepository.findAll()

  fun getTrade(id: Long): ScannerTrade? = scannerTradeRepository.findById(id)

  fun updateTrade(id: Long, request: UpdateScannerTradeRequest): ScannerTrade {
    val existing = scannerTradeRepository.findById(id)
      ?: throw IllegalArgumentException("Scanner trade $id not found")
    val updated = existing.copy(notes = request.notes)
    return scannerTradeRepository.save(updated)
  }

  fun deleteTrade(id: Long) {
    scannerTradeRepository.delete(id)
    logger.info("Deleted scanner trade $id")
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

  companion object {
    private const val BATCH_SIZE = 150
    private const val DEFAULT_NEAR_MISS_LIMIT = 10

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
