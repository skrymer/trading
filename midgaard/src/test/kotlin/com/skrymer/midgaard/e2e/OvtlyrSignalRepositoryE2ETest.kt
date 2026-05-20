package com.skrymer.midgaard.e2e

import com.skrymer.midgaard.model.OvtlyrSignal
import com.skrymer.midgaard.model.OvtlyrSignalType
import com.skrymer.midgaard.repository.OvtlyrSignalRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import kotlin.test.assertEquals

class OvtlyrSignalRepositoryE2ETest : AbstractIntegrationTest() {
    @Autowired
    private lateinit var repository: OvtlyrSignalRepository

    @Test
    fun `saved ovtlyr signals are returned by findBySymbol, ordered by date`() {
        // Given: two signals for one symbol, persisted newest-first
        val sell = OvtlyrSignal("OSRT", LocalDate.of(2026, 5, 18), OvtlyrSignalType.SELL)
        val buy = OvtlyrSignal("OSRT", LocalDate.of(2026, 5, 11), OvtlyrSignalType.BUY)
        repository.upsert(listOf(sell, buy))

        // When
        val result = repository.findBySymbol("OSRT")

        // Then: both round-trip, oldest first
        assertEquals(listOf(buy, sell), result)
    }

    @Test
    fun `upsert is idempotent — re-running on an existing key replaces, never duplicates`() {
        // Given: a BUY signal persisted for one symbol+date
        val date = LocalDate.of(2026, 5, 12)
        repository.upsert(listOf(OvtlyrSignal("OSU2", date, OvtlyrSignalType.BUY)))

        // When: the same (symbol, date) is upserted again with a different signal
        repository.upsert(listOf(OvtlyrSignal("OSU2", date, OvtlyrSignalType.SELL)))

        // Then: still one row, signal updated to the latest value
        val result = repository.findBySymbol("OSU2")
        assertEquals(listOf(OvtlyrSignal("OSU2", date, OvtlyrSignalType.SELL)), result)
    }
}
