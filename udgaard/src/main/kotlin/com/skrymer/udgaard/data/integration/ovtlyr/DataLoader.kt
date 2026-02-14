package com.skrymer.udgaard.data.integration.ovtlyr

import com.skrymer.udgaard.data.model.SectorSymbol
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.service.BreadthService
import com.skrymer.udgaard.data.service.StockService
import com.skrymer.udgaard.data.service.SymbolService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Loads data from ovtlyr into H2 database.
 */
@Component
class DataLoader(
  private val breadthService: BreadthService,
  private val stockService: StockService,
  private val symbolService: SymbolService,
) {
  fun loadData() {
    runBlocking { loadBreadthForAll() }
    runBlocking { loadStocks(true) }
  }

  fun loadStocks(forceFetch: Boolean = false): List<Stock> =
    stockService.getStocksBySymbols(
      symbolService.getAll().map { it.symbol },
      forceFetch,
    )

  @OptIn(ExperimentalCoroutinesApi::class)
  private suspend fun loadBreadthForAll() =
    supervisorScope {
      val logger = LoggerFactory.getLogger("BreadthLoader")
      val limited = Dispatchers.IO.limitedParallelism(10)

      logger.info("Loading market breadth and ${SectorSymbol.entries.size} sector breadth data")

      // Load market breadth (FULLSTOCK)
      val marketJob =
        async(limited) {
          runCatching { breadthService.getMarketBreadth(refresh = true) }
            .onFailure { e -> logger.warn("Failed to fetch market breadth: {}", e.message, e) }
        }

      // Load all sector breadth
      val sectorJobs =
        SectorSymbol.entries.map { sector ->
          async(limited) {
            runCatching { breadthService.getSectorBreadth(sector, refresh = true) }
              .onFailure { e -> logger.warn("Failed to fetch sector={}: {}", sector, e.message, e) }
          }
        }

      // Wait for all breadth loading to complete
      marketJob.await()
      sectorJobs.forEach { it.await() }

      logger.info("Breadth data loading completed")
    }
}
