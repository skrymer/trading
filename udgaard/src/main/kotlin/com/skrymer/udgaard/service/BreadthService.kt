package com.skrymer.udgaard.service

import com.skrymer.udgaard.integration.ovtlyr.OvtlyrClient
import com.skrymer.udgaard.integration.ovtlyr.dto.OvtlyrStockInformation
import com.skrymer.udgaard.model.Breadth
import com.skrymer.udgaard.model.BreadthSymbol
import com.skrymer.udgaard.model.SectorSymbol
import com.skrymer.udgaard.model.StockSymbol
import com.skrymer.udgaard.repository.BreadthRepository
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Service for managing breadth data (market and sector breadth).
 * Provides clear separation between market (FULLSTOCK) and sector operations.
 */
@Service
class BreadthService(
    val ovtlyrClient: OvtlyrClient,
    val breadthRepository: BreadthRepository
) {

    /**
     * Get market breadth (FULLSTOCK - all stocks combined).
     */
    fun getMarketBreadth(refresh: Boolean = false): Breadth? {
        return getBreadth(BreadthSymbol.Market(), refresh)
    }

    /**
     * Get market breadth for a specific date range.
     */
    fun getMarketBreadth(fromDate: LocalDate, toDate: LocalDate, refresh: Boolean = false): Breadth? {
        return getBreadth(BreadthSymbol.Market(), fromDate, toDate, refresh)
    }

    /**
     * Get sector breadth for a specific sector.
     */
    fun getSectorBreadth(sector: SectorSymbol, refresh: Boolean = false): Breadth? {
        return getBreadth(BreadthSymbol.Sector(sector), refresh)
    }

    /**
     * Get sector breadth for a specific date range.
     */
    fun getSectorBreadth(sector: SectorSymbol, fromDate: LocalDate, toDate: LocalDate, refresh: Boolean = false): Breadth? {
        return getBreadth(BreadthSymbol.Sector(sector), fromDate, toDate, refresh)
    }

    /**
     * Get breadth data for any symbol (market or sector).
     */
    fun getBreadth(symbol: BreadthSymbol, refresh: Boolean = false): Breadth? {
        return if (refresh) {
            fetchBreadth(symbol)
        } else {
            breadthRepository.findBySymbol(symbol.toIdentifier())
                ?: fetchBreadth(symbol)
        }
    }

    /**
     * Get breadth data for any symbol with date filtering.
     */
    fun getBreadth(symbol: BreadthSymbol, fromDate: LocalDate, toDate: LocalDate, refresh: Boolean = false): Breadth? {
        val breadth = getBreadth(symbol, refresh)

        return if (breadth == null) {
            null
        } else {
            Breadth(
                breadth.symbol ?: symbol,
                breadth.quotes
                    .filter { it.quoteDate?.isAfter(fromDate) == true }
                    .filter { it.quoteDate?.isBefore(toDate) == true }
                    .toMutableList()
            )
        }
    }

    /**
     * Refresh all breadth data (market + all sectors).
     */
    fun refreshAll(): Map<String, String> {
        // Refresh market (FULLSTOCK) first
        fetchBreadth(BreadthSymbol.Market())

        // Refresh all sectors
        val sectors = SectorSymbol.entries
        sectors.forEach { sector ->
            fetchBreadth(BreadthSymbol.Sector(sector))
        }

        return mapOf(
            "status" to "success",
            "message" to "Refreshed market and ${sectors.size} sectors"
        )
    }

    /**
     * Fetch breadth data from Ovtlyr and save to database.
     */
    private fun fetchBreadth(symbol: BreadthSymbol): Breadth? {
        val ovtlyrBreadth = ovtlyrClient.getBreadth(symbol.toIdentifier())

        return if (ovtlyrBreadth == null) {
            null
        } else {
            // Delete existing breadth if it exists (cascade delete will remove quotes)
            val identifier = symbol.toIdentifier()
            breadthRepository.findBySymbol(identifier)?.let { existingBreadth ->
                breadthRepository.delete(existingBreadth)
                breadthRepository.flush() // Ensure delete is committed before insert
            }

            val stockInSector = getStockInSector(symbol)
            breadthRepository.save(ovtlyrBreadth.toModel(stockInSector))
        }
    }

    /**
     * Get a representative stock from a sector for heatmap data.
     * Returns null for market breadth.
     */
    private fun getStockInSector(symbol: BreadthSymbol): OvtlyrStockInformation? {
        return when (symbol) {
            is BreadthSymbol.Market -> null // Market doesn't need a representative stock
            is BreadthSymbol.Sector -> when (symbol.symbol) {
                SectorSymbol.XLB -> ovtlyrClient.getStockInformation(StockSymbol.ALB.name)
                SectorSymbol.XLE -> ovtlyrClient.getStockInformation(StockSymbol.XOM.name)
                SectorSymbol.XLV -> ovtlyrClient.getStockInformation(StockSymbol.LLY.name)
                SectorSymbol.XLC -> ovtlyrClient.getStockInformation(StockSymbol.GOOGL.name)
                SectorSymbol.XLK -> ovtlyrClient.getStockInformation(StockSymbol.NVDA.name)
                SectorSymbol.XLRE -> ovtlyrClient.getStockInformation(StockSymbol.AMT.name)
                SectorSymbol.XLI -> ovtlyrClient.getStockInformation(StockSymbol.GE.name)
                SectorSymbol.XLF -> ovtlyrClient.getStockInformation(StockSymbol.JPM.name)
                SectorSymbol.XLY -> ovtlyrClient.getStockInformation(StockSymbol.AMZN.name)
                SectorSymbol.XLP -> ovtlyrClient.getStockInformation(StockSymbol.WMT.name)
                SectorSymbol.XLU -> ovtlyrClient.getStockInformation(StockSymbol.NEE.name)
            }
        }
    }
}
