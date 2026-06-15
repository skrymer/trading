package com.skrymer.midgaard.repository

import com.skrymer.midgaard.jooq.tables.references.FUNDAMENTALS
import com.skrymer.midgaard.model.Fundamental
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class FundamentalsRepository(
    private val dsl: DSLContext,
) {
    fun findBySymbol(symbol: String): List<Fundamental> =
        dsl
            .selectFrom(FUNDAMENTALS)
            .where(FUNDAMENTALS.SYMBOL.eq(symbol))
            .orderBy(FUNDAMENTALS.FISCAL_DATE_ENDING.asc())
            .fetch()
            .map { record ->
                Fundamental(
                    symbol = record.symbol,
                    fiscalDateEnding = record.fiscalDateEnding,
                    filingDate = record.filingDate,
                    grossProfit = record.grossProfit,
                    costOfRevenue = record.costOfRevenue,
                    totalRevenue = record.totalRevenue,
                    operatingIncome = record.operatingIncome,
                    netIncome = record.netIncome,
                    totalAssets = record.totalAssets,
                    totalStockholderEquity = record.totalStockholderEquity,
                    totalCurrentAssets = record.totalCurrentAssets,
                    totalCurrentLiabilities = record.totalCurrentLiabilities,
                    sharesOutstanding = record.sharesOutstanding,
                )
            }

    @Transactional
    fun upsert(fundamentals: List<Fundamental>) {
        if (fundamentals.isEmpty()) return

        dsl.batched { ctx ->
            for (f in fundamentals) {
                ctx
                    .dsl()
                    .insertInto(FUNDAMENTALS)
                    .set(FUNDAMENTALS.SYMBOL, f.symbol)
                    .set(FUNDAMENTALS.FISCAL_DATE_ENDING, f.fiscalDateEnding)
                    .set(FUNDAMENTALS.FILING_DATE, f.filingDate)
                    .set(FUNDAMENTALS.GROSS_PROFIT, f.grossProfit)
                    .set(FUNDAMENTALS.COST_OF_REVENUE, f.costOfRevenue)
                    .set(FUNDAMENTALS.TOTAL_REVENUE, f.totalRevenue)
                    .set(FUNDAMENTALS.OPERATING_INCOME, f.operatingIncome)
                    .set(FUNDAMENTALS.NET_INCOME, f.netIncome)
                    .set(FUNDAMENTALS.TOTAL_ASSETS, f.totalAssets)
                    .set(FUNDAMENTALS.TOTAL_STOCKHOLDER_EQUITY, f.totalStockholderEquity)
                    .set(FUNDAMENTALS.TOTAL_CURRENT_ASSETS, f.totalCurrentAssets)
                    .set(FUNDAMENTALS.TOTAL_CURRENT_LIABILITIES, f.totalCurrentLiabilities)
                    .set(FUNDAMENTALS.SHARES_OUTSTANDING, f.sharesOutstanding)
                    .onConflict(FUNDAMENTALS.SYMBOL, FUNDAMENTALS.FISCAL_DATE_ENDING)
                    .doUpdate()
                    .set(FUNDAMENTALS.FILING_DATE, f.filingDate)
                    .set(FUNDAMENTALS.GROSS_PROFIT, f.grossProfit)
                    .set(FUNDAMENTALS.COST_OF_REVENUE, f.costOfRevenue)
                    .set(FUNDAMENTALS.TOTAL_REVENUE, f.totalRevenue)
                    .set(FUNDAMENTALS.OPERATING_INCOME, f.operatingIncome)
                    .set(FUNDAMENTALS.NET_INCOME, f.netIncome)
                    .set(FUNDAMENTALS.TOTAL_ASSETS, f.totalAssets)
                    .set(FUNDAMENTALS.TOTAL_STOCKHOLDER_EQUITY, f.totalStockholderEquity)
                    .set(FUNDAMENTALS.TOTAL_CURRENT_ASSETS, f.totalCurrentAssets)
                    .set(FUNDAMENTALS.TOTAL_CURRENT_LIABILITIES, f.totalCurrentLiabilities)
                    .set(FUNDAMENTALS.SHARES_OUTSTANDING, f.sharesOutstanding)
                    .execute()
            }
        }
    }
}
