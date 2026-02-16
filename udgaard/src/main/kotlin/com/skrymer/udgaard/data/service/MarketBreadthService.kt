package com.skrymer.udgaard.data.service

import com.skrymer.udgaard.data.model.MarketBreadthDaily
import com.skrymer.udgaard.data.repository.MarketBreadthRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class MarketBreadthService(
  private val repository: MarketBreadthRepository,
  private val technicalIndicatorService: TechnicalIndicatorService,
) {
  private val logger = LoggerFactory.getLogger(MarketBreadthService::class.java)

  fun refreshMarketBreadth() {
    val start = System.currentTimeMillis()
    val rawRows = repository.findAllRaw()

    if (rawRows.isEmpty()) {
      logger.info("No market breadth data available for EMA calculation")
      return
    }

    val breadthValues = rawRows.map { it.breadthPercent }

    val ema5Values = technicalIndicatorService.calculateEMA(breadthValues, 5)
    val ema10Values = technicalIndicatorService.calculateEMA(breadthValues, 10)
    val ema20Values = technicalIndicatorService.calculateEMA(breadthValues, 20)
    val ema50Values = technicalIndicatorService.calculateEMA(breadthValues, 50)

    val enrichedRows = rawRows.mapIndexed { i, row ->
      row.copy(
        ema5 = ema5Values[i],
        ema10 = ema10Values[i],
        ema20 = ema20Values[i],
        ema50 = ema50Values[i],
      )
    }

    repository.refreshMarketBreadthDaily(enrichedRows)
    logger.info(
      "Refreshed market breadth: ${enrichedRows.size} rows in ${System.currentTimeMillis() - start}ms",
    )
  }

  fun getAllMarketBreadthAsMap(): Map<LocalDate, MarketBreadthDaily> =
    repository.findAllAsMap()
}
