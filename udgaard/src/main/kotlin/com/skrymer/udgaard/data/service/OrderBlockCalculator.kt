package com.skrymer.udgaard.data.service

import com.skrymer.udgaard.data.model.OrderBlock
import com.skrymer.udgaard.data.model.OrderBlockSensitivity
import com.skrymer.udgaard.data.model.OrderBlockType
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * Service for calculating order blocks using Rate of Change (ROC) momentum detection.
 *
 * Based on Python implementation that detects high-momentum moves
 * and identifies the origin candle (last opposite-colored candle) as the order block.
 *
 * Algorithm:
 * 1. Calculate ROC: (open - open[4]) / open[4] * 100
 * 2. When ROC crosses threshold, look back 4-15 bars for last opposite candle
 * 3. Mark that candle as order block with its high/low range
 * 4. Track active blocks and mitigate them when price closes through
 */
@Component
class OrderBlockCalculator {
  companion object {
    // Default sensitivity (28 in raw form, converted to 0.28 for threshold)
    const val DEFAULT_SENSITIVITY = 28.0

    // Minimum bars between same-type order blocks to prevent clustering
    const val SAME_TYPE_SPACING = 5

    // Minimum bars between cross-type order blocks
    const val CROSS_TYPE_SPACING = 5

    // Lookback window to find origin candle
    const val LOOKBACK_MIN = 4
    const val LOOKBACK_MAX = 15

    // ROC calculation period
    const val ROC_PERIOD = 4

    // Volume lookback period for relative volume calculation
    const val VOLUME_LOOKBACK = 20
  }

  /**
   * Represents a recent ROC crossing event
   */
  data class Crossing(
    val index: Int,
    val type: OrderBlockType,
  )

  /**
   * Calculate order blocks for a stock's quote history
   *
   * @param quotes List of stock quotes sorted by date (oldest to newest)
   * @param sensitivity Sensitivity parameter (default 28, converted to 0.28 for threshold)
   * @param sensitivityLevel Sensitivity level enum (HIGH=28%, LOW=50%) for tracking
   * @param sameTypeSpacing Minimum bars between same-type blocks
   * @param crossTypeSpacing Minimum bars between cross-type blocks
   * @return List of calculated order blocks
   */
  fun calculateOrderBlocks(
    quotes: List<StockQuote>,
    sensitivity: Double = DEFAULT_SENSITIVITY,
    sensitivityLevel: OrderBlockSensitivity = OrderBlockSensitivity.HIGH,
    sameTypeSpacing: Int = SAME_TYPE_SPACING,
    crossTypeSpacing: Int = CROSS_TYPE_SPACING,
  ): List<OrderBlock> {
    if (quotes.size < LOOKBACK_MAX + ROC_PERIOD) {
      return emptyList()
    }

    val sortedQuotes = quotes.sortedBy { it.date }
    val orderBlockDomains = mutableListOf<OrderBlock>()
    val recentCrossings = mutableListOf<Crossing>()

    // ROC returns percentage (e.g., 0.69 for 0.69% move)
    // Sensitivity input is in "whole number" form (e.g., 28 for 28%)
    // We divide by 100 to match ROC scale: 28 / 100 = 0.28
    // Then we compare ROC (0.69) > threshold (0.28) to detect crossings
    val threshold = sensitivity / 100.0

    // Track active order blocks for mitigation detection
    val activeBlocks = mutableListOf<OrderBlock>()

    // Start from index 5 (ROC_PERIOD + 1) to allow ROC calculation
    for (i in 5 until sortedQuotes.size) {
      // Calculate ROC for current and previous bar
      val roc = calculateRateOfChange(sortedQuotes, i)
      val prevRoc = calculateRateOfChange(sortedQuotes, i - 1)

      // Check for bearish crossing (ROC crosses under negative threshold)
      // Python: prevRoc > -sensitivity and roc < -sensitivity
      val bearishCrossing = prevRoc > -threshold && roc < -threshold

      // Check for bullish crossing (ROC crosses over positive threshold)
      // Python: prevRoc < sensitivity and roc > sensitivity
      val bullishCrossing = prevRoc < threshold && roc > threshold

      // Process crossings
      val blockType =
        when {
          bearishCrossing -> OrderBlockType.BEARISH
          bullishCrossing -> OrderBlockType.BULLISH
          else -> null
        }

      if (blockType != null) {
        // Check if this crossing is too close to a previous one.
        // All crossings (skipped or not) are recorded in recentCrossings to match
        // TradingView's cross_index tracking, which updates on every crossing event.
        // Only non-skipped crossings produce OBs.
        var skip = false
        for (crossing in recentCrossings.reversed()) {
          val spacing = if (crossing.type == blockType) sameTypeSpacing else crossTypeSpacing
          if (i - crossing.index <= spacing) {
            skip = true
            break
          }
        }

        // Record ALL crossings for spacing checks (matches TV's cross_index behavior)
        recentCrossings.add(Crossing(i, blockType))

        if (!skip) {
          // Find the origin candle and create order block
          findAndCreateOrderBlockDomain(
            sortedQuotes,
            i,
            blockType,
            roc,
            sensitivityLevel,
          )?.let { block ->
            orderBlockDomains.add(block)
            // Only track blocks that weren't already mitigated by findMitigationDate.
            // If endDate is already set, the correct mitigation date was found during
            // forward scanning — adding to activeBlocks would let mitigateBlocks overwrite it.
            if (block.endDate == null) {
              activeBlocks.add(block)
            }
          }
        }
      }

      // Check for mitigation of active blocks
      mitigateBlocks(activeBlocks, sortedQuotes[i], sortedQuotes.getOrNull(i - 1))
    }

    return orderBlockDomains
  }

  /**
   * Calculate Rate of Change (ROC) as percentage
   *
   * The Python implementation does an unusual thing that works:
   * - Divides sensitivity by 100 (28 → 0.28)
   * - Multiplies ROC by 100 (0.0069 → 0.69)
   * - Compares them: 0.69 > 0.28 ✓
   *
   * This creates an "apples to oranges" comparison that actually works correctly
   * for detecting crossings because:
   * - For bearish: -0.69 < -0.28 = TRUE (further left on number line)
   * - For bullish: 0.69 > 0.28 = TRUE (further right on number line)
   */
  private fun calculateRateOfChange(
    quotes: List<StockQuote>,
    index: Int,
  ): Double {
    if (index < ROC_PERIOD) return 0.0

    val currentOpen = quotes[index].openPrice
    val previousOpen = quotes[index - ROC_PERIOD].openPrice

    if (previousOpen == 0.0) return 0.0

    // Return as percentage (multiply by 100) to match Python implementation
    return ((currentOpen - previousOpen) / previousOpen) * 100.0
  }

  /**
   * Find the origin candle and create an order block
   *
   * For bearish blocks: Find last green (bullish) candle in lookback window (i-4 to i-15)
   * For bullish blocks: Find last red (bearish) candle in lookback window (i-4 to i-15)
   */
  private fun findAndCreateOrderBlockDomain(
    quotes: List<StockQuote>,
    triggerIndex: Int,
    type: OrderBlockType,
    roc: Double,
    sensitivityLevel: OrderBlockSensitivity,
  ): OrderBlock? {
    // Look back from i-4 to i-15 to find the origin candle
    for (j in (triggerIndex - LOOKBACK_MIN) downTo (triggerIndex - LOOKBACK_MAX)) {
      if (j < 0) continue

      val quote = quotes[j]
      val isBullishCandle = quote.closePrice > quote.openPrice
      val isBearishCandle = quote.closePrice < quote.openPrice

      // For bearish order blocks, find last bullish candle
      // For bullish order blocks, find last bearish candle
      val isOriginCandle =
        when (type) {
          OrderBlockType.BEARISH -> isBullishCandle
          OrderBlockType.BULLISH -> isBearishCandle
        }

      if (isOriginCandle) {
        // Calculate volume strength
        val volumeStrength = calculateVolumeStrength(quotes, j)

        // Find the end date (mitigation point in the future)
        val endDate = findMitigationDate(quotes, j, type, quote)

        return OrderBlock(
          low = quote.low,
          high = quote.high,
          startDate = quote.date ?: LocalDate.now(),
          endDate = endDate,
          orderBlockType = type,
          volume = quote.volume,
          volumeStrength = volumeStrength,
          sensitivity = sensitivityLevel, // Use passed sensitivity level
          rateOfChange = roc, // Already in percentage form from calculateRateOfChange()
        )
      }
    }

    return null
  }

  /**
   * Find the date when the order block gets mitigated
   * Returns null if block is still active
   *
   * Uses close[1] (previous bar's close) for mitigation checking.
   * Scans from the bar after the origin candle to catch mitigation that happens
   * between the origin and the trigger (e.g., during the impulse move).
   */
  private fun findMitigationDate(
    quotes: List<StockQuote>,
    startIndex: Int,
    type: OrderBlockType,
    originQuote: StockQuote,
  ): LocalDate? {
    // Start from bar after origin candle — the OB zone exists from the origin,
    // so any close through it after that point counts as mitigation
    for (k in (startIndex + 1) until quotes.size) {
      // Check if PREVIOUS bar's close mitigated the OB (matches TradingView's close[1] logic)
      if (k > 0) {
        val previousQuote = quotes[k - 1]
        val isMitigated =
          when (type) {
            OrderBlockType.BEARISH -> previousQuote.closePrice > originQuote.high
            OrderBlockType.BULLISH -> previousQuote.closePrice < originQuote.low
          }

        if (isMitigated) {
          // Return current bar's date (the bar AFTER the mitigation)
          return quotes[k].date
        }
      }
    }

    // Block is still active
    return null
  }

  /**
   * Calculate relative volume strength
   * Uses average volume over lookback period
   */
  private fun calculateVolumeStrength(
    quotes: List<StockQuote>,
    index: Int,
  ): Double {
    if (index < VOLUME_LOOKBACK) {
      return 1.0 // Not enough data for volume calculation
    }

    val currentVolume = quotes[index].volume

    // If current volume is 0, return default
    if (currentVolume == 0L) {
      return 1.0
    }

    // Calculate average volume over lookback period
    val startIndex = maxOf(0, index - VOLUME_LOOKBACK)
    val volumeList =
      quotes
        .subList(startIndex, index)
        .map { it.volume }
        .filter { it > 0 } // Filter out zero volumes

    if (volumeList.isEmpty()) {
      return 1.0
    }

    val avgVolume = volumeList.average()

    return if (avgVolume > 0) {
      currentVolume.toDouble() / avgVolume
    } else {
      1.0
    }
  }

  /**
   * Check if active order blocks should be mitigated
   *
   * TradingView uses close[1] (previous bar's close) for mitigation.
   * We check if the previous bar's close went through the OB boundary.
   *
   * Bullish blocks are mitigated when previous close < low
   * Bearish blocks are mitigated when previous close > high
   */
  private fun mitigateBlocks(
    activeBlocks: MutableList<OrderBlock>,
    currentQuote: StockQuote,
    previousQuote: StockQuote?,
  ) {
    // If no previous quote available, cannot check mitigation
    if (previousQuote == null) {
      return
    }

    val blocksToMitigate =
      activeBlocks.filter { block ->
        when (block.orderBlockType) {
          OrderBlockType.BULLISH -> previousQuote.closePrice < block.low
          OrderBlockType.BEARISH -> previousQuote.closePrice > block.high
        }
      }

    // Update mitigated blocks with end date (current bar's date, since previous bar caused mitigation)
    blocksToMitigate.forEach { block ->
      block.endDate = currentQuote.date
    }

    // Remove mitigated blocks from active list
    activeBlocks.removeAll(blocksToMitigate)
  }
}
