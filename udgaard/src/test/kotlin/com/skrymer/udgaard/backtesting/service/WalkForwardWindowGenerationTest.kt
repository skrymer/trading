package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.backtesting.model.WalkForwardConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class WalkForwardWindowGenerationTest {
  private val service = WalkForwardService(
    backtestService = mock(),
    sectorBreadthRepository = mock(),
    marketBreadthRepository = mock(),
  )

  private val start = LocalDate.of(2016, 1, 1)
  private val end = LocalDate.of(2025, 12, 31)

  private fun config(inMonths: Int, oosMonths: Int, stepMonths: Int, s: LocalDate = start, e: LocalDate = end) =
    WalkForwardConfig(inMonths, oosMonths, stepMonths, s, e)

  @Test
  fun `year-equivalent default 60-12-12 produces the same 4 windows as the prior year-based logic`() {
    val windows = service.generateWindows(config(60, 12, 12))
    assertEquals(4, windows.size)
    // First window: IS 2016-01-01 → 2021-01-01, OOS 2021-01-02 → 2022-01-01
    assertEquals(LocalDate.of(2016, 1, 1), windows[0].isStart)
    assertEquals(LocalDate.of(2021, 1, 1), windows[0].isEnd)
    assertEquals(LocalDate.of(2021, 1, 2), windows[0].oosStart)
    assertEquals(LocalDate.of(2022, 1, 1), windows[0].oosEnd)
    // Fourth window starts 3 years later
    assertEquals(LocalDate.of(2019, 1, 1), windows[3].isStart)
  }

  @Test
  fun `quarterly disjoint 36-3-3 produces many windows with non-overlapping OOS slices`() {
    val windows = service.generateWindows(config(36, 3, 3))
    assertTrue(windows.size >= 20, "expected >= 20 windows, got ${windows.size}")
    // Each OOS slice begins exactly the day after the previous OOS slice ended
    windows.zipWithNext().forEach { (prev, next) ->
      assertEquals(prev.oosEnd.plusDays(1), next.oosStart, "OOS slices must be contiguous and disjoint")
    }
    // Each OOS slice is ~3 calendar months ± a few days (month-length variance)
    windows.forEach { w ->
      val days = ChronoUnit.DAYS.between(w.oosStart, w.oosEnd.plusDays(1))
      assertTrue(days in 89..93, "OOS slice should be ~3 months, got $days days")
    }
  }

  @Test
  fun `rejects stepMonths smaller than outOfSampleMonths to prevent OOS overlap`() {
    // Step 3mo with 12mo OOS would produce windows with 75% OOS overlap — aggregate WFE math
    // would double-count trades. The guard must reject this config.
    assertThrows<IllegalArgumentException> { service.generateWindows(config(60, 12, 3)) }
    assertThrows<IllegalArgumentException> { service.generateWindows(config(36, 6, 3)) }
    // Equal is OK (disjoint — boundary case).
    service.generateWindows(config(36, 3, 3))
    // Step larger than OOS is OK (disjoint with gaps between OOS windows — not useful but valid).
    service.generateWindows(config(36, 3, 12))
  }

  @Test
  fun `rejects zero or negative inSampleMonths`() {
    assertThrows<IllegalArgumentException> { service.generateWindows(config(0, 3, 3)) }
    assertThrows<IllegalArgumentException> { service.generateWindows(config(-1, 3, 3)) }
  }

  @Test
  fun `rejects zero or negative outOfSampleMonths`() {
    assertThrows<IllegalArgumentException> { service.generateWindows(config(36, 0, 3)) }
    assertThrows<IllegalArgumentException> { service.generateWindows(config(36, -2, 3)) }
  }

  @Test
  fun `rejects zero or negative stepMonths`() {
    assertThrows<IllegalArgumentException> { service.generateWindows(config(36, 3, 0)) }
    assertThrows<IllegalArgumentException> { service.generateWindows(config(36, 3, -3)) }
  }

  @Test
  fun `OOS end past config endDate terminates loop without emitting a truncated window`() {
    // Very short window: only the first IS+OOS fits cleanly
    val windows = service.generateWindows(
      config(36, 3, 12, s = LocalDate.of(2020, 1, 1), e = LocalDate.of(2023, 6, 30)),
    )
    // Expected: only window whose OOS end ≤ 2023-06-30 is emitted
    windows.forEach { w ->
      assertTrue(!w.oosEnd.isAfter(LocalDate.of(2023, 6, 30)))
    }
  }

  @Test
  fun `rejects startDate not before endDate`() {
    assertThrows<IllegalArgumentException> {
      service.generateWindows(
        config(36, 3, 3, s = LocalDate.of(2023, 1, 1), e = LocalDate.of(2023, 1, 1)),
      )
    }
    assertThrows<IllegalArgumentException> {
      service.generateWindows(
        config(36, 3, 3, s = LocalDate.of(2023, 6, 1), e = LocalDate.of(2023, 1, 1)),
      )
    }
  }
}
