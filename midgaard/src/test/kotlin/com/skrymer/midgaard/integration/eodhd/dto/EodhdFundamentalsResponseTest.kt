package com.skrymer.midgaard.integration.eodhd.dto

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Parsing coverage for the EODHD `Financials` sections (ADR 0019 L1). A fundamental record is the
 * merge of one income-statement quarter and the same-fiscal-date balance-sheet quarter, stamped with
 * its `filing_date` — the point-in-time visibility key (CONTEXT *Point-in-time fundamentals*).
 */
class EodhdFundamentalsResponseTest {
    @Test
    fun `toFundamentals parses EODHD's flat colon-delimited Financials filter keys`() {
        // Given EODHD's ACTUAL response to filter=...,Financials::Income_Statement::quarterly,
        // Financials::Balance_Sheet::quarterly — the nested paths come back as LITERAL flat top-level
        // keys (no nested "Financials" object), each a map keyed by fiscal date.
        val json =
            """
            {
              "Financials::Income_Statement::quarterly": {
                "2024-03-31": { "date": "2024-03-31", "filing_date": "2024-05-02",
                  "grossProfit": "42000", "totalRevenue": "90000", "operatingIncome": "28000" }
              },
              "Financials::Balance_Sheet::quarterly": {
                "2024-03-31": { "date": "2024-03-31", "filing_date": "2024-05-02", "totalAssets": "350000" }
              }
            }
            """.trimIndent()

        // When parsed and mapped
        val response = jacksonObjectMapper().readValue(json, EodhdFundamentalsResponse::class.java)
        val fundamentals = response.toFundamentals("AAPL")

        // Then the flat keys are read and merged into one record
        assertEquals(1, fundamentals.size)
        val f = fundamentals.first()
        assertEquals(LocalDate.of(2024, 3, 31), f.fiscalDateEnding)
        assertEquals(LocalDate.of(2024, 5, 2), f.filingDate)
        assertEquals(BigDecimal("42000"), f.grossProfit)
        assertEquals(BigDecimal("90000"), f.totalRevenue)
        assertEquals(BigDecimal("28000"), f.operatingIncome)
        assertEquals(BigDecimal("350000"), f.totalAssets)
    }

    @Test
    fun `toFundamentals merges income-statement and balance-sheet quarters by fiscal date`() {
        // Given one quarter present in both statements, filed after the fiscal-period end
        val response =
            EodhdFundamentalsResponse(
                incomeStatementQuarterly =
                    mapOf(
                        "2024-03-31" to
                            incomeEntry(
                                "2024-03-31",
                                "2024-05-02",
                                grossProfit = "42000",
                                totalRevenue = "90000",
                                operatingIncome = "28000",
                            ),
                    ),
                balanceSheetQuarterly =
                    mapOf("2024-03-31" to balanceEntry("2024-03-31", "2024-05-02", totalAssets = "350000")),
            )

        // When
        val fundamentals = response.toFundamentals("AAPL")

        // Then the merged record carries both statements' line items, keyed by fiscal date and stamped with filing_date
        assertEquals(1, fundamentals.size)
        val f = fundamentals.first()
        assertEquals("AAPL", f.symbol)
        assertEquals(LocalDate.of(2024, 3, 31), f.fiscalDateEnding)
        assertEquals(LocalDate.of(2024, 5, 2), f.filingDate)
        assertEquals(BigDecimal("42000"), f.grossProfit)
        assertEquals(BigDecimal("90000"), f.totalRevenue)
        assertEquals(BigDecimal("28000"), f.operatingIncome)
        assertEquals(BigDecimal("350000"), f.totalAssets)
    }

    @Test
    fun `toFundamentals reads split-adjusted shares outstanding from the balance sheet`() {
        // Given a balance-sheet quarter carrying commonStockSharesOutstanding (16B, current/split-adjusted basis)
        val response =
            EodhdFundamentalsResponse(
                balanceSheetQuarterly =
                    mapOf(
                        "2024-03-31" to
                            balanceEntry(
                                "2024-03-31",
                                "2024-05-02",
                                commonStockSharesOutstanding = "16000000000",
                            ),
                    ),
            )

        // When mapped
        val f = response.toFundamentals("AAPL").single()

        // Then the share count is carried onto the fundamental, the share leg of the point-in-time cap (ADR 0027)
        assertEquals(16_000_000_000L, f.sharesOutstanding)
    }

    @Test
    fun `toFundamentals leaves shares outstanding null when the balance sheet omits it`() {
        // Given a balance-sheet quarter with no commonStockSharesOutstanding field
        val response =
            EodhdFundamentalsResponse(
                balanceSheetQuarterly =
                    mapOf("2024-03-31" to balanceEntry("2024-03-31", "2024-05-02", totalAssets = "350000")),
            )

        // When mapped
        val f = response.toFundamentals("AAPL").single()

        // Then shares outstanding is null — the name simply has no cap reading
        assertNull(f.sharesOutstanding)
    }

    @Test
    fun `toFundamentals rounds the EODHD share-count float artifact to a whole share`() {
        // Given EODHD's float-precision artifact for ~17.11B shares (note the trailing ...999998)
        val response =
            EodhdFundamentalsResponse(
                balanceSheetQuarterly =
                    mapOf(
                        "2024-03-31" to
                            balanceEntry(
                                "2024-03-31",
                                "2024-05-02",
                                commonStockSharesOutstanding = "17113687999.999998",
                            ),
                    ),
            )

        // When mapped
        val f = response.toFundamentals("AAPL").single()

        // Then it is rounded to the nearest whole share, not truncated or stored fractionally
        assertEquals(17_113_688_000L, f.sharesOutstanding)
    }

    @Test
    fun `toFundamentals returns empty when the Financials keys are absent`() {
        // Given a fundamentals response with no Financials keys (e.g. an ETF, or a non-financials fetch)
        val response = EodhdFundamentalsResponse()

        // When / Then
        assertTrue(response.toFundamentals("SPY").isEmpty())
    }

    @Test
    fun `toFundamentals keeps a quarter present in only one statement, leaving the other side null`() {
        // Given a fiscal date that appears in the income statement but not the balance sheet
        val response =
            EodhdFundamentalsResponse(
                incomeStatementQuarterly = mapOf("2024-03-31" to incomeEntry("2024-03-31", "2024-05-02", grossProfit = "42000")),
            )

        // When
        val f = response.toFundamentals("AAPL").single()

        // Then the income leg is populated and the absent balance-sheet leg is null, not an error
        assertEquals(BigDecimal("42000"), f.grossProfit)
        assertNull(f.totalAssets)
    }

    @Test
    fun `toFundamentals maps an omitted line item to null while keeping the record`() {
        // Given an income-statement quarter that omits operatingIncome
        val response =
            EodhdFundamentalsResponse(
                incomeStatementQuarterly =
                    mapOf("2024-03-31" to incomeEntry("2024-03-31", "2024-05-02", grossProfit = "42000", operatingIncome = null)),
            )

        // When
        val f = response.toFundamentals("AAPL").single()

        // Then present fields survive and the omitted one is null
        assertEquals(BigDecimal("42000"), f.grossProfit)
        assertNull(f.operatingIncome)
    }

    @Test
    fun `toFundamentals sorts quarters newest fiscal date first`() {
        // Given three quarters in arbitrary map order
        val response =
            EodhdFundamentalsResponse(
                incomeStatementQuarterly =
                    mapOf(
                        "2024-03-31" to incomeEntry("2024-03-31", "2024-05-02"),
                        "2024-09-30" to incomeEntry("2024-09-30", "2024-10-31"),
                        "2024-06-30" to incomeEntry("2024-06-30", "2024-08-01"),
                    ),
            )

        // When
        val dates = response.toFundamentals("AAPL").map { it.fiscalDateEnding }

        // Then newest-first
        assertEquals(
            listOf(LocalDate.of(2024, 9, 30), LocalDate.of(2024, 6, 30), LocalDate.of(2024, 3, 31)),
            dates,
        )
    }

    @Test
    fun `NA Financials filter keys deserialize to null instead of throwing`() {
        // Given EODHD's literal "NA" string for the flat Financials keys (an ETF with no statements)
        val json =
            """
            { "General": { "Sector": "Financial Services" },
              "Financials::Income_Statement::quarterly": "NA",
              "Financials::Balance_Sheet::quarterly": "NA" }
            """.trimIndent()

        // When the full payload is parsed
        val response = jacksonObjectMapper().readValue(json, EodhdFundamentalsResponse::class.java)

        // Then the lenient deserializers drop the "NA" keys to null and the (shared) fetch survives
        assertNull(response.incomeStatementQuarterly)
        assertNull(response.balanceSheetQuarterly)
        assertTrue(response.toFundamentals("SPY").isEmpty())
    }

    @Test
    fun `a mix of NA and present flat keys parses the present one without cross-corruption`() {
        // Given one flat key is "NA" (thinly-covered symbol) and the other is a real quarterly map
        val json =
            """
            { "Financials::Income_Statement::quarterly": "NA",
              "Financials::Balance_Sheet::quarterly": {
                "2024-03-31": { "date": "2024-03-31", "filing_date": "2024-05-02", "totalAssets": "350000" }
              } }
            """.trimIndent()

        // When parsed — the "NA" key's skipChildren must not corrupt the next key's position
        val response = jacksonObjectMapper().readValue(json, EodhdFundamentalsResponse::class.java)
        val f = response.toFundamentals("X").single()

        // Then the present balance-sheet leg is read and the absent income leg is null
        assertNull(f.grossProfit)
        assertEquals(BigDecimal("350000"), f.totalAssets)
    }

    @Test
    fun `numeric line items coerce to BigDecimal`() {
        // Given EODHD sends a line item as a JSON number rather than a string
        val json =
            """
            { "Financials::Income_Statement::quarterly": {
                "2024-03-31": { "date": "2024-03-31", "filing_date": "2024-05-02", "grossProfit": 42000.5 }
              } }
            """.trimIndent()

        // When parsed
        val response = jacksonObjectMapper().readValue(json, EodhdFundamentalsResponse::class.java)

        // Then Jackson coerces the number into BigDecimal (the KDoc's "strings or numbers" claim)
        assertEquals(BigDecimal("42000.5"), response.toFundamentals("X").single().grossProfit)
    }

    private fun incomeEntry(
        date: String?,
        filingDate: String?,
        grossProfit: String? = null,
        costOfRevenue: String? = null,
        totalRevenue: String? = null,
        operatingIncome: String? = null,
        netIncome: String? = null,
    ): EodhdIncomeStatementEntry =
        EodhdIncomeStatementEntry(
            date = date,
            filingDate = filingDate,
            grossProfit = grossProfit?.let { BigDecimal(it) },
            costOfRevenue = costOfRevenue?.let { BigDecimal(it) },
            totalRevenue = totalRevenue?.let { BigDecimal(it) },
            operatingIncome = operatingIncome?.let { BigDecimal(it) },
            netIncome = netIncome?.let { BigDecimal(it) },
        )

    private fun balanceEntry(
        date: String?,
        filingDate: String?,
        totalAssets: String? = null,
        totalStockholderEquity: String? = null,
        totalCurrentAssets: String? = null,
        totalCurrentLiabilities: String? = null,
        commonStockSharesOutstanding: String? = null,
    ): EodhdBalanceSheetEntry =
        EodhdBalanceSheetEntry(
            date = date,
            filingDate = filingDate,
            totalAssets = totalAssets?.let { BigDecimal(it) },
            totalStockholderEquity = totalStockholderEquity?.let { BigDecimal(it) },
            totalCurrentAssets = totalCurrentAssets?.let { BigDecimal(it) },
            totalCurrentLiabilities = totalCurrentLiabilities?.let { BigDecimal(it) },
            commonStockSharesOutstanding = commonStockSharesOutstanding?.let { BigDecimal(it) },
        )
}
