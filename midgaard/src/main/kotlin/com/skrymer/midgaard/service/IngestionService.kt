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
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@Service
class IngestionService(
    @Qualifier("alphaVantageOhlcv") private val alphaVantageOhlcv: OhlcvProvider,
    @Qualifier("massiveOhlcv") private val massiveOhlcv: OhlcvProvider,
    private val indicatorProvider: IndicatorProvider,
    private val earningsProvider: EarningsProvider,
    private val companyInfoProvider: CompanyInfoProvider,
    private val rateLimiterService: RateLimiterService,
    private val indicatorCalculator: IndicatorCalculator,
    private val quoteRepository: QuoteRepository,
    private val earningsRepository: EarningsRepository,
    private val symbolRepository: SymbolRepository,
    private val ingestionStatusRepository: IngestionStatusRepository,
) {
    @Volatile
    var bulkProgress: BulkProgress? = null

    /**
     * Initial ingest — uses AlphaVantage for OHLCV + ATR + ADX, computes EMAs + Donchian locally.
     */
    suspend fun initialIngest(symbol: String): IngestionResult =
        try {
            logger.info("Starting initial ingest for $symbol")
            val bars = fetchInitialBars(symbol)
            if (bars == null) {
                updateStatus(symbol, 0, null, IngestionState.FAILED)
                IngestionResult(symbol, false, message = "No OHLCV data from AlphaVantage")
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
     * Daily update — uses Massive for recent OHLCV, extends indicators from baseline.
     */
    suspend fun updateSymbol(symbol: String): IngestionResult =
        try {
            logger.info("Starting update for $symbol")
            val freshBars = fetchFreshBars(symbol)
            if (freshBars == null) {
                IngestionResult(symbol, true, 0, "No new bars available")
            } else {
                val seedQuotes = quoteRepository.getLastNQuotes(symbol, SEED_LOOKBACK)
                if (seedQuotes.isEmpty()) {
                    IngestionResult(symbol, false, message = "No seed data for indicators")
                } else {
                    val quotes = buildUpdateQuotes(seedQuotes, freshBars)
                    quoteRepository.upsertQuotes(quotes)
                    logger.info("Saved ${quotes.size} new quotes for $symbol")
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
        val symbols = symbolRepository.findAll().filter { it.assetType.name == "STOCK" }
        logger.info("Starting bulk initial ingest for ${symbols.size} symbols")
        val progress = BulkProgress(total = symbols.size)
        bulkProgress = progress
        val job = Job()
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO + job).launch {
            for (symbol in symbols) {
                if (!job.isActive) break
                processSymbolIngest(symbol.symbol, progress, ::initialIngest)
            }
            logger.info("Bulk initial ingest: ${progress.succeeded.get()} succeeded, ${progress.failed.get()} failed")
        }
        return job
    }

    fun updateAll(): Job {
        val symbols = ingestionStatusRepository.findByStatus(IngestionState.COMPLETE)
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
        rateLimiterService.acquirePermit("alphavantage")
        val bars = alphaVantageOhlcv.getDailyBars(symbol, "full", MIN_DATE)
        if (!bars.isNullOrEmpty()) {
            logger.info("Fetched ${bars.size} OHLCV bars for $symbol from AlphaVantage")
        }
        return bars?.ifEmpty { null }
    }

    private suspend fun buildInitialQuotes(
        symbol: String,
        bars: List<RawBar>,
    ): List<Quote> {
        rateLimiterService.acquirePermit("alphavantage")
        val atrMap = indicatorProvider.getATR(symbol, MIN_DATE) ?: emptyMap()
        rateLimiterService.acquirePermit("alphavantage")
        val adxMap = indicatorProvider.getADX(symbol, MIN_DATE) ?: emptyMap()
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
                indicatorSource = if (atrMap.containsKey(bar.date)) IndicatorSource.ALPHAVANTAGE else IndicatorSource.CALCULATED,
            )
        }
    }

    private suspend fun fetchAndSaveSupplementaryData(symbol: String) {
        rateLimiterService.acquirePermit("alphavantage")
        val earnings = earningsProvider.getEarnings(symbol)
        if (!earnings.isNullOrEmpty()) {
            earningsRepository.upsertEarnings(earnings)
            logger.info("Saved ${earnings.size} earnings for $symbol")
        }
        rateLimiterService.acquirePermit("alphavantage")
        val companyInfo = companyInfoProvider.getCompanyInfo(symbol)
        if (companyInfo?.sector != null) {
            val existingSymbol = symbolRepository.findBySymbol(symbol)
            if (existingSymbol != null) {
                symbolRepository.upsertSymbol(
                    existingSymbol.copy(
                        sector = companyInfo.sector,
                        sectorSymbol = SectorMapping.toSectorSymbol(companyInfo.sector),
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
        rateLimiterService.acquirePermit("massive")
        val freshBars =
            massiveOhlcv
                .getDailyBars(symbol, "compact", lastBarDate.minusDays(5))
                ?.filter { it.date.isAfter(lastBarDate) }
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
        private val MIN_DATE: LocalDate = LocalDate.of(2000, 1, 1)
        private const val SEED_LOOKBACK = 250
        private const val BULK_UPDATE_PARALLELISM = 10
    }
}

data class BulkProgress(
    val total: Int,
    val completed: AtomicInteger = AtomicInteger(0),
    val succeeded: AtomicInteger = AtomicInteger(0),
    val failed: AtomicInteger = AtomicInteger(0),
    val errors: ConcurrentHashMap<String, String> = ConcurrentHashMap(),
)
