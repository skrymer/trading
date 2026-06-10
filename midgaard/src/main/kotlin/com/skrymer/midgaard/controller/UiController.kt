package com.skrymer.midgaard.controller

import com.skrymer.midgaard.integrity.DataIntegrityService
import com.skrymer.midgaard.integrity.Severity
import com.skrymer.midgaard.model.AssetType
import com.skrymer.midgaard.model.IngestionState
import com.skrymer.midgaard.repository.IngestionStatusRepository
import com.skrymer.midgaard.repository.QuoteRepository
import com.skrymer.midgaard.repository.SymbolRepository
import com.skrymer.midgaard.repository.TreasuryYieldRepository
import com.skrymer.midgaard.service.ApiKeyService
import com.skrymer.midgaard.service.DelistedIngestionService
import com.skrymer.midgaard.service.IngestionService
import com.skrymer.midgaard.service.OvtlyrBackfillService
import com.skrymer.midgaard.service.ProviderRateLimitStats
import com.skrymer.midgaard.service.QualityPercentileService
import com.skrymer.midgaard.service.RateLimiterService
import com.skrymer.midgaard.service.RelativeStrengthService
import com.skrymer.midgaard.service.TreasuryYieldIngestionService
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes

// One handler per UI page/action — the function count tracks the number of screens and
// triggers, not class complexity, so the TooManyFunctions heuristic doesn't apply here.
@Suppress("TooManyFunctions")
@Controller
@ConditionalOnProperty("app.ui.enabled", havingValue = "true", matchIfMissing = true)
class UiController(
    private val symbolRepository: SymbolRepository,
    private val quoteRepository: QuoteRepository,
    private val ingestionStatusRepository: IngestionStatusRepository,
    private val ingestionService: IngestionService,
    private val delistedIngestionService: DelistedIngestionService,
    private val rateLimiterService: RateLimiterService,
    private val apiKeyService: ApiKeyService,
    private val dataIntegrityService: DataIntegrityService,
    private val ovtlyrBackfillService: OvtlyrBackfillService,
    private val treasuryYieldIngestionService: TreasuryYieldIngestionService,
    private val treasuryYieldRepository: TreasuryYieldRepository,
    private val relativeStrengthService: RelativeStrengthService,
    private val qualityPercentileService: QualityPercentileService,
    @param:Value("\${alphavantage.api.baseUrl}") private val avBaseUrl: String,
    @param:Value("\${massive.api.baseUrl:}") private val massiveBaseUrl: String,
    @param:Value("\${finnhub.api.baseUrl:https://finnhub.io}") private val finnhubBaseUrl: String,
    @param:Value("\${eodhd.api.baseUrl:https://eodhd.com/api}") private val eodhdBaseUrl: String,
) {
    private val logger = LoggerFactory.getLogger(UiController::class.java)

    @GetMapping("/")
    fun dashboard(model: Model): String {
        model.addAttribute("totalStocks", symbolRepository.countByAssetType(AssetType.STOCK))
        model.addAttribute("totalQuotes", quoteRepository.getTotalQuoteCount())
        model.addAttribute("pendingCount", ingestionStatusRepository.countByStatus(IngestionState.PENDING))
        model.addAttribute("completeCount", ingestionStatusRepository.countByStatus(IngestionState.COMPLETE))
        model.addAttribute("failedCount", ingestionStatusRepository.countByStatus(IngestionState.FAILED))
        model.addAttribute("rateLimits", rateLimiterService.getAllProviderStats())
        model.addAttribute("lastUpdated", ingestionStatusRepository.getLastUpdated())
        return "dashboard"
    }

    @GetMapping("/symbols")
    fun symbolList(
        model: Model,
        @RequestParam(required = false) status: String?,
    ): String {
        val statuses = ingestionStatusRepository.findAll().associateBy { it.symbol }
        val symbols = symbolRepository.findAll()

        data class SymbolRow(
            val symbol: String,
            val assetType: String,
            val sector: String?,
            val barCount: Int,
            val lastBarDate: String?,
            val status: String,
            val lastIngested: String?,
        )

        val rows =
            symbols
                .map { sym ->
                    val ingestion = statuses[sym.symbol]
                    SymbolRow(
                        symbol = sym.symbol,
                        assetType = sym.assetType.name,
                        sector = sym.sector,
                        barCount = ingestion?.barCount ?: 0,
                        lastBarDate = ingestion?.lastBarDate?.toString(),
                        status = ingestion?.status?.name ?: "PENDING",
                        lastIngested = ingestion?.lastIngested?.toString()?.take(19),
                    )
                }.let { rows ->
                    if (status != null) rows.filter { it.status == status.uppercase() } else rows
                }

        model.addAttribute("symbols", rows)
        model.addAttribute("filterStatus", status)
        model.addAttribute("totalCount", symbols.size)
        return "symbols"
    }

    @GetMapping("/symbols/{symbol}")
    fun symbolDetail(
        model: Model,
        @PathVariable symbol: String,
    ): String {
        val sym = symbolRepository.findBySymbol(symbol.uppercase())
        val ingestion = ingestionStatusRepository.findBySymbol(symbol.uppercase())
        val quoteCount = quoteRepository.countBySymbol(symbol.uppercase())
        val lastQuotes = quoteRepository.getLastNQuotes(symbol.uppercase(), 5)

        model.addAttribute("symbol", sym)
        model.addAttribute("ingestion", ingestion)
        model.addAttribute("quoteCount", quoteCount)
        model.addAttribute("lastQuotes", lastQuotes)
        return "symbol-detail"
    }

    @GetMapping("/ingestion")
    fun ingestionProgress(model: Model): String {
        val progress = ingestionService.bulkProgress
        model.addAttribute("progress", progress)
        model.addAttribute("active", progress != null)
        model.addAttribute("failedCount", ingestionStatusRepository.countByStatus(IngestionState.FAILED))
        model.addAttribute("notCompleteCount", ingestionStatusRepository.countNotComplete())
        model.addAttribute("delistedRunStats", delistedIngestionService.lastRunStats)
        model.addAttribute("violationCount", dataIntegrityService.violationCount())
        model.addAttribute("ovtlyrConfigured", apiKeyService.getStatus()["ovtlyrConfigured"] ?: false)
        model.addAttribute("ovtlyrProgress", ovtlyrBackfillService.progress)
        model.addAttribute("relativeStrengthActive", relativeStrengthService.isRecomputeActive())
        model.addAttribute("relativeStrengthLastRun", relativeStrengthService.lastRunRowsWritten())
        model.addAttribute("qualityPercentileActive", qualityPercentileService.isRecomputeActive())
        model.addAttribute("qualityPercentileLastRun", qualityPercentileService.lastRunRowsWritten())
        model.addAttribute("treasuryYieldStatus", treasuryYieldRepository.status(TreasuryYieldIngestionService.MATURITY_US3M))
        return "ingestion"
    }

    @GetMapping("/integrity")
    fun integrity(model: Model): String {
        // ViolationRepository.findAll() already sorts by Severity.ordinal — no resort needed here.
        val violations = dataIntegrityService.latestViolations()
        model.addAttribute("violations", violations)
        model.addAttribute("violationCount", violations.size)
        model.addAttribute("criticalCount", violations.count { it.severity == Severity.CRITICAL })
        return "integrity"
    }

    @PostMapping("/integrity/validate")
    fun runValidationFromUi(): String {
        dataIntegrityService.runAll()
        return "redirect:/integrity"
    }

    @PostMapping("/ingestion/initial/all")
    fun startInitialIngestAll(): String {
        ingestionService.initialIngestAll()
        return "redirect:/ingestion"
    }

    @PostMapping("/ingestion/initial/failed")
    fun startRetryFailedIngests(): String {
        ingestionService.retryFailedIngests()
        return "redirect:/ingestion"
    }

    @PostMapping("/ingestion/initial/not-complete")
    fun startRetryNotComplete(): String {
        ingestionService.retryNotComplete()
        return "redirect:/ingestion"
    }

    @PostMapping("/ingestion/update/all")
    fun startUpdateAll(): String {
        ingestionService.updateAll()
        return "redirect:/ingestion"
    }

    @PostMapping("/ingestion/delisted/discover")
    fun startDelistedDiscovery(): String {
        delistedIngestionService.discoverDelisted()
        return "redirect:/ingestion"
    }

    @PostMapping("/ingestion/recompute-relative-strength")
    fun startRelativeStrengthRecompute(): String {
        relativeStrengthService.recomputeAllAsync()
        return "redirect:/ingestion"
    }

    @PostMapping("/ingestion/recompute-quality-percentile")
    fun startQualityPercentileRecompute(): String {
        qualityPercentileService.recomputeAllAsync()
        return "redirect:/ingestion"
    }

    @PostMapping("/ingestion/initial/{symbol}")
    fun startInitialIngest(
        @PathVariable symbol: String,
    ): String {
        runBlocking { ingestionService.initialIngest(symbol.uppercase()) }
        return "redirect:/symbols/${symbol.uppercase()}"
    }

    @PostMapping("/ingestion/update/{symbol}")
    fun startUpdate(
        @PathVariable symbol: String,
    ): String {
        runBlocking { ingestionService.updateSymbol(symbol.uppercase()) }
        return "redirect:/symbols/${symbol.uppercase()}"
    }

    @GetMapping("/providers")
    fun providers(model: Model): String {
        val stats = rateLimiterService.getAllProviderStats()
        val maskedKeys = apiKeyService.getMaskedKeys()

        val providers =
            listOf(
                ProviderViewModel(
                    name = "AlphaVantage",
                    baseUrl = avBaseUrl,
                    maskedApiKey = maskedKeys["alphaVantage"] ?: "Not configured",
                    stats = stats["alphavantage"],
                ),
                ProviderViewModel(
                    name = "Massive (Polygon)",
                    baseUrl = massiveBaseUrl,
                    maskedApiKey = maskedKeys["massive"] ?: "Not configured",
                    stats = stats["massive"],
                ),
                ProviderViewModel(
                    name = "Finnhub",
                    baseUrl = finnhubBaseUrl,
                    maskedApiKey = maskedKeys["finnhub"] ?: "Not configured",
                    stats = stats["finnhub"],
                ),
                ProviderViewModel(
                    name = "EODHD",
                    baseUrl = eodhdBaseUrl,
                    maskedApiKey = maskedKeys["eodhd"] ?: "Not configured",
                    stats = stats["eodhd"],
                ),
            )

        model.addAttribute("providers", providers)
        model.addAttribute("ovtlyrConfigured", apiKeyService.getStatus()["ovtlyrConfigured"] ?: false)
        model.addAttribute("ovtlyrCookieUserIdMasked", maskedKeys["ovtlyrCookieUserId"] ?: "Not configured")
        model.addAttribute("ovtlyrCookieTokenMasked", maskedKeys["ovtlyrCookieToken"] ?: "Not configured")
        model.addAttribute("ovtlyrProjectIdMasked", maskedKeys["ovtlyrProjectId"] ?: "Not configured")
        return "providers"
    }

    @PostMapping("/providers")
    fun saveApiKeys(
        @RequestParam alphaVantageApiKey: String?,
        @RequestParam massiveApiKey: String?,
        @RequestParam finnhubApiKey: String?,
        @RequestParam eodhdApiKey: String?,
        redirectAttributes: RedirectAttributes,
    ): String {
        val avKey = alphaVantageApiKey?.takeIf { it.isNotBlank() && !it.startsWith("•") }
        val massiveKey = massiveApiKey?.takeIf { it.isNotBlank() && !it.startsWith("•") }
        val fhKey = finnhubApiKey?.takeIf { it.isNotBlank() && !it.startsWith("•") }
        val eodhdKey = eodhdApiKey?.takeIf { it.isNotBlank() && !it.startsWith("•") }

        val anyProvided = listOf(avKey, massiveKey, fhKey, eodhdKey).any { it != null }
        if (anyProvided) {
            apiKeyService.saveApiKeys(avKey, massiveKey, fhKey, eodhdKey)
            redirectAttributes.addFlashAttribute("success", "API keys updated successfully")
        }

        return "redirect:/providers"
    }

    @PostMapping("/providers/ovtlyr")
    fun saveOvtlyrCredentials(
        @RequestParam ovtlyrCookieUserId: String?,
        @RequestParam ovtlyrCookieToken: String?,
        @RequestParam ovtlyrProjectId: String?,
        redirectAttributes: RedirectAttributes,
    ): String {
        // A field left blank — or still showing the masked placeholder — means "keep current".
        val userId = ovtlyrCookieUserId?.takeIf { it.isNotBlank() && !it.startsWith("•") }
        val token = ovtlyrCookieToken?.takeIf { it.isNotBlank() && !it.startsWith("•") }
        val projectId = ovtlyrProjectId?.takeIf { it.isNotBlank() && !it.startsWith("•") }

        if (listOf(userId, token, projectId).any { it != null }) {
            apiKeyService.saveOvtlyrCredentials(userId, token, projectId)
            redirectAttributes.addFlashAttribute("success", "Ovtlyr credentials updated successfully")
        }

        return "redirect:/providers"
    }

    @PostMapping("/ingestion/ovtlyr/backfill")
    fun startOvtlyrBackfill(): String {
        ovtlyrBackfillService.runBackfill()
        return "redirect:/ingestion"
    }

    @PostMapping("/ingestion/treasury-yields")
    fun ingestTreasuryYields(redirectAttributes: RedirectAttributes): String {
        try {
            val count = runBlocking { treasuryYieldIngestionService.ingest() }
            if (count > 0) {
                redirectAttributes.addFlashAttribute("success", "Ingested $count treasury-yield rows.")
            } else {
                // The provider returned no data (e.g. missing EODHD key or US3M.GBOND unavailable) — the
                // ingestion "succeeds" but writes nothing. Surface it instead of a silent no-op.
                redirectAttributes.addFlashAttribute(
                    "error",
                    "No treasury-yield rows ingested — EODHD returned no data. Check the EODHD API key and US3M.GBOND availability.",
                )
            }
        } catch (e: Exception) {
            logger.warn("Treasury-yield ingestion failed", e)
            redirectAttributes.addFlashAttribute("error", "Treasury-yield ingestion failed: ${e.message}")
        }
        return "redirect:/ingestion"
    }

    data class ProviderViewModel(
        val name: String,
        val baseUrl: String,
        val maskedApiKey: String,
        val stats: ProviderRateLimitStats?,
    )
}
