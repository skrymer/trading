package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.backtesting.strategy.condition.entry.EntryCondition
import com.skrymer.udgaard.backtesting.strategy.condition.exit.ExitCondition
import com.skrymer.udgaard.e2e.AbstractIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * Parameterised round-trip check for every Spring-discovered condition's `parseConfig`.
 *
 * Asserts: `condition.parseConfig(emptyMap()).description() == condition.description()` and
 * the same for `getMetadata()`. The Spring-managed singleton holds the constructor defaults,
 * so calling parseConfig with an empty map must reconstruct an equivalent instance.
 *
 * Adding a new condition with @Component automatically extends this test — no manual list to
 * maintain.
 */
class ConditionParseConfigTest : AbstractIntegrationTest() {
  @Autowired
  private lateinit var entryConditions: List<EntryCondition>

  @Autowired
  private lateinit var exitConditions: List<ExitCondition>

  @Test
  fun `every entry condition parseConfig with empty map round-trips defaults`() {
    val failures = mutableListOf<String>()
    for (c in entryConditions) {
      val rebuilt = c.parseConfig(emptyMap())
      if (rebuilt.description() != c.description()) {
        failures += "${c::class.simpleName}: description mismatch — '${c.description()}' vs '${rebuilt.description()}'"
      }
      if (rebuilt.getMetadata() != c.getMetadata()) {
        failures += "${c::class.simpleName}: metadata mismatch — ${c.getMetadata()} vs ${rebuilt.getMetadata()}"
      }
    }
    assertEquals(emptyList<String>(), failures, "parseConfig round-trip failures (defaults)")
  }

  @Test
  fun `every exit condition parseConfig with empty map round-trips defaults`() {
    val failures = mutableListOf<String>()
    for (c in exitConditions) {
      val rebuilt = c.parseConfig(emptyMap())
      if (rebuilt.description() != c.description()) {
        failures += "${c::class.simpleName}: description mismatch — '${c.description()}' vs '${rebuilt.description()}'"
      }
      if (rebuilt.getMetadata() != c.getMetadata()) {
        failures += "${c::class.simpleName}: metadata mismatch — ${c.getMetadata()} vs ${rebuilt.getMetadata()}"
      }
    }
    assertEquals(emptyList<String>(), failures, "parseConfig round-trip failures (defaults)")
  }
}
