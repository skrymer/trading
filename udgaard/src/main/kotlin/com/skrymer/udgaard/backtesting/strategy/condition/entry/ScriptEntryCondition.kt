package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.dto.ParameterMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.backtesting.service.EntryPredicate
import com.skrymer.udgaard.backtesting.service.ScriptPredicateCompiler
import com.skrymer.udgaard.backtesting.service.stringOr
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Entry condition whose pass/fail logic is a user-supplied Kotlin script — lets a strategy
 * express signals not covered by the fixed condition catalogue.
 *
 * The script is a Kotlin expression over `stock: Stock`, `quote: StockQuote`,
 * `context: BacktestContext` that must yield `Boolean`. It is compiled once at strategy-build
 * time (`parseConfig`); a script that fails to compile fails the request loudly there rather
 * than per bar. Scripts must be pure and deterministic — a script that reads wall-clock time
 * or randomness silently breaks backtest reproducibility.
 */
@Component
class ScriptEntryCondition(
  private val compiler: ScriptPredicateCompiler,
  private val script: String = DEFAULT_SCRIPT,
) : EntryCondition {
  private val predicate: EntryPredicate by lazy { compiler.compileEntry(script) }

  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean = predicate(stock, quote, context)

  override fun description(): String = "Script: $script"

  override fun getMetadata() =
    ConditionMetadata(
      type = "script",
      displayName = "Custom Script",
      description = "Entry condition defined by a Kotlin script over stock, quote, context (must yield Boolean)",
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

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): ConditionEvaluationResult =
    ConditionEvaluationResult(
      conditionType = "ScriptEntryCondition",
      description = description(),
      passed = evaluate(stock, quote, context),
    )

  override fun parseConfig(parameters: Map<String, Any>): EntryCondition {
    val configured = ScriptEntryCondition(compiler, parameters.stringOr("script", DEFAULT_SCRIPT))
    configured.predicate // force compilation now — a bad script fails here, not per bar
    return configured
  }

  companion object {
    private const val DEFAULT_SCRIPT = "false"
  }
}
