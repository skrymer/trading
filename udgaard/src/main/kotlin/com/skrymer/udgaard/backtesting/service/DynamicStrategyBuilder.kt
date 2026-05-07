package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.backtesting.dto.CustomStrategyConfig
import com.skrymer.udgaard.backtesting.dto.PredefinedStrategyConfig
import com.skrymer.udgaard.backtesting.dto.StrategyConfig
import com.skrymer.udgaard.backtesting.strategy.CompositeEntryStrategy
import com.skrymer.udgaard.backtesting.strategy.CompositeExitStrategy
import com.skrymer.udgaard.backtesting.strategy.EntryStrategy
import com.skrymer.udgaard.backtesting.strategy.ExitStrategy
import com.skrymer.udgaard.backtesting.strategy.condition.LogicalOperator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class DynamicStrategyBuilder(
  private val strategyRegistry: StrategyRegistry,
  private val conditionRegistry: ConditionRegistry,
) {
  private val logger = LoggerFactory.getLogger(DynamicStrategyBuilder::class.java)

  fun buildEntryStrategy(config: StrategyConfig): EntryStrategy? =
    when (config) {
      is PredefinedStrategyConfig -> strategyRegistry.createEntryStrategy(config.name)
      is CustomStrategyConfig -> buildCustomEntryStrategy(config)
    }

  fun buildExitStrategy(config: StrategyConfig): ExitStrategy? =
    when (config) {
      is PredefinedStrategyConfig -> strategyRegistry.createExitStrategy(config.name)
      is CustomStrategyConfig -> buildCustomExitStrategy(config)
    }

  private fun buildCustomEntryStrategy(config: CustomStrategyConfig): CompositeEntryStrategy =
    CompositeEntryStrategy(
      conditions = config.conditions.map { conditionRegistry.buildEntryCondition(it) },
      operator = parseOperator(config.operator, default = LogicalOperator.AND),
      strategyDescription = config.description,
    )

  private fun buildCustomExitStrategy(config: CustomStrategyConfig): CompositeExitStrategy {
    val operator = parseOperator(config.operator, default = LogicalOperator.OR)
    if (operator == LogicalOperator.AND) {
      logger.warn(
        "Using AND operator for exit strategy - ALL conditions must be true simultaneously. This is rarely desired for exits.",
      )
    }
    return CompositeExitStrategy(
      exitConditions = config.conditions.map { conditionRegistry.buildExitCondition(it) },
      operator = operator,
      strategyDescription = config.description,
    )
  }

  private fun parseOperator(value: String, default: LogicalOperator): LogicalOperator =
    when (value.uppercase()) {
      "AND" -> LogicalOperator.AND
      "OR" -> LogicalOperator.OR
      "NOT" -> LogicalOperator.NOT
      "" -> default
      else -> default
    }
}
