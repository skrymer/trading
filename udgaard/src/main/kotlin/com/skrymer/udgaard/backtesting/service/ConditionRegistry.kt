package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.backtesting.dto.ConditionConfig
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.strategy.condition.entry.EntryCondition
import com.skrymer.udgaard.backtesting.strategy.condition.exit.ExitCondition
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Registry + factory for entry and exit conditions.
 *
 * Spring auto-discovers all `@Component`-annotated `EntryCondition` and `ExitCondition`
 * implementations and injects them. Each condition declares its wire-type identity via
 * `getMetadata().type`; the registry indexes by `type.lowercase()` and routes
 * `ConditionConfig` instances to the matching singleton's `parseConfig`.
 *
 * Duplicate types throw `IllegalStateException` at boot.
 */
@Service
class ConditionRegistry(
  entryConditions: List<EntryCondition>,
  exitConditions: List<ExitCondition>,
) {
  private val logger = LoggerFactory.getLogger(ConditionRegistry::class.java)

  private val entryByType: Map<String, EntryCondition> =
    associateByTypeOrThrow(entryConditions, "entry") { it.getMetadata().type.lowercase() }
  private val exitByType: Map<String, ExitCondition> =
    associateByTypeOrThrow(exitConditions, "exit") { it.getMetadata().type.lowercase() }

  init {
    logger.info("Registered ${entryByType.size} entry conditions, ${exitByType.size} exit conditions")
  }

  /**
   * Build a configured entry condition from a wire-format config. Routes by
   * `config.type.lowercase()`; missing parameters fall back to constructor defaults.
   */
  fun buildEntryCondition(config: ConditionConfig): EntryCondition =
    (
      entryByType[config.type.lowercase()]
        ?: throw IllegalArgumentException("Unknown entry condition type: ${config.type}")
    ).parseConfig(config.parameters)

  /**
   * Build a configured exit condition from a wire-format config. Routes by
   * `config.type.lowercase()`; missing parameters fall back to constructor defaults.
   */
  fun buildExitCondition(config: ConditionConfig): ExitCondition =
    (
      exitByType[config.type.lowercase()]
        ?: throw IllegalArgumentException("Unknown exit condition type: ${config.type}")
    ).parseConfig(config.parameters)

  fun getEntryConditionMetadata(): List<ConditionMetadata> = entryByType.values.map { it.getMetadata() }

  fun getExitConditionMetadata(): List<ConditionMetadata> = exitByType.values.map { it.getMetadata() }

  private fun <T> associateByTypeOrThrow(
    items: List<T>,
    side: String,
    keySelector: (T) -> String,
  ): Map<String, T> {
    val grouped = items.groupBy(keySelector)
    val duplicates = grouped.filterValues { it.size > 1 }
    if (duplicates.isNotEmpty()) {
      val description = duplicates.entries.joinToString("; ") { (type, dupes) ->
        "type='$type' shared by ${dupes.map { it!!::class.simpleName }}"
      }
      throw IllegalStateException("Duplicate $side condition types detected: $description")
    }
    return grouped.mapValues { it.value.single() }
  }
}
