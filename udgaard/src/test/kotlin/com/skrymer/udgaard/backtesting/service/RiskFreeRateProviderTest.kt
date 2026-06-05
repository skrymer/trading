package com.skrymer.udgaard.backtesting.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

private const val RF_EPSILON = 1e-9

class RiskFreeRateProviderTest {
  @Test
  fun `netAnnualRate uses the most recent gross yield on-or-before the date, net of expense once`() {
    // Given a sparse gross-yield series (in percent) and a 0.10pct SGOV expense
    val provider =
      RiskFreeRateProvider(
        grossYieldPctByDate =
          mapOf(
            LocalDate.of(2014, 6, 2) to 0.035,
            LocalDate.of(2025, 5, 1) to 4.2931,
          ),
        expensePct = 0.10,
      )

    // When asking for a date strictly after the latest print
    val rate = provider.netAnnualRate(LocalDate.of(2025, 5, 15))

    // Then it uses the 2025-05-01 print (no look-ahead) and subtracts the expense once,
    // returning a fraction: (4.2931 - 0.10) / 100
    assertEquals(0.0419310, rate, RF_EPSILON)
  }

  @Test
  fun `netAnnualRate floors at zero when the gross yield is below the expense (deep ZIRP)`() {
    // Given a deep-ZIRP gross yield (0.05pct) below the 0.10pct SGOV expense
    val provider =
      RiskFreeRateProvider(mapOf(LocalDate.of(2021, 3, 1) to 0.05), expensePct = 0.10)

    // When the net rate would be negative (0.05 - 0.10)
    val rate = provider.netAnnualRate(LocalDate.of(2021, 3, 15))

    // Then it floors at 0 — idle cash never carries a negative rate (SGOV fee-waiver behavior);
    // a real operator holds plain cash at 0% rather than a vehicle with known negative net carry
    assertEquals(0.0, rate, RF_EPSILON)
  }

  @Test
  fun `netAnnualRate is zero when no yield is known on-or-before the date`() {
    // Given a series whose earliest print is 2014, and an empty series
    val populated =
      RiskFreeRateProvider(mapOf(LocalDate.of(2014, 6, 2) to 0.035), expensePct = 0.10)
    val empty = RiskFreeRateProvider(emptyMap(), expensePct = 0.10)

    // When asking for a date before any print, or against an empty series
    // Then the rate is exactly zero — never extrapolated backwards, never a wrong guess
    assertEquals(0.0, populated.netAnnualRate(LocalDate.of(2000, 1, 1)), RF_EPSILON)
    assertEquals(0.0, empty.netAnnualRate(LocalDate.of(2025, 5, 1)), RF_EPSILON)
  }

  @Test
  fun `stepRate accrues calendar-day ACT-360 on the rate effective at the step end date`() {
    // Given a 3.6pct gross series (net 3.5pct after the 0.10pct expense) effective from a Friday
    val provider =
      RiskFreeRateProvider(mapOf(LocalDate.of(2025, 5, 2) to 3.6), expensePct = 0.10)
    val friday = LocalDate.of(2025, 5, 2)
    val monday = LocalDate.of(2025, 5, 5)

    // When accruing the step from Friday to Monday (3 calendar days — cash earns over the weekend)
    val step = provider.stepRate(friday, monday)

    // Then the accrual is netAnnualRate(end) * calendarDays / 360 = 0.035 * 3 / 360
    assertEquals(0.035 * 3.0 / 360.0, step, RF_EPSILON)
  }
}
