package com.skrymer.udgaard.portfolio.integration.broker

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.temporal.ChronoUnit

/**
 * Broker-agnostic trade processor.
 * Handles partial close splitting and option roll detection.
 */
@Service
class TradeProcessor {
  /**
   * Aggregate multiple executions of the same trade into single trades.
   * IBKR often splits trades into multiple executions that should be treated as one.
   *
   * Criteria for aggregation:
   * - Same symbol
   * - Same direction (BUY/SELL)
   * - Same open/close indicator
   * - Same trade date
   * - Same related order ID (if available)
   *
   * @param trades - List of standardized trades
   * @return List of aggregated trades
   */
  fun aggregateExecutions(trades: List<StandardizedTrade>): List<StandardizedTrade> {
    val aggregated = mutableListOf<StandardizedTrade>()

    trades
      .groupBy { trade ->
        // Group key: symbol + direction + openClose + date + orderId
        AggregationKey(
          symbol = trade.symbol,
          direction = trade.direction,
          openClose = trade.openClose,
          tradeDate = trade.tradeDate,
          relatedOrderId = trade.relatedOrderId,
        )
      }.forEach { (key, executions) ->
        if (executions.size == 1) {
          // Single execution, no aggregation needed
          aggregated.add(executions.first())
        } else {
          // Multiple executions - aggregate them
          val totalQuantity = executions.sumOf { it.quantity }
          val totalCommission = executions.mapNotNull { it.commission }.sum()
          val avgPrice = executions.sumOf { it.price * it.quantity } / totalQuantity

          // Use first execution as template, update quantity/commission/price
          val aggregatedTrade =
            executions
              .first()
              .copy(
                quantity = totalQuantity,
                price = avgPrice,
                commission = totalCommission,
              )

          logger.debug(
            "Aggregated ${executions.size} executions for ${key.symbol}: " +
              "${executions.map { it.quantity }.joinToString("+")} = $totalQuantity contracts",
          )

          aggregated.add(aggregatedTrade)
        }
      }

    if (aggregated.size < trades.size) {
      logger.info("Aggregated ${trades.size} executions into ${aggregated.size} trades")
    }

    return aggregated
  }

  /**
   * Split partial closes into individual lots using FIFO matching.
   *
   * Example: Buy 100, Sell 50, Sell 50 → 2 lots (50+50, 50+50)
   *
   * @param trades - List of standardized trades
   * @return List of matched lots
   */
  fun splitPartialCloses(trades: List<StandardizedTrade>): List<TradeLot> {
    val lots = mutableListOf<TradeLot>()

    // First, aggregate executions to combine partial fills
    val aggregatedTrades = aggregateExecutions(trades)

    // Group by symbol
    aggregatedTrades.groupBy { it.symbol }.forEach { (symbol, symbolTrades) ->
      // Separate OPEN and CLOSE trades
      val openTrades =
        symbolTrades
          .filter { it.openClose == OpenCloseIndicator.OPEN }
          .sortedBy { it.tradeDate }
      val closeTrades =
        symbolTrades
          .filter { it.openClose == OpenCloseIndicator.CLOSE }
          .sortedBy { it.tradeDate }

      // FIFO matching
      val openQueue = openTrades.toMutableList()
      val closeQueue = closeTrades.toMutableList()

      while (closeQueue.isNotEmpty()) {
        val close = closeQueue.removeFirst()
        var remainingCloseQty = close.quantity

        while (remainingCloseQty > 0 && openQueue.isNotEmpty()) {
          val open = openQueue.first()
          val matchQty = minOf(remainingCloseQty, open.quantity)

          // Create matched lot
          lots.add(
            TradeLot(
              openTrade = open.copy(quantity = matchQty),
              closeTrade = close.copy(quantity = matchQty),
              quantity = matchQty,
              symbol = symbol,
            ),
          )

          // Update quantities
          remainingCloseQty -= matchQty
          if (open.quantity == matchQty) {
            openQueue.removeFirst()
          } else {
            openQueue[0] = open.copy(quantity = open.quantity - matchQty)
          }
        }

        if (remainingCloseQty > 0) {
          logger.warn("Unmatched close trade for $symbol: ${close.brokerTradeId}, remaining qty: $remainingCloseQty")
        }
      }

      // Remaining open positions (not yet closed)
      openQueue.forEach { open ->
        lots.add(
          TradeLot(
            openTrade = open,
            closeTrade = null,
            quantity = open.quantity,
            symbol = symbol,
          ),
        )
      }
    }

    logger.info("Split ${trades.size} trades into ${lots.size} lots")
    return lots
  }

  /**
   * Detect option rolls: Close old position + Open new position same day.
   *
   * Criteria for detecting a roll:
   * - Both trades are options
   * - Same underlying symbol
   * - Same PUT/CALL
   * - Different strike OR different expiry
   * - Within 1 day of each other
   * - Higher confidence if same relatedOrderId
   *
   * @param lots - List of trade lots
   * @return List of detected roll pairs
   */
  fun detectOptionRolls(lots: List<TradeLot>): List<RollPair> {
    val rolls = mutableListOf<RollPair>()
    val processedLots = mutableSetOf<TradeLot>()

    // Only process closed option positions
    val closedOptionLots =
      lots.filter {
        it.closeTrade != null &&
          it.openTrade.assetType == AssetType.OPTION
      }

    closedOptionLots.forEach { closedLot ->
      // Skip if already processed as part of a roll
      if (closedLot in processedLots) return@forEach

      // Look for opening trade same day, same underlying, different strike/expiry
      // Can be either OPEN or CLOSED (to handle fully closed roll chains)
      val openedLot =
        lots.find { openLot ->
          openLot !in processedLots &&
            openLot != closedLot &&
            openLot.openTrade.assetType == AssetType.OPTION &&
            openLot.openTrade.optionDetails?.underlyingSymbol == closedLot.openTrade.optionDetails?.underlyingSymbol &&
            openLot.openTrade.optionDetails?.optionType == closedLot.openTrade.optionDetails?.optionType &&
            (
              openLot.openTrade.optionDetails?.strike != closedLot.openTrade.optionDetails?.strike ||
                openLot.openTrade.optionDetails?.expiry != closedLot.openTrade.optionDetails?.expiry
            ) &&
            ChronoUnit.DAYS.between(closedLot.closeTrade!!.tradeDate, openLot.openTrade.tradeDate) <= 1
        }

      if (openedLot != null) {
        // Check if this is high-confidence roll (same order ID)
        val highConfidence =
          closedLot.closeTrade!!.relatedOrderId != null &&
            closedLot.closeTrade!!.relatedOrderId == openedLot.openTrade.relatedOrderId

        logger.info(
          "Detected option roll: ${closedLot.symbol} " +
            "${closedLot.openTrade.optionDetails?.strike} → ${openedLot.openTrade.optionDetails?.strike} " +
            "(confidence: ${if (highConfidence) "high" else "medium"})",
        )

        rolls.add(
          RollPair(
            closedLot = closedLot,
            openedLot = openedLot,
            highConfidence = highConfidence,
          ),
        )

        // Mark only the closed lot as processed
        // The opened lot can be a closed lot in the next roll
        processedLots.add(closedLot)
      }
    }

    logger.info("Detected ${rolls.size} option rolls")
    return rolls
  }

  /**
   * Build roll chains from detected roll pairs.
   * A roll chain is a sequence of consecutive rolls: A → B → C
   * Each chain should be imported as a single position with multiple strikes.
   *
   * @param rollPairs - List of detected roll pairs
   * @return List of roll chains
   */
  fun buildRollChains(rollPairs: List<RollPair>): List<RollChain> {
    val chains = mutableListOf<RollChain>()
    val processedLots = mutableSetOf<TradeLot>()

    rollPairs.forEach { rollPair ->
      // Skip if this roll's closed lot was already processed
      if (rollPair.closedLot in processedLots) return@forEach

      // Build chain by walking backwards to find the start
      var currentLot = rollPair.closedLot
      while (true) {
        val prevRoll = rollPairs.find { it.openedLot == currentLot }
        if (prevRoll != null && prevRoll.closedLot !in processedLots) {
          currentLot = prevRoll.closedLot
        } else {
          break
        }
      }

      // Now walk forwards from start, building the chain
      val chainLots = mutableListOf<TradeLot>()
      chainLots.add(currentLot)
      processedLots.add(currentLot)

      while (true) {
        val nextRoll = rollPairs.find { it.closedLot == currentLot }
        if (nextRoll != null) {
          chainLots.add(nextRoll.openedLot)
          processedLots.add(nextRoll.openedLot)
          currentLot = nextRoll.openedLot
        } else {
          break
        }
      }

      // Create roll chain
      val underlying = chainLots
        .first()
        .openTrade.optionDetails
        ?.underlyingSymbol
        ?: chainLots.first().symbol
      val lastLot = chainLots.last()

      chains.add(
        RollChain(
          lots = chainLots,
          underlying = underlying,
          startDate = chainLots.first().openTrade.tradeDate,
          endDate = lastLot.closeTrade?.tradeDate,
          isClosed = lastLot.closeTrade != null,
        ),
      )

      logger.info(
        "Built roll chain for $underlying: ${chainLots.size} lots " +
          "(${chainLots.first().symbol} → ${chainLots.last().symbol})",
      )
    }

    logger.info("Built ${chains.size} roll chains from ${rollPairs.size} roll pairs")
    return chains
  }

  companion object {
    private val logger: Logger = LoggerFactory.getLogger(TradeProcessor::class.java)
  }
}

/**
 * Represents a matched trade lot (opening trade + optional closing trade)
 */
data class TradeLot(
  val openTrade: StandardizedTrade,
  val closeTrade: StandardizedTrade?,
  val quantity: Int,
  val symbol: String,
)

/**
 * Represents a detected option roll (close old + open new)
 */
data class RollPair(
  val closedLot: TradeLot,
  val openedLot: TradeLot,
  val highConfidence: Boolean = false,
)

/**
 * Represents a chain of rolls (A → B → C)
 * Multiple consecutive rolls should be tracked as a single position
 */
data class RollChain(
  val lots: List<TradeLot>,
  val underlying: String,
  val startDate: java.time.LocalDate,
  val endDate: java.time.LocalDate?,
  val isClosed: Boolean,
)

/**
 * Key for aggregating multiple executions of the same trade
 */
private data class AggregationKey(
  val symbol: String,
  val direction: TradeDirection,
  val openClose: OpenCloseIndicator,
  val tradeDate: java.time.LocalDate,
  val relatedOrderId: String?,
)
