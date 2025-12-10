package com.skrymer.udgaard.integration

import com.skrymer.udgaard.integration.alphavantage.dto.AlphaVantageEtfProfile

/**
 * Interface for ETF data providers
 *
 * Implementations should provide ETF profile information including
 * holdings, sector weightings, and other ETF-specific metadata.
 */
interface EtfProvider {
    /**
     * Get ETF profile information
     *
     * Returns comprehensive ETF data including:
     * - Holdings and their weights
     * - Sector allocations
     * - Asset class breakdown
     * - Expense ratio and other fund metrics
     *
     * @param symbol ETF symbol (e.g., "SPY", "QQQ", "IWM")
     * @return ETF profile data, or null if unavailable
     */
    fun getEtfProfile(symbol: String): AlphaVantageEtfProfile?
}
