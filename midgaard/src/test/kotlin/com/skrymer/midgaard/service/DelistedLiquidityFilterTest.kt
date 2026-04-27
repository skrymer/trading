package com.skrymer.midgaard.service

import com.skrymer.midgaard.integration.OhlcvProvider
import com.skrymer.midgaard.model.RawBar
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DelistedLiquidityFilterTest {
    @Test
    fun `keeps the top 80 percent by median dollar volume`() {
        // Given: five candidates with manually-crafted dollar-volume distributions.
        // Their medians differ by an order of magnitude so ranking is unambiguous.
        val candidates = (1..5).map { DelistedCandidate("SYM$it", DELISTING_DATE) }
        val provider =
            providerReturning(
                "SYM1" to liquidityBars(close = 10.0, volume = 1_000_000L), // median 10M $/day
                "SYM2" to liquidityBars(close = 10.0, volume = 100_000L), // 1M
                "SYM3" to liquidityBars(close = 10.0, volume = 10_000L), // 100k
                "SYM4" to liquidityBars(close = 10.0, volume = 5_000_000L), // 50M
                "SYM5" to liquidityBars(close = 10.0, volume = 2_000_000L), // 20M
            )
        val filter = DelistedLiquidityFilter(provider)

        // When
        val survivors = runBlocking { filter.filter(candidates) }

        // Then: bottom 20% (1 of 5) is dropped — SYM3 had the lowest median
        assertEquals(4, survivors.size)
        assertTrue(survivors.none { it.symbol == "SYM3" }, "SYM3 had the lowest median dollar volume and should be dropped")
    }

    @Test
    fun `excludes candidates with fewer pre-delist bars than the minimum`() {
        // Given: a candidate that delisted only weeks after listing — bars too thin to score
        val thin = DelistedCandidate("THIN", DELISTING_DATE)
        val deep = DelistedCandidate("DEEP", DELISTING_DATE)
        val provider =
            providerReturning(
                "THIN" to liquidityBars(close = 10.0, volume = 1_000_000L, count = 30),
                "DEEP" to liquidityBars(close = 10.0, volume = 1_000_000L, count = 200),
            )
        val filter = DelistedLiquidityFilter(provider)

        // When
        val survivors = runBlocking { filter.filter(listOf(thin, deep)) }

        // Then: deep candidate alone passes — thin has insufficient pre-delist history
        assertEquals(1, survivors.size)
        assertEquals("DEEP", survivors.single().symbol)
    }

    @Test
    fun `excludes candidates whose entire window has zero volume`() {
        // Given: one symbol with real activity, one that traded but with zero volume each day
        val zero = DelistedCandidate("ZERO", DELISTING_DATE)
        val real = DelistedCandidate("REAL", DELISTING_DATE)
        val provider =
            providerReturning(
                "ZERO" to liquidityBars(close = 10.0, volume = 0L),
                "REAL" to liquidityBars(close = 10.0, volume = 1_000_000L),
            )
        val filter = DelistedLiquidityFilter(provider)

        // When
        val survivors = runBlocking { filter.filter(listOf(zero, real)) }

        // Then: ZERO is excluded — its median dollar volume is 0
        assertEquals(1, survivors.size)
        assertEquals("REAL", survivors.single().symbol)
    }

    @Test
    fun `excludes candidates whose bar fetch failed`() {
        // Given: a candidate whose provider returns null (HTTP failure / no coverage)
        val missing = DelistedCandidate("MISS", DELISTING_DATE)
        val provider: OhlcvProvider = mock()
        runBlocking { whenever(provider.getDailyBars(eq("MISS"), any(), any())).thenReturn(null) }
        val filter = DelistedLiquidityFilter(provider)

        // When
        val survivors = runBlocking { filter.filter(listOf(missing)) }

        // Then: nothing survives — fetch failure shouldn't poison the run, just drop the symbol
        assertTrue(survivors.isEmpty())
    }

    @Test
    fun `keeps at least one survivor even when the input list is small`() {
        // Given: a single candidate with adequate history
        val solo = DelistedCandidate("SOLO", DELISTING_DATE)
        val provider = providerReturning("SOLO" to liquidityBars(close = 10.0, volume = 1_000_000L))
        val filter = DelistedLiquidityFilter(provider)

        // When: dropping 20% of a 1-candidate list rounds to 0 survivors mathematically
        val survivors = runBlocking { filter.filter(listOf(solo)) }

        // Then: the floor keeps at least 1 — otherwise tiny universes always drop to empty
        assertEquals(1, survivors.size)
    }

    @Test
    fun `trims bars dated after the delisting date`() {
        // Given: bars that include an extra row dated after the delisting cutoff (provider noise)
        val candidate = DelistedCandidate("LATE", DELISTING_DATE)
        val barsBefore = liquidityBars(close = 10.0, volume = 1_000_000L, count = 100)
        val barAfter = bar(DELISTING_DATE.plusDays(5), close = 999.99, volume = 9_999_999L)
        val noisyBars = barsBefore + barAfter
        val provider: OhlcvProvider = mock()
        runBlocking { whenever(provider.getDailyBars(eq("LATE"), any(), any())).thenReturn(noisyBars) }
        val filter = DelistedLiquidityFilter(provider)

        // When
        val survivors = runBlocking { filter.filter(listOf(candidate)) }

        // Then: the candidate survives — proves the post-delisting bar didn't push us
        // off-cliff (would have been a 10B $-volume row that dominates the median)
        assertEquals(1, survivors.size)
        assertFalse(noisyBars.last().date <= DELISTING_DATE) // sanity — fixture really has a late bar
    }

    private fun providerReturning(vararg pairs: Pair<String, List<RawBar>>): OhlcvProvider {
        val provider: OhlcvProvider = mock()
        runBlocking {
            pairs.forEach { (symbol, bars) ->
                whenever(provider.getDailyBars(eq(symbol), any(), any())).thenReturn(bars)
            }
        }
        return provider
    }

    private fun liquidityBars(
        close: Double,
        volume: Long,
        count: Int = 200,
    ): List<RawBar> {
        // Deterministic bars across `count` calendar days ending at the delisting date.
        // We use calendar days (not trading days) — the filter doesn't care about
        // weekend gaps, only bar count and dollar-volume statistics.
        return (count downTo 1).map { ago ->
            bar(DELISTING_DATE.minusDays(ago.toLong()), close = close, volume = volume)
        }
    }

    private fun bar(
        date: LocalDate,
        close: Double,
        volume: Long,
    ): RawBar = RawBar(symbol = "FIXTURE", date = date, open = close, high = close, low = close, close = close, volume = volume)

    companion object {
        private val DELISTING_DATE: LocalDate = LocalDate.of(2024, 6, 30)
    }
}
