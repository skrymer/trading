package com.skrymer.udgaard.data.integration.massive.dto

import com.skrymer.udgaard.data.model.SectorSymbol
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class MassiveTickerOverviewTest {
  @Nested
  inner class SicCodeMapping {
    @Test
    fun `SIC 3571 (computer hardware) maps to XLK`() {
      val results = MassiveTickerResults(ticker = "AAPL", sicCode = "3571")
      assertEquals(SectorSymbol.XLK, results.toSectorSymbol())
    }

    @Test
    fun `SIC 6020 (banking) maps to XLF`() {
      val results = MassiveTickerResults(ticker = "JPM", sicCode = "6020")
      assertEquals(SectorSymbol.XLF, results.toSectorSymbol())
    }

    @Test
    fun `SIC 2836 (biological products) maps to XLV`() {
      val results = MassiveTickerResults(ticker = "JNJ", sicCode = "2836")
      assertEquals(SectorSymbol.XLV, results.toSectorSymbol())
    }

    @Test
    fun `SIC 1311 (oil and gas extraction) maps to XLE`() {
      val results = MassiveTickerResults(ticker = "XOM", sicCode = "1311")
      assertEquals(SectorSymbol.XLE, results.toSectorSymbol())
    }

    @Test
    fun `SIC 4813 (telephone communications) maps to XLC`() {
      val results = MassiveTickerResults(ticker = "T", sicCode = "4813")
      assertEquals(SectorSymbol.XLC, results.toSectorSymbol())
    }

    @Test
    fun `SIC 4911 (electric services) maps to XLU`() {
      val results = MassiveTickerResults(ticker = "NEE", sicCode = "4911")
      assertEquals(SectorSymbol.XLU, results.toSectorSymbol())
    }

    @Test
    fun `SIC 6512 (real estate) maps to XLRE`() {
      val results = MassiveTickerResults(ticker = "AMT", sicCode = "6512")
      assertEquals(SectorSymbol.XLRE, results.toSectorSymbol())
    }

    @Test
    fun `SIC 7372 (software) maps to XLK`() {
      val results = MassiveTickerResults(ticker = "MSFT", sicCode = "7372")
      assertEquals(SectorSymbol.XLK, results.toSectorSymbol())
    }

    @Test
    fun `SIC 1500 (construction) maps to XLI`() {
      val results = MassiveTickerResults(ticker = "CAT", sicCode = "1500")
      assertEquals(SectorSymbol.XLI, results.toSectorSymbol())
    }

    @Test
    fun `SIC 2050 (food products) maps to XLP`() {
      val results = MassiveTickerResults(ticker = "GIS", sicCode = "2050")
      assertEquals(SectorSymbol.XLP, results.toSectorSymbol())
    }

    @Test
    fun `SIC 5944 (retail jewelry) maps to XLY`() {
      val results = MassiveTickerResults(ticker = "TIF", sicCode = "5944")
      assertEquals(SectorSymbol.XLY, results.toSectorSymbol())
    }

    @Test
    fun `SIC 1040 (gold mining) maps to XLB`() {
      val results = MassiveTickerResults(ticker = "NEM", sicCode = "1040")
      assertEquals(SectorSymbol.XLB, results.toSectorSymbol())
    }

    @Test
    fun `SIC 2911 (petroleum refining) maps to XLE`() {
      val results = MassiveTickerResults(ticker = "VLO", sicCode = "2911")
      assertEquals(SectorSymbol.XLE, results.toSectorSymbol())
    }

    @Test
    fun `SIC 8011 (health services) maps to XLV`() {
      val results = MassiveTickerResults(ticker = "UNH", sicCode = "8011")
      assertEquals(SectorSymbol.XLV, results.toSectorSymbol())
    }

    @Test
    fun `SIC 8742 (management consulting) maps to XLK`() {
      val results = MassiveTickerResults(ticker = "ACN", sicCode = "8742")
      assertEquals(SectorSymbol.XLK, results.toSectorSymbol())
    }
  }

  @Nested
  inner class NullAndInvalidInput {
    @Test
    fun `null SIC code returns null`() {
      val results = MassiveTickerResults(ticker = "TEST", sicCode = null)
      assertNull(results.toSectorSymbol())
    }

    @Test
    fun `empty SIC code returns null`() {
      val results = MassiveTickerResults(ticker = "TEST", sicCode = "")
      assertNull(results.toSectorSymbol())
    }

    @Test
    fun `non-numeric SIC code returns null`() {
      val results = MassiveTickerResults(ticker = "TEST", sicCode = "abc")
      assertNull(results.toSectorSymbol())
    }

    @Test
    fun `SIC code 0 returns null (unmapped range)`() {
      val results = MassiveTickerResults(ticker = "TEST", sicCode = "0")
      assertNull(results.toSectorSymbol())
    }

    @Test
    fun `SIC code 999 returns null (unmapped range)`() {
      val results = MassiveTickerResults(ticker = "TEST", sicCode = "999")
      assertNull(results.toSectorSymbol())
    }
  }

  @Nested
  inner class DtoHelpers {
    @Test
    fun `hasError returns true when status is ERROR`() {
      val dto = MassiveTickerOverview(status = "ERROR", error = "Not found")
      assertTrue(dto.hasError())
    }

    @Test
    fun `hasError returns true when error message present`() {
      val dto = MassiveTickerOverview(error = "API key invalid")
      assertTrue(dto.hasError())
    }

    @Test
    fun `hasError returns true when message present`() {
      val dto = MassiveTickerOverview(message = "Rate limited")
      assertTrue(dto.hasError())
    }

    @Test
    fun `hasError returns false for valid response`() {
      val dto = MassiveTickerOverview(
        status = "OK",
        results = MassiveTickerResults(ticker = "AAPL"),
      )
      assertFalse(dto.hasError())
    }

    @Test
    fun `isValid returns true when results and ticker present`() {
      val dto = MassiveTickerOverview(
        status = "OK",
        results = MassiveTickerResults(ticker = "AAPL"),
      )
      assertTrue(dto.isValid())
    }

    @Test
    fun `isValid returns false when results null`() {
      val dto = MassiveTickerOverview(status = "OK")
      assertFalse(dto.isValid())
    }

    @Test
    fun `isValid returns false when ticker null`() {
      val dto = MassiveTickerOverview(
        status = "OK",
        results = MassiveTickerResults(ticker = null),
      )
      assertFalse(dto.isValid())
    }

    @Test
    fun `toCompanyInfo maps sector and market cap`() {
      val dto = MassiveTickerOverview(
        status = "OK",
        results = MassiveTickerResults(
          ticker = "AAPL",
          sicCode = "3571",
          marketCap = 3000000000000L,
        ),
      )

      val companyInfo = dto.toCompanyInfo()
      assertNotNull(companyInfo)
      assertEquals(SectorSymbol.XLK, companyInfo.sectorSymbol)
      assertEquals(3000000000000L, companyInfo.marketCap)
    }

    @Test
    fun `toCompanyInfo handles null SIC code and market cap`() {
      val dto = MassiveTickerOverview(
        status = "OK",
        results = MassiveTickerResults(
          ticker = "AAPL",
          sicCode = null,
          marketCap = null,
        ),
      )

      val companyInfo = dto.toCompanyInfo()
      assertNull(companyInfo.sectorSymbol)
      assertNull(companyInfo.marketCap)
    }

    @Test
    fun `getErrorDescription returns error when present`() {
      val dto = MassiveTickerOverview(error = "Not found")
      assertEquals("Not found", dto.getErrorDescription())
    }

    @Test
    fun `getErrorDescription returns message when error null`() {
      val dto = MassiveTickerOverview(message = "Rate limited")
      assertEquals("Rate limited", dto.getErrorDescription())
    }

    @Test
    fun `getErrorDescription returns Unknown error when both null`() {
      val dto = MassiveTickerOverview()
      assertEquals("Unknown error", dto.getErrorDescription())
    }
  }
}
