package com.skrymer.udgaard.portfolio.integration.broker

import com.skrymer.udgaard.portfolio.model.OptionType
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals

class TradeProcessorTest {
  private val processor = TradeProcessor()

  @Test
  fun `roll detection should prefer same-order-ID match over arbitrary first match`() {
    // Scenario: Close NEM $100C on Jan 20, two new opens on the same day.
    // The correct roll target shares the same relatedOrderId (combo order).
    // The wrong candidate appears FIRST in the list.

    val closeDate = LocalDate.of(2026, 1, 20)

    val closedLot = makeLot(
      symbol = "NEM   C100",
      underlying = "NEM",
      strike = 100.0,
      expiry = LocalDate.of(2026, 1, 30),
      openDate = LocalDate.of(2026, 1, 12),
      openOrderId = "order-1",
      closeDate = closeDate,
      closeOrderId = "order-2",
    )

    // Wrong candidate: different order ID, appears first in list
    val wrongCandidate = makeLot(
      symbol = "NEM   C120",
      underlying = "NEM",
      strike = 120.0,
      expiry = LocalDate.of(2026, 2, 20),
      openDate = closeDate,
      openOrderId = "order-99",
      closeDate = null,
      closeOrderId = null,
    )

    // Correct candidate: same order ID as the close (combo order), appears second
    val correctCandidate = makeLot(
      symbol = "NEM   C105",
      underlying = "NEM",
      strike = 105.0,
      expiry = LocalDate.of(2026, 2, 20),
      openDate = closeDate,
      openOrderId = "order-2",
      closeDate = null,
      closeOrderId = null,
    )

    // Wrong candidate appears first in the list
    val lots = listOf(closedLot, wrongCandidate, correctCandidate)
    val rolls = processor.detectOptionRolls(lots)

    assertEquals(1, rolls.size, "Should detect exactly 1 roll")
    assertEquals(
      correctCandidate,
      rolls[0].openedLot,
      "Should pair with the correct candidate (same order ID), not the first match",
    )
  }

  @Test
  fun `roll detection with single candidate should work regardless of list order`() {
    val closeDate = LocalDate.of(2026, 1, 20)

    val closedLot = makeLot(
      symbol = "NEM   C100",
      underlying = "NEM",
      strike = 100.0,
      expiry = LocalDate.of(2026, 1, 30),
      openDate = LocalDate.of(2026, 1, 12),
      openOrderId = "order-1",
      closeDate = closeDate,
      closeOrderId = "order-2",
    )

    val rollTarget = makeLot(
      symbol = "NEM   C110",
      underlying = "NEM",
      strike = 110.0,
      expiry = LocalDate.of(2026, 2, 20),
      openDate = closeDate,
      openOrderId = "order-2",
      closeDate = null,
      closeOrderId = null,
    )

    val rolls = processor.detectOptionRolls(listOf(closedLot, rollTarget))

    assertEquals(1, rolls.size)
    assertEquals(rollTarget, rolls[0].openedLot)
    assertEquals(true, rolls[0].highConfidence, "Same order ID should be high confidence")
  }

  private fun makeLot(
    symbol: String,
    underlying: String,
    strike: Double,
    expiry: LocalDate,
    openDate: LocalDate,
    openOrderId: String?,
    closeDate: LocalDate?,
    closeOrderId: String?,
  ): TradeLot {
    val openTrade = StandardizedTrade(
      brokerTradeId = "open-$symbol-$strike",
      symbol = symbol,
      tradeDate = openDate,
      tradeTime = null,
      quantity = 2,
      price = 10.0,
      direction = TradeDirection.BUY,
      openClose = OpenCloseIndicator.OPEN,
      assetType = AssetType.OPTION,
      optionDetails = OptionDetails(
        underlyingSymbol = underlying,
        optionType = OptionType.CALL,
        strike = strike,
        expiry = expiry,
      ),
      linkedTradeId = null,
      relatedOrderId = openOrderId,
      commission = -1.0,
      netAmount = -2000.0,
    )

    val closeTrade = closeDate?.let {
      StandardizedTrade(
        brokerTradeId = "close-$symbol-$strike",
        symbol = symbol,
        tradeDate = it,
        tradeTime = null,
        quantity = 2,
        price = 15.0,
        direction = TradeDirection.SELL,
        openClose = OpenCloseIndicator.CLOSE,
        assetType = AssetType.OPTION,
        optionDetails = OptionDetails(
          underlyingSymbol = underlying,
          optionType = OptionType.CALL,
          strike = strike,
          expiry = expiry,
        ),
        linkedTradeId = null,
        relatedOrderId = closeOrderId,
        commission = -1.0,
        netAmount = 3000.0,
      )
    }

    return TradeLot(
      openTrade = openTrade,
      closeTrade = closeTrade,
      quantity = 2,
      symbol = symbol,
    )
  }
}
