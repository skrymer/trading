package com.skrymer.udgaard.model.strategy

import org.springframework.stereotype.Component

/**
 * Annotation to mark a strategy as available for backtesting.
 * Strategies with this annotation will be automatically discovered and made available in the API.
 *
 * @param name The display name of the strategy (e.g., "PlanAlpha", "PlanBeta")
 * @param type The type of strategy (entry or exit)
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Component
annotation class RegisteredStrategy(
  val name: String,
  val type: StrategyType,
)

enum class StrategyType {
  ENTRY,
  EXIT,
}
