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
    fun `toFundamentals merges income-statement and balance-sheet quarters by fiscal date`() {
        // Given a payload with one quarter present in both statements, filed after the fiscal-period end
        val response =
            EodhdFundamentalsResponse(
                financials =
                    financials(
                        income =
                            mapOf(
                                "2024-03-31" to
                                    incomeEntry(
                                        date = "2024-03-31",
                                        filingDate = "2024-05-02",
                                        grossProfit = "42000",
                                        totalRevenue = "90000",
                                        operatingIncome = "28000",
                                    ),
                            ),
                        balance =
                            mapOf("2024-03-31" to balanceEntry(date = "2024-03-31", filingDate = "2024-05-02", totalAssets = "350000")),
                    ),
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
    fun `toFundamentals returns empty when the Financials section is absent`() {
        // Given a fundamentals response with no Financials block (e.g. an ETF, or a non-financials fetch)
        val response = EodhdFundamentalsResponse()

        // When / Then
        assertTrue(response.toFundamentals("SPY").isEmpty())
    }

    @Test
    fun `toFundamentals keeps a quarter present in only one statement, leaving the other side null`() {
        // Given a fiscal date that appears in the income statement but not the balance sheet
        val response =
            EodhdFundamentalsResponse(
                financials =
                    EodhdFinancialsSection(
                        incomeStatement =
                            EodhdIncomeStatement(
                                quarterly =
                                    mapOf(
                                        "2024-03-31" to incomeEntry("2024-03-31", "2024-05-02", grossProfit = "42000"),
                                    ),
                            ),
                    ),
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
                financials =
                    EodhdFinancialsSection(
                        incomeStatement =
                            EodhdIncomeStatement(
                                quarterly =
                                    mapOf(
                                        "2024-03-31" to
                                            incomeEntry("2024-03-31", "2024-05-02", grossProfit = "42000", operatingIncome = null),
                                    ),
                            ),
                    ),
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
                financials =
                    EodhdFinancialsSection(
                        incomeStatement =
                            EodhdIncomeStatement(
                                quarterly =
                                    mapOf(
                                        "2024-03-31" to incomeEntry("2024-03-31", "2024-05-02"),
                                        "2024-09-30" to incomeEntry("2024-09-30", "2024-10-31"),
                                        "2024-06-30" to incomeEntry("2024-06-30", "2024-08-01"),
                                    ),
                            ),
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
    fun `a NA Financials section deserializes to null instead of throwing`() {
        // Given EODHD's literal "NA" string for a symbol with no statements (typical for ETFs)
        val json = """{ "General": { "Sector": "Financial Services" }, "Financials": "NA" }"""

        // When the full payload is parsed
        val response = jacksonObjectMapper().readValue(json, EodhdFundamentalsResponse::class.java)

        // Then the lenient deserializer drops Financials to null and the fetch survives
        assertNull(response.financials)
        assertTrue(response.toFundamentals("SPY").isEmpty())
    }

    private fun financials(
        income: Map<String, EodhdIncomeStatementEntry> = emptyMap(),
        balance: Map<String, EodhdBalanceSheetEntry> = emptyMap(),
    ) = EodhdFinancialsSection(
        incomeStatement = EodhdIncomeStatement(quarterly = income),
        balanceSheet = EodhdBalanceSheet(quarterly = balance),
    )

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
    ): EodhdBalanceSheetEntry =
        EodhdBalanceSheetEntry(
            date = date,
            filingDate = filingDate,
            totalAssets = totalAssets?.let { BigDecimal(it) },
            totalStockholderEquity = totalStockholderEquity?.let { BigDecimal(it) },
            totalCurrentAssets = totalCurrentAssets?.let { BigDecimal(it) },
            totalCurrentLiabilities = totalCurrentLiabilities?.let { BigDecimal(it) },
        )
}
