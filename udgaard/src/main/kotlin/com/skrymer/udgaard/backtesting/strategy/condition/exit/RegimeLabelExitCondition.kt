package com.skrymer.udgaard.backtesting.strategy.condition.exit

import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.dto.ParameterMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.backtesting.model.RegimeLabel
import com.skrymer.udgaard.backtesting.strategy.condition.entry.RegimeLabelCondition
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Market-level regime exit: closes a position when the day's published regime label (the
 * pre-registered 5-label read-out, ADR 0023) is one of the configured exit labels. Acts only on a
 * confirmed read — an unlabeled day or a missing read never exits (a data gap is not a regime).
 * The read-out carries one frozen canonical parameterisation; this condition selects labels, never
 * tunes the classifier.
 */
@Component
class RegimeLabelExitCondition(
  private val exitLabels: Set<RegimeLabel> = setOf(RegimeLabel.CRISIS),
) : ExitCondition {
  init {
    RegimeLabelCondition.requireGateable(exitLabels)
  }

  override fun shouldExit(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
  ): Boolean = false

  override fun shouldExit(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean {
    val label = context.getRegimeLabel(quote.date) ?: return false
    return label in exitLabels
  }

  override fun exitReason(): String =
    "Regime label entered ${exitLabels.sorted().joinToString(", ")}"

  override fun description(): String =
    "Exit when the published regime label is one of ${exitLabels.sorted().joinToString(", ")}"

  override fun getMetadata() =
    ConditionMetadata(
      type = "regimeLabelExit",
      displayName = "Regime label exit",
      description =
        "Closes the position when the published 5-label regime read-out (THRUST/GRIND/NARROW/CHOP/CRISIS) " +
          "labels the day with one of the exit labels; unlabeled days never exit",
      parameters =
        listOf(
          ParameterMetadata(
            name = "labels",
            displayName = "Exit labels",
            type = "stringList",
            defaultValue = exitLabels.sorted().map { it.name },
            options = RegimeLabelCondition.GATEABLE_LABELS.sorted().map { it.name },
          ),
        ),
      category = "Signal",
    )

  override fun parseConfig(parameters: Map<String, Any>): ExitCondition {
    val labels =
      (parameters["labels"] as? List<*>)
        ?.mapNotNull { raw -> RegimeLabel.entries.firstOrNull { it.name.equals(raw.toString(), ignoreCase = true) } }
        ?.toSet()
    return if (labels.isNullOrEmpty()) this else RegimeLabelExitCondition(labels)
  }
}
