package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.dto.ParameterMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.backtesting.model.RegimeLabel
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Market-level regime gate: permits entries only when the day's published regime label (the
 * pre-registered 5-label read-out, ADR 0023) is one of the allowed labels. The read-out itself
 * carries one frozen canonical parameterisation — this condition selects labels, never tunes the
 * classifier. An unlabeled day fails closed (no read cannot confirm a regime).
 */
@Component
class RegimeLabelCondition(
  private val allowedLabels: Set<RegimeLabel> = setOf(RegimeLabel.THRUST),
) : EntryCondition {
  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean {
    val label = context.getRegimeLabel(quote.date) ?: return false
    return label in allowedLabels
  }

  override fun description(): String =
    "Regime label is one of ${allowedLabels.sorted().joinToString(", ")}"

  override fun getMetadata() =
    ConditionMetadata(
      type = "regimeLabelIn",
      displayName = "Regime label in set",
      description =
        "Permits entries only when the published 5-label regime read-out (THRUST/GRIND/NARROW/CHOP/CRISIS) " +
          "labels the day with one of the allowed labels; unlabeled days fail closed",
      parameters =
        listOf(
          ParameterMetadata(
            name = "labels",
            displayName = "Allowed labels",
            type = "stringList",
            defaultValue = allowedLabels.sorted().map { it.name },
          ),
        ),
      category = "Market",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): ConditionEvaluationResult {
    val label = context.getRegimeLabel(quote.date)
    val passed = label != null && label in allowedLabels
    return ConditionEvaluationResult(
      conditionType = "RegimeLabelCondition",
      description = description(),
      passed = passed,
      actualValue = label?.name ?: "unlabeled",
      threshold = allowedLabels.sorted().joinToString(", "),
      message =
        when {
          passed -> "Regime ${label.name} is in the allowed set for ${quote.date} ✓"
          label == null -> "No defensible regime read for ${quote.date} — fails closed ✗"
          else -> "Regime ${label.name} is not in the allowed set for ${quote.date} ✗"
        },
    )
  }

  override fun parseConfig(parameters: Map<String, Any>): EntryCondition {
    val labels =
      (parameters["labels"] as? List<*>)
        ?.mapNotNull { raw -> RegimeLabel.entries.firstOrNull { it.name.equals(raw.toString(), ignoreCase = true) } }
        ?.toSet()
    return if (labels.isNullOrEmpty()) this else RegimeLabelCondition(labels)
  }
}
