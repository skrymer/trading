package com.skrymer.udgaard.integration.ovtlyr

import com.skrymer.udgaard.model.MarketSymbol
import com.skrymer.udgaard.integration.ovtlyr.dto.OvtlyrMarketBreadth
import com.skrymer.udgaard.repository.MarketBreadthRepository
import org.springframework.stereotype.Component

/**
 * Loads data from ovtlyr into mongo.
 */
@Component
class DataLoader(private val ovtlyrClient: OvtlyrClient, private val marketBreadthRepository: MarketBreadthRepository) {
    fun loadData() {
        try {
            loadMarkBreadthForAllSectors()
        } catch (e: Exception) {
            // TODO: handle exception logging
        }
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
