package com.skrymer.udgaard.portfolio.integration.broker

import org.slf4j.LoggerFactory
import java.time.temporal.ChronoUnit

/**
 * Broker-import pipeline types: `StandardizedTrade` → `TradeLot` → `RollPair` → `RollChain`.
 * Each stage is a pure transformation; companion factories own the construction logic.
 */
private val logger = LoggerFactory.getLogger("com.skrymer.udgaard.portfolio.integration.broker.TradePipeline")

/**
 * Represents a matched trade lot (opening trade + optional closing trade).
 */
data class TradeLot(
  val openTrade: StandardizedTrade,
  val closeTrade: StandardizedTrade?,
  val quantity: Int,
  val symbol: String,
) {
  companion object {
    /**
     * Build trade lots from a flat list of broker executions: same-day partial fills with the
     * same `relatedOrderId` are aggregated first, then opens are FIFO-matched against closes per
     * symbol. Unmatched closes are logged and dropped (broker-export anomaly tolerance);
     * unmatched opens become still-open lots (`closeTrade == null`).
     */
    fun from(trades: List<StandardizedTrade>): List<TradeLot> {
      val aggregated = aggregateSameDayFills(trades)
      val lots = mutableListOf<TradeLot>()
      aggregated.groupBy { it.symbol }.forEach { (symbol, symbolTrades) ->
        val opens =
          symbolTrades
            .filter { it.openClose == OpenCloseIndicator.OPEN }
            .sortedBy { it.tradeDate }
            .toMutableList()
        val closes =
          symbolTrades
            .filter { it.openClose == OpenCloseIndicator.CLOSE }
            .sortedBy { it.tradeDate }
        closes.forEach { close ->
          var remaining = close.quantity
          while (remaining > 0 && opens.isNotEmpty()) {
            val open = opens.first()
            val match = minOf(remaining, open.quantity)
            lots.add(
              TradeLot(
                openTrade = open.copy(quantity = match),
                closeTrade = close.copy(quantity = match),
                quantity = match,
                symbol = symbol,
              ),
            )
            remaining -= match
            if (open.quantity == match) opens.removeFirst() else opens[0] = open.copy(quantity = open.quantity - match)
          }
          if (remaining > 0) {
            logger.warn("Unmatched close trade for $symbol: ${close.brokerTradeId}, remaining qty: $remaining")
          }
        }
        opens.forEach { open ->
          lots.add(TradeLot(openTrade = open, closeTrade = null, quantity = open.quantity, symbol = symbol))
        }
      }
      logger.info("Split ${trades.size} trades into ${lots.size} lots")
      return lots
    }

    private fun aggregateSameDayFills(trades: List<StandardizedTrade>): List<StandardizedTrade> {
      val aggregated =
        trades
          .groupBy { trade ->
            AggregationKey(
              symbol = trade.symbol,
              direction = trade.direction,
              openClose = trade.openClose,
              tradeDate = trade.tradeDate,
              relatedOrderId = trade.relatedOrderId,
            )
          }.map { (key, fills) ->
            if (fills.size == 1) {
              fills.first()
            } else {
              val totalQuantity = fills.sumOf { it.quantity }
              val totalCommission = fills.mapNotNull { it.commission }.sum()
              val avgPrice = fills.sumOf { it.price * it.quantity } / totalQuantity
              logger.debug(
                "Aggregated ${fills.size} executions for ${key.symbol}: " +
                  "${fills.map { it.quantity }.joinToString("+")} = $totalQuantity contracts",
              )
              fills.first().copy(quantity = totalQuantity, price = avgPrice, commission = totalCommission)
            }
          }
      if (aggregated.size < trades.size) {
        logger.info("Aggregated ${trades.size} executions into ${aggregated.size} trades")
      }
      return aggregated
    }
  }
}

/**
 * Represents a detected option roll (close old + open new).
 */
data class RollPair(
  val closedLot: TradeLot,
  val openedLot: TradeLot,
  val highConfidence: Boolean = false,
) {
  companion object {
    /**
     * Detect option rolls in a lot list: a closed option lot is paired with a same-underlying,
     * same-option-type open lot whose open date is within 1 day of the close. When the close
     * trade and an open candidate share the same `relatedOrderId` (combo order), that candidate
     * wins and the pairing is marked high-confidence.
     */
    fun detectFrom(lots: List<TradeLot>): List<RollPair> {
      val rolls = mutableListOf<RollPair>()
      val closed =
        lots.filter {
          it.closeTrade != null && it.openTrade.assetType == AssetType.OPTION
        }
      val processedLots = mutableSetOf<TradeLot>()
      closed.forEach { closedLot ->
        val candidates =
          lots.filter { other ->
            other != closedLot &&
              other !in processedLots &&
              other.openTrade.assetType == AssetType.OPTION &&
              other.openTrade.optionDetails?.underlyingSymbol ==
              closedLot.openTrade.optionDetails?.underlyingSymbol &&
              other.openTrade.optionDetails?.optionType ==
              closedLot.openTrade.optionDetails?.optionType &&
              (
                other.openTrade.optionDetails?.strike != closedLot.openTrade.optionDetails?.strike ||
                  other.openTrade.optionDetails?.expiry != closedLot.openTrade.optionDetails?.expiry
              ) &&
              ChronoUnit.DAYS.between(
                closedLot.closeTrade!!.tradeDate,
                other.openTrade.tradeDate,
              ) <= 1
          }
        val closeOrderId = closedLot.closeTrade!!.relatedOrderId
        val candidate =
          if (closeOrderId != null) {
            candidates.firstOrNull { it.openTrade.relatedOrderId == closeOrderId } ?: candidates.firstOrNull()
          } else {
            candidates.firstOrNull()
          }
        if (candidate != null) {
          val highConfidence = closeOrderId != null && closeOrderId == candidate.openTrade.relatedOrderId
          logger.info(
            "Detected option roll: ${closedLot.symbol} " +
              "${closedLot.openTrade.optionDetails?.strike} → ${candidate.openTrade.optionDetails?.strike} " +
              "(confidence: ${if (highConfidence) "high" else "medium"})",
          )
          rolls.add(RollPair(closedLot = closedLot, openedLot = candidate, highConfidence = highConfidence))
          // Once a closed lot has been paired into a roll, it must not be re-claimed by a later
          // close in the same chain (otherwise B's close in A→B→C could pair back to A).
          processedLots.add(closedLot)
        }
      }
      logger.info("Detected ${rolls.size} option rolls")
      return rolls
    }
  }
}

/**
 * A chain of consecutive rolls (A → B → C). Multiple consecutive rolls of the same underlying
 * are tracked as one position with several strikes/expiries.
 */
data class RollChain(
  val lots: List<TradeLot>,
  val underlying: String,
  val startDate: java.time.LocalDate,
  val endDate: java.time.LocalDate?,
  val isClosed: Boolean,
) {
  companion object {
    /**
     * Assemble chains from a list of `RollPair`s: each pair contributes either a new chain
     * (if its closed lot starts a fresh sequence) or extends an existing one. The walk is
     * bidirectional, so the order of the input pairs does not affect the output.
     */
    fun buildFrom(rollPairs: List<RollPair>): List<RollChain> {
      val chains = mutableListOf<RollChain>()
      val processedLots = mutableSetOf<TradeLot>()

      rollPairs.forEach { rollPair ->
        if (rollPair.closedLot in processedLots) return@forEach

        var currentLot = rollPair.closedLot
        while (true) {
          val prevRoll = rollPairs.find { it.openedLot == currentLot }
          if (prevRoll != null && prevRoll.closedLot !in processedLots) {
            currentLot = prevRoll.closedLot
          } else {
            break
          }
        }

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

        val underlying =
          chainLots
            .first()
            .openTrade.optionDetails
            ?.underlyingSymbol ?: chainLots.first().symbol
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
        logger.info("Built roll chain for $underlying: ${chainLots.size} lots (${chainLots.first().symbol} → ${chainLots.last().symbol})")
      }

      logger.info("Built ${chains.size} roll chains from ${rollPairs.size} roll pairs")
      return chains
    }
  }
}

/**
 * Aggregation grouping key for collapsing same-day partial fills inside `TradeLot.from`.
 */
private data class AggregationKey(
  val symbol: String,
  val direction: TradeDirection,
  val openClose: OpenCloseIndicator,
  val tradeDate: java.time.LocalDate,
  val relatedOrderId: String?,
)
