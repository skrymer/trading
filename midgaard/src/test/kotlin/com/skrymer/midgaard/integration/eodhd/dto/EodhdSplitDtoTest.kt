package com.skrymer.midgaard.integration.eodhd.dto

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Parsing coverage for EODHD's `/splits` rows. The provider returns the split as a
 * `"numerator/denominator"` string (e.g. `"4.000000/1.000000"`); [EodhdSplitDto.toSplit] turns it into a
 * numeric new-shares-per-old [Split.ratio], the leg of the cumulative split factor k(t) (ADR 0027).
 */
class EodhdSplitDtoTest {
    private val minDate = LocalDate.of(2000, 1, 1)

    @Test
    fun `toSplit parses a forward split ratio string into new-shares-per-old`() {
        // Given EODHD's 4:1 forward split for AAPL on 2020-08-31
        val dto = EodhdSplitDto(date = "2020-08-31", split = "4.000000/1.000000")

        // When mapped
        val split = dto.toSplit("AAPL", minDate)!!

        // Then the ex-date and ratio carry through (4 new shares per old)
        assertEquals(LocalDate.of(2020, 8, 31), split.exDate)
        assertEquals(4.0, split.ratio, 0.0)
    }

    @Test
    fun `toSplit maps a reverse split to a fractional ratio`() {
        // Given a 1:8 reverse split (8 old shares become 1)
        val dto = EodhdSplitDto(date = "2011-07-01", split = "1.000000/8.000000")

        // When mapped
        val split = dto.toSplit("XYZ", minDate)!!

        // Then the ratio is 1/8 = 0.125 (fewer shares per old)
        assertEquals(0.125, split.ratio, 0.0)
    }

    @ParameterizedTest
    @ValueSource(strings = ["4.000000/0.000000", "garbage", "4.000000", "0.000000/1.000000", "4/0/1"])
    fun `toSplit drops a malformed or zero-denominator ratio`(raw: String) {
        // Given a row whose split string can't yield a usable positive ratio
        val dto = EodhdSplitDto(date = "2020-08-31", split = raw)

        // When / Then: the row is dropped (null) rather than crashing the fetch or producing a NaN/Inf ratio
        assertNull(dto.toSplit("AAPL", minDate))
    }
}
