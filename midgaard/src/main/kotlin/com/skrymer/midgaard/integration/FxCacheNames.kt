package com.skrymer.midgaard.integration

import java.time.LocalDate

/**
 * Cache name constants shared by every `FxProvider` implementation. Owned by neither
 * `EodhdFxClient` nor `AlphaVantageFxClient` so either client can be deleted in the
 * future without breaking the other's `@Cacheable` annotations.
 */
object FxCacheNames {
    const val FX_CURRENT = "fxCurrent"
    const val FX_HISTORICAL_SERIES = "fxHistoricalSeries"
}

/**
 * Looks up `date` in the cached series, walking backwards up to 5 days when the exact
 * date is missing (weekends + US market holidays don't have FX prints — the closest
 * prior business-day close is the right answer for portfolio P&L calculations).
 *
 * Returns null when neither the exact date nor any of the 5 prior days are in the series
 * (e.g. date is older than the series' first entry, or the series is empty).
 */
fun closestRateAtOrBefore(
    series: Map<LocalDate, Double>,
    date: LocalDate,
): Double? {
    for (offset in 0L..5L) {
        series[date.minusDays(offset)]?.let { return it }
    }
    return null
}
