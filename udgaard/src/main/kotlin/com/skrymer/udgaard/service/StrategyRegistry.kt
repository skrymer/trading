package com.skrymer.udgaard.service

import com.skrymer.udgaard.model.strategy.*
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Service

/**
 * Registry for managing available trading strategies.
 * Automatically discovers strategies annotated with @RegisteredStrategy
 * and allows manual registration of DSL-based strategies.
 */
@Service
class StrategyRegistry(
  private val applicationContext: ApplicationContext,
) {
  private val logger = LoggerFactory.getLogger(StrategyRegistry::class.java)

  private val entryStrategies = mutableMapOf<String, () -> EntryStrategy>()
  private val exitStrategies = mutableMapOf<String, () -> ExitStrategy>()

  @PostConstruct
  fun initialize() {
    // Auto-discover annotated strategies
    discoverAnnotatedStrategies()

    // Register DSL-based exit strategies
    registerDslExitStrategies()

    logger.info("Strategy Registry initialized:")
    logger.info("  Entry strategies: ${entryStrategies.keys.sorted()}")
    logger.info("  Exit strategies: ${exitStrategies.keys.sorted()}")
  }

  private fun discoverAnnotatedStrategies() {
    // Find all beans with @RegisteredStrategy annotation
    val beansWithAnnotation = applicationContext.getBeansWithAnnotation(RegisteredStrategy::class.java)

    beansWithAnnotation.forEach { (_, bean) ->
      val annotation = bean.javaClass.getAnnotation(RegisteredStrategy::class.java)
      if (annotation != null) {
        when (annotation.type) {
          StrategyType.ENTRY -> {
            if (bean is EntryStrategy) {
              entryStrategies[annotation.name] = { bean }
              logger.debug("Registered entry strategy: ${annotation.name}")
            }
          }
          StrategyType.EXIT -> {
            if (bean is ExitStrategy) {
              exitStrategies[annotation.name] = { bean }
              logger.debug("Registered exit strategy: ${annotation.name}")
            }
          }
        }
      }
    }
  }

  private fun registerDslExitStrategies() {
    // Register all DSL-based exit strategies
    exitStrategies["HalfAtr"] = { exitStrategy { stopLoss(0.5) } }
    exitStrategies["Heatmap"] = { exitStrategy { heatmapThreshold() } }
    exitStrategies["LessGreedy"] = { exitStrategy { heatmapDeclining() } }
    exitStrategies["MarketAndSectorBreadthReverses"] = { exitStrategy { marketAndSectorDowntrend() } }
    exitStrategies["PriceUnder10Ema"] = { exitStrategy { priceBelowEma(10) } }
    exitStrategies["PriceUnder50Ema"] = { exitStrategy { priceBelowEma(50) } }
    exitStrategies["SellSignal"] = { exitStrategy { sellSignal() } }
    exitStrategies["WithinOrderBlock"] = { exitStrategy { orderBlock(120) } }
    exitStrategies["BelowPriorDaysLow"] = { exitStrategy { belowPreviousDayLow() } }
    exitStrategies["TenTwentyBearishCross"] = { exitStrategy { emaCross(10, 20) } }
  }

  /**
   * Get all available entry strategy names.
   */
  fun getAvailableEntryStrategies(): List<String> = entryStrategies.keys.sorted()

  /**
   * Get all available exit strategy names.
   */
  fun getAvailableExitStrategies(): List<String> = exitStrategies.keys.sorted()

  /**
   * Create an entry strategy instance by name.
   */
  fun createEntryStrategy(name: String): EntryStrategy? {
    val factory = entryStrategies[name]
    return factory?.invoke()
  }

  /**
   * Create an exit strategy instance by name.
   */
  fun createExitStrategy(name: String): ExitStrategy? {
    val factory = exitStrategies[name]
    return factory?.invoke()
  }

  /**
   * Manually register an entry strategy.
   * Useful for testing or dynamic strategy creation.
   */
  fun registerEntryStrategy(
    name: String,
    factory: () -> EntryStrategy,
  ) {
    entryStrategies[name] = factory
    logger.info("Manually registered entry strategy: $name")
  }

  /**
   * Manually register an exit strategy.
   * Useful for testing or dynamic strategy creation.
   */
  fun registerExitStrategy(
    name: String,
    factory: () -> ExitStrategy,
  ) {
    exitStrategies[name] = factory
    logger.info("Manually registered exit strategy: $name")
  }
}
