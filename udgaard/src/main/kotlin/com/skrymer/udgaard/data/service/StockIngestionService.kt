package com.skrymer.udgaard.data.service

import com.skrymer.udgaard.data.dto.ReconcileResult
import com.skrymer.udgaard.data.dto.RefreshProgress
import com.skrymer.udgaard.data.dto.RefreshTask
import com.skrymer.udgaard.data.dto.RefreshType
import com.skrymer.udgaard.data.integration.StockProvider
import com.skrymer.udgaard.data.integration.midgaard.MidgaardClient
import com.skrymer.udgaard.data.model.AssetType
import com.skrymer.udgaard.data.model.Earning
import com.skrymer.udgaard.data.model.OrderBlockSensitivity
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import com.skrymer.udgaard.data.repository.MarketBreadthRepository
import com.skrymer.udgaard.data.repository.StockJooqRepository
import com.skrymer.udgaard.service.UserSettingsJooqRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference

@Service
class StockIngestionService(
  private val stockRepository: StockJooqRepository,
  private val stockProvider: StockProvider,
  private val technicalIndicatorService: TechnicalIndicatorService,
  private val orderBlockCalculator: OrderBlockCalculator,
  private val midgaardClient: MidgaardClient,
  private val sectorBreadthService: SectorBreadthService,
  private val marketBreadthService: MarketBreadthService,
  private val marketBreadthRepository: MarketBreadthRepository,
  private val userSettingsRepository: UserSettingsJooqRepository,
  private val clock: Clock,
) {
  private val logger = LoggerFactory.getLogger(StockIngestionService::class.java)
  private val refreshQueue = ConcurrentLinkedQueue<RefreshTask>()

  @Volatile
  private var isProcessing = false

  private val currentProgress = AtomicReference(RefreshProgress())

  @Volatile
  private var processingJob: Job? = null

  // Wall-clock timestamp of the user's last completed refresh. Persisted via user_settings so
  // it survives JVM restarts. Distinct from MAX(stock_quotes.quote_date), which is the latest
  // bar *date* — those advance only on market sessions and lag the refresh action.
  val lastRefreshedAt: LocalDateTime?
    get() = userSettingsRepository
      .findByKey(LAST_REFRESHED_AT_KEY)
      ?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }

  // Single-source-of-truth for "a refresh just finished, record it". Called from every
  // successful refresh entrypoint (single-stock and batch) so the UI's "last refreshed"
  // reflects any completed refresh action, not just bulk runs.
  private fun markRefreshComplete() {
    userSettingsRepository.upsert(LAST_REFRESHED_AT_KEY, LocalDateTime.now(clock).toString())
  }

  // ── Public API ─────────────────────────────────────────────────────

  /**
   * Refresh a single stock: fetch from provider → save to DB.
   */
  fun refreshStock(symbol: String): Stock? {
    val stock = fetchAndBuildStock(symbol) ?: return null
    val saved = saveStock(stock)
    markRefreshComplete()
    return saved
  }

  /**
   * Reconcile the local universe with Midgaard's catalogue, then queue a full refresh.
   *
   * Midgaard is the single source of truth for which symbols exist (ADR 0011): any local
   * stock absent from the catalogue is a drifted-dead ticker and is pruned (cascading to its
   * quotes / order blocks / earnings). A null or empty catalogue is treated as an unusable
   * response and leaves the universe untouched, so a transient Midgaard outage can never
   * wipe it.
   */
  fun reconcileAndRefreshAll(): ReconcileResult {
    val catalogue = midgaardClient.getAllSymbols()
    if (catalogue.isNullOrEmpty()) {
      logger.warn("Midgaard catalogue lookup returned no symbols; leaving the universe untouched")
      return ReconcileResult(reconciled = false, queued = 0, pruned = 0)
    }
    val catalogueSymbols = catalogue.map { it.symbol }
    val pruned = pruneStocksNotIn(catalogueSymbols)
    queueStockRefresh(catalogueSymbols)
    return ReconcileResult(reconciled = true, queued = catalogueSymbols.size, pruned = pruned)
  }

  private fun pruneStocksNotIn(catalogueSymbols: List<String>): Int {
    val catalogue = catalogueSymbols.toSet()
    val drifted = stockRepository.findAllSymbols().filterNot { it in catalogue }
    if (drifted.isNotEmpty()) {
      logger.info("Pruning ${drifted.size} stock(s) absent from the Midgaard catalogue: ${drifted.joinToString(", ")}")
      stockRepository.batchDelete(drifted)
    }
    return drifted.size
  }

  /**
   * Queue stock symbols for async batch refresh with progress tracking.
   */
  fun queueStockRefresh(symbols: List<String>) {
    symbols.forEach { refreshQueue.add(RefreshTask(type = RefreshType.STOCK, identifier = it)) }
    currentProgress.set(RefreshProgress(total = refreshQueue.size))
    logger.info("Queued ${symbols.size} stocks for refresh. Total queue size: ${refreshQueue.size}")
    startProcessing()
  }

  fun getProgress(): RefreshProgress = currentProgress.get()

  fun clearQueue() {
    logger.info("Clearing refresh queue")
    refreshQueue.clear()
    currentProgress.set(RefreshProgress())
    processingJob?.cancel()
    isProcessing = false
  }

  // ── Stock fetch pipeline ───────────────────────────────────────────

  /**
   * Fetch quotes from the provider, enrich with trend, and calculate order blocks.
   * Returns a fully built Stock entity, or null on failure.
   */
  internal fun fetchAndBuildStock(symbol: String): Stock? =
    runCatching {
      logger.info("Fetching $symbol")
      val quotes = fetchQuotes(symbol)
      val enrichedQuotes = enrichWithTrend(quotes, symbol)
      val orderBlocks = calculateOrderBlocks(enrichedQuotes)
      val sortedQuotes = enrichedQuotes.sortedBy { it.date }
      val symbolInfo = midgaardClient.getSymbolInfo(symbol)
      val earnings = resolveEarnings(symbol)
      val ovtlyrSignals = midgaardClient.getOvtlyrSignals(symbol) ?: emptyList()
      Stock(
        symbol = symbol,
        assetType = resolveAssetType(symbolInfo?.assetType, symbol),
        sectorSymbol = symbolInfo?.sectorSymbol,
        quotes = sortedQuotes,
        orderBlocks = orderBlocks.toMutableList(),
        earnings = earnings,
        ovtlyrSignals = ovtlyrSignals,
        listingDate = sortedQuotes.firstOrNull()?.date,
        delistingDate = resolveDelistingDate(symbolInfo?.delistedAt, sortedQuotes.lastOrNull()?.date),
      )
    }.onFailure { error ->
      logger.error("✗ $symbol failed: ${error.message ?: error::class.simpleName}", error)
    }.getOrNull()

  // Fall back to the last-known earnings on upstream-fetch failure rather than wiping the
  // row set. An empty earnings list silently inverts `noEarningsWithinDays` (and similar
  // filters) into "always pass", so any provider outage would otherwise cause the scanner
  // to take trades straight into earnings catalysts. Stale-but-present beats empty-because-
  // we-failed.
  private fun resolveEarnings(symbol: String): List<Earning> =
    runCatching { stockProvider.getEarnings(symbol) }
      .onFailure { logger.warn("Earnings fetch failed for $symbol, keeping existing rows: ${it.message}") }
      .getOrNull()
      ?: stockRepository.findEarnings(symbol)

  /**
   * Authoritative delisting date if the provider knows one (the symbol came in
   * through the delisted-bootstrap path). Falls back to the
   * 90-days-without-data heuristic for symbols that delisted between bulk
   * runs without the provider noticing yet.
   */
  private fun resolveDelistingDate(providerDelistedAt: LocalDate?, lastQuoteDate: LocalDate?): LocalDate? =
    providerDelistedAt
      ?: lastQuoteDate?.let { lastDate ->
        val cutoff = LocalDate.now().minusDays(90)
        if (lastDate.isBefore(cutoff)) lastDate else null
      }

  // Map Midgaard's asset-type string onto Udgaard's enum. An unknown value (the two enums
  // live in separate deployables and can drift) is logged loudly and stored as null rather
  // than thrown — a single unrecognised type must not silently drop the symbol from ingestion.
  // Null is treated as STOCK downstream (the overwhelming majority).
  private fun resolveAssetType(rawAssetType: String?, symbol: String): AssetType? {
    if (rawAssetType == null) return null
    return runCatching { AssetType.valueOf(rawAssetType) }
      .getOrElse {
        logger.warn("Unknown asset type '$rawAssetType' from provider for $symbol; storing null (treated as STOCK)")
        null
      }
  }

  private fun fetchQuotes(symbol: String): List<StockQuote> =
    stockProvider.getDailyAdjustedTimeSeries(symbol)
      ?: throw IllegalStateException("No data from provider for $symbol")

  private fun enrichWithTrend(quotes: List<StockQuote>, symbol: String): List<StockQuote> =
    technicalIndicatorService.enrichWithIndicators(quotes, symbol)

  private fun calculateOrderBlocks(quotes: List<StockQuote>) =
    orderBlockCalculator.calculateOrderBlocks(
      quotes = quotes,
      sensitivity = 28.0,
      sensitivityLevel = OrderBlockSensitivity.HIGH,
    ) + orderBlockCalculator.calculateOrderBlocks(
      quotes = quotes,
      sensitivity = 50.0,
      sensitivityLevel = OrderBlockSensitivity.LOW,
    )

  private fun saveStock(stock: Stock): Stock? =
    try {
      stockRepository.save(stock)
      logger.info("✓ ${stock.symbol} completed")
      stock
    } catch (e: Exception) {
      logger.error("✗ ${stock.symbol} database save failed: ${e.message ?: e::class.simpleName}", e)
      null
    }

  // ── Batch processing ───────────────────────────────────────────────

  /**
   * Fetch a batch of stocks in parallel: delete existing → fetch → batch save.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  private fun batchRefreshStocks(symbols: List<String>): List<Stock> =
    runBlocking {
      val sorted = symbols.sorted()
      val limited = Dispatchers.IO.limitedParallelism(20)

      stockRepository.batchDelete(sorted)

      val results = sorted
        .map { symbol -> async(limited) { symbol to fetchAndBuildStock(symbol) } }
        .awaitAll()

      val stocks = results.mapNotNull { it.second }
      val stocksWithData = stocks.filter { it.quotes.isNotEmpty() }

      logBatchFailures(results, sorted.size)

      if (stocksWithData.isNotEmpty()) {
        val totalQuotes = stocksWithData.sumOf { it.quotes.size }
        logger.info("Saving ${stocksWithData.size} stocks ($totalQuotes quotes) to database")
        stockRepository.batchInsert(stocksWithData)
        logger.info("Batch save complete")
      }

      stocksWithData
    }

  private fun logBatchFailures(results: List<Pair<String, Stock?>>, totalCount: Int) {
    val failed = results.filter { it.second == null }.map { it.first }
    val empty = results.mapNotNull { it.second }.filter { it.quotes.isEmpty() }

    if (failed.isNotEmpty()) {
      logger.error(
        "STOCK FETCH FAILURE: ${failed.size}/$totalCount " +
          "stocks failed to fetch: ${failed.joinToString(", ")}",
      )
    }
    if (empty.isNotEmpty()) {
      logger.warn(
        "EMPTY DATA: ${empty.size}/$totalCount stocks fetched " +
          "but had no quote data: ${empty.map { it.symbol }.joinToString(", ")}",
      )
    }
  }

  // ── Queue processing ───────────────────────────────────────────────

  @Synchronized
  private fun startProcessing() {
    if (isProcessing) {
      logger.info("Already processing, skipping startProcessing()")
      return
    }

    isProcessing = true

    processingJob = CoroutineScope(Dispatchers.IO).launch {
      val errorDetails = mutableListOf<String>()
      var lastProgressLog = 0

      while (refreshQueue.isNotEmpty()) {
        val batch = pollBatch()
        if (batch.isEmpty()) break

        try {
          val symbols = batch.map { it.identifier }
          logger.info("Starting batch: ${symbols.size} stocks")

          val stocks = batchRefreshStocks(symbols)
          val failedSymbols = symbols.filterNot { s -> stocks.any { it.symbol == s } }

          failedSymbols.forEach { errorDetails.add("$it: fetch failed") }
          updateProgress(succeeded = stocks.size, failed = failedSymbols.size, stocks = stocks, failedSymbols = failedSymbols)
        } catch (e: Exception) {
          batch.forEach { errorDetails.add("${it.identifier}: Batch failure - ${e.message}") }
          updateProgress(succeeded = 0, failed = batch.size, stocks = emptyList(), failedSymbols = batch.map { it.identifier })
        }

        lastProgressLog = logProgressIfNeeded(lastProgressLog)
      }

      isProcessing = false
      refreshBreadth()
      logCompletionSummary(errorDetails)
      markRefreshComplete()
      currentProgress.set(RefreshProgress())
    }
  }

  private fun pollBatch(size: Int = 20): List<RefreshTask> {
    val tasks = mutableListOf<RefreshTask>()
    repeat(size) { refreshQueue.poll()?.let { tasks.add(it) } }
    return tasks
  }

  private fun updateProgress(
    succeeded: Int,
    failed: Int,
    stocks: List<Stock>,
    failedSymbols: List<String>,
  ) {
    currentProgress.updateAndGet { prev ->
      prev.copy(
        completed = prev.completed + succeeded,
        failed = prev.failed + failed,
        lastSuccess = stocks.lastOrNull()?.symbol ?: prev.lastSuccess,
        lastError = if (failedSymbols.isNotEmpty()) {
          "Failed symbols: ${failedSymbols.joinToString(", ")}"
        } else {
          prev.lastError
        },
      )
    }
  }

  private fun logProgressIfNeeded(lastProgressLog: Int): Int {
    val progress = currentProgress.get()
    val progressCompleted = progress.completed + progress.failed
    if (progressCompleted - lastProgressLog >= 10 || progressCompleted == progress.total) {
      val percentage = String.format("%.1f", progressCompleted.toDouble() / progress.total * 100)
      logger.info("Progress: $progressCompleted/${progress.total} completed ($percentage%)")
      return progressCompleted
    }
    return lastProgressLog
  }

  private fun refreshBreadth() {
    try {
      marketBreadthRepository.refreshBreadthDaily()
    } catch (e: Exception) {
      logger.error("Failed to refresh market breadth daily: ${e.message}")
    }
    try {
      sectorBreadthService.refreshSectorBreadth()
    } catch (e: Exception) {
      logger.error("Failed to refresh sector breadth: ${e.message}")
    }
    try {
      marketBreadthService.refreshMarketBreadth()
    } catch (e: Exception) {
      logger.error("Failed to refresh market breadth EMAs: ${e.message}")
    }
  }

  private fun logCompletionSummary(errorDetails: List<String>) {
    val progress = currentProgress.get()
    val totalProcessed = progress.completed + progress.failed
    val successRate = if (totalProcessed > 0) {
      String.format("%.1f", progress.completed.toDouble() / totalProcessed * 100)
    } else {
      "0.0"
    }

    logger.info("")
    logger.info("=== Refresh Complete ===")
    logger.info(
      "Total: $totalProcessed | Succeeded: ${progress.completed} " +
        "| Failed: ${progress.failed} ($successRate% success)",
    )

    if (errorDetails.isNotEmpty()) {
      logger.info("")
      logger.info("Failed items (${errorDetails.size}):")
      errorDetails.forEach { logger.info("  - $it") }
    }
    logger.info("========================")
    logger.info("")
  }

  companion object {
    private const val LAST_REFRESHED_AT_KEY = "data.last_refreshed_at"
  }
}
