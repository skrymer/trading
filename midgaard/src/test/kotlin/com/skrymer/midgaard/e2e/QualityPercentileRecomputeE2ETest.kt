package com.skrymer.midgaard.e2e

import com.skrymer.midgaard.model.Fundamental
import com.skrymer.midgaard.model.Quote
import com.skrymer.midgaard.repository.FundamentalsRepository
import com.skrymer.midgaard.repository.QuoteRepository
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Integration coverage for the SQL cross-sectional quality-percentile recompute (ADR 0019 L2). Exercises
 * the as-of join + trailing-4-filing TTM against a real Postgres: the gross-profitability metric
 * (grossProfit_TTM / totalAssets_asof), midpoint ranking, the degeneracy exclusions, the peer-count
 * floor, and the 2000-01-01 calendar floor (CONTEXT *Gross-profitability quality percentile*).
 */
class QualityPercentileRecomputeE2ETest : AbstractIntegrationTest() {
    @Autowired
    private lateinit var quoteRepository: QuoteRepository

    @Autowired
    private lateinit var fundamentalsRepository: FundamentalsRepository

    @Autowired
    private lateinit var dsl: DSLContext

    private val earliest = LocalDate.of(2000, 1, 1)

    @BeforeEach
    fun clear() {
        dsl.execute("DELETE FROM quotes")
        dsl.execute("DELETE FROM fundamentals")
    }

    /** One quote row on [date] so the recompute has a row to write a percentile onto. */
    private fun quote(
        symbol: String,
        date: LocalDate,
    ) = Quote(
        symbol = symbol,
        date = date,
        open = BigDecimal.ONE,
        high = BigDecimal.ONE,
        low = BigDecimal.ONE,
        close = BigDecimal.ONE,
        volume = 0L,
    )

    /** Four equally-spaced quarterly filings whose gross profit sums to [ttmGrossProfit]; constant total assets. */
    private fun fourQuarters(
        symbol: String,
        ttmGrossProfit: Double,
        totalAssets: Double,
    ): List<Fundamental> {
        val perQuarter = BigDecimal.valueOf(ttmGrossProfit / 4.0)
        val fiscalDates = listOf("2023-03-31", "2023-06-30", "2023-09-30", "2023-12-31")
        val filingDates = listOf("2023-05-01", "2023-08-01", "2023-11-01", "2024-02-01")
        return fiscalDates.indices.map { i ->
            Fundamental(
                symbol = symbol,
                fiscalDateEnding = LocalDate.parse(fiscalDates[i]),
                filingDate = LocalDate.parse(filingDates[i]),
                grossProfit = perQuarter,
                totalAssets = BigDecimal.valueOf(totalAssets),
            )
        }
    }

    private fun qualityOn(
        symbol: String,
        date: LocalDate,
    ): Double? =
        quoteRepository
            .findBySymbol(symbol)
            .first { it.date == date }
            .qualityPercentile
            ?.toDouble()

    private fun filing(
        symbol: String,
        fiscal: String,
        filing: String,
        grossProfit: Double?,
        totalAssets: Double?,
    ) = Fundamental(
        symbol = symbol,
        fiscalDateEnding = LocalDate.parse(fiscal),
        filingDate = LocalDate.parse(filing),
        grossProfit = grossProfit?.let { BigDecimal.valueOf(it) },
        totalAssets = totalAssets?.let { BigDecimal.valueOf(it) },
    )

    /** Four quarters for [symbol] with explicit per-quarter gross profit and total assets. */
    private fun quarters(
        symbol: String,
        grossProfits: List<Double>,
        totalAssets: List<Double>,
    ): List<Fundamental> {
        val fiscalDates = listOf("2023-03-31", "2023-06-30", "2023-09-30", "2023-12-31")
        val filingDates = listOf("2023-05-01", "2023-08-01", "2023-11-01", "2024-02-01")
        return fiscalDates.indices.map { i -> filing(symbol, fiscalDates[i], filingDates[i], grossProfits[i], totalAssets[i]) }
    }

    @Test
    fun `ranks symbols by trailing-twelve-month gross profitability into midpoint percentiles`() {
        // Given three symbols whose TTM gross-profitability is QLOW 0.04 < QMID 0.08 < QHIGH 0.12,
        // all four quarters filed before the trading date
        val d = LocalDate.of(2024, 3, 1)
        quoteRepository.upsertQuotes(listOf(quote("QLOW", d), quote("QMID", d), quote("QHIGH", d)))
        fundamentalsRepository.upsert(fourQuarters("QLOW", ttmGrossProfit = 40.0, totalAssets = 1000.0))
        fundamentalsRepository.upsert(fourQuarters("QMID", ttmGrossProfit = 80.0, totalAssets = 1000.0))
        fundamentalsRepository.upsert(fourQuarters("QHIGH", ttmGrossProfit = 120.0, totalAssets = 1000.0))

        // When the pass recomputes with no peer floor
        val written = quoteRepository.recomputeQualityPercentiles(earliest, minPeers = 1, earliestDate = earliest)

        // Then the three rows get midpoint percentiles ordered by gross profitability
        assertEquals(3, written)
        assertEquals(100.0 * 0.5 / 3, qualityOn("QLOW", d)!!, 1e-3)
        assertEquals(100.0 * 1.5 / 3, qualityOn("QMID", d)!!, 1e-3)
        assertEquals(100.0 * 2.5 / 3, qualityOn("QHIGH", d)!!, 1e-3)
    }

    @Test
    fun `total assets is the latest filing point-in-time value, not the sum across quarters`() {
        // Given two symbols whose ranking flips depending on whether total assets is summed or point-in-time.
        // PIT: TTM gross 100, total assets [1000,1000,1000,100] → point-in-time 100/100 = 1.0; summed 100/3100.
        // REF: TTM gross 50, total assets [100,100,100,100]     → point-in-time 50/100  = 0.5; summed 50/400.
        // Point-in-time: PIT 1.0 > REF 0.5. Summed: PIT 0.032 < REF 0.125 (ordering would FLIP).
        val d = LocalDate.of(2024, 3, 1)
        quoteRepository.upsertQuotes(listOf(quote("QPIT", d), quote("QREF", d)))
        fundamentalsRepository.upsert(
            quarters("QPIT", grossProfits = listOf(25.0, 25.0, 25.0, 25.0), totalAssets = listOf(1000.0, 1000.0, 1000.0, 100.0)),
        )
        fundamentalsRepository.upsert(
            quarters("QREF", grossProfits = listOf(12.5, 12.5, 12.5, 12.5), totalAssets = listOf(100.0, 100.0, 100.0, 100.0)),
        )

        // When
        quoteRepository.recomputeQualityPercentiles(earliest, minPeers = 1, earliestDate = earliest)

        // Then PIT ranks above REF — only true if the denominator is the latest filing's 100, not the 3100 sum
        assertEquals(100.0 * 1.5 / 2, qualityOn("QPIT", d)!!, 1e-3)
        assertEquals(100.0 * 0.5 / 2, qualityOn("QREF", d)!!, 1e-3)
    }

    @Test
    fun `a negative trailing gross profit is kept and ranks at the bottom, not excluded`() {
        // Given a loss-making symbol (TTM gross −40) alongside a profitable one (TTM gross 80)
        val d = LocalDate.of(2024, 3, 1)
        quoteRepository.upsertQuotes(listOf(quote("QNEG", d), quote("QPOS", d)))
        fundamentalsRepository.upsert(
            quarters("QNEG", grossProfits = listOf(-10.0, -10.0, -10.0, -10.0), totalAssets = listOf(1000.0, 1000.0, 1000.0, 1000.0)),
        )
        fundamentalsRepository.upsert(
            quarters("QPOS", grossProfits = listOf(20.0, 20.0, 20.0, 20.0), totalAssets = listOf(1000.0, 1000.0, 1000.0, 1000.0)),
        )

        // When
        quoteRepository.recomputeQualityPercentiles(earliest, minPeers = 1, earliestDate = earliest)

        // Then the loss-maker is ranked (not excluded) and sits at the bottom of the cross-section
        assertEquals(100.0 * 0.5 / 2, qualityOn("QNEG", d)!!, 1e-3)
        assertEquals(100.0 * 1.5 / 2, qualityOn("QPOS", d)!!, 1e-3)
    }

    @Test
    fun `a symbol with fewer than four quarterly filings is excluded`() {
        // Given one symbol with only three filings and one with the full four
        val d = LocalDate.of(2024, 3, 1)
        quoteRepository.upsertQuotes(listOf(quote("QTHREE", d), quote("QFOUR", d)))
        fundamentalsRepository.upsert(
            listOf(
                filing("QTHREE", "2023-06-30", "2023-08-01", grossProfit = 10.0, totalAssets = 1000.0),
                filing("QTHREE", "2023-09-30", "2023-11-01", grossProfit = 10.0, totalAssets = 1000.0),
                filing("QTHREE", "2023-12-31", "2024-02-01", grossProfit = 10.0, totalAssets = 1000.0),
            ),
        )
        fundamentalsRepository.upsert(
            quarters("QFOUR", grossProfits = listOf(10.0, 10.0, 10.0, 10.0), totalAssets = listOf(1000.0, 1000.0, 1000.0, 1000.0)),
        )

        // When
        quoteRepository.recomputeQualityPercentiles(earliest, minPeers = 1, earliestDate = earliest)

        // Then the three-filing symbol has no TTM (fails closed); only the four-filing symbol is ranked
        assertNull(qualityOn("QTHREE", d))
        assertEquals(100.0 * 0.5 / 1, qualityOn("QFOUR", d)!!, 1e-3)
    }

    @Test
    fun `a missing gross-profit quarter inside the trailing window excludes the symbol`() {
        // Given four filings where one quarter is missing gross profit (balance-sheet-only quarter)
        val d = LocalDate.of(2024, 3, 1)
        quoteRepository.upsertQuotes(listOf(quote("QGAP", d)))
        fundamentalsRepository.upsert(
            quarters("QGAP", grossProfits = listOf(10.0, 10.0, 10.0, 10.0), totalAssets = listOf(1000.0, 1000.0, 1000.0, 1000.0))
                .mapIndexed { i, f -> if (i == 1) f.copy(grossProfit = null) else f },
        )

        // When
        quoteRepository.recomputeQualityPercentiles(earliest, minPeers = 1, earliestDate = earliest)

        // Then the incomplete TTM fails closed
        assertNull(qualityOn("QGAP", d))
    }

    @Test
    fun `a non-positive total assets excludes the symbol`() {
        // Given a symbol whose latest filing reports zero total assets
        val d = LocalDate.of(2024, 3, 1)
        quoteRepository.upsertQuotes(listOf(quote("QZERO", d)))
        fundamentalsRepository.upsert(
            quarters("QZERO", grossProfits = listOf(10.0, 10.0, 10.0, 10.0), totalAssets = listOf(1000.0, 1000.0, 1000.0, 0.0)),
        )

        // When
        quoteRepository.recomputeQualityPercentiles(earliest, minPeers = 1, earliestDate = earliest)

        // Then the degenerate denominator fails closed
        assertNull(qualityOn("QZERO", d))
    }

    @Test
    fun `as-of join ignores a filing whose filing date is after the trading date`() {
        // Given three filings visible before D and a fourth filed AFTER D
        val d = LocalDate.of(2024, 1, 1)
        quoteRepository.upsertQuotes(listOf(quote("QAOF", d)))
        fundamentalsRepository.upsert(
            listOf(
                filing("QAOF", "2023-03-31", "2023-05-01", grossProfit = 10.0, totalAssets = 1000.0),
                filing("QAOF", "2023-06-30", "2023-08-01", grossProfit = 10.0, totalAssets = 1000.0),
                filing("QAOF", "2023-09-30", "2023-11-01", grossProfit = 10.0, totalAssets = 1000.0),
                filing("QAOF", "2023-12-31", "2024-02-01", grossProfit = 10.0, totalAssets = 1000.0),
            ),
        )

        // When the pass runs as of D (the fourth filing's filing_date 2024-02-01 is after D)
        quoteRepository.recomputeQualityPercentiles(earliest, minPeers = 1, earliestDate = earliest)

        // Then only three filings are visible → no TTM → fail closed (future filing is not counted)
        assertNull(qualityOn("QAOF", d))
    }

    @Test
    fun `omits a date whose qualifying peer count is below the floor`() {
        // Given only two symbols qualify on D but the floor is three
        val d = LocalDate.of(2024, 3, 1)
        quoteRepository.upsertQuotes(listOf(quote("QPA", d), quote("QPB", d)))
        fundamentalsRepository.upsert(
            quarters("QPA", grossProfits = listOf(10.0, 10.0, 10.0, 10.0), totalAssets = listOf(1000.0, 1000.0, 1000.0, 1000.0)),
        )
        fundamentalsRepository.upsert(
            quarters("QPB", grossProfits = listOf(20.0, 20.0, 20.0, 20.0), totalAssets = listOf(1000.0, 1000.0, 1000.0, 1000.0)),
        )

        // When recomputed with a peer floor of 3
        quoteRepository.recomputeQualityPercentiles(earliest, minPeers = 3, earliestDate = earliest)

        // Then a thin cross-section yields no percentile
        assertNull(qualityOn("QPA", d))
        assertNull(qualityOn("QPB", d))
    }

    @Test
    fun `a trading date before the calendar floor is never ranked`() {
        // Given a fully-qualified symbol but a trading date before 2000-01-01
        val d = LocalDate.of(1999, 6, 1)
        quoteRepository.upsertQuotes(listOf(quote("QOLD", d)))
        fundamentalsRepository.upsert(
            listOf(
                filing("QOLD", "1998-03-31", "1998-05-01", grossProfit = 10.0, totalAssets = 1000.0),
                filing("QOLD", "1998-06-30", "1998-08-01", grossProfit = 10.0, totalAssets = 1000.0),
                filing("QOLD", "1998-09-30", "1998-11-01", grossProfit = 10.0, totalAssets = 1000.0),
                filing("QOLD", "1998-12-31", "1999-02-01", grossProfit = 10.0, totalAssets = 1000.0),
            ),
        )

        // When recomputed with the standard 2000-01-01 floor
        quoteRepository.recomputeQualityPercentiles(earliest, minPeers = 1, earliestDate = earliest)

        // Then the pre-2000 survivorship-tilted date is excluded
        assertNull(qualityOn("QOLD", d))
    }
}
