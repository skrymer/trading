package com.skrymer.udgaard.portfolio.integration.broker

import com.skrymer.udgaard.portfolio.model.OptionType
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals

class RollPairTest {
  @Test
  fun `detectFrom pairs a closed option lot with a same-underlying open lot opened the next day`() {
    // Given: close NEM $100C on Jan 20, open NEM $110C on Jan 21 (next-day roll, no order ID).
    val closedLot = optionLot(
      symbol = "NEM   C100",
      underlying = "NEM",
      strike = 100.0,
      expiry = LocalDate.of(2026, 1, 30),
      openDate = LocalDate.of(2026, 1, 12),
      closeDate = LocalDate.of(2026, 1, 20),
      openOrderId = null,
      closeOrderId = null,
    )
    val openedLot = optionLot(
      symbol = "NEM   C110",
      underlying = "NEM",
      strike = 110.0,
      expiry = LocalDate.of(2026, 2, 20),
      openDate = LocalDate.of(2026, 1, 21),
      closeDate = null,
      openOrderId = null,
      closeOrderId = null,
    )

    // When
    val rolls = RollPair.detectFrom(listOf(closedLot, openedLot))

    // Then
    assertEquals(1, rolls.size, "Same-underlying close→open within 1 day should pair")
    assertEquals(closedLot, rolls[0].closedLot)
    assertEquals(openedLot, rolls[0].openedLot)
    assertEquals(false, rolls[0].highConfidence, "No order ID match → medium confidence")
  }

  @Test
  fun `detectFrom prefers a candidate sharing the close trade's order ID over an arbitrary first match`() {
    // Migrated from TradeProcessorTest. Combo orders (roll executed as one ticket) carry the same
    // relatedOrderId on close + new-open. The matching-orderId candidate must win even when a
    // non-matching candidate appears earlier in the input list.
    val closeDate = LocalDate.of(2026, 1, 20)
    val closedLot = optionLot(
      symbol = "NEM   C100",
      underlying = "NEM",
      strike = 100.0,
      expiry = LocalDate.of(2026, 1, 30),
      openDate = LocalDate.of(2026, 1, 12),
      closeDate = closeDate,
      openOrderId = "order-1",
      closeOrderId = "order-2",
    )
    val wrongCandidateFirst = optionLot(
      symbol = "NEM   C120",
      underlying = "NEM",
      strike = 120.0,
      expiry = LocalDate.of(2026, 2, 20),
      openDate = closeDate,
      closeDate = null,
      openOrderId = "order-99",
      closeOrderId = null,
    )
    val correctCandidateSecond = optionLot(
      symbol = "NEM   C105",
      underlying = "NEM",
      strike = 105.0,
      expiry = LocalDate.of(2026, 2, 20),
      openDate = closeDate,
      closeDate = null,
      openOrderId = "order-2",
      closeOrderId = null,
    )

    // When: wrong candidate appears first
    val rolls = RollPair.detectFrom(listOf(closedLot, wrongCandidateFirst, correctCandidateSecond))

    // Then: matching-orderId candidate wins
    assertEquals(1, rolls.size)
    assertEquals(correctCandidateSecond, rolls[0].openedLot, "Matching-orderId candidate must beat first-match")
    assertEquals(true, rolls[0].highConfidence, "Same-orderId pairing is high confidence")
  }

  @Test
  fun `detectFrom pairs single same-orderId candidate regardless of input list order with high confidence`() {
    // Migrated from TradeProcessorTest. Single-candidate sanity check — verifies the
    // common case (one closed lot, one valid open lot) doesn't depend on list ordering.
    val closeDate = LocalDate.of(2026, 1, 20)
    val closedLot = optionLot(
      symbol = "NEM   C100",
      underlying = "NEM",
      strike = 100.0,
      expiry = LocalDate.of(2026, 1, 30),
      openDate = LocalDate.of(2026, 1, 12),
      closeDate = closeDate,
      openOrderId = "order-1",
      closeOrderId = "order-2",
    )
    val rollTarget = optionLot(
      symbol = "NEM   C110",
      underlying = "NEM",
      strike = 110.0,
      expiry = LocalDate.of(2026, 2, 20),
      openDate = closeDate,
      closeDate = null,
      openOrderId = "order-2",
      closeOrderId = null,
    )

    // When
    val rolls = RollPair.detectFrom(listOf(closedLot, rollTarget))

    // Then
    assertEquals(1, rolls.size)
    assertEquals(rollTarget, rolls[0].openedLot)
    assertEquals(true, rolls[0].highConfidence, "Same orderId → high confidence")
  }

  @Test
  fun `detectFrom does not pair when the new open is more than one day after the close`() {
    // Given: close Jan 20, open Jan 25 — 5-day gap is too large for a roll. The trader is
    // probably opening a fresh position, not rolling.
    val closedLot = optionLot(
      symbol = "NEM   C100",
      underlying = "NEM",
      strike = 100.0,
      expiry = LocalDate.of(2026, 1, 30),
      openDate = LocalDate.of(2026, 1, 12),
      closeDate = LocalDate.of(2026, 1, 20),
      openOrderId = null,
      closeOrderId = null,
    )
    val laterOpen = optionLot(
      symbol = "NEM   C110",
      underlying = "NEM",
      strike = 110.0,
      expiry = LocalDate.of(2026, 2, 20),
      openDate = LocalDate.of(2026, 1, 25),
      closeDate = null,
      openOrderId = null,
      closeOrderId = null,
    )

    // When
    val rolls = RollPair.detectFrom(listOf(closedLot, laterOpen))

    // Then
    assertEquals(0, rolls.size, "Open more than 1 day after close is not a roll")
  }

  @Test
  fun `detectFrom does not pair a closed call with an opened put on the same underlying`() {
    // Given: close NEM CALL Jan 20, open NEM PUT Jan 21. Same underlying, same date window,
    // but a CALL→PUT swap is a strategy change (not a roll) — must NOT pair.
    val closedCall = optionLot(
      symbol = "NEM   C100",
      underlying = "NEM",
      strike = 100.0,
      expiry = LocalDate.of(2026, 1, 30),
      openDate = LocalDate.of(2026, 1, 12),
      closeDate = LocalDate.of(2026, 1, 20),
      openOrderId = null,
      closeOrderId = null,
      optionType = OptionType.CALL,
    )
    val openedPut = optionLot(
      symbol = "NEM   P100",
      underlying = "NEM",
      strike = 100.0,
      expiry = LocalDate.of(2026, 2, 20),
      openDate = LocalDate.of(2026, 1, 21),
      closeDate = null,
      openOrderId = null,
      closeOrderId = null,
      optionType = OptionType.PUT,
    )

    // When
    val rolls = RollPair.detectFrom(listOf(closedCall, openedPut))

    // Then
    assertEquals(0, rolls.size, "CALL→PUT is not a roll")
  }

  @Test
  fun `detectFrom does not pair lots with identical strike and expiry (same-contract re-entry)`() {
    // Given: close NEM C100 (Feb 20 expiry) on Jan 20, then re-open NEM C100 (same Feb 20 expiry)
    // on Jan 21. Same exact contract — this is a flatten + re-establish (e.g., for tax / rebalance),
    // NOT a roll. A roll requires a strike OR expiry change.
    val closedLot = optionLot(
      symbol = "NEM   C100",
      underlying = "NEM",
      strike = 100.0,
      expiry = LocalDate.of(2026, 2, 20),
      openDate = LocalDate.of(2026, 1, 12),
      closeDate = LocalDate.of(2026, 1, 20),
      openOrderId = null,
      closeOrderId = null,
    )
    val identicalReopen = optionLot(
      symbol = "NEM   C100",
      underlying = "NEM",
      strike = 100.0,
      expiry = LocalDate.of(2026, 2, 20),
      openDate = LocalDate.of(2026, 1, 21),
      closeDate = null,
      openOrderId = null,
      closeOrderId = null,
    )

    // When
    val rolls = RollPair.detectFrom(listOf(closedLot, identicalReopen))

    // Then
    assertEquals(0, rolls.size, "Same strike + same expiry is a re-entry, not a roll")
  }

  @Test
  fun `detectFrom does not re-pair an already-paired closed lot when a later close finds it as a candidate`() {
    // Given: 3-lot chain. A closed Jan 20, B closed Jan 30, C still open.
    // After A→B is paired, processing B's close must NOT re-pair back to A. The 1-day window check
    // accepts negative gaps (B.close=Jan30 → A.open=Jan12 is -18 days, which is `<= 1`), so without
    // a "skip already-paired closed lots" guard the FIFO firstOrNull could pick A over C.
    val lotA = optionLot(
      symbol = "NEM   C100",
      underlying = "NEM",
      strike = 100.0,
      expiry = LocalDate.of(2026, 2, 20),
      openDate = LocalDate.of(2026, 1, 12),
      closeDate = LocalDate.of(2026, 1, 20),
      openOrderId = null,
      closeOrderId = null,
    )
    val lotB = optionLot(
      symbol = "NEM   C110",
      underlying = "NEM",
      strike = 110.0,
      expiry = LocalDate.of(2026, 3, 20),
      openDate = LocalDate.of(2026, 1, 21),
      closeDate = LocalDate.of(2026, 1, 30),
      openOrderId = null,
      closeOrderId = null,
    )
    val lotC = optionLot(
      symbol = "NEM   C120",
      underlying = "NEM",
      strike = 120.0,
      expiry = LocalDate.of(2026, 4, 17),
      openDate = LocalDate.of(2026, 1, 31),
      closeDate = null,
      openOrderId = null,
      closeOrderId = null,
    )

    // When
    val rolls = RollPair.detectFrom(listOf(lotA, lotB, lotC))

    // Then: exactly two pairs A→B and B→C — never B→A.
    assertEquals(2, rolls.size, "Three-lot chain should produce two roll pairs")
    val pairFromA = rolls.find { it.closedLot == lotA }
    val pairFromB = rolls.find { it.closedLot == lotB }
    assertEquals(lotB, pairFromA?.openedLot, "A's close should pair with B")
    assertEquals(lotC, pairFromB?.openedLot, "B's close should pair with C, not back to A")
  }

  private fun optionLot(
    symbol: String,
    underlying: String,
    strike: Double,
    expiry: LocalDate,
    openDate: LocalDate,
    closeDate: LocalDate?,
    openOrderId: String?,
    closeOrderId: String?,
    optionType: OptionType = OptionType.CALL,
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
        optionType = optionType,
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
          optionType = optionType,
          strike = strike,
          expiry = expiry,
        ),
        linkedTradeId = null,
        relatedOrderId = closeOrderId,
        commission = -1.0,
        netAmount = 3000.0,
      )
    }
    return TradeLot(openTrade = openTrade, closeTrade = closeTrade, quantity = 2, symbol = symbol)
  }
}
