package com.skrymer.midgaard.integration.eodhd

import com.skrymer.midgaard.integration.eodhd.dto.EodhdBarDto
import com.skrymer.midgaard.model.TreasuryYield
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals

class TreasuryYieldMapperTest {
    @Test
    fun `toYields maps each EOD bar close to a gross yield for the maturity`() {
        // Given EODHD gov-bond bars where close is the yield in percent (un-haircut)
        val bars =
            listOf(
                bar(date = "2007-06-01", close = 4.719),
                bar(date = "2014-06-02", close = 0.035),
            )

        // When mapping to the treasury-yield series for the US3M maturity
        val yields = TreasuryYieldMapper.toYields("US3M", bars, minDate = LocalDate.of(2000, 1, 1))

        // Then each bar's close becomes the gross yieldPct, keyed by maturity and date
        assertEquals(
            listOf(
                TreasuryYield("US3M", LocalDate.of(2007, 6, 1), 4.719),
                TreasuryYield("US3M", LocalDate.of(2014, 6, 2), 0.035),
            ),
            yields,
        )
    }

    private fun bar(
        date: String,
        close: Double,
    ) = EodhdBarDto(
        date = date,
        open = close,
        high = close,
        low = close,
        close = close,
        adjustedClose = close,
        volume = 0L,
    )
}
