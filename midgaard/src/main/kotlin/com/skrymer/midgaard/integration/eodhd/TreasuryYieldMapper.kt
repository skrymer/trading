package com.skrymer.midgaard.integration.eodhd

import com.skrymer.midgaard.integration.eodhd.dto.EodhdBarDto
import com.skrymer.midgaard.model.TreasuryYield
import java.time.LocalDate

/**
 * Maps EODHD gov-bond EOD bars into a treasury-yield series. The bond endpoint returns the
 * standard OHLCV bar shape where `close` is the yield in percent; the yield is taken gross
 * (no split/dividend adjustment factor, unlike equity bars) and stored as-is. See ADR 0016.
 */
object TreasuryYieldMapper {
    fun toYields(
        maturity: String,
        bars: List<EodhdBarDto>,
        minDate: LocalDate = LocalDate.of(2000, 1, 1),
    ): List<TreasuryYield> =
        bars
            .mapNotNull { bar ->
                val date = runCatching { LocalDate.parse(bar.date) }.getOrNull()?.takeIf { !it.isBefore(minDate) }
                val yieldPct = bar.close
                if (date == null || yieldPct == null) null else TreasuryYield(maturity, date, yieldPct)
            }.sortedBy { it.date }
}
