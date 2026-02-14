package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.strategy.condition.entry.EntryCondition
import com.skrymer.udgaard.backtesting.strategy.condition.exit.ExitCondition
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Registry for entry and exit conditions.
 * Auto-discovers all condition implementations via Spring dependency injection.
 *
 * Conditions are automatically registered when they:
 * 1. Implement EntryCondition or ExitCondition interface
 * 2. Are annotated with @Component
 */
@Service
class ConditionRegistry(
  private val entryConditions: List<EntryCondition>,
  private val exitConditions: List<ExitCondition>,
) {
  private val logger = LoggerFactory.getLogger(ConditionRegistry::class.java)

  init {
    logger.info("Registered ${entryConditions.size} entry conditions")
    logger.info("Registered ${exitConditions.size} exit conditions")
  }

  /**
   * Get metadata for all entry conditions.
   * Used by the API to expose available conditions to the frontend.
   */
  fun getEntryConditionMetadata(): List<ConditionMetadata> = entryConditions.map { it.getMetadata() }

  /**
   * Get metadata for all exit conditions.
   * Used by the API to expose available conditions to the frontend.
   */
  fun getExitConditionMetadata(): List<ConditionMetadata> = exitConditions.map { it.getMetadata() }
}
