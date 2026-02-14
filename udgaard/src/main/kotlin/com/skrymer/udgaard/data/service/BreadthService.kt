package com.skrymer.udgaard.data.service

import com.skrymer.udgaard.data.integration.ovtlyr.OvtlyrClient
import com.skrymer.udgaard.data.integration.ovtlyr.dto.OvtlyrStockInformation
import com.skrymer.udgaard.data.model.Breadth
import com.skrymer.udgaard.data.model.BreadthSymbol
import com.skrymer.udgaard.data.model.SectorSymbol
import com.skrymer.udgaard.data.repository.BreadthJooqRepository
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Service for managing breadth data (market and sector breadth).
 * Provides clear separation between market (FULLSTOCK) and sector operations.
 */
@Service
class BreadthService(
  val ovtlyrClient: OvtlyrClient,
  val breadthRepository: BreadthJooqRepository,
) {
  /**
   * Get market breadth (FULLSTOCK - all stocks combined).
   */
  fun getMarketBreadth(refresh: Boolean = false): Breadth? = getBreadth(BreadthSymbol.Market(), refresh)

  /**
   * Get market breadth for a specific date range.
   */
  fun getMarketBreadth(
    fromDate: LocalDate,
    toDate: LocalDate,
    refresh: Boolean = false,
  ): Breadth? = getBreadth(BreadthSymbol.Market(), fromDate, toDate, refresh)

  /**
   * Get sector breadth for a specific sector.
   */
  fun getSectorBreadth(
    sector: SectorSymbol,
    refresh: Boolean = false,
  ): Breadth? = getBreadth(BreadthSymbol.Sector(sector), refresh)

  /**
   * Get sector breadth for a specific date range.
   */
  fun getSectorBreadth(
    sector: SectorSymbol,
    fromDate: LocalDate,
    toDate: LocalDate,
    refresh: Boolean = false,
  ): Breadth? = getBreadth(BreadthSymbol.Sector(sector), fromDate, toDate, refresh)

  /**
   * Get breadth data for any symbol (market or sector).
   */
  fun getBreadth(
    symbol: BreadthSymbol,
    refresh: Boolean = false,
  ): Breadth? =
    if (refresh) {
      fetchBreadth(symbol)
    } else {
      breadthRepository.findBySymbol(symbol.toIdentifier())
        ?: fetchBreadth(symbol)
    }

  /**
   * Get breadth data for any symbol with date filtering.
   */
  fun getBreadth(
    symbol: BreadthSymbol,
    fromDate: LocalDate,
    toDate: LocalDate,
    refresh: Boolean = false,
  ): Breadth? {
    val breadth = getBreadth(symbol, refresh)

    return if (breadth == null) {
      null
    } else {
      Breadth(
        symbolType = breadth.symbolType,
        symbolValue = breadth.symbolValue,
        quotes = breadth.quotes
          .filter { it.quoteDate.isAfter(fromDate) }
          .filter { it.quoteDate.isBefore(toDate) }
          .toMutableList(),
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
      "message" to "Refreshed market and ${sectors.size} sectors",
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
        breadthRepository.delete(symbol.toIdentifier())
      }

      val stockInSector = getStockInSector(symbol)
      breadthRepository.save(ovtlyrBreadth.toModel(stockInSector))
    }
  }

  /**
   * Get a representative stock from a sector for heatmap data.
   * Returns null for market breadth.
   */
  private fun getStockInSector(symbol: BreadthSymbol): OvtlyrStockInformation? =
    when (symbol) {
      is BreadthSymbol.Market -> null // Market doesn't need a representative stock
      is BreadthSymbol.Sector ->
        when (symbol.symbol) {
          SectorSymbol.XLB -> ovtlyrClient.getStockInformation("ALB")
          SectorSymbol.XLE -> ovtlyrClient.getStockInformation("XOM")
          SectorSymbol.XLV -> ovtlyrClient.getStockInformation("LLY")
          SectorSymbol.XLC -> ovtlyrClient.getStockInformation("GOOGL")
          SectorSymbol.XLK -> ovtlyrClient.getStockInformation("NVDA")
          SectorSymbol.XLRE -> ovtlyrClient.getStockInformation("AMT")
          SectorSymbol.XLI -> ovtlyrClient.getStockInformation("GE")
          SectorSymbol.XLF -> ovtlyrClient.getStockInformation("JPM")
          SectorSymbol.XLY -> ovtlyrClient.getStockInformation("AMZN")
          SectorSymbol.XLP -> ovtlyrClient.getStockInformation("WMT")
          SectorSymbol.XLU -> ovtlyrClient.getStockInformation("NEE")
        }
    }
}
