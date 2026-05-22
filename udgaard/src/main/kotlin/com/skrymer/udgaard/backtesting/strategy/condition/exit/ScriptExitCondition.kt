package com.skrymer.udgaard.backtesting.strategy.condition.exit

import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.dto.ParameterMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.backtesting.service.ExitPredicate
import com.skrymer.udgaard.backtesting.service.ScriptPredicateCompiler
import com.skrymer.udgaard.backtesting.service.stringOr
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Exit condition whose trigger logic is a user-supplied Kotlin script — the exit-side
 * counterpart of [com.skrymer.udgaard.backtesting.strategy.condition.entry.ScriptEntryCondition].
 *
 * The script is a Kotlin expression over `stock: Stock`, `entryQuote: StockQuote?`,
 * `quote: StockQuote`, `context: BacktestContext` that must yield `Boolean` (true = exit now).
 * `entryQuote` is nullable — handling that is the script's responsibility. Compiled once at
 * strategy-build time (`parseConfig`); a script that fails to compile fails the request loudly
 * there rather than per bar. Scripts must be pure and deterministic.
 */
@Component
class ScriptExitCondition(
  private val compiler: ScriptPredicateCompiler,
  private val script: String = DEFAULT_SCRIPT,
) : ExitCondition {
  private val predicate: ExitPredicate by lazy { compiler.compileExit(script) }

  override fun shouldExit(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
  ): Boolean = shouldExit(stock, entryQuote, quote, BacktestContext.EMPTY)

  override fun shouldExit(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean = predicate(stock, entryQuote, quote, context)

  override fun exitReason(): String = "Script exit triggered"

  override fun description(): String = "Script: $script"

  override fun getMetadata() =
    ConditionMetadata(
      type = "script",
      displayName = "Custom Script",
      description = "Exit condition defined by a Kotlin script over stock, entryQuote, quote, context (must yield Boolean)",
      parameters =
        listOf(
          ParameterMetadata(
            name = "script",
            displayName = "Kotlin Script",
            type = "string",
            defaultValue = DEFAULT_SCRIPT,
          ),
        ),
      category = "Script",
    )

  override fun parseConfig(parameters: Map<String, Any>): ExitCondition {
    val configured = ScriptExitCondition(compiler, parameters.stringOr("script", DEFAULT_SCRIPT))
    configured.predicate // force compilation now — a bad script fails here, not per bar
    return configured
  }

  companion object {
    private const val DEFAULT_SCRIPT = "false"
  }
}
