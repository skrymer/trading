package com.skrymer.udgaard.backtesting.strategy.condition.exit

import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.backtesting.model.RegimeLabel
import com.skrymer.udgaard.backtesting.model.RegimeReadoutDaily
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class RegimeLabelExitConditionTest {
  private val crisisDay = LocalDate.of(2026, 1, 5)
  private val thrustDay = LocalDate.of(2026, 1, 6)
  private val unlabeledDay = LocalDate.of(2026, 1, 7)

  private fun contextWith(vararg reads: Pair<LocalDate, RegimeLabel?>) =
    BacktestContext.EMPTY.copy(
      regimeReadoutMap =
        reads.associate { (date, label) ->
          date to RegimeReadoutDaily(quoteDate = date, rawLabel = label, publishedLabel = label)
        },
    )

  @Test
  fun `exits when the day's published regime label is in the exit set`() {
    // Given: an exit-on-CRISIS condition on a tape that turns CRISIS then recovers to THRUST
    val condition = RegimeLabelExitCondition(setOf(RegimeLabel.CRISIS))
    val context = contextWith(crisisDay to RegimeLabel.CRISIS, thrustDay to RegimeLabel.THRUST)

    // When / Then: the CRISIS day exits, the THRUST day holds
    assertTrue(condition.shouldExit(Stock(), null, StockQuote(date = crisisDay), context))
    assertFalse(condition.shouldExit(Stock(), null, StockQuote(date = thrustDay), context))
  }

  @Test
  fun `holds on a day with no defensible regime read`() {
    // Given: an exit-on-CRISIS condition on an unlabeled day and a day with no read at all —
    // an exit fires only on a confirmed regime, never on a data gap
    val condition = RegimeLabelExitCondition(setOf(RegimeLabel.CRISIS))
    val context = contextWith(unlabeledDay to null)

    // When / Then
    assertFalse(condition.shouldExit(Stock(), null, StockQuote(date = unlabeledDay), context))
    assertFalse(condition.shouldExit(Stock(), null, StockQuote(date = LocalDate.of(2026, 1, 8)), context))
  }
}
