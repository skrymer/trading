package com.skrymer.udgaard.integration.ovtlyr

import com.skrymer.udgaard.model.EtfEntity
import com.skrymer.udgaard.model.EtfSymbol
import com.skrymer.udgaard.model.SectorSymbol
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockSymbol
import com.skrymer.udgaard.service.BreadthService
import com.skrymer.udgaard.service.EtfService
import com.skrymer.udgaard.service.StockService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Loads data from ovtlyr into mongo.
 */
@Component
class DataLoader(
  private val breadthService: BreadthService,
  private val stockService: StockService,
  private val etfService: EtfService,
) {
  fun loadData() {
    runBlocking { loadBreadthForAll() }
    runBlocking { loadStocks(true) }
    runBlocking { loadEtfs() }
  }

  fun loadStocks(forceFetch: Boolean = false): List<Stock> =
    stockService.getStocksBySymbols(
      StockSymbol.entries.map {
        it.symbol
      },
      forceFetch,
    )

  fun loadEtfs(): List<EtfEntity> {
    val logger = LoggerFactory.getLogger("EtfLoader")
    logger.info("Loading ${EtfSymbol.entries.size} ETFs from Ovtlyr")

    return EtfSymbol.entries.mapNotNull { etfSymbol ->
      runCatching {
        etfService.refreshEtf(etfSymbol.name)
      }.onFailure { e ->
        logger.warn("Failed to fetch ETF ${etfSymbol.name}: ${e.message}", e)
      }.getOrNull()
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private suspend fun loadBreadthForAll() =
    supervisorScope {
      val logger = LoggerFactory.getLogger("BreadthLoader")
      val limited = Dispatchers.IO.limitedParallelism(10)

      // Load market breadth (FULLSTOCK)
      async(limited) {
        runCatching { breadthService.getMarketBreadth(refresh = true) }
          .onFailure { e -> logger.warn("Failed to fetch market breadth: {}", e.message, e) }
      }

      // Load all sector breadth
      SectorSymbol.entries.forEach { sector ->
        async(limited) {
          runCatching { breadthService.getSectorBreadth(sector, refresh = true) }
            .onFailure { e -> logger.warn("Failed to fetch sector={}: {}", sector, e.message, e) }
        }
      }
    }
}
