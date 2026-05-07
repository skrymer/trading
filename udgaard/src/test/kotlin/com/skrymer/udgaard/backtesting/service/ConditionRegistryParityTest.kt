package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.backtesting.dto.ConditionConfig
import com.skrymer.udgaard.backtesting.dto.ParameterMetadata
import com.skrymer.udgaard.backtesting.service.legacy.LegacyDynamicStrategyBuilder
import com.skrymer.udgaard.backtesting.strategy.condition.entry.EntryCondition
import com.skrymer.udgaard.backtesting.strategy.condition.exit.ExitCondition
import com.skrymer.udgaard.e2e.AbstractIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * Behavioural parity check: build every Spring-registered condition via both the legacy
 * `when`-table dispatch (`LegacyDynamicStrategyBuilder`) and the new `ConditionRegistry`,
 * then assert `description()` + `getMetadata()` equality. Default-args path only — anything
 * exotic is covered by per-condition tests.
 *
 * Both sides feed the same default `ConditionConfig(type)` (empty parameters), so the
 * legacy `?: default` path and the new `parameters.intOr(...)` path each fall back to their
 * defaults. If they produce different descriptions, the constructor-default value moved
 * during the migration.
 *
 * **Delete this test (and `LegacyDynamicStrategyBuilder`) once the migration ships and
 * stays green for one release cycle.** The fixture exists solely to catch translation bugs
 * in the per-condition `parseConfig` rewrites.
 */
class ConditionRegistryParityTest : AbstractIntegrationTest() {
  @Autowired
  private lateinit var registry: ConditionRegistry

  @Autowired
  private lateinit var entryConditions: List<EntryCondition>

  @Autowired
  private lateinit var exitConditions: List<ExitCondition>

  private val legacy = LegacyDynamicStrategyBuilder()

  @Test
  fun `every entry condition produces identical default-args output via legacy and registry`() {
    val failures = mutableListOf<String>()
    for (singleton in entryConditions) {
      val type = singleton.getMetadata().type
      val config = ConditionConfig(type = type)
      val viaLegacy =
        runCatching { legacy.buildEntryCondition(config) }
          .getOrElse {
            failures += "$type: legacy threw ${it::class.simpleName}: ${it.message}"
            continue
          }
      val viaRegistry = registry.buildEntryCondition(config)

      if (viaLegacy::class != viaRegistry::class) {
        failures += "$type: class mismatch — legacy=${viaLegacy::class.simpleName}, registry=${viaRegistry::class.simpleName}"
      }
      if (viaLegacy.description() != viaRegistry.description()) {
        failures += "$type: description mismatch — '${viaLegacy.description()}' vs '${viaRegistry.description()}'"
      }
      if (viaLegacy.getMetadata() != viaRegistry.getMetadata()) {
        failures += "$type: metadata mismatch — ${viaLegacy.getMetadata()} vs ${viaRegistry.getMetadata()}"
      }
    }
    assertEquals(emptyList<String>(), failures, "Entry condition parity failures")
  }

  @Test
  fun `every exit condition produces identical default-args output via legacy and registry`() {
    val failures = mutableListOf<String>()
    for (singleton in exitConditions) {
      val type = singleton.getMetadata().type
      val config = ConditionConfig(type = type)
      val viaLegacy =
        runCatching { legacy.buildExitCondition(config) }
          .getOrElse {
            failures += "$type: legacy threw ${it::class.simpleName}: ${it.message}"
            continue
          }
      val viaRegistry = registry.buildExitCondition(config)

      if (viaLegacy::class != viaRegistry::class) {
        failures += "$type: class mismatch — legacy=${viaLegacy::class.simpleName}, registry=${viaRegistry::class.simpleName}"
      }
      if (viaLegacy.description() != viaRegistry.description()) {
        failures += "$type: description mismatch — '${viaLegacy.description()}' vs '${viaRegistry.description()}'"
      }
      if (viaLegacy.getMetadata() != viaRegistry.getMetadata()) {
        failures += "$type: metadata mismatch — ${viaLegacy.getMetadata()} vs ${viaRegistry.getMetadata()}"
      }
    }
    assertEquals(emptyList<String>(), failures, "Exit condition parity failures")
  }

  @Test
  fun `every entry condition produces identical non-default-args output via legacy and registry`() {
    val failures =
      entryConditions.flatMap { singleton ->
        val type = singleton.getMetadata().type
        overridableParams(type, singleton.getMetadata().parameters)
          .flatMap { (param, override) -> compareEntry(type, param.name, override) }
      }
    assertEquals(emptyList<String>(), failures, "Entry condition non-default parity failures")
  }

  @Test
  fun `every exit condition produces identical non-default-args output via legacy and registry`() {
    val failures =
      exitConditions.flatMap { singleton ->
        val type = singleton.getMetadata().type
        overridableParams(type, singleton.getMetadata().parameters)
          .flatMap { (param, override) -> compareExit(type, param.name, override) }
      }
    assertEquals(emptyList<String>(), failures, "Exit condition non-default parity failures")
  }

  private fun overridableParams(
    type: String,
    parameters: List<ParameterMetadata>,
  ): List<Pair<ParameterMetadata, Any>> =
    parameters
      .filterNot { type to it.name in LEGACY_IGNORED_PARAMS }
      .mapNotNull { param -> generateOverride(param)?.let { param to it } }

  private fun compareEntry(
    type: String,
    paramName: String,
    override: Any,
  ): List<String> {
    val config = ConditionConfig(type = type, parameters = mapOf(paramName to override))
    val viaLegacy = runCatching { legacy.buildEntryCondition(config) }.getOrNull() ?: return emptyList()
    val viaRegistry = runCatching { registry.buildEntryCondition(config) }.getOrNull() ?: return emptyList()
    return if (viaLegacy.description() == viaRegistry.description()) {
      emptyList()
    } else {
      listOf("$type[$paramName=$override]: '${viaLegacy.description()}' vs '${viaRegistry.description()}'")
    }
  }

  private fun compareExit(
    type: String,
    paramName: String,
    override: Any,
  ): List<String> {
    val config = ConditionConfig(type = type, parameters = mapOf(paramName to override))
    val viaLegacy = runCatching { legacy.buildExitCondition(config) }.getOrNull() ?: return emptyList()
    val viaRegistry = runCatching { registry.buildExitCondition(config) }.getOrNull() ?: return emptyList()
    return if (viaLegacy.description() == viaRegistry.description()) {
      emptyList()
    } else {
      listOf("$type[$paramName=$override]: '${viaLegacy.description()}' vs '${viaRegistry.description()}'")
    }
  }

  private fun generateOverride(param: ParameterMetadata): Any? =
    when (param.type) {
      "boolean" -> (param.defaultValue as? Boolean)?.let { !it }
      "number" -> {
        val default = (param.defaultValue as? Number)?.toDouble()
        when {
          default == null -> null
          param.max != null && param.max.toDouble() != default -> param.max.toDouble()
          param.min != null && param.min.toDouble() != default -> param.min.toDouble()
          else -> default + 1.0
        }
      }
      "string" -> {
        val default = param.defaultValue as? String
        param.options?.firstOrNull { it != default }
      }
      else -> null
    }

  companion object {
    // Pre-existing legacy bugs that the refactor surfaces — legacy ignored the wire-config key,
    // the per-condition `parseConfig` correctly reads it. Whitelisted from non-default parity
    // so the test asserts "no *new* translation deltas". Remove an entry when the legacy
    // reference is deleted alongside this fixture.
    private val LEGACY_IGNORED_PARAMS: Set<Pair<String, String>> =
      setOf(
        "valueZone" to "emaPeriod",
        "bearishOrderBlock" to "useHighPrice",
      )
  }
}
