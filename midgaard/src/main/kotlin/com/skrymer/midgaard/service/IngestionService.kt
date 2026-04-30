package com.skrymer.midgaard.service

import com.skrymer.midgaard.integration.CompanyInfoProvider
import com.skrymer.midgaard.integration.EarningsProvider
import com.skrymer.midgaard.integration.IndicatorProvider
import com.skrymer.midgaard.integration.OhlcvProvider
import com.skrymer.midgaard.model.IndicatorSource
import com.skrymer.midgaard.model.IngestionResult
import com.skrymer.midgaard.model.IngestionState
import com.skrymer.midgaard.model.Quote
import com.skrymer.midgaard.model.RawBar
import com.skrymer.midgaard.model.SectorMapping
import com.skrymer.midgaard.repository.EarningsRepository
import com.skrymer.midgaard.repository.IngestionStatusRepository
import com.skrymer.midgaard.repository.MarketHolidayRepository
import com.skrymer.midgaard.repository.QuoteRepository
import com.skrymer.midgaard.repository.SymbolRepository
import com.skrymer.midgaard.service.IndicatorCalculator.Companion.toBigDecimal4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Bulk + per-symbol ingest pipeline. Provider-agnostic: providers are picked
 * by `ProviderConfiguration` based on `app.ingest.provider`, this service just
 * receives the chosen interfaces. Each provider self-rate-limits internally
 * (caller doesn't know or care which permit key to ask for).
 *
 * Two orthogonal config knobs:
 *   - `app.ingest.provider` — chooses the provider behind ohlcv/indicators/
 *     earnings/companyInfo. Wired in `ProviderConfiguration`.
 *   - `app.ingest.indicators` — `local` recomputes ATR/ADX from raw bars via
 *     `IndicatorCalculator`; `api` calls the indicator provider's API. Default
 *     is `local` (provider-neutral; safe across split events).
 */
@Service
@Suppress("LongParameterList", "TooManyFunctions")
class IngestionService(
    @param:Qualifier("ohlcv") private val ohlcv: OhlcvProvider,
    @param:Qualifier("indicators") private val indicators: IndicatorProvider,
    @param:Qualifier("earnings") private val earnings: EarningsProvider,
    @param:Qualifier("companyInfo") private val companyInfo: CompanyInfoProvider,
    private val indicatorCalculator: IndicatorCalculator,
    private val quoteRepository: QuoteRepository,
    private val earningsRepository: EarningsRepository,
    private val symbolRepository: SymbolRepository,
    private val ingestionStatusRepository: IngestionStatusRepository,
    private val marketHolidayRepository: MarketHolidayRepository,
    @param:Value("\${app.ingest.indicators:LOCAL}") private val indicatorsMode: IndicatorsMode,
) {
    @Volatile
    var bulkProgress: BulkProgress? = null

    /**
     * Initial ingest — fetches OHLCV + ATR + ADX from the configured ingest source
     * and computes EMAs + Donchian locally.
     */
    suspend fun initialIngest(symbol: String): IngestionResult =
        try {
            logger.info("Starting initial ingest for $symbol")
            val bars = fetchInitialBars(symbol)
            if (bars == null) {
                updateStatus(symbol, 0, null, IngestionState.FAILED)
                IngestionResult(symbol, false, message = "No OHLCV data available")
            } else {
                val quotes = buildInitialQuotes(symbol, bars)
                quoteRepository.upsertQuotes(quotes)
                logger.info("Saved ${quotes.size} quotes for $symbol")
                fetchAndSaveSupplementaryData(symbol)
                updateStatus(symbol, quotes.size, quotes.maxByOrNull { it.date }?.date, IngestionState.COMPLETE)
                IngestionResult(symbol, true, quotes.size)
            }
        } catch (e: Exception) {
            logger.error("Failed initial ingest for $symbol: ${e.message}", e)
            updateStatus(symbol, 0, null, IngestionState.FAILED)
            IngestionResult(symbol, false, message = e.message)
        }

    /**
     * Daily update — fetches recent OHLCV from the configured provider and
     * extends indicators from the stored baseline.
     */
    suspend fun updateSymbol(symbol: String): IngestionResult =
        try {
            logger.info("Starting update for $symbol")
            val freshBars = fetchFreshBars(symbol)
            if (freshBars == null) {
                IngestionResult(symbol, true, 0, "No new bars available")
            } else {
                val allSeedQuotes = quoteRepository.getLastNQuotes(symbol, SEED_LOOKBACK)
                val firstFreshDate = freshBars.minOf { it.date }
                val seedQuotes = allSeedQuotes.filter { it.date.isBefore(firstFreshDate) }
                if (seedQuotes.isEmpty()) {
                    IngestionResult(symbol, false, message = "No seed data for indicators")
                } else {
                    val quotes = buildUpdateQuotes(seedQuotes, freshBars)
                    quoteRepository.upsertQuotes(quotes)
                    logger.info("Upserted ${quotes.size} quotes for $symbol")
                    val totalCount = quoteRepository.countBySymbol(symbol)
                    updateStatus(symbol, totalCount, quotes.maxByOrNull { it.date }?.date, IngestionState.COMPLETE)
                    IngestionResult(symbol, true, quotes.size)
                }
            }
        } catch (e: Exception) {
            logger.error("Failed update for $symbol: ${e.message}", e)
            IngestionResult(symbol, false, message = e.message)
        }

    fun initialIngestAll(): Job {
        val symbols = symbolRepository.findAll().map { it.symbol }
        return runParallelInitialIngest("bulk initial", symbols)
    }

    /**
     * Re-runs `initialIngest` only for symbols whose previous attempt left them
     * in `IngestionState.FAILED`. Useful after a partial bulk ingest (e.g. when
     * the upstream daily quota was exhausted mid-run) — finishes the long tail
     * without paying for the symbols that already succeeded.
     */
    fun retryFailedIngests(): Job {
        val failed = ingestionStatusRepository.findByStatus(IngestionState.FAILED).map { it.symbol }
        return runParallelInitialIngest("retry-failed", failed)
    }

    /**
     * Runs initial ingest for every symbol that hasn't successfully completed yet:
     * FAILED, PENDING, PARTIAL, or symbols freshly added to `symbols` without an
     * `ingestion_status` row (e.g. the delisted-bootstrap V6 migration adds 1k+
     * rows with no ingestion state). Single button covers the whole "left to do"
     * pile so a partial ingest run can be resumed in one click.
     */
    fun retryNotComplete(): Job {
        val symbols = ingestionStatusRepository.findNotCompleteSymbols()
        return runParallelInitialIngest("retry-not-complete", symbols)
    }

    /**
     * Runs the initial-ingest pipeline in parallel for an arbitrary symbol list.
     * Exposed so the delisted-ingest path can reuse the same pipeline after
     * persisting its enriched symbol rows.
     */
    fun runParallelInitialIngest(
        label: String,
        symbols: List<String>,
    ): Job {
        logger.info("Starting $label ingest for ${symbols.size} symbols")
        val progress = BulkProgress(total = symbols.size)
        bulkProgress = progress
        val job = Job()
        val semaphore = Semaphore(BULK_INITIAL_PARALLELISM)
        val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + job)
        scope.launch {
            symbols
                .map { symbol ->
                    launch {
                        if (!job.isActive) return@launch
                        semaphore.withPermit { processSymbolIngest(symbol, progress, ::initialIngest) }
                    }
                }.forEach { it.join() }
            logger.info("$label ingest: ${progress.succeeded.get()} succeeded, ${progress.failed.get()} failed")
        }
        return job
    }

    fun updateAll(): Job {
        // Skip delisted symbols — daily fetches against EODHD return empty bar
        // arrays past the delisting date but still consume weighted quota.
        val symbols = ingestionStatusRepository.findActiveByStatus(IngestionState.COMPLETE)
        logger.info("Starting bulk update for ${symbols.size} symbols")
        val progress = BulkProgress(total = symbols.size)
        bulkProgress = progress
        val job = Job()
        val semaphore = Semaphore(BULK_UPDATE_PARALLELISM)
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO + job).launch {
            for (status in symbols) {
                if (!job.isActive) break
                semaphore.withPermit { processSymbolIngest(status.symbol, progress, ::updateSymbol) }
            }
            logger.info("Bulk update: ${progress.succeeded.get()} succeeded, ${progress.failed.get()} failed")
        }
        return job
    }

    private suspend fun fetchInitialBars(symbol: String): List<RawBar>? {
        val raw = ohlcv.getDailyBars(symbol, "full", MIN_DATE) ?: return null
        val holidays = marketHolidayRepository.findHolidayDates()
        var skippedFiller = 0
        var skippedHoliday = 0
        val tradingBars =
            raw.filter { bar ->
                when {
                    bar.volume == 0L -> {
                        skippedFiller++
                        false
                    }
                    bar.date in holidays -> {
                        skippedHoliday++
                        false
                    }
                    else -> true
                }
            }
        if (tradingBars.isNotEmpty()) {
            val parts =
                buildList {
                    if (skippedFiller > 0) add("$skippedFiller synthetic-filler")
                    if (skippedHoliday > 0) add("$skippedHoliday market-holiday")
                }
            val skippedNote = if (parts.isNotEmpty()) " (skipped ${parts.joinToString(" + ")} bars)" else ""
            logger.info("Fetched ${tradingBars.size} OHLCV bars for $symbol$skippedNote")
        }
        return tradingBars.ifEmpty { null }
    }

    private suspend fun buildInitialQuotes(
        symbol: String,
        bars: List<RawBar>,
    ): List<Quote> {
        val (atrMap, adxMap) = fetchOrComputeIndicators(symbol, bars)
        val emas = indicatorCalculator.calculateAllEMAs(bars)
        val donchian = indicatorCalculator.calculateDonchianUpper(bars)
        return bars.mapIndexed { index, bar ->
            buildQuote(
                bar = bar,
                atr = atrMap[bar.date],
                adx = adxMap[bar.date],
                emas = emas,
                index = index,
                donchianUpper5 = donchian.getOrNull(index),
                indicatorSource = IndicatorSource.CALCULATED,
            )
        }
    }

    private suspend fun fetchOrComputeIndicators(
        symbol: String,
        bars: List<RawBar>,
    ): Pair<Map<LocalDate, Double>, Map<LocalDate, Double>> =
        when (indicatorsMode) {
            IndicatorsMode.LOCAL -> {
                val atrSeries = indicatorCalculator.calculateATR(bars)
                val adxSeries = indicatorCalculator.calculateADX(bars)
                mapByDate(bars, atrSeries) to mapByDate(bars, adxSeries)
            }
            IndicatorsMode.API -> {
                val atrMap = indicators.getATR(symbol, MIN_DATE) ?: emptyMap()
                val adxMap = indicators.getADX(symbol, MIN_DATE) ?: emptyMap()
                atrMap to adxMap
            }
        }

    private fun mapByDate(
        bars: List<RawBar>,
        values: List<Double>,
    ): Map<LocalDate, Double> =
        bars
            .zip(values)
            .filter { (_, value) -> value > 0.0 }
            .associate { (bar, value) -> bar.date to value }

    private suspend fun fetchAndSaveSupplementaryData(symbol: String) {
        val earningsList = earnings.getEarnings(symbol)
        if (!earningsList.isNullOrEmpty()) {
            earningsRepository.upsertEarnings(earningsList)
            logger.info("Saved ${earningsList.size} earnings for $symbol")
        }
        val info = companyInfo.getCompanyInfo(symbol)
        if (info?.sector != null) {
            val existingSymbol = symbolRepository.findBySymbol(symbol)
            if (existingSymbol != null) {
                symbolRepository.upsertSymbol(
                    existingSymbol.copy(
                        sector = info.sector,
                        sectorSymbol = SectorMapping.toSectorSymbol(info.sector),
                    ),
                )
            }
        }
    }

    private suspend fun fetchFreshBars(symbol: String): List<RawBar>? {
        val lastBarDate = quoteRepository.getLastBarDate(symbol)
        if (lastBarDate == null) {
            logger.warn("No existing data for $symbol, use initialIngest instead")
            return null
        }
        val fetchFrom = lastBarDate.minusDays(OVERLAP_DAYS)
        // volume=0: synthetic-filler bars on delisted issuers (VCP_STRATEGY_V2 §3.9).
        // date in holidays: phantom provider bars on US market-closed days — single-stock
        // holiday rows otherwise skew downstream breadth queries to 0% on those dates.
        val holidays = marketHolidayRepository.findHolidayDates()
        val freshBars =
            ohlcv
                .getDailyBars(symbol, "compact", fetchFrom)
                ?.filter { !it.date.isBefore(fetchFrom) && it.volume > 0L && it.date !in holidays }
                ?.ifEmpty { null }
        if (freshBars == null) logger.info("No new bars for $symbol")
        return freshBars
    }

    private fun buildUpdateQuotes(
        seedQuotes: List<Quote>,
        freshBars: List<RawBar>,
    ): List<Quote> {
        val lastQuote = seedQuotes.last()
        val emas = indicatorCalculator.extendEMAs(lastQuote.lastEmaValues(), freshBars)
        val atrs =
            indicatorCalculator.extendATR(
                lastAtr = lastQuote.atr?.toDouble() ?: 0.0,
                previousClose = lastQuote.close.toDouble(),
                newBars = freshBars,
            )
        val seedBars = seedQuotes.map { it.toRawBar() }
        val adxValues = indicatorCalculator.extendADX(seedBars, freshBars)
        val donchian =
            indicatorCalculator.extendDonchianUpper(
                seedBars.takeLast(IndicatorCalculator.DONCHIAN_PERIOD - 1),
                freshBars,
            )
        return freshBars.mapIndexed { index, bar ->
            buildQuote(
                bar,
                atrs.getOrNull(index),
                adxValues.getOrNull(index),
                emas,
                index,
                donchian.getOrNull(index),
                IndicatorSource.CALCULATED,
            )
        }
    }

    private fun buildQuote(
        bar: RawBar,
        atr: Double?,
        adx: Double?,
        emas: Map<Int, List<Double>>,
        index: Int,
        donchianUpper5: Double?,
        indicatorSource: IndicatorSource,
    ): Quote =
        Quote(
            symbol = bar.symbol,
            date = bar.date,
            open = bar.open.toBigDecimal4(),
            high = bar.high.toBigDecimal4(),
            low = bar.low.toBigDecimal4(),
            close = bar.close.toBigDecimal4(),
            volume = bar.volume,
            atr = atr?.toBigDecimal4(),
            adx = adx?.toBigDecimal4(),
            ema5 = emas[5]?.getOrNull(index)?.toBigDecimal4(),
            ema10 = emas[10]?.getOrNull(index)?.toBigDecimal4(),
            ema20 = emas[20]?.getOrNull(index)?.toBigDecimal4(),
            ema50 = emas[50]?.getOrNull(index)?.toBigDecimal4(),
            ema100 = emas[100]?.getOrNull(index)?.toBigDecimal4(),
            ema200 = emas[200]?.getOrNull(index)?.toBigDecimal4(),
            donchianUpper5 = donchianUpper5?.toBigDecimal4(),
            indicatorSource = indicatorSource,
        )

    private suspend fun processSymbolIngest(
        symbol: String,
        progress: BulkProgress,
        action: suspend (String) -> IngestionResult,
    ) {
        try {
            val result = action(symbol)
            if (result.success) {
                progress.succeeded.incrementAndGet()
            } else {
                progress.failed.incrementAndGet()
                progress.errors[symbol] = result.message ?: "Unknown error"
            }
        } catch (e: Exception) {
            progress.failed.incrementAndGet()
            progress.errors[symbol] = e.message ?: "Unknown error"
        }
        progress.completed.incrementAndGet()
    }

    private fun Quote.lastEmaValues(): Map<Int, Double> =
        mapOf(
            5 to (ema5?.toDouble() ?: 0.0),
            10 to (ema10?.toDouble() ?: 0.0),
            20 to (ema20?.toDouble() ?: 0.0),
            50 to (ema50?.toDouble() ?: 0.0),
            100 to (ema100?.toDouble() ?: 0.0),
            200 to (ema200?.toDouble() ?: 0.0),
        )

    private fun Quote.toRawBar(): RawBar = RawBar(symbol, date, open.toDouble(), high.toDouble(), low.toDouble(), close.toDouble(), volume)

    private fun updateStatus(
        symbol: String,
        barCount: Int,
        lastBarDate: LocalDate?,
        status: IngestionState,
    ) {
        ingestionStatusRepository.upsert(symbol, barCount, lastBarDate, status)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(IngestionService::class.java)

        // Pull from 1995 so a 2000-start backtest has 5 years of indicator warmup baked in.
        // Providers naturally clip to whatever data they actually have (most names ~1997+).
        private val MIN_DATE: LocalDate = LocalDate.of(1995, 1, 1)
        private const val SEED_LOOKBACK = 250
        private const val OVERLAP_DAYS = 5L
        private const val BULK_UPDATE_PARALLELISM = 10
        private const val BULK_INITIAL_PARALLELISM = 10
    }
}

data class BulkProgress(
    val total: Int,
    val completed: AtomicInteger = AtomicInteger(0),
    val succeeded: AtomicInteger = AtomicInteger(0),
    val failed: AtomicInteger = AtomicInteger(0),
    val errors: ConcurrentHashMap<String, String> = ConcurrentHashMap(),
    val logLines: java.util.concurrent.ConcurrentLinkedDeque<String> = java.util.concurrent.ConcurrentLinkedDeque(),
) {
    fun log(message: String) {
        val timestamp = LocalDateTime.now().format(LOG_TIMESTAMP_FORMAT)
        logLines.addLast("$timestamp - $message")
        while (logLines.size > MAX_LOG_LINES) logLines.pollFirst()
    }

    companion object {
        private const val MAX_LOG_LINES = 500
        private val LOG_TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
