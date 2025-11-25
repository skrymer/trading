package com.skrymer.udgaard.factory

import com.skrymer.udgaard.integration.ovtlyr.dto.OvtlyrStockInformation
import com.skrymer.udgaard.model.Breadth
import com.skrymer.udgaard.model.OrderBlock
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import java.time.LocalDate

/**
 * Factory for creating Stock domain objects from various data sources.
 * Centralizes stock creation logic and decouples DTOs from domain models.
 */
interface StockFactory {
    /**
     * Create quotes only, without full stock construction.
     * Used when quotes are needed for intermediate calculations (e.g., order block calculation).
     *
     * @param stockInformation - Raw stock data from Ovtlyr
     * @param marketBreadth - Market breadth data for context
     * @param sectorBreadth - Sector breadth data for context
     * @param spy - SPY reference data
     * @param alphaQuotes - Optional AlphaVantage quotes for volume enrichment
     * @param alphaATR - Optional AlphaVantage ATR values for enrichment
     * @return List of enriched StockQuote objects
     */
    fun createQuotes(
        stockInformation: OvtlyrStockInformation,
        marketBreadth: Breadth?,
        sectorBreadth: Breadth?,
        spy: OvtlyrStockInformation,
        alphaQuotes: List<StockQuote>?,
        alphaATR: Map<LocalDate, Double>? = null
    ): List<StockQuote>

    /**
     * Create a Stock from Ovtlyr data with enrichment from other sources.
     *
     * @param stockInformation - Raw stock data from Ovtlyr
     * @param marketBreadth - Market breadth data for context
     * @param sectorBreadth - Sector breadth data for context
     * @param spy - SPY reference data
     * @param alphaQuotes - Optional AlphaVantage quotes for volume enrichment
     * @param calculatedOrderBlocks - Calculated order blocks to include
     * @param alphaATR - Optional AlphaVantage ATR values for enrichment
     * @return Fully constructed Stock entity
     */
    fun createStock(
        stockInformation: OvtlyrStockInformation,
        marketBreadth: Breadth?,
        sectorBreadth: Breadth?,
        spy: OvtlyrStockInformation,
        alphaQuotes: List<StockQuote>?,
        calculatedOrderBlocks: List<OrderBlock>,
        alphaATR: Map<LocalDate, Double>? = null
    ): Stock
}
