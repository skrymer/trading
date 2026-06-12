package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.backtesting.model.RegimeLabel
import com.skrymer.udgaard.backtesting.model.RegimeReadoutDaily
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class RegimeDecompositionServiceTest {
  private val service = RegimeDecompositionService()

  private fun readoutOf(vararg reads: Pair<LocalDate, RegimeLabel?>) =
    reads.associate { (date, label) ->
      date to RegimeReadoutDaily(quoteDate = date, rawLabel = label, publishedLabel = label)
    }

  private fun samplesOn(date: LocalDate, count: Int, returnPct: Double, sector: String = "XLK") =
    (0 until count).map { RegimeTradeSample(entryDate = date, returnPct = returnPct, sector = sector) }

  @Test
  fun `trades bucket by the published regime label at their entry date with per-bucket edge`() {
    // Given: 35 trades entered on a THRUST day averaging +1.2% and 32 on a NARROW day averaging -0.4%
    val thrustDay = LocalDate.of(2010, 3, 1)
    val narrowDay = LocalDate.of(2023, 6, 1)
    val readout = readoutOf(thrustDay to RegimeLabel.THRUST, narrowDay to RegimeLabel.NARROW)
    val samples = samplesOn(thrustDay, 35, 1.2) + samplesOn(narrowDay, 32, -0.4)

    // When
    val decomposition = service.decompose(samples, readout)

    // Then: one row per regime with the bucket's trade count and mean per-trade return (edge)
    val thrustRow = decomposition.rows.first { it.label == RegimeLabel.THRUST }
    val narrowRow = decomposition.rows.first { it.label == RegimeLabel.NARROW }
    assertEquals(35, thrustRow.tradeCount)
    assertEquals(1.2, thrustRow.edge!!, 1e-9)
    assertEquals(32, narrowRow.tradeCount)
    assertEquals(-0.4, narrowRow.edge!!, 1e-9)
  }

  @Test
  fun `a bucket below the trade floor reports its count but no inferable statistics`() {
    // Given: 12 trades on a CRISIS day with a juicy mean — far too few to infer anything — and a
    // 30-trade THRUST bucket sitting exactly on the floor
    val crisisDay = LocalDate.of(2008, 10, 10)
    val thrustDay = LocalDate.of(2010, 3, 1)
    val readout = readoutOf(crisisDay to RegimeLabel.CRISIS, thrustDay to RegimeLabel.THRUST)
    val samples = samplesOn(crisisDay, 12, 5.0) + samplesOn(thrustDay, 30, 1.0)

    // When
    val decomposition = service.decompose(samples, readout)

    // Then: the thin bucket keeps its N but carries no edge and is flagged do-not-infer; the
    // bucket on the floor is inferable
    val crisisRow = decomposition.rows.first { it.label == RegimeLabel.CRISIS }
    val thrustRow = decomposition.rows.first { it.label == RegimeLabel.THRUST }
    assertEquals(12, crisisRow.tradeCount)
    assertEquals(null, crisisRow.edge)
    assertEquals(true, crisisRow.insufficient)
    assertEquals(false, thrustRow.insufficient)
    assertEquals(1.0, thrustRow.edge!!, 1e-9)
  }

  @Test
  fun `a bucket's standard error is clustered by entry month, not per-trade iid`() {
    // Given: one THRUST bucket of 40 trades split across two months — 20 trades at +2% in March,
    // 20 at 0% in June. Two clusters, deviation sums +-20 around the +1% mean:
    // CR0 variance = (20^2 + 20^2) / 40^2 = 0.5 -> SE = sqrt(0.5) ~ 0.7071.
    // (An iid SE would be ~0.16 — five times too confident for two regime spells.)
    val marchDay = LocalDate.of(2010, 3, 1)
    val juneDay = LocalDate.of(2010, 6, 1)
    val readout = readoutOf(marchDay to RegimeLabel.THRUST, juneDay to RegimeLabel.THRUST)
    val samples = samplesOn(marchDay, 20, 2.0) + samplesOn(juneDay, 20, 0.0)

    // When
    val decomposition = service.decompose(samples, readout)

    // Then
    val row = decomposition.rows.first { it.label == RegimeLabel.THRUST }
    assertEquals(kotlin.math.sqrt(0.5), row.standardError!!, 1e-9)
  }

  @Test
  fun `unlabeled entries bucket separately and transition-boundary entries are counted`() {
    // Given: 30 trades on a clean THRUST day, 2 on a day with no read at all, 3 on an unlabeled
    // day, and 5 on a transition-boundary day (raw CHOP still publishing THRUST mid-dwell)
    val thrustDay = LocalDate.of(2010, 3, 1)
    val unlabeledDay = LocalDate.of(2010, 3, 2)
    val boundaryDay = LocalDate.of(2010, 3, 3)
    val readout =
      readoutOf(thrustDay to RegimeLabel.THRUST, unlabeledDay to null) +
        mapOf(boundaryDay to RegimeReadoutDaily(boundaryDay, rawLabel = RegimeLabel.CHOP, publishedLabel = RegimeLabel.THRUST))
    val samples =
      samplesOn(thrustDay, 30, 1.0) +
        samplesOn(LocalDate.of(2010, 4, 9), 2, 0.5) +
        samplesOn(unlabeledDay, 3, 0.5) +
        samplesOn(boundaryDay, 5, 2.0)

    // When
    val decomposition = service.decompose(samples, readout)

    // Then: no-read and null-read entries share the unlabeled bucket; boundary entries bucket
    // under the published label but are surfaced as a divergence count for the analyst
    val unlabeledRow = decomposition.rows.first { it.label == null }
    val thrustRow = decomposition.rows.first { it.label == RegimeLabel.THRUST }
    assertEquals(5, unlabeledRow.tradeCount)
    assertEquals(35, thrustRow.tradeCount)
    assertEquals(5, decomposition.rawPublishedDivergenceCount)
    assertEquals(40, decomposition.totalTrades)
  }

  @Test
  fun `each regime row drills down into per-sector cells with the floor applied per cell`() {
    // Given: a THRUST bucket of 40 XLK trades (+1.5%) and 10 XLE trades (-0.5%) — the bucket is
    // inferable but the thin XLE cell is not
    val thrustDay = LocalDate.of(2010, 3, 1)
    val readout = readoutOf(thrustDay to RegimeLabel.THRUST)
    val samples = samplesOn(thrustDay, 40, 1.5, sector = "XLK") + samplesOn(thrustDay, 10, -0.5, sector = "XLE")

    // When
    val decomposition = service.decompose(samples, readout)

    // Then
    val row = decomposition.rows.first { it.label == RegimeLabel.THRUST }
    val xlk = row.sectors.first { it.sector == "XLK" }
    val xle = row.sectors.first { it.sector == "XLE" }
    assertEquals(40, xlk.tradeCount)
    assertEquals(1.5, xlk.edge!!, 1e-9)
    assertEquals(false, xlk.insufficient)
    assertEquals(10, xle.tradeCount)
    assertEquals(null, xle.edge)
    assertEquals(true, xle.insufficient)
  }
}
