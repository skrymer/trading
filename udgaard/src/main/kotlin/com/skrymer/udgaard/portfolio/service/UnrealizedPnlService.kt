package com.skrymer.udgaard.portfolio.service

import com.skrymer.udgaard.data.integration.StockProvider
import com.skrymer.udgaard.portfolio.dto.PositionUnrealizedPnlResponse
import com.skrymer.udgaard.portfolio.integration.options.OptionsDataProvider
import com.skrymer.udgaard.portfolio.model.Execution
import com.skrymer.udgaard.portfolio.model.InstrumentType
import com.skrymer.udgaard.portfolio.model.PositionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Service for calculating unrealized P&L for open positions using live market data
 */
@Service
class UnrealizedPnlService(
  private val positionService: PositionService,
  private val stockProvider: StockProvider,
  private val optionsDataProvider: OptionsDataProvider,
) {
  companion object {
    private val logger: Logger = LoggerFactory.getLogger(UnrealizedPnlService::class.java)

    /**
     * Get the last completed trading day
     * AlphaVantage only has data for completed trading days, not the current day
     */
    private fun getLastTradingDay(): LocalDate {
      val today = LocalDate.now()
      return when (today.dayOfWeek) {
        DayOfWeek.MONDAY -> today.minusDays(3) // Go back to Friday
        DayOfWeek.SUNDAY -> today.minusDays(2) // Go back to Friday
        DayOfWeek.SATURDAY -> today.minusDays(1) // Go back to Friday
        else -> today.minusDays(1) // Tuesday-Friday: use yesterday
      }
    }
  }

  /**
   * Calculate rolled credits from execution history and determine the correct entry price
   * Looks for SELL/BUY pairs within 2 days with matching quantities
   * Returns: Pair(rolledCredits, effectiveEntryPrice)
   */
  private fun calculateRolledCreditsAndEntryPrice(
    positionId: Long,
    multiplier: Int,
    fallbackEntryPrice: Double,
  ): Pair<Double, Double> {
    try {
      val positionWithExecutions = positionService.getPositionWithExecutions(positionId)
      val executions = positionWithExecutions.executions.sortedBy { it.executionDate }

      if (executions.isEmpty()) return Pair(0.0, fallbackEntryPrice)

      val used = mutableSetOf<Long>()
      var totalCredits = 0.0
      var mostRecentRollBuyExecution: Execution? = null

      for (i in executions.indices) {
        if (used.contains(executions[i].id!!)) continue

        val current = executions[i]
        if (current.quantity >= 0) continue // Only look for SELLs

        // Look for matching BUY within next 5 executions and within 2 days
        for (j in i + 1 until minOf(i + 5, executions.size)) {
          if (used.contains(executions[j].id!!)) continue

          val next = executions[j]
          if (next.quantity <= 0) continue // Must be BUY

          val daysDiff =
            kotlin.math.abs(
              ChronoUnit.DAYS.between(
                current.executionDate,
                next.executionDate,
              ),
            )

          if (daysDiff <= 2 && kotlin.math.abs(current.quantity) == next.quantity) {
            val sellProceeds = kotlin.math.abs(current.quantity) * current.price * multiplier
            val buyCost = next.quantity * next.price * multiplier
            val totalCommission = kotlin.math.abs(current.commission ?: 0.0) + kotlin.math.abs(next.commission ?: 0.0)
            val credit = sellProceeds - buyCost - totalCommission

            totalCredits += credit
            used.add(current.id!!)
            used.add(next.id!!)
            mostRecentRollBuyExecution = next // Track the most recent roll BUY
            break
          }
        }
      }

      // If we found roll pairs, use the most recent roll BUY price as entry price
      // Otherwise, use the fallback (position's average entry price)
      val effectiveEntryPrice = mostRecentRollBuyExecution?.price ?: fallbackEntryPrice

      logger.debug(
        "Position $positionId: Calculated rolled credits: $$totalCredits from ${used.size / 2} roll pairs, " +
          "effective entry price: $$effectiveEntryPrice",
      )
      return Pair(totalCredits, effectiveEntryPrice)
    } catch (e: Exception) {
      logger.warn("Error calculating rolled credits for position $positionId: ${e.message}")
      return Pair(0.0, fallbackEntryPrice)
    }
  }

  fun calculateUnrealizedPnl(portfolioId: Long): List<PositionUnrealizedPnlResponse> =
    runBlocking(Dispatchers.IO) {
      val openPositions = positionService.getPositions(portfolioId, PositionStatus.OPEN)

      logger.info("Calculating unrealized P&L for ${openPositions.size} open positions in portfolio $portfolioId")

      // Process all positions in parallel
      openPositions
        .map { position ->
          async {
            try {
              // Calculate rolled credits and determine effective entry price
              val (rolledCredits, effectiveEntryPrice) =
                calculateRolledCreditsAndEntryPrice(
                  position.id!!,
                  position.multiplier,
                  position.averageEntryPrice,
                )

              val currentPrice =
                when (position.instrumentType) {
                  InstrumentType.OPTION -> {
                    // Fetch current option price from AlphaVantage
                    if (position.strikePrice == null ||
                      position.expirationDate == null ||
                      position.optionType == null ||
                      position.underlyingSymbol == null
                    ) {
                      logger.warn("Position ${position.id} is missing required option fields")
                      null
                    } else {
                      val lastTradingDay = getLastTradingDay().toString()
                      logger.debug("Using last trading day: $lastTradingDay for position ${position.id}")
                      val contract =
                        optionsDataProvider.findOptionContract(
                          symbol = position.underlyingSymbol,
                          strike = position.strikePrice,
                          expiration = position.expirationDate.toString(),
                          optionType = position.optionType,
                          date = lastTradingDay,
                        )
                      contract?.price
                    }
                  }
                  InstrumentType.STOCK, InstrumentType.LEVERAGED_ETF -> {
                    // Fetch latest stock price from AlphaVantage
                    val quotes = stockProvider.getDailyAdjustedTimeSeries(position.symbol, "compact")
                    quotes?.firstOrNull()?.closePrice
                  }
                }

              // Calculate unrealized P&L if we have current price
              // Use effectiveEntryPrice (from most recent roll) instead of position.averageEntryPrice
              val currentPositionUnrealizedPnl =
                if (currentPrice != null) {
                  val priceDiff = currentPrice - effectiveEntryPrice
                  val quantity = position.currentQuantity
                  val multiplier = position.multiplier
                  priceDiff * quantity * multiplier
                } else {
                  null
                }

              // Total unrealized P&L includes current position unrealized P&L + rolled credits
              val totalUnrealizedPnl =
                if (currentPositionUnrealizedPnl != null) {
                  currentPositionUnrealizedPnl + rolledCredits
                } else {
                  null
                }

              val unrealizedPnlPercentage =
                if (totalUnrealizedPnl != null && position.totalCost > 0) {
                  (totalUnrealizedPnl / position.totalCost) * 100
                } else {
                  null
                }

              logger.debug(
                "Position ${position.id} (${position.symbol}): " +
                  "effectiveEntry=$effectiveEntryPrice, current=$currentPrice, " +
                  "unrealized P&L=$totalUnrealizedPnl (current: $currentPositionUnrealizedPnl + rolled: $rolledCredits)",
              )

              PositionUnrealizedPnlResponse(
                positionId = position.id,
                symbol = position.symbol,
                currentPrice = currentPrice,
                averageEntryPrice = effectiveEntryPrice,
                unrealizedPnl = totalUnrealizedPnl,
                unrealizedPnlPercentage = unrealizedPnlPercentage,
              )
            } catch (e: Exception) {
              logger.error("Error calculating unrealized P&L for position ${position.id}: ${e.message}", e)
              // Return position with null values on error
              PositionUnrealizedPnlResponse(
                positionId = position.id!!,
                symbol = position.symbol,
                currentPrice = null,
                averageEntryPrice = position.averageEntryPrice,
                unrealizedPnl = null,
                unrealizedPnlPercentage = null,
              )
            }
          }
        }.awaitAll()
    }
}
