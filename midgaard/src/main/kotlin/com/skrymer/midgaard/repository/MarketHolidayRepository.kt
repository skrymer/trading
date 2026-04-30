package com.skrymer.midgaard.repository

import com.skrymer.midgaard.jooq.tables.references.MARKET_HOLIDAYS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class MarketHolidayRepository(
    private val dsl: DSLContext,
) {
    // Cached lazily — bulk-ingest does 4000+ symbols × 10-way parallelism
    // and the table is statically seeded (V7), so one SELECT per JVM lifetime
    // beats one per symbol. A new Flyway migration with more holidays implies
    // a restart, which busts the lazy.
    private val cache: Map<String, Set<LocalDate>> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { fetchAll() }

    // Set, not List — caller does an O(1) `contains` per bar on the ingestion hot path.
    fun findHolidayDates(exchange: String = DEFAULT_EXCHANGE): Set<LocalDate> = cache[exchange] ?: emptySet()

    private fun fetchAll(): Map<String, Set<LocalDate>> =
        dsl
            .select(MARKET_HOLIDAYS.EXCHANGE, MARKET_HOLIDAYS.QUOTE_DATE)
            .from(MARKET_HOLIDAYS)
            .fetch()
            .groupBy({ it[MARKET_HOLIDAYS.EXCHANGE]!! }, { it[MARKET_HOLIDAYS.QUOTE_DATE]!! })
            .mapValues { (_, dates) -> dates.toSet() }

    companion object {
        const val DEFAULT_EXCHANGE = "US"
    }
}
