package com.skrymer.udgaard.portfolio.integration.broker

import com.skrymer.udgaard.portfolio.model.OptionType
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals

class RollChainTest {
  @Test
  fun `buildFrom assembles a single chain across three consecutive rolls A to B to C`() {
    // Given: three lots forming a chain — A rolled to B (Jan 21), B rolled to C (Jan 28).
    // C is still open. Two RollPairs encode the connections.
    val lotA = optionLot(strike = 100.0, openDate = LocalDate.of(2026, 1, 12), closeDate = LocalDate.of(2026, 1, 20))
    val lotB = optionLot(strike = 110.0, openDate = LocalDate.of(2026, 1, 21), closeDate = LocalDate.of(2026, 1, 27))
    val lotC = optionLot(strike = 120.0, openDate = LocalDate.of(2026, 1, 28), closeDate = null)
    val rolls = listOf(
      RollPair(closedLot = lotA, openedLot = lotB, highConfidence = true),
      RollPair(closedLot = lotB, openedLot = lotC, highConfidence = true),
    )

    // When
    val chains = RollChain.buildFrom(rolls)

    // Then: one chain containing all three lots in order, still open (lotC has no close).
    assertEquals(1, chains.size, "Two consecutive rolls form one chain, not two")
    assertEquals(listOf(lotA, lotB, lotC), chains[0].lots)
    assertEquals("NEM", chains[0].underlying)
    assertEquals(LocalDate.of(2026, 1, 12), chains[0].startDate)
    assertEquals(null, chains[0].endDate, "Chain ends with the final lot's closeDate; here lotC is still open")
    assertEquals(false, chains[0].isClosed)
  }

  @Test
  fun `buildFrom produces two separate two-lot chains for two disjoint roll pairs`() {
    // Given: two unrelated rolls. The second roll's lots are NOT chained to the first.
    val lotA = optionLot(strike = 100.0, openDate = LocalDate.of(2026, 1, 12), closeDate = LocalDate.of(2026, 1, 20))
    val lotB = optionLot(strike = 110.0, openDate = LocalDate.of(2026, 1, 21), closeDate = null)
    val lotX = optionLot(strike = 200.0, openDate = LocalDate.of(2026, 2, 12), closeDate = LocalDate.of(2026, 2, 20))
    val lotY = optionLot(strike = 210.0, openDate = LocalDate.of(2026, 2, 21), closeDate = null)
    val rolls = listOf(
      RollPair(closedLot = lotA, openedLot = lotB, highConfidence = true),
      RollPair(closedLot = lotX, openedLot = lotY, highConfidence = true),
    )

    // When
    val chains = RollChain.buildFrom(rolls)

    // Then: two chains of two lots each — disjoint rolls don't merge.
    assertEquals(2, chains.size)
    assertEquals(listOf(lotA, lotB), chains[0].lots)
    assertEquals(listOf(lotX, lotY), chains[1].lots)
  }

  @Test
  fun `buildFrom produces the same chain regardless of the input rolls' order`() {
    // Given: A→B→C chain encoded as 2 RollPairs, supplied in REVERSE order (B→C first, A→B second).
    // The chain assembly walks bidirectionally; result must not depend on input ordering.
    val lotA = optionLot(strike = 100.0, openDate = LocalDate.of(2026, 1, 12), closeDate = LocalDate.of(2026, 1, 20))
    val lotB = optionLot(strike = 110.0, openDate = LocalDate.of(2026, 1, 21), closeDate = LocalDate.of(2026, 1, 27))
    val lotC = optionLot(strike = 120.0, openDate = LocalDate.of(2026, 1, 28), closeDate = null)

    // When: input order is [B→C, A→B]
    val chains = RollChain.buildFrom(
      listOf(
        RollPair(closedLot = lotB, openedLot = lotC, highConfidence = true),
        RollPair(closedLot = lotA, openedLot = lotB, highConfidence = true),
      ),
    )

    // Then: same chain as the in-order tracer test
    assertEquals(1, chains.size)
    assertEquals(listOf(lotA, lotB, lotC), chains[0].lots)
  }

  private fun optionLot(strike: Double, openDate: LocalDate, closeDate: LocalDate?): TradeLot {
    val openTrade = StandardizedTrade(
      brokerTradeId = "open-$strike",
      symbol = "NEM   C$strike",
      tradeDate = openDate,
      tradeTime = null,
      quantity = 2,
      price = 10.0,
      direction = TradeDirection.BUY,
      openClose = OpenCloseIndicator.OPEN,
      assetType = AssetType.OPTION,
      optionDetails = OptionDetails(
        underlyingSymbol = "NEM",
        optionType = OptionType.CALL,
        strike = strike,
        expiry = LocalDate.of(2026, 2, 20),
      ),
      linkedTradeId = null,
      relatedOrderId = null,
      commission = -1.0,
      netAmount = -2000.0,
    )
    val closeTrade = closeDate?.let {
      StandardizedTrade(
        brokerTradeId = "close-$strike",
        symbol = "NEM   C$strike",
        tradeDate = it,
        tradeTime = null,
        quantity = 2,
        price = 15.0,
        direction = TradeDirection.SELL,
        openClose = OpenCloseIndicator.CLOSE,
        assetType = AssetType.OPTION,
        optionDetails = OptionDetails(
          underlyingSymbol = "NEM",
          optionType = OptionType.CALL,
          strike = strike,
          expiry = LocalDate.of(2026, 2, 20),
        ),
        linkedTradeId = null,
        relatedOrderId = null,
        commission = -1.0,
        netAmount = 3000.0,
      )
    }
    return TradeLot(openTrade = openTrade, closeTrade = closeTrade, quantity = 2, symbol = "NEM   C$strike")
  }
}
