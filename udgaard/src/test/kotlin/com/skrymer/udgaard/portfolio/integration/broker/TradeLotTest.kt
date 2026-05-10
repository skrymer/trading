package com.skrymer.udgaard.portfolio.integration.broker

import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals

class TradeLotTest {
  @Test
  fun `from splits one buy of 100 closed by two sells of 50 into two FIFO-matched lots`() {
    // Given: AAPL — buy 100 on day 1, sell 50 on day 2, sell 50 on day 3
    val buy100 = stockTrade("buy-100", "AAPL", LocalDate.of(2026, 1, 1), 100, OpenCloseIndicator.OPEN, TradeDirection.BUY)
    val sell50A = stockTrade("sell-50A", "AAPL", LocalDate.of(2026, 1, 2), 50, OpenCloseIndicator.CLOSE, TradeDirection.SELL)
    val sell50B = stockTrade("sell-50B", "AAPL", LocalDate.of(2026, 1, 3), 50, OpenCloseIndicator.CLOSE, TradeDirection.SELL)

    // When
    val lots = TradeLot.from(listOf(buy100, sell50A, sell50B))

    // Then: two lots of 50; the single buy pairs against each of the two closes in date order.
    assertEquals(2, lots.size, "buy 100 → sell 50 + sell 50 should produce two lots")
    assertEquals(50, lots[0].quantity)
    assertEquals(50, lots[1].quantity)
    assertEquals("buy-100", lots[0].openTrade.brokerTradeId)
    assertEquals("sell-50A", lots[0].closeTrade?.brokerTradeId)
    assertEquals("buy-100", lots[1].openTrade.brokerTradeId)
    assertEquals("sell-50B", lots[1].closeTrade?.brokerTradeId)
  }

  @Test
  fun `from does not cross-match opens and closes across different symbols`() {
    // Given: AAPL opens but only an MSFT close — they must not pair.
    val aaplBuy = stockTrade("aapl-buy", "AAPL", LocalDate.of(2026, 1, 1), 100, OpenCloseIndicator.OPEN, TradeDirection.BUY)
    val msftSell = stockTrade("msft-sell", "MSFT", LocalDate.of(2026, 1, 2), 100, OpenCloseIndicator.CLOSE, TradeDirection.SELL)

    // When
    val lots = TradeLot.from(listOf(aaplBuy, msftSell))

    // Then: AAPL stays open (unmatched lot), MSFT close is dropped (no AAPL→MSFT pairing).
    assertEquals(1, lots.size, "Only the AAPL open should produce a (still-open) lot")
    assertEquals("AAPL", lots[0].symbol)
    assertEquals(null, lots[0].closeTrade, "No close in AAPL → unmatched lot")
  }

  @Test
  fun `from drops a close trade that has no matching open without throwing`() {
    // Given: only a close (no preceding buy). Real-world data anomaly — broker exports
    // sometimes contain orphan closes when the open was outside the export window.
    val orphanSell = stockTrade("orphan", "AAPL", LocalDate.of(2026, 1, 1), 100, OpenCloseIndicator.CLOSE, TradeDirection.SELL)

    // When
    val lots = TradeLot.from(listOf(orphanSell))

    // Then: no exception, no lot — orphan dropped silently (the production code logs a warning,
    // but the public contract is "tolerate orphan closes").
    assertEquals(0, lots.size)
  }

  @Test
  fun `from aggregates same-day partial fills sharing direction and order ID before matching`() {
    // Given: brokers (IBKR in particular) split a single 100-share buy into two 50-share fills
    // sharing the same relatedOrderId. They should collapse to one open of 100 *before* FIFO
    // matches against the close — otherwise we'd create two 50-share lots instead of one 100.
    val partialBuyA = stockTrade(
      "fill-A",
      "AAPL",
      LocalDate.of(2026, 1, 1),
      50,
      OpenCloseIndicator.OPEN,
      TradeDirection.BUY,
      relatedOrderId = "order-1",
    )
    val partialBuyB = stockTrade(
      "fill-B",
      "AAPL",
      LocalDate.of(2026, 1, 1),
      50,
      OpenCloseIndicator.OPEN,
      TradeDirection.BUY,
      relatedOrderId = "order-1",
    )
    val close = stockTrade("close", "AAPL", LocalDate.of(2026, 1, 5), 100, OpenCloseIndicator.CLOSE, TradeDirection.SELL)

    // When
    val lots = TradeLot.from(listOf(partialBuyA, partialBuyB, close))

    // Then: one lot of 100 (fills aggregated), not two lots of 50.
    assertEquals(1, lots.size, "Same-day partial fills sharing order ID must aggregate to one open")
    assertEquals(100, lots[0].quantity)
  }

  private fun stockTrade(
    id: String,
    symbol: String,
    date: LocalDate,
    quantity: Int,
    openClose: OpenCloseIndicator,
    direction: TradeDirection,
    relatedOrderId: String? = null,
  ) = StandardizedTrade(
    brokerTradeId = id,
    symbol = symbol,
    tradeDate = date,
    tradeTime = null,
    quantity = quantity,
    price = 100.0,
    direction = direction,
    openClose = openClose,
    assetType = AssetType.STOCK,
    optionDetails = null,
    linkedTradeId = null,
    relatedOrderId = relatedOrderId,
    commission = -1.0,
    netAmount = quantity * 100.0 * if (direction == TradeDirection.BUY) -1.0 else 1.0,
  )
}
