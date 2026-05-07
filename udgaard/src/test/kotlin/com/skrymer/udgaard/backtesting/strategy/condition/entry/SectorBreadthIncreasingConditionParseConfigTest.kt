package com.skrymer.udgaard.backtesting.strategy.condition.entry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SectorBreadthIncreasingConditionParseConfigTest {
  @Test
  fun `parseConfig with custom sectorSymbol propagates to the new instance`() {
    // Given
    val singleton = SectorBreadthIncreasingCondition()

    // When
    val parsed = singleton.parseConfig(mapOf("sectorSymbol" to "XLF"))

    // Then: description includes the configured sector symbol
    assertEquals("XLF sector breadth increasing for 3 consecutive days", parsed.description())
  }

  @Test
  fun `parseConfig falls back to default sectorSymbol XLK when key missing`() {
    // Given
    val singleton = SectorBreadthIncreasingCondition()

    // When
    val parsed = singleton.parseConfig(emptyMap())

    // Then
    assertEquals(singleton.description(), parsed.description())
    assertEquals("XLK sector breadth increasing for 3 consecutive days", parsed.description())
  }

  @Test
  fun `parseConfig honours both days and sectorSymbol`() {
    // Given
    val singleton = SectorBreadthIncreasingCondition()

    // When
    val parsed = singleton.parseConfig(mapOf("days" to 7, "sectorSymbol" to "XLE"))

    // Then
    assertEquals("XLE sector breadth increasing for 7 consecutive days", parsed.description())
  }
}
