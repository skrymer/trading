package com.skrymer.udgaard.integration.ovtlyr

import com.skrymer.udgaard.model.MarketSymbol
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockSymbol
import com.skrymer.udgaard.repository.MarketBreadthRepository
import com.skrymer.udgaard.service.StockService
import org.springframework.stereotype.Component

/**
 * Loads data from ovtlyr into mongo.
 */
@Component
class DataLoader(
    private val ovtlyrClient: OvtlyrClient,
    private val marketBreadthRepository: MarketBreadthRepository,
    private val stockService: StockService) {
    fun loadData() {
        try {
            loadMarkBreadthForAllSectors()
            loadTopStocks()
        } catch (e: Exception) {
            // TODO: handle exception logging
        }
    }

    fun loadTopStocks(): List<Stock> {
        return stockService.getStocks(StockSymbol.entries.map { it.name })
    }

    private fun loadMarkBreadthForAllSectors() {
        MarketSymbol.entries.forEach { symbol ->
            val response = ovtlyrClient.getMarketBreadth(symbol.name)
            if(response != null) {
                marketBreadthRepository.save(response.toModel())
            } else {
                println("Could not load market breadth for sector ${symbol.description}")
            }
            println(response)
        }
    }
}
