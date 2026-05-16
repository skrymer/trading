package com.skrymer.udgaard.data.mapper

import com.skrymer.udgaard.data.model.Earning
import com.skrymer.udgaard.jooq.tables.pojos.Earnings
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StockMapperTest {
  private val mapper = StockMapper()

  @Test
  fun `toDomain reads canonical reportedEps column`() {
    // Given an Earnings row populated with only the canonical reportedEps column
    val pojo = canonicalEarningsRow(reportedEps = BigDecimal("1.50"))

    // When mapped to the domain model
    val domain = mapper.toDomain(pojo)

    // Then the reported EPS comes from the canonical column
    assertEquals(1.50, domain.reportedEPS)
  }

  @Test
  fun `toDomain reads canonical estimatedEps column`() {
    // Given an Earnings row populated with only the canonical estimatedEps column
    val pojo = canonicalEarningsRow(estimatedEps = BigDecimal("1.45"))

    // When mapped to the domain model
    val domain = mapper.toDomain(pojo)

    // Then the estimated EPS comes from the canonical column
    assertEquals(1.45, domain.estimatedEPS)
  }

  @Test
  fun `toDomain reads symbol from stockSymbol`() {
    // Given an Earnings row whose only symbol value is on stockSymbol
    val pojo = canonicalEarningsRow(stockSymbol = "AAPL")

    // When mapped to the domain model
    val domain = mapper.toDomain(pojo)

    // Then the symbol comes from stockSymbol
    assertEquals("AAPL", domain.symbol)
  }

  @Test
  fun `toPojo writes canonical columns`() {
    // Given a domain Earning
    val domain =
      Earning(
        symbol = "AAPL",
        fiscalDateEnding = LocalDate.of(2024, 3, 31),
        reportedDate = LocalDate.of(2024, 5, 2),
        reportedEPS = 1.50,
        estimatedEPS = 1.45,
        surprise = 0.05,
        surprisePercentage = 3.45,
        reportTime = "AfterMarket",
      )

    // When mapped to the persistence pojo
    val pojo = mapper.toPojo(domain)

    // Then canonical columns carry the values (legacy columns no longer exist in the schema)
    assertEquals(BigDecimal("1.5"), pojo.reportedEps)
    assertEquals(BigDecimal("1.45"), pojo.estimatedEps)
    assertEquals("AAPL", pojo.stockSymbol)
    assertEquals(BigDecimal("0.05"), pojo.surprise)
    assertEquals(BigDecimal("3.45"), pojo.surprisePercentage)
    assertEquals("AfterMarket", pojo.reportTime)
  }

  @Test
  fun `toDomain preserves null surprisePercentage`() {
    // Given an Earnings row with null surprisePercentage (common for older Midgaard rows)
    val pojo = canonicalEarningsRow(reportedEps = BigDecimal("1.50"), surprisePercentage = null)

    // When mapped to the domain model
    val domain = mapper.toDomain(pojo)

    // Then the null is preserved (no silent coercion to zero)
    assertNull(domain.surprisePercentage)
  }

  private fun canonicalEarningsRow(
    stockSymbol: String = "TEST",
    fiscalDateEnding: LocalDate = LocalDate.of(2024, 3, 31),
    reportedDate: LocalDate? = LocalDate.of(2024, 5, 2),
    reportedEps: BigDecimal? = null,
    estimatedEps: BigDecimal? = null,
    surprise: BigDecimal? = null,
    surprisePercentage: BigDecimal? = null,
    reportTime: String? = null,
  ): Earnings =
    Earnings(
      stockSymbol = stockSymbol,
      fiscalDateEnding = fiscalDateEnding,
      reportedDate = reportedDate,
      reportedEps = reportedEps,
      estimatedEps = estimatedEps,
      surprise = surprise,
      surprisePercentage = surprisePercentage,
      reportTime = reportTime,
    )
}
