package com.skrymer.midgaard.integration

import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Acceptance-level test for the closest-prior-business-day FX lookup. The series cache
 * lets us answer arbitrary dates from one provider call per pair, but only if this
 * lookup correctly walks backwards through weekends + holidays.
 */
class ClosestRateAtOrBeforeTest {
    @Test
    fun `exact-date hit returns its rate`() {
        // Given: a series with the requested date present
        val series = mapOf(LocalDate.of(2024, 6, 14) to 1.5234)

        // When / Then
        assertEquals(1.5234, closestRateAtOrBefore(series, LocalDate.of(2024, 6, 14)))
    }

    @Test
    fun `Saturday lookup returns Friday close (1-day walk-back)`() {
        // Given: weekday close, asking for Saturday
        val series = mapOf(LocalDate.of(2024, 6, 14) to 1.5234) // Friday

        // When
        val rate = closestRateAtOrBefore(series, LocalDate.of(2024, 6, 15)) // Saturday

        // Then
        assertEquals(1.5234, rate)
    }

    @Test
    fun `Monday lookup returns Friday close when Mon is a holiday (3-day walk-back)`() {
        // Given: only Friday is in the series; Saturday + Sunday + Monday all missing
        val series = mapOf(LocalDate.of(2024, 6, 14) to 1.5234) // Friday

        // When
        val rate = closestRateAtOrBefore(series, LocalDate.of(2024, 6, 17)) // Monday (assume holiday)

        // Then
        assertEquals(1.5234, rate)
    }

    @Test
    fun `walk-back caps at 5 days — older gaps return null`() {
        // Given: series ends on a date 6+ days before the query
        val series = mapOf(LocalDate.of(2024, 6, 1) to 1.5)

        // When: querying 6 days later
        val rate = closestRateAtOrBefore(series, LocalDate.of(2024, 6, 7))

        // Then: walk-back of 5 days isn't enough, returns null rather than digging arbitrarily far
        // (5-day cap covers normal weekend + US holiday combinations but not multi-week gaps,
        // which would indicate a different problem the caller should surface)
        assertNull(rate)
    }

    @Test
    fun `empty series returns null without error`() {
        assertNull(closestRateAtOrBefore(emptyMap(), LocalDate.of(2024, 6, 14)))
    }

    @Test
    fun `date before earliest series entry returns null`() {
        // Given: series starts in 2020
        val series = mapOf(LocalDate.of(2020, 1, 1) to 1.0)

        // When: query 2019
        val rate = closestRateAtOrBefore(series, LocalDate.of(2019, 12, 31))

        // Then: walk-back goes forward in time from query date, not back to series; null.
        assertNull(rate)
    }
}
