package com.skrymer.midgaard.service

import com.skrymer.midgaard.integration.edgar.EdgarClient
import com.skrymer.midgaard.integration.eodhd.EodhdFundamentalsClient
import com.skrymer.midgaard.integration.eodhd.EodhdSymbolListClient
import com.skrymer.midgaard.integration.eodhd.dto.EodhdDelistedSymbolDto
import com.skrymer.midgaard.integration.eodhd.dto.EodhdFundamentalsResponse
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

/**
 * Discovers and persists ~1,500 delisted US tickers across all 11 sectors so
 * Midgaard's symbol catalogue covers historical delistings. The OHLCV/indicator
 * fetch happens later through the existing initial-ingest path — discovery and
 * ingestion are intentionally decoupled so a half-finished discovery doesn't
 * leave the catalogue in a weird mid-state.
 *
 * Pipeline:
 *   1. Pull EODHD's catalogue (~50k delisted US rows).
 *   2. Filter Common Stock on major exchanges with sane tickers — drops ETFs,
 *      pink-sheet pump-and-dumps, and weird code formats with one cheap pass.
 *   3. Walk the survivors alphabetically; for each, fetch CIK + delistedDate
 *      via cached `EodhdFundamentalsClient` and SIC via `EdgarClient`.
 *   4. Bucket by sector. Each bucket caps at `perSectorCap` (~137) so we get
 *      sector spread instead of pure liquidity skew. Stop the walk when all
 *      buckets are full OR we hit the safety budget on fundamentals fetches.
 *   5. Persist accumulated symbols into `symbols` with `delisted_at`, `cik`,
 *      `sector` populated. No OHLCV fetch — that's `IngestionService`'s job.
 *
 * Budget: ~4,000 fundamentals × 10 weighted = 40k EODHD weighted/run, plus
 * ~4,000 EDGAR calls (separate quota at 10/sec).
 */
@Service
class DelistedIngestionService(
    private val symbolListClient: EodhdSymbolListClient,
    private val fundamentalsClient: EodhdFundamentalsClient,
    private val edgarClient: EdgarClient,
    private val symbolRepository: SymbolRepository,
) {
    @Volatile
    private var _lastRunStats: DelistedRunStats? = null
    val lastRunStats: DelistedRunStats? get() = _lastRunStats

    fun discoverDelisted(config: Config = Config()): Job {
        val job = Job()
        val scope = CoroutineScope(Dispatchers.IO + job)
        scope.launch {
            try {
                runPipeline(config)
            } catch (e: Exception) {
                logger.error("Delisted discovery failed: ${e.message}", e)
                _lastRunStats = DelistedRunStats(failed = true)
            }
        }
        return job
    }

    internal suspend fun runPipeline(config: Config = Config()) {
        logger.info("Starting delisted discovery (target ${config.totalCap} symbols)")
        val rawList = symbolListClient.getDelistedSymbols()
        if (rawList == null) {
            logger.warn("Delisted symbol list fetch failed — aborting")
            _lastRunStats = DelistedRunStats(failed = true)
            return
        }
        val candidates = rawList.filter { isViableCandidate(it) }.sortedBy { it.code }
        logger.info("Catalogue ${rawList.size} rows -> ${candidates.size} viable candidates")

        val survivors = walkAndBucket(candidates, config)

        _lastRunStats =
            DelistedRunStats(
                catalogueSize = rawList.size,
                viableCount = candidates.size,
                fundamentalsFetched = survivors.fetchesUsed,
                survivorCount = survivors.bucketed.size,
                persistedCount = survivors.bucketed.size,
                bySector = survivors.bucketed.groupingBy { it.sector }.eachCount(),
                failed = false,
            )
        logger.info(
            "Discovery complete: persisted ${survivors.bucketed.size} delisted symbols " +
                "after ${survivors.fetchesUsed} fundamentals fetches",
        )
    }

    private fun isViableCandidate(dto: EodhdDelistedSymbolDto): Boolean =
        dto.type.equals("Common Stock", ignoreCase = true) &&
            dto.exchange in MAJOR_EXCHANGES &&
            TICKER_PATTERN.matches(dto.code)

    /**
     * Walks the candidate list, enriching each with fundamentals + EDGAR until
     * either every sector bucket is full, the total cap is reached, or we
     * exhaust the safety budget on fundamentals fetches.
     */
    private suspend fun walkAndBucket(
        candidates: List<EodhdDelistedSymbolDto>,
        config: Config,
    ): WalkResult {
        val buckets: MutableMap<String, MutableList<EnrichedSymbol>> = mutableMapOf()
        var fetchesUsed = 0
        for (dto in candidates) {
            if (shouldStopWalk(buckets, fetchesUsed, config)) break
            // Each attempt costs ~10 weighted EODHD calls regardless of whether
            // we end up using the result, so count attempts for the budget.
            fetchesUsed++
            val enriched = enrich(dto)
            if (enriched != null) addToBucketIfRoom(buckets, enriched, config)
            if (fetchesUsed % PROGRESS_LOG_INTERVAL == 0) {
                logger.info("Walk progress: $fetchesUsed fetches, ${totalAccepted(buckets)} persisted across ${buckets.size} sectors")
            }
        }
        return WalkResult(buckets.values.flatten(), fetchesUsed)
    }

    private fun shouldStopWalk(
        buckets: Map<String, List<EnrichedSymbol>>,
        fetchesUsed: Int,
        config: Config,
    ): Boolean =
        totalAccepted(buckets) >= config.totalCap ||
            fetchesUsed >= config.fundamentalsBudget ||
            allSectorsAtCap(buckets, config)

    private fun addToBucketIfRoom(
        buckets: MutableMap<String, MutableList<EnrichedSymbol>>,
        enriched: EnrichedSymbol,
        config: Config,
    ) {
        val bucket = buckets.getOrPut(enriched.sector) { mutableListOf() }
        if (bucket.size < config.perSectorCap) {
            bucket.add(enriched)
            // Persist eagerly so a long-running walk surfaces partial results
            // in the DB. Future Claude/agents can re-resume by retriggering
            // and the @Cacheable on EodhdFundamentalsClient + ON CONFLICT DO
            // UPDATE keep it idempotent.
            symbolRepository.upsertSymbol(toSymbol(enriched))
        }
    }

    private suspend fun enrich(dto: EodhdDelistedSymbolDto): EnrichedSymbol? {
        val fundamentals = fundamentalsClient.fetch(dto.code, "${dto.code}.US") ?: return null
        val delistedDate = fundamentals.general?.parsedDelistedDate ?: return null
        val cik = fundamentals.general.cik
        val sector = resolveSector(cik)
        return EnrichedSymbol(dto, fundamentals, delistedDate, cik, sector)
    }

    private suspend fun resolveSector(cik: String?): String {
        val sic = cik?.takeIf { it.isNotBlank() }?.let { edgarClient.getSubmission(it)?.sicCode }
        return sic?.let { SicToGicsMapping.gicsSectorFor(it) } ?: SECTOR_FALLBACK
    }

    private fun toSymbol(enriched: EnrichedSymbol): Symbol =
        Symbol(
            symbol = enriched.dto.code,
            assetType = AssetType.STOCK,
            sector = enriched.sector,
            sectorSymbol = SectorMapping.toSectorSymbol(enriched.sector),
            delistedAt = enriched.delistedDate,
            cik = enriched.cik,
        )

    private fun totalAccepted(buckets: Map<String, List<EnrichedSymbol>>): Int = buckets.values.sumOf { it.size }

    private fun allSectorsAtCap(
        buckets: Map<String, List<EnrichedSymbol>>,
        config: Config,
    ): Boolean = ALL_SECTORS.all { (buckets[it]?.size ?: 0) >= config.perSectorCap }

    data class Config(
        val totalCap: Int = 1_500,
        val perSectorCap: Int = 150,
        val fundamentalsBudget: Int = 4_000,
    )

    private data class EnrichedSymbol(
        val dto: EodhdDelistedSymbolDto,
        val fundamentals: EodhdFundamentalsResponse,
        val delistedDate: java.time.LocalDate,
        val cik: String?,
        val sector: String,
    )

    private data class WalkResult(
        val bucketed: List<EnrichedSymbol>,
        val fetchesUsed: Int,
    )

    companion object {
        private val logger = LoggerFactory.getLogger(DelistedIngestionService::class.java)
        private const val SECTOR_FALLBACK = "INDUSTRIALS"
        private const val PROGRESS_LOG_INTERVAL = 100

        // EODHD reports both NYSE and NYSE ARCA distinctly — keep both.
        private val MAJOR_EXCHANGES = setOf("NYSE", "NASDAQ", "AMEX", "NYSE MKT", "NYSE ARCA", "BATS", "ARCA")

        // 1-5 uppercase letters, optional dash + 1-2 letter class share suffix (e.g. BRK-B).
        private val TICKER_PATTERN = Regex("^[A-Z]{1,5}(-[A-Z]{1,2})?$")

        // The 11 sectors used by `SectorMapping`. Order doesn't matter — this is just
        // the set we check for "all buckets full" termination.
        private val ALL_SECTORS =
            setOf(
                "TECHNOLOGY",
                "FINANCIAL SERVICES",
                "HEALTHCARE",
                "ENERGY",
                "INDUSTRIALS",
                "CONSUMER CYCLICAL",
                "CONSUMER DEFENSIVE",
                "COMMUNICATION SERVICES",
                "BASIC MATERIALS",
                "REAL ESTATE",
                "UTILITIES",
            )
    }
}

data class DelistedRunStats(
    val catalogueSize: Int = 0,
    val viableCount: Int = 0,
    val fundamentalsFetched: Int = 0,
    val survivorCount: Int = 0,
    val persistedCount: Int = 0,
    val bySector: Map<String, Int> = emptyMap(),
    val failed: Boolean = false,
)
