package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.backtesting.model.RegimeLabel
import com.skrymer.udgaard.backtesting.model.RegimeReadoutDaily
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import com.skrymer.udgaard.data.repository.SectorBreadthRepository
import com.skrymer.udgaard.data.repository.StockJooqRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDate

class RegimeSectorMatrixServiceTest {
  private val stockRepository: StockJooqRepository = mock()
  private val sectorBreadthRepository: SectorBreadthRepository = mock()
  private val regimeReadoutService: RegimeReadoutService = mock()
  private val service = RegimeSectorMatrixService(stockRepository, sectorBreadthRepository, regimeReadoutService)

  private fun dates(n: Int) = (0 until n).map { LocalDate.of(2010, 1, 1).plusDays(it.toLong()) }

  private fun readoutOf(days: List<LocalDate>, label: (Int) -> RegimeLabel?) =
    days.mapIndexed { i, d -> d to RegimeReadoutDaily(quoteDate = d, rawLabel = label(i), publishedLabel = label(i)) }.toMap()

  @Test
  fun `sector daily returns bucket by the day's published label with per-cell annualized mean`() {
    // Given: a 10-day THRUST spell then a 10-day NARROW spell; XLK gains 1% every THRUST day and
    // is flat through NARROW
    val days = dates(20)
    val readout = readoutOf(days) { i -> if (i < 10) RegimeLabel.THRUST else RegimeLabel.NARROW }
    val xlkCloses =
      days
        .mapIndexed { i, d ->
          d to if (i < 10) 100.0 * Math.pow(1.01, i.toDouble()) else 100.0 * Math.pow(1.01, 9.0)
        }.toMap()

    // When
    val matrix = service.matrix(readout, mapOf("XLK" to xlkCloses))

    // Then: the THRUST cell carries XLK's ~1%-a-day annualized (x252); the NARROW cell is ~flat.
    // Day 0 has no prior close, so the THRUST cell rests on 9 return days.
    val thrustCell = matrix.cells.first { it.label == RegimeLabel.THRUST && it.sector == "XLK" }
    val narrowCell = matrix.cells.first { it.label == RegimeLabel.NARROW && it.sector == "XLK" }
    assertEquals(9, thrustCell.dayCount)
    assertEquals(0.01 * 252, thrustCell.annualizedReturn, 1e-9)
    assertEquals(10, narrowCell.dayCount)
    assertEquals(0.0, narrowCell.annualizedReturn, 1e-9)
  }

  @Test
  fun `a cell's standard error clusters by regime spell, not per-day iid`() {
    // Given: two THRUST spells (days 0-9 and 20-29) split by a NARROW spell; XLK returns +1% a day
    // in the first THRUST spell and -1% a day in the second. The THRUST cell holds 19 return days
    // but only TWO independent spells: mean = -1/1900, cluster deviation sums +-180/1900,
    // CR0 daily SE = sqrt(2*(180/1900)^2 / 19^2) ~ 0.00705148 -> annualized ~ 1.776973.
    val days = dates(30)
    val readout = readoutOf(days) { i -> if (i in 10..19) RegimeLabel.NARROW else RegimeLabel.THRUST }
    var close = 100.0
    val closes = LinkedHashMap<LocalDate, Double>()
    days.forEachIndexed { i, d ->
      val dailyReturn = when {
        i == 0 -> 0.0
        i in 1..9 -> 0.01
        i in 10..19 -> 0.0
        else -> -0.01
      }
      close *= (1 + dailyReturn)
      closes[d] = close
    }

    // When
    val matrix = service.matrix(readout, mapOf("XLK" to closes))

    // Then
    val thrustCell = matrix.cells.first { it.label == RegimeLabel.THRUST && it.sector == "XLK" }
    assertEquals(19, thrustCell.dayCount)
    assertEquals(2, thrustCell.spellCount)
    assertEquals(1.776973, thrustCell.annualizedStandardError!!, 1e-4)
  }

  @Test
  fun `loadMatrix builds the matrix from every sector ETF's closes over the requested window`() {
    // Given: the universe knows one sector (XLK), a 20-day labeled window, and XLK closes
    val after = LocalDate.of(2010, 1, 1)
    val before = LocalDate.of(2010, 1, 20)
    val days = dates(20)
    whenever(sectorBreadthRepository.getLatestSectorCounts()).thenReturn(mapOf("XLK" to 250))
    whenever(regimeReadoutService.loadReadoutSeries(eq(after), eq(before), any()))
      .thenReturn(readoutOf(days) { RegimeLabel.THRUST })
    whenever(stockRepository.findBySymbol(eq("XLK"), anyOrNull()))
      .thenReturn(Stock(quotes = days.mapIndexed { i, d -> StockQuote(symbol = "XLK", date = d, closePrice = 100.0 + i) }))

    // When
    val matrix = service.loadMatrix(after, before)

    // Then: the XLK THRUST cell exists with the window's return days
    val cell = matrix.cells.first { it.label == RegimeLabel.THRUST && it.sector == "XLK" }
    assertEquals(19, cell.dayCount)
  }
}
