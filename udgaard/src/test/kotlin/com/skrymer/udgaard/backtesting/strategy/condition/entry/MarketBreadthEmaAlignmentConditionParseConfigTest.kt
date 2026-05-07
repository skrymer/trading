package com.skrymer.udgaard.backtesting.strategy.condition.entry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MarketBreadthEmaAlignmentConditionParseConfigTest {
  @Test
  fun `parseConfig with custom emaPeriods string produces an instance reflecting those periods`() {
    // Given
    val singleton = MarketBreadthEmaAlignmentCondition()

    // When
    val parsed = singleton.parseConfig(mapOf("emaPeriods" to "3,7,15"))

    // Then: description encodes the parsed periods
    assertEquals(
      "Market breadth EMA alignment (EMA3 > EMA7 > EMA15)",
      parsed.description(),
    )
  }

  @Test
  fun `parseConfig with empty parameters falls back to default 5,10,20`() {
    // Given
    val singleton = MarketBreadthEmaAlignmentCondition()

    // When
    val parsed = singleton.parseConfig(emptyMap())

    // Then
    assertEquals(singleton.description(), parsed.description())
  }

  @Test
  fun `parseConfig trims whitespace inside emaPeriods string`() {
    // Given
    val singleton = MarketBreadthEmaAlignmentCondition()

    // When: input has extra whitespace around each period
    val parsed = singleton.parseConfig(mapOf("emaPeriods" to " 5 , 10 , 50 "))

    // Then
    assertEquals(
      "Market breadth EMA alignment (EMA5 > EMA10 > EMA50)",
      parsed.description(),
    )
  }

  @Test
  fun `parseConfig with non-numeric emaPeriods token falls back to default instead of throwing`() {
    // Given
    val singleton = MarketBreadthEmaAlignmentCondition()

    // When: a typo or stray token sneaks in
    val parsed = singleton.parseConfig(mapOf("emaPeriods" to "5,abc,20"))

    // Then: parseConfig contract — malformed value is treated as "missing", default applies
    assertEquals(singleton.description(), parsed.description())
  }

  @Test
  fun `parseConfig with empty emaPeriods string falls back to default`() {
    // Given
    val singleton = MarketBreadthEmaAlignmentCondition()

    // When: caller cleared the field but left it in the map
    val parsed = singleton.parseConfig(mapOf("emaPeriods" to ""))

    // Then
    assertEquals(singleton.description(), parsed.description())
  }
}
