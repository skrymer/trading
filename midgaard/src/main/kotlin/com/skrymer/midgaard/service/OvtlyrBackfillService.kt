package com.skrymer.midgaard.service

import com.skrymer.midgaard.integration.ovtlyr.OvtlyrClient
import com.skrymer.midgaard.integration.ovtlyr.OvtlyrCredentials
import com.skrymer.midgaard.integration.ovtlyr.OvtlyrPayloadMapper
import com.skrymer.midgaard.repository.OvtlyrSignalRepository
import com.skrymer.midgaard.repository.SymbolRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * One-time backfill of Ovtlyr signals. Loops the symbol universe, fetches each symbol's
 * full ovtlyr history, extracts buy/sell calls, and upserts them. Manually triggered —
 * there is no scheduled variant (see the "Ovtlyr signal" grilling decisions).
 */
@Service
class OvtlyrBackfillService(
    private val symbolRepository: SymbolRepository,
    private val ovtlyrClient: OvtlyrClient,
    private val signalRepository: OvtlyrSignalRepository,
    private val apiKeyService: ApiKeyService,
) {
    fun runBackfill() {
        val credentials =
            OvtlyrCredentials(
                cookieUserId = apiKeyService.getOvtlyrCookieUserId(),
                cookieToken = apiKeyService.getOvtlyrCookieToken(),
                projectId = apiKeyService.getOvtlyrProjectId(),
            )
        for (symbol in symbolRepository.findAll()) {
            // Best-effort per symbol: one symbol's failure must not abort a multi-thousand-
            // symbol backfill. Log and move on.
            runCatching {
                val payload = ovtlyrClient.getStockInformation(symbol.symbol, credentials) ?: return@runCatching
                signalRepository.upsert(OvtlyrPayloadMapper.toSignals(symbol.symbol, payload))
            }.onFailure { logger.error("Ovtlyr backfill failed for ${symbol.symbol}: ${it.message}", it) }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(OvtlyrBackfillService::class.java)
    }
}
