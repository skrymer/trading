package com.skrymer.udgaard.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class EtfSymbolTest {
  @Test
  fun `fromString should return correct enum value for valid symbol`() {
    assertEquals(EtfSymbol.SPY, EtfSymbol.fromString("SPY"))
    assertEquals(EtfSymbol.QQQ, EtfSymbol.fromString("QQQ"))
    assertEquals(EtfSymbol.IWM, EtfSymbol.fromString("IWM"))
    assertEquals(EtfSymbol.DIA, EtfSymbol.fromString("DIA"))
  }

  @Test
  fun `fromString should be case insensitive`() {
    assertEquals(EtfSymbol.SPY, EtfSymbol.fromString("spy"))
    assertEquals(EtfSymbol.QQQ, EtfSymbol.fromString("qqq"))
    assertEquals(EtfSymbol.TQQQ, EtfSymbol.fromString("tqqq"))
  }

  @Test
  fun `fromString should return null for invalid symbol`() {
    assertNull(EtfSymbol.fromString("INVALID"))
    assertNull(EtfSymbol.fromString("XYZ"))
  }

  @Test
  fun `fromString should return null for null input`() {
    assertNull(EtfSymbol.fromString(null))
  }

  @Test
  fun `getStandardEtfs should return only non-leveraged ETFs`() {
    val standardEtfs = EtfSymbol.getStandardEtfs()

    assertEquals(4, standardEtfs.size)
    assertTrue(standardEtfs.contains(EtfSymbol.SPY))
    assertTrue(standardEtfs.contains(EtfSymbol.QQQ))
    assertTrue(standardEtfs.contains(EtfSymbol.IWM))
    assertTrue(standardEtfs.contains(EtfSymbol.DIA))
    assertFalse(standardEtfs.contains(EtfSymbol.TQQQ))
    assertFalse(standardEtfs.contains(EtfSymbol.SQQQ))
  }

  @Test
  fun `getLeveragedEtfs should return only leveraged ETFs`() {
    val leveragedEtfs = EtfSymbol.getLeveragedEtfs()

    assertEquals(4, leveragedEtfs.size)
    assertTrue(leveragedEtfs.contains(EtfSymbol.TQQQ))
    assertTrue(leveragedEtfs.contains(EtfSymbol.SQQQ))
    assertTrue(leveragedEtfs.contains(EtfSymbol.UPRO))
    assertTrue(leveragedEtfs.contains(EtfSymbol.SPXU))
    assertFalse(leveragedEtfs.contains(EtfSymbol.SPY))
    assertFalse(leveragedEtfs.contains(EtfSymbol.QQQ))
  }

  @Test
  fun `isLeveraged should return true for leveraged ETFs`() {
    assertTrue(EtfSymbol.TQQQ.isLeveraged())
    assertTrue(EtfSymbol.SQQQ.isLeveraged())
    assertTrue(EtfSymbol.UPRO.isLeveraged())
    assertTrue(EtfSymbol.SPXU.isLeveraged())
  }

  @Test
  fun `isLeveraged should return false for standard ETFs`() {
    assertFalse(EtfSymbol.SPY.isLeveraged())
    assertFalse(EtfSymbol.QQQ.isLeveraged())
    assertFalse(EtfSymbol.IWM.isLeveraged())
    assertFalse(EtfSymbol.DIA.isLeveraged())
  }

  @Test
  fun `isInverse should return true for inverse ETFs`() {
    assertTrue(EtfSymbol.SQQQ.isInverse())
    assertTrue(EtfSymbol.SPXU.isInverse())
  }

  @Test
  fun `isInverse should return false for non-inverse ETFs`() {
    assertFalse(EtfSymbol.SPY.isInverse())
    assertFalse(EtfSymbol.QQQ.isInverse())
    assertFalse(EtfSymbol.TQQQ.isInverse()) // Leveraged but not inverse
    assertFalse(EtfSymbol.UPRO.isInverse()) // Leveraged but not inverse
  }

  @Test
  fun `getLeverageMultiplier should return correct values`() {
    assertEquals(1.0, EtfSymbol.SPY.getLeverageMultiplier(), 0.001)
    assertEquals(1.0, EtfSymbol.QQQ.getLeverageMultiplier(), 0.001)
    assertEquals(3.0, EtfSymbol.TQQQ.getLeverageMultiplier(), 0.001)
    assertEquals(-3.0, EtfSymbol.SQQQ.getLeverageMultiplier(), 0.001)
    assertEquals(3.0, EtfSymbol.UPRO.getLeverageMultiplier(), 0.001)
    assertEquals(-3.0, EtfSymbol.SPXU.getLeverageMultiplier(), 0.001)
  }

  @Test
  fun `getUnderlyingEtf should return correct underlying for leveraged ETFs`() {
    assertEquals(EtfSymbol.QQQ, EtfSymbol.TQQQ.getUnderlyingEtf())
    assertEquals(EtfSymbol.QQQ, EtfSymbol.SQQQ.getUnderlyingEtf())
    assertEquals(EtfSymbol.SPY, EtfSymbol.UPRO.getUnderlyingEtf())
    assertEquals(EtfSymbol.SPY, EtfSymbol.SPXU.getUnderlyingEtf())
  }

  @Test
  fun `getUnderlyingEtf should return null for standard ETFs`() {
    assertNull(EtfSymbol.SPY.getUnderlyingEtf())
    assertNull(EtfSymbol.QQQ.getUnderlyingEtf())
    assertNull(EtfSymbol.IWM.getUnderlyingEtf())
    assertNull(EtfSymbol.DIA.getUnderlyingEtf())
  }

  @Test
  fun `description should be set for all ETFs`() {
    EtfSymbol.entries.forEach { etfSymbol ->
      assertNotNull(etfSymbol.description)
      assertFalse(etfSymbol.description.isBlank())
    }
  }

  @Test
  fun `all enum values should be accessible`() {
    val allSymbols = EtfSymbol.entries

    assertTrue(allSymbols.size >= 8, "Should have at least 8 ETF symbols")
    assertTrue(allSymbols.contains(EtfSymbol.SPY))
    assertTrue(allSymbols.contains(EtfSymbol.QQQ))
    assertTrue(allSymbols.contains(EtfSymbol.IWM))
    assertTrue(allSymbols.contains(EtfSymbol.DIA))
    assertTrue(allSymbols.contains(EtfSymbol.TQQQ))
    assertTrue(allSymbols.contains(EtfSymbol.SQQQ))
    assertTrue(allSymbols.contains(EtfSymbol.UPRO))
    assertTrue(allSymbols.contains(EtfSymbol.SPXU))
  }
}
