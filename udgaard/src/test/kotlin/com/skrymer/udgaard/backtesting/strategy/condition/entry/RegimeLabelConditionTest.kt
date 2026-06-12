package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.backtesting.model.RegimeLabel
import com.skrymer.udgaard.backtesting.model.RegimeReadoutDaily
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class RegimeLabelConditionTest {
  private val thrustDay = LocalDate.of(2026, 1, 5)
  private val narrowDay = LocalDate.of(2026, 1, 6)

  private fun contextWith(vararg reads: Pair<LocalDate, RegimeLabel?>) =
    BacktestContext.EMPTY.copy(
      regimeReadoutMap =
        reads.associate { (date, label) ->
          date to RegimeReadoutDaily(quoteDate = date, rawLabel = label, publishedLabel = label)
        },
    )

  @Test
  fun `permits entry only when the day's published regime label is in the allowed set`() {
    // Given: a condition allowing THRUST and GRIND, on a tape labeled THRUST then NARROW
    val condition = RegimeLabelCondition(setOf(RegimeLabel.THRUST, RegimeLabel.GRIND))
    val context = contextWith(thrustDay to RegimeLabel.THRUST, narrowDay to RegimeLabel.NARROW)

    // When / Then: the THRUST day passes, the NARROW day does not
    assertTrue(condition.evaluate(Stock(), StockQuote(date = thrustDay), context))
    assertFalse(condition.evaluate(Stock(), StockQuote(date = narrowDay), context))
  }

  @Test
  fun `fails closed on a day with no defensible regime read`() {
    // Given: a condition allowing every label, on a day whose read is unlabeled and a day with no
    // read at all — neither can confirm a regime
    val condition = RegimeLabelCondition(RegimeLabel.entries.toSet())
    val unlabeledDay = LocalDate.of(2026, 1, 7)
    val context = contextWith(unlabeledDay to null)

    // When / Then
    assertFalse(condition.evaluate(Stock(), StockQuote(date = unlabeledDay), context))
    assertFalse(condition.evaluate(Stock(), StockQuote(date = LocalDate.of(2026, 1, 8)), context))
  }

  @Test
  fun `parses the allowed-label set from config case-insensitively`() {
    // Given: a DSL config naming labels in mixed case
    val parsed = RegimeLabelCondition().parseConfig(mapOf("labels" to listOf("narrow", "CRISIS")))
    val context = contextWith(thrustDay to RegimeLabel.NARROW, narrowDay to RegimeLabel.THRUST)

    // When / Then: the parsed condition allows exactly the configured labels
    assertTrue(parsed.evaluate(Stock(), StockQuote(date = thrustDay), context))
    assertFalse(parsed.evaluate(Stock(), StockQuote(date = narrowDay), context))
  }
}
