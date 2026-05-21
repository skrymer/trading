package com.skrymer.udgaard.backtesting.strategy.condition.entry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ValueZoneConditionParseConfigTest {
  @Test
  fun `parseConfig with a custom emaPeriod produces an instance reflecting that period`() {
    // Given
    val singleton = ValueZoneCondition()

    // When: the emaPeriod wire key is supplied
    val parsed = singleton.parseConfig(mapOf("emaPeriod" to 50))

    // Then: the parsed period flows through to behaviour
    assertEquals(
      "Price within value zone (50EMA < price < 50EMA + 2.0ATR)",
      parsed.description(),
    )
  }

  @Test
  fun `parseConfig with empty parameters falls back to the default emaPeriod`() {
    // Given
    val singleton = ValueZoneCondition()

    // When
    val parsed = singleton.parseConfig(emptyMap())

    // Then
    assertEquals(singleton.description(), parsed.description())
  }
}
