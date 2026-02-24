package com.skrymer.udgaard.data.service

import com.skrymer.udgaard.data.model.StockQuote
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.sql.DriverManager
import java.time.LocalDate
import kotlin.math.abs

/**
 * Compares locally calculated ATR and ADX against AlphaVantage values stored in the local PostgreSQL.
 *
 * This test connects directly to the local database (no Spring context needed).
 * Disabled by default — run with:
 *
 *   INDICATOR_COMPARISON=true ./gradlew test --tests "*IndicatorComparisonLocalTest*"
 *
 * Prerequisites:
 * - Local PostgreSQL running (docker compose up -d postgres)
 * - Stock data loaded (e.g., via data-manager refresh)
 */
@EnabledIfEnvironmentVariable(named = "INDICATOR_COMPARISON", matches = "true")
class IndicatorComparisonLocalTest {
  private val indicatorService = TechnicalIndicatorService()
  private val stockSymbols = listOf("AAPL", "MSFT", "GOOGL", "AMZN", "NVDA")

  @Test
  fun `compare calculated ATR with AlphaVantage ATR`() {
    val connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)
    val dsl = DSL.using(connection, SQLDialect.POSTGRES)

    val allResults = mutableListOf<ComparisonResult>()

    for (symbol in stockSymbols) {
      val quotes = loadQuotes(dsl, symbol)
      if (quotes.size < 15) {
        println("Skipping $symbol — only ${quotes.size} quotes")
        continue
      }

      val calculatedAtr = indicatorService.calculateATR(quotes, 14)

      val comparisons = quotes.mapIndexedNotNull { index, quote ->
        val stored = quote.atr
        val calculated = calculatedAtr[index]
        if (stored > 0.0 && calculated > 0.0) {
          ComparisonRow(quote.date, stored, calculated)
        } else {
          null
        }
      }

      if (comparisons.isNotEmpty()) {
        allResults.add(ComparisonResult(symbol, comparisons))
      }
    }

    connection.close()
    printSummary("ATR", allResults)
  }

  @Test
  fun `compare calculated ADX with AlphaVantage ADX`() {
    val connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)
    val dsl = DSL.using(connection, SQLDialect.POSTGRES)

    val allResults = mutableListOf<ComparisonResult>()

    for (symbol in stockSymbols) {
      val quotes = loadQuotesWithADX(dsl, symbol)
      if (quotes.size < 29) {
        println("Skipping $symbol — only ${quotes.size} quotes (need 29 for ADX)")
        continue
      }

      val calculatedAdx = indicatorService.calculateADX(quotes, 14)

      val comparisons = quotes.mapIndexedNotNull { index, quote ->
        val stored = quote.adx
        val calculated = calculatedAdx[index]
        if (stored != null && stored > 0.0 && calculated > 0.0) {
          ComparisonRow(quote.date, stored, calculated)
        } else {
          null
        }
      }

      if (comparisons.isNotEmpty()) {
        allResults.add(ComparisonResult(symbol, comparisons))
      } else {
        println("$symbol: No overlapping ADX data")
      }
    }

    connection.close()
    printSummary("ADX", allResults)
  }

  private fun loadQuotes(dsl: org.jooq.DSLContext, symbol: String): List<StockQuote> =
    dsl
      .select(
        DSL.field("quote_date", LocalDate::class.java),
        DSL.field("open_price", Double::class.java),
        DSL.field("high_price", Double::class.java),
        DSL.field("low_price", Double::class.java),
        DSL.field("close_price", Double::class.java),
        DSL.field("atr", Double::class.java),
        DSL.field("volume", Long::class.java),
      ).from(DSL.table("stock_quotes"))
      .where(DSL.field("stock_symbol").eq(symbol))
      .orderBy(DSL.field("quote_date").asc())
      .fetch { record ->
        StockQuote(
          symbol = symbol,
          date = record.get("quote_date", LocalDate::class.java)!!,
          openPrice = record.get("open_price", Double::class.java) ?: 0.0,
          high = record.get("high_price", Double::class.java) ?: 0.0,
          low = record.get("low_price", Double::class.java) ?: 0.0,
          closePrice = record.get("close_price", Double::class.java) ?: 0.0,
          atr = record.get("atr", Double::class.java) ?: 0.0,
          volume = record.get("volume", Long::class.java) ?: 0L,
        )
      }

  private fun loadQuotesWithADX(dsl: org.jooq.DSLContext, symbol: String): List<StockQuote> =
    dsl
      .select(
        DSL.field("quote_date", LocalDate::class.java),
        DSL.field("open_price", Double::class.java),
        DSL.field("high_price", Double::class.java),
        DSL.field("low_price", Double::class.java),
        DSL.field("close_price", Double::class.java),
        DSL.field("atr", Double::class.java),
        DSL.field("adx", Double::class.java),
        DSL.field("volume", Long::class.java),
      ).from(DSL.table("stock_quotes"))
      .where(DSL.field("stock_symbol").eq(symbol))
      .orderBy(DSL.field("quote_date").asc())
      .fetch { record ->
        StockQuote(
          symbol = symbol,
          date = record.get("quote_date", LocalDate::class.java)!!,
          openPrice = record.get("open_price", Double::class.java) ?: 0.0,
          high = record.get("high_price", Double::class.java) ?: 0.0,
          low = record.get("low_price", Double::class.java) ?: 0.0,
          closePrice = record.get("close_price", Double::class.java) ?: 0.0,
          atr = record.get("atr", Double::class.java) ?: 0.0,
          adx = record.get("adx", Double::class.java),
          volume = record.get("volume", Long::class.java) ?: 0L,
        )
      }

  private fun printSummary(indicator: String, allResults: List<ComparisonResult>) {
    if (allResults.isEmpty()) {
      println("\nNo $indicator data to compare")
      return
    }

    for (result in allResults) {
      val rows = result.rows
      val avgPct = rows.map { it.percentDiff }.average()
      val maxPct = rows.maxOf { it.percentDiff }

      println("\n=== ${result.symbol} $indicator (${rows.size} data points) ===")
      println("Avg diff: ${"%.4f".format(rows.map { it.difference }.average())} | Max diff: ${"%.4f".format(rows.maxOf { it.difference })}")
      println("Avg %: ${"%.2f".format(avgPct)}% | Max %: ${"%.2f".format(maxPct)}%")

      println("\nFirst 3 (early convergence):")
      println("%-12s %10s %10s %10s %8s".format("Date", "AV $indicator", "Calc", "Diff", "% Diff"))
      rows.take(3).forEach { r ->
        println("%-12s %10.4f %10.4f %10.4f %7.2f%%".format(r.date, r.stored, r.calculated, r.difference, r.percentDiff))
      }
      println("Last 3 (recent):")
      rows.takeLast(3).forEach { r ->
        println("%-12s %10.4f %10.4f %10.4f %7.2f%%".format(r.date, r.stored, r.calculated, r.difference, r.percentDiff))
      }
    }

    println("\n\n========================================")
    println("$indicator OVERALL SUMMARY")
    println("========================================")
    println("%-8s %6s %10s %10s %8s %8s".format("Symbol", "Count", "Avg Diff", "Max Diff", "Avg %", "Max %"))
    allResults.forEach { r ->
      val rows = r.rows
      println(
        "%-8s %6d %10.4f %10.4f %7.2f%% %7.2f%%".format(
          r.symbol,
          rows.size,
          rows.map { it.difference }.average(),
          rows.maxOf { it.difference },
          rows.map { it.percentDiff }.average(),
          rows.maxOf { it.percentDiff },
        ),
      )
    }

    val overallAvgPct = allResults.flatMap { it.rows }.map { it.percentDiff }.average()
    val overallMaxPct = allResults.flatMap { it.rows }.maxOf { it.percentDiff }
    println("\nOverall average % diff: ${"%.2f".format(overallAvgPct)}%")
    println("Overall max % diff: ${"%.2f".format(overallMaxPct)}%")

    if (overallAvgPct < 1.0) {
      println("\nConclusion: Calculated $indicator closely matches AlphaVantage (<1% average deviation)")
    } else if (overallAvgPct < 5.0) {
      println("\nConclusion: Minor $indicator differences — likely due to different historical data starting points")
    } else {
      println("\nConclusion: Significant $indicator differences — may indicate different formula or data source issues")
    }
  }

  private data class ComparisonRow(
    val date: LocalDate,
    val stored: Double,
    val calculated: Double,
  ) {
    val difference: Double = abs(stored - calculated)
    val percentDiff: Double = if (stored > 0) difference / stored * 100 else 0.0
  }

  private data class ComparisonResult(
    val symbol: String,
    val rows: List<ComparisonRow>,
  )

  companion object {
    private const val DB_URL = "jdbc:postgresql://localhost:5432/trading"
    private const val DB_USER = "trading"
    private const val DB_PASS = "trading"
  }
}
