package com.skrymer.udgaard.backtesting.strategy.condition.exit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BearishOrderBlockExitParseConfigTest {
  @Test
  fun `parseConfig with custom ageInDays propagates through to the new instance`() {
    // Given
    val singleton = BearishOrderBlockExit()

    // When
    val parsed = singleton.parseConfig(mapOf("ageInDays" to 60))

    // Then
    assertEquals("Bearish order block (age > 60d)", parsed.description())
  }

  @Test
  fun `parseConfig with empty parameters falls back to defaults`() {
    // Given
    val singleton = BearishOrderBlockExit()

    // When
    val parsed = singleton.parseConfig(emptyMap())

    // Then
    assertEquals(singleton.description(), parsed.description())
    assertEquals(singleton.getMetadata(), parsed.getMetadata())
  }

  @Test
  fun `parseConfig honours useHighPrice flag in the metadata round-trip`() {
    // Given: the singleton defaults to useHighPrice=false; metadata advertises the field.
    // Wire-level coverage matters because the old when-dispatch never read this field, so
    // the deepening introduces a new code path that must be exercised.
    val singleton = BearishOrderBlockExit()

    // When
    val parsed = singleton.parseConfig(mapOf("useHighPrice" to true))

    // Then: the parsed instance reports useHighPrice=true via its metadata
    val metadata = parsed.getMetadata()
    val useHighPriceParam = metadata.parameters.single { it.name == "useHighPrice" }
    assertEquals(true, useHighPriceParam.defaultValue)
  }
}
