package com.skrymer.midgaard.service

import com.skrymer.midgaard.integration.edgar.EdgarClient
import com.skrymer.midgaard.integration.eodhd.EodhdFundamentalsClient
import com.skrymer.midgaard.integration.eodhd.EodhdSymbolListClient
import com.skrymer.midgaard.integration.eodhd.dto.EodhdDelistedSymbolDto
import com.skrymer.midgaard.model.AssetType
import com.skrymer.midgaard.model.SectorMapping
import com.skrymer.midgaard.model.Symbol
import com.skrymer.midgaard.repository.SymbolRepository
import com.skrymer.midgaard.service.sector.SicToGicsMapping
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * One-shot bootstrap that adds delisted US tickers to Midgaard's catalogue so
 * downstream backtests can include them in pre-2010 windows where survivorship
 * bias is severe.
 *
 * Pipeline:
 *   1. Pull every delisted "Common Stock" symbol from EODHD's catalogue.
 *   2. Drop bottom-20% by 6-month pre-delist median dollar volume.
 *   3. For each survivor, look up CIK + delisting date via the cached
 *      `EodhdFundamentalsClient`. Look up SIC via `EdgarClient`. Resolve sector
 *      via `SicToGicsMapping`, falling back to `INDUSTRIALS` when CIK or SIC
 *      are missing — partial sector data shouldn't fail the ingest.
 *   4. Persist each enriched row into `symbols`.
 *   5. Hand the symbol list to `IngestionService.runParallelInitialIngest()` —
 *      same pipeline that ingests live symbols, so OHLCV + locally-recomputed
 *      ATR/ADX/EMAs land via the existing path.
 *
 * Lives outside `IngestionService` to keep that service provider-agnostic. The
 * delisted-symbol catalogue and SEC EDGAR aren't toggleable providers, so they
 * don't fit the `app.ingest.provider` interface model.
 */
@Service
class DelistedIngestionService(
    private val symbolListClient: EodhdSymbolListClient,
    private val fundamentalsClient: EodhdFundamentalsClient,
    private val edgarClient: EdgarClient,
    private val liquidityFilter: DelistedLiquidityFilter,
    private val symbolRepository: SymbolRepository,
    private val ingestionService: IngestionService,
) {
    @Volatile
    private var _lastRunStats: DelistedRunStats? = null
    val lastRunStats: DelistedRunStats? get() = _lastRunStats

    /**
     * Kicks off the delisted-ingest pipeline. Returns a `Job` so the caller
     * (REST controller) can return to the user immediately while ingest
     * continues on a background coroutine — same pattern as
     * `IngestionService.initialIngestAll()`.
     */
    fun ingestDelisted(): Job {
        val job = Job()
        val scope = CoroutineScope(Dispatchers.IO + job)
        scope.launch {
            try {
                runPipeline()
            } catch (e: Exception) {
                logger.error("Delisted ingest pipeline failed: ${e.message}", e)
            }
        }
        return job
    }

    internal suspend fun runPipeline() {
        logger.info("Starting delisted ingest")
        val rawList = symbolListClient.getDelistedSymbols()
        if (rawList == null) {
            logger.warn("Delisted symbol list fetch failed — aborting")
            _lastRunStats = DelistedRunStats(failed = true)
            return
        }
        val commonStock = rawList.filter { isCommonStock(it) }
        logger.info("Delisted catalogue: ${rawList.size} rows, ${commonStock.size} are Common Stock")

        val candidates = enrichCandidates(commonStock)
        logger.info("Enriched ${candidates.size} delisted candidates with delisting metadata")

        val survivors = liquidityFilter.filter(candidates)
        logger.info("Liquidity filter kept ${survivors.size} of ${candidates.size}")

        val persisted = persistSymbols(survivors, commonStock)
        logger.info("Persisted ${persisted.size} delisted symbols; handing off to bulk-initial ingest")

        ingestionService.runParallelInitialIngest("delisted-bootstrap", persisted)

        _lastRunStats =
            DelistedRunStats(
                catalogueSize = rawList.size,
                commonStockCount = commonStock.size,
                enrichedCount = candidates.size,
                survivorCount = survivors.size,
                persistedCount = persisted.size,
                failed = false,
            )
    }

    private suspend fun enrichCandidates(commonStock: List<EodhdDelistedSymbolDto>): List<DelistedCandidate> =
        commonStock.mapNotNull { dto ->
            val fundamentals = fundamentalsClient.fetch(dto.code, "${dto.code}.US")
            val delistedDate = fundamentals?.general?.parsedDelistedDate ?: return@mapNotNull null
            DelistedCandidate(
                symbol = dto.code,
                delistedDate = delistedDate,
                name = dto.name,
            )
        }

    private suspend fun persistSymbols(
        survivors: List<DelistedCandidate>,
        commonStock: List<EodhdDelistedSymbolDto>,
    ): List<String> {
        // Re-look up the fundamentals so we have CIK alongside delisting date.
        // The `EodhdFundamentalsClient` cache makes this free — same key.
        val byCode = commonStock.associateBy { it.code }
        return survivors.mapNotNull { candidate ->
            val dto = byCode[candidate.symbol] ?: return@mapNotNull null
            val fundamentals = fundamentalsClient.fetch(candidate.symbol, "${candidate.symbol}.US")
            val cik = fundamentals?.general?.cik
            val sector = resolveSector(cik)
            val symbol =
                Symbol(
                    symbol = candidate.symbol,
                    assetType = inferAssetType(dto),
                    sector = sector,
                    sectorSymbol = SectorMapping.toSectorSymbol(sector),
                    delistedAt = candidate.delistedDate,
                    cik = cik,
                )
            symbolRepository.upsertSymbol(symbol)
            candidate.symbol
        }
    }

    /**
     * SIC → sector lookup, falling back to "INDUSTRIALS" when CIK is missing
     * (foreign issuers, ticker reuses) or EDGAR returns no SIC for the CIK.
     * Logs a warn so we can audit how many delistings hit the fallback.
     */
    private suspend fun resolveSector(cik: String?): String {
        if (cik.isNullOrBlank()) {
            logger.warn("No CIK available for delisted symbol — defaulting sector to INDUSTRIALS")
            return SECTOR_FALLBACK
        }
        val submission = edgarClient.getSubmission(cik)
        val sic = submission?.sicCode
        if (sic == null) {
            logger.warn("No SIC from EDGAR for CIK $cik — defaulting sector to INDUSTRIALS")
            return SECTOR_FALLBACK
        }
        return SicToGicsMapping.gicsSectorFor(sic) ?: SECTOR_FALLBACK
    }

    private fun isCommonStock(dto: EodhdDelistedSymbolDto): Boolean = dto.type.equals("Common Stock", ignoreCase = true)

    private fun inferAssetType(dto: EodhdDelistedSymbolDto): AssetType =
        when {
            dto.type.equals("ETF", ignoreCase = true) -> AssetType.ETF
            else -> AssetType.STOCK
        }

    companion object {
        private val logger = LoggerFactory.getLogger(DelistedIngestionService::class.java)
        private const val SECTOR_FALLBACK = "INDUSTRIALS"
    }
}

/**
 * Per-run summary for diagnostics + UI display. Reset on each `ingestDelisted`
 * invocation; null before the first run.
 */
data class DelistedRunStats(
    val catalogueSize: Int = 0,
    val commonStockCount: Int = 0,
    val enrichedCount: Int = 0,
    val survivorCount: Int = 0,
    val persistedCount: Int = 0,
    val startedAt: LocalDate = LocalDate.now(),
    val failed: Boolean = false,
)
