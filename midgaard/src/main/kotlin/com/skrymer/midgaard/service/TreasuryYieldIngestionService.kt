package com.skrymer.midgaard.service

import com.skrymer.midgaard.integration.eodhd.EodhdGovBondClient
import com.skrymer.midgaard.repository.TreasuryYieldRepository
import org.springframework.stereotype.Service

/**
 * Ingests the treasury-yield reference series the backtest engine credits idle cash at (ADR 0016).
 * A single low-cardinality series (one maturity, full daily history) — unlike the per-symbol ovtlyr
 * backfill there is no symbol loop or progress tracking to manage.
 */
@Service
class TreasuryYieldIngestionService(
    private val govBondClient: EodhdGovBondClient,
    private val repository: TreasuryYieldRepository,
) {
    suspend fun ingest(): Int {
        val yields = govBondClient.fetchYields(MATURITY_US3M, TICKER_US3M)
        repository.upsert(yields ?: return 0)
        return yields.size
    }

    companion object {
        const val MATURITY_US3M = "US3M"
        const val TICKER_US3M = "US3M.GBOND"
    }
}
