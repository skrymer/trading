package com.skrymer.udgaard.data.service

import com.skrymer.udgaard.data.model.SectorBreadthDaily
import com.skrymer.udgaard.data.repository.SectorBreadthRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.argumentCaptor
import java.time.LocalDate

class SectorBreadthServiceTest {
  private lateinit var service: SectorBreadthService
  private lateinit var repository: SectorBreadthRepository
  private val technicalIndicatorService = TechnicalIndicatorService()

  @BeforeEach
  fun setup() {
    repository = mock(SectorBreadthRepository::class.java)
    service = SectorBreadthService(repository, technicalIndicatorService)
  }

  @Test
  fun `refreshSectorBreadth calculates correct bull percentages and EMAs`() {
    val rawRows = (1..20).map { day ->
      SectorBreadthDaily(
        sectorSymbol = "XLK",
        quoteDate = LocalDate.of(2024, 1, 1).plusDays(day.toLong()),
        stocksInUptrend = 50 + day,
        stocksInDowntrend = 50 - day,
        totalStocks = 100,
        bullPercentage = 50.0 + day,
      )
    }
    `when`(repository.calculateRawSectorBreadth()).thenReturn(rawRows)

    service.refreshSectorBreadth()

    val captor = argumentCaptor<List<SectorBreadthDaily>>()
    verify(repository).refreshSectorBreadthDaily(captor.capture())

    val enriched = captor.firstValue
    assertEquals(20, enriched.size)

    // First 4 rows should have ema5 = 0 (not enough data for period 5)
    assertEquals(0.0, enriched[0].ema5)
    assertEquals(0.0, enriched[3].ema5)

    // 5th row should have ema5 = SMA of first 5 bull percentages
    // bull percentages: 51, 52, 53, 54, 55 → SMA = 53.0
    assertEquals(53.0, enriched[4].ema5)

    // All rows should have ema50 = 0 (only 20 data points, not enough for period 50)
    enriched.forEach { row ->
      assertEquals(0.0, row.ema50)
    }

    // EMA10 for first 9 rows should be 0
    assertEquals(0.0, enriched[8].ema10)
    // 10th row should have ema10 = SMA of first 10 bull percentages
    // bull percentages: 51..60 → SMA = 55.5
    assertEquals(55.5, enriched[9].ema10)
  }

  @Test
  fun `refreshSectorBreadth handles multiple sectors`() {
    val xlkRows = (1..10).map { day ->
      SectorBreadthDaily(
        sectorSymbol = "XLK",
        quoteDate = LocalDate.of(2024, 1, 1).plusDays(day.toLong()),
        stocksInUptrend = 60,
        stocksInDowntrend = 40,
        totalStocks = 100,
        bullPercentage = 60.0,
      )
    }
    val xlfRows = (1..10).map { day ->
      SectorBreadthDaily(
        sectorSymbol = "XLF",
        quoteDate = LocalDate.of(2024, 1, 1).plusDays(day.toLong()),
        stocksInUptrend = 40,
        stocksInDowntrend = 60,
        totalStocks = 100,
        bullPercentage = 40.0,
      )
    }
    `when`(repository.calculateRawSectorBreadth()).thenReturn(xlkRows + xlfRows)

    service.refreshSectorBreadth()

    val captor = argumentCaptor<List<SectorBreadthDaily>>()
    verify(repository).refreshSectorBreadthDaily(captor.capture())

    val enriched = captor.firstValue
    assertEquals(20, enriched.size)

    val xlk = enriched.filter { it.sectorSymbol == "XLK" }
    val xlf = enriched.filter { it.sectorSymbol == "XLF" }
    assertEquals(10, xlk.size)
    assertEquals(10, xlf.size)

    // EMA5 for constant 60% → SMA of first 5 = 60.0
    assertEquals(60.0, xlk[4].ema5)
    // EMA5 for constant 40% → SMA of first 5 = 40.0
    assertEquals(40.0, xlf[4].ema5)
  }

  @Test
  fun `refreshSectorBreadth handles empty data gracefully`() {
    `when`(repository.calculateRawSectorBreadth()).thenReturn(emptyList())

    service.refreshSectorBreadth()

    verify(repository).calculateRawSectorBreadth()
  }
}
