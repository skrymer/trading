package com.skrymer.udgaard.data.service

import com.skrymer.udgaard.data.model.SectorBreadthDaily
import com.skrymer.udgaard.data.repository.SectorBreadthRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class SectorBreadthService(
  private val repository: SectorBreadthRepository,
  private val technicalIndicatorService: TechnicalIndicatorService,
) {
  private val logger = LoggerFactory.getLogger(SectorBreadthService::class.java)

  fun refreshSectorBreadth() {
    val start = System.currentTimeMillis()
    val rawRows = repository.calculateRawSectorBreadth()

    if (rawRows.isEmpty()) {
      logger.info("No stock data available for sector breadth calculation")
      return
    }

    val bySector = rawRows.groupBy { it.sectorSymbol }
    val enrichedRows = bySector.flatMap { (sector, rows) ->
      val bullPercentages = rows.map { it.bullPercentage }

      val ema5Values = technicalIndicatorService.calculateEMA(bullPercentages, 5)
      val ema10Values = technicalIndicatorService.calculateEMA(bullPercentages, 10)
      val ema20Values = technicalIndicatorService.calculateEMA(bullPercentages, 20)
      val ema50Values = technicalIndicatorService.calculateEMA(bullPercentages, 50)
      val (donchianUpper, donchianLower) =
        technicalIndicatorService.calculateDonchianBands(bullPercentages, 20)

      rows.mapIndexed { i, row ->
        row.copy(
          ema5 = ema5Values[i],
          ema10 = ema10Values[i],
          ema20 = ema20Values[i],
          ema50 = ema50Values[i],
          donchianUpperBand = donchianUpper[i],
          donchianLowerBand = donchianLower[i],
        )
      }
    }

    repository.refreshSectorBreadthDaily(enrichedRows)
    logger.info(
      "Refreshed sector breadth: ${bySector.size} sectors, ${enrichedRows.size} rows in ${System.currentTimeMillis() - start}ms",
    )
  }

  fun getSectorBreadth(sectorSymbol: String): List<SectorBreadthDaily> =
    repository.findBySector(sectorSymbol)

  fun getAllSectorBreadthAsMap(): Map<String, Map<LocalDate, SectorBreadthDaily>> =
    repository.findAllAsMap()
}
