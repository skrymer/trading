package com.skrymer.udgaard.data.service

import com.skrymer.udgaard.data.model.MarketBreadthDaily
import com.skrymer.udgaard.data.repository.MarketBreadthRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.argumentCaptor
import java.time.LocalDate

class MarketBreadthServiceTest {
  private lateinit var service: MarketBreadthService
  private lateinit var repository: MarketBreadthRepository
  private val technicalIndicatorService = TechnicalIndicatorService()

  @BeforeEach
  fun setup() {
    repository = mock(MarketBreadthRepository::class.java)
    service = MarketBreadthService(repository, technicalIndicatorService)
  }

  @Test
  fun `refreshMarketBreadth calculates correct EMAs`() {
    val rawRows = (1..20).map { day ->
      MarketBreadthDaily(
        quoteDate = LocalDate.of(2024, 1, 1).plusDays(day.toLong()),
        breadthPercent = 50.0 + day,
      )
    }
    `when`(repository.findAllRaw()).thenReturn(rawRows)

    service.refreshMarketBreadth()

    val captor = argumentCaptor<List<MarketBreadthDaily>>()
    verify(repository).refreshMarketBreadthDaily(captor.capture())

    val enriched = captor.firstValue
    assertEquals(20, enriched.size)

    // First 4 rows should have ema5 = 0 (not enough data for period 5)
    assertEquals(0.0, enriched[0].ema5)
    assertEquals(0.0, enriched[3].ema5)

    // 5th row should have ema5 = SMA of first 5 breadth values
    // breadth: 51, 52, 53, 54, 55 → SMA = 53.0
    assertEquals(53.0, enriched[4].ema5)

    // All rows should have ema50 = 0 (only 20 data points, not enough for period 50)
    enriched.forEach { row ->
      assertEquals(0.0, row.ema50)
    }

    // EMA10 for first 9 rows should be 0
    assertEquals(0.0, enriched[8].ema10)
    // 10th row should have ema10 = SMA of first 10 breadth values
    // breadth: 51..60 → SMA = 55.5
    assertEquals(55.5, enriched[9].ema10)
  }

  @Test
  fun `refreshMarketBreadth handles empty data gracefully`() {
    `when`(repository.findAllRaw()).thenReturn(emptyList())

    service.refreshMarketBreadth()

    verify(repository).findAllRaw()
  }
}
