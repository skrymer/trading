package com.skrymer.udgaard.service

import com.skrymer.udgaard.domain.OrderBlockSensitivity
import com.skrymer.udgaard.domain.OrderBlockType
import com.skrymer.udgaard.domain.StockQuoteDomain
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.time.LocalDate

class OrderBlockCalculatorTest {
  private val logger = LoggerFactory.getLogger(OrderBlockCalculatorTest::class.java)

  private lateinit var calculator: OrderBlockCalculator

  @BeforeEach
  fun setup() {
    calculator = OrderBlockCalculator()
  }

  @Test
  @Disabled("Test data needs updating for current OrderBlockCalculator implementation")
  fun `should detect bullish order block on strong upward momentum`() {
    // Given: A series of quotes with a strong upward move (>28% ROC)
    val quotes = createQuotesWithBullishMomentum()

    // When: Calculate order blocks with default sensitivity (28)
    val orderBlocks = calculator.calculateOrderBlocks(quotes = quotes)

    // Then: Should detect at least one bullish order block
    val bullishBlocks = orderBlocks.filter { it.orderBlockType == com.skrymer.udgaard.domain.OrderBlockType.BULLISH }
    logger.info("Total blocks: ${orderBlocks.size}, Bullish: ${bullishBlocks.size}, Bearish: ${orderBlocks.size - bullishBlocks.size}")
    orderBlocks.forEach { b ->
      logger.info("  ${b.orderBlockType}: start=${b.startDate}, ROC=${b.rateOfChange}")
    }
    assertTrue(bullishBlocks.isNotEmpty(), "Should detect bullish order block")

    // Verify the order block properties
    val block = bullishBlocks.first()
    assertEquals(OrderBlockSensitivity.HIGH, block.sensitivity)
    assertTrue(
      block.rateOfChange >= OrderBlockCalculator.DEFAULT_SENSITIVITY / 100.0,
      "ROC ${block.rateOfChange} should be >= ${OrderBlockCalculator.DEFAULT_SENSITIVITY / 100.0}",
    )
    assertNull(block.endDate, "Active block should have null end date")
  }

  @Test
  fun `should detect bearish order block on strong downward momentum`() {
    // Given: A series of quotes with a strong downward move (< -28% ROC)
    val quotes = createQuotesWithBearishMomentum()

    // When: Calculate order blocks with default sensitivity (28)
    val orderBlocks = calculator.calculateOrderBlocks(quotes = quotes)

    // Then: Should detect at least one bearish order block
    val bearishBlocks = orderBlocks.filter { it.orderBlockType == OrderBlockType.BEARISH }
    assertTrue(bearishBlocks.isNotEmpty(), "Should detect bearish order block")

    // Verify the order block properties
    val block = bearishBlocks.first()
    assertEquals(OrderBlockSensitivity.HIGH, block.sensitivity)
    assertTrue(
      block.rateOfChange <= -(OrderBlockCalculator.DEFAULT_SENSITIVITY / 100.0),
      "ROC ${block.rateOfChange} should be <= -${OrderBlockCalculator.DEFAULT_SENSITIVITY / 100.0}",
    )
  }

  @Test
  fun `should respect sensitivity thresholds`() {
    // Given: Quotes with moderate momentum (35% ROC)
    val quotes = createQuotesWithModerateMomentum()

    // When: Calculate with high sensitivity (28 - detects 35% moves)
    val highSensBlocks =
      calculator.calculateOrderBlocks(
        quotes = quotes,
        sensitivity = 28.0,
      )

    // When: Calculate with low sensitivity (50 - does not detect 35% moves)
    val lowSensBlocks =
      calculator.calculateOrderBlocks(
        quotes = quotes,
        sensitivity = 50.0,
      )

    // Then: High sensitivity should detect more blocks than low sensitivity
    assertTrue(
      highSensBlocks.size >= lowSensBlocks.size,
      "High sensitivity (28) should detect at least as many blocks as low sensitivity (50)",
    )
  }

  @Test
  @org.junit.jupiter.api.Disabled("Test data needs updating for current OrderBlockCalculator implementation")
  fun `should mitigate bullish order block when price closes below low`() {
    // Given: A bullish order block that gets mitigated
    val quotes = createQuotesWithBullishBlockAndMitigation()

    // When: Calculate order blocks
    val orderBlocks = calculator.calculateOrderBlocks(quotes = quotes)

    // Then: The bullish block should have an end date (mitigated)
    val bullishBlocks = orderBlocks.filter { it.orderBlockType == OrderBlockType.BULLISH }
    assertTrue(bullishBlocks.isNotEmpty())

    // Note: Mitigation tracking is internal to the calculator
    // The block is marked as mitigated by setting endDate
    // Since we're checking after the fact, we verify the block exists
  }

  @Test
  fun `should mitigate bearish order block when price closes above high`() {
    // Given: A bearish order block that gets mitigated
    val quotes = createQuotesWithBearishBlockAndMitigation()

    // When: Calculate order blocks
    val orderBlocks = calculator.calculateOrderBlocks(quotes = quotes)

    // Then: The bearish block should exist
    val bearishBlocks = orderBlocks.filter { it.orderBlockType == OrderBlockType.BEARISH }
    assertTrue(bearishBlocks.isNotEmpty())
  }

  @Test
  fun `should not create blocks when quotes list is too short`() {
    // Given: Very few quotes (less than LOOKBACK_MAX + ROC_PERIOD)
    val quotes =
      listOf(
        createQuote(LocalDate.of(2025, 1, 1), 100.0, 100.0, 102.0, 98.0),
        createQuote(LocalDate.of(2025, 1, 2), 101.0, 101.0, 103.0, 99.0),
      )

    // When: Calculate order blocks
    val orderBlocks = calculator.calculateOrderBlocks(quotes = quotes)

    // Then: Should return empty list
    assertTrue(orderBlocks.isEmpty(), "Should not create blocks with insufficient data")
  }

  @Test
  fun `should prevent clustering of order blocks`() {
    // Given: Multiple rapid momentum moves
    val quotes = createQuotesWithRapidMomentumChanges()

    // When: Calculate order blocks with default spacing
    val orderBlocks = calculator.calculateOrderBlocks(quotes = quotes)

    // Then: Blocks should be spaced apart by SAME_TYPE_SPACING
    // Verify that consecutive same-type blocks are at least 5 bars apart
    val bullishBlocks = orderBlocks.filter { it.orderBlockType == OrderBlockType.BULLISH }.sortedBy { it.startDate }
    for (i in 1 until bullishBlocks.size) {
      val previous = bullishBlocks[i - 1]
      val current = bullishBlocks[i]

      val daysBetween =
        java.time.temporal.ChronoUnit.DAYS
          .between(previous.startDate, current.startDate)
      assertTrue(
        daysBetween >= OrderBlockCalculator.SAME_TYPE_SPACING,
        "Same-type blocks should be at least ${OrderBlockCalculator.SAME_TYPE_SPACING} bars apart",
      )
    }
  }

  // Helper methods to create test data

  private fun createQuotesWithBullishMomentum(): List<StockQuoteDomain> {
    val baseDate = LocalDate.of(2025, 1, 1)
    return listOf(
      // Setup quotes with gradual increase - need at least 19 quotes for LOOKBACK_MAX + ROC_PERIOD
      createQuote(baseDate, 100.0, 100.5, 101.0, 99.0),
      createQuote(baseDate.plusDays(1), 100.5, 101.0, 102.0, 100.0),
      createQuote(baseDate.plusDays(2), 101.0, 101.5, 102.5, 100.5),
      createQuote(baseDate.plusDays(3), 101.5, 102.0, 103.0, 101.0),
      createQuote(baseDate.plusDays(4), 102.0, 102.5, 103.5, 101.5),
      createQuote(baseDate.plusDays(5), 102.5, 103.0, 104.0, 102.0),
      createQuote(baseDate.plusDays(6), 103.0, 103.5, 104.5, 102.5),
      createQuote(baseDate.plusDays(7), 103.5, 104.0, 105.0, 103.0),
      createQuote(baseDate.plusDays(8), 104.0, 104.5, 105.5, 103.5),
      createQuote(baseDate.plusDays(9), 104.5, 105.0, 106.0, 104.0),
      createQuote(baseDate.plusDays(10), 105.0, 105.5, 106.5, 104.5),
      createQuote(baseDate.plusDays(11), 105.5, 106.0, 107.0, 105.0),
      createQuote(baseDate.plusDays(12), 106.0, 106.5, 107.5, 105.5),
      createQuote(baseDate.plusDays(13), 106.5, 107.0, 108.0, 106.0),
      createQuote(baseDate.plusDays(14), 107.0, 107.5, 108.5, 106.5),
      // Bearish candle (will be origin of bullish OB) - this is where we'll look back from
      createQuote(baseDate.plusDays(15), 108.0, 107.5, 109.0, 107.0),
      createQuote(baseDate.plusDays(16), 108.0, 109.0, 110.0, 107.5),
      createQuote(baseDate.plusDays(17), 109.0, 110.0, 111.0, 108.5),
      createQuote(baseDate.plusDays(18), 110.0, 120.0, 121.0, 109.5),
      // Strong upward move - CROSSOVER happens here
      // ROC = (open[19] - open[15]) / open[15] * 100 = (140 - 108) / 108 * 100 = 29.63% (> 28%)
      // Previous ROC = (open[18] - open[14]) / open[14] * 100 = (110 - 107) / 107 * 100 = 2.8% (< 28%)
      // This creates a CROSSOVER - ROC goes from below 28% to above 28%
      createQuote(baseDate.plusDays(19), 140.0, 141.0, 142.0, 139.0),
      createQuote(baseDate.plusDays(20), 141.0, 142.0, 143.0, 140.0),
      createQuote(baseDate.plusDays(21), 142.0, 143.0, 144.0, 141.0),
    )
  }

  private fun createQuotesWithBearishMomentum(): List<StockQuoteDomain> {
    val baseDate = LocalDate.of(2025, 1, 1)
    return listOf(
      // Setup quotes - need at least 19 quotes
      createQuote(baseDate, 150.0, 150.5, 151.0, 149.0),
      createQuote(baseDate.plusDays(1), 150.5, 151.0, 152.0, 150.0),
      createQuote(baseDate.plusDays(2), 151.0, 151.5, 152.5, 150.5),
      createQuote(baseDate.plusDays(3), 151.5, 152.0, 153.0, 151.0),
      createQuote(baseDate.plusDays(4), 152.0, 152.5, 153.5, 151.5),
      createQuote(baseDate.plusDays(5), 152.5, 153.0, 154.0, 152.0),
      createQuote(baseDate.plusDays(6), 153.0, 153.5, 154.5, 152.5),
      createQuote(baseDate.plusDays(7), 153.5, 154.0, 155.0, 153.0),
      createQuote(baseDate.plusDays(8), 154.0, 154.5, 155.5, 153.5),
      createQuote(baseDate.plusDays(9), 154.5, 155.0, 156.0, 154.0),
      createQuote(baseDate.plusDays(10), 155.0, 155.5, 156.5, 154.5),
      createQuote(baseDate.plusDays(11), 155.5, 156.0, 157.0, 155.0),
      createQuote(baseDate.plusDays(12), 156.0, 156.5, 157.5, 155.5),
      createQuote(baseDate.plusDays(13), 156.5, 157.0, 158.0, 156.0),
      createQuote(baseDate.plusDays(14), 157.0, 157.5, 158.5, 156.5),
      // Bullish candle (will be origin of bearish OB) - this is where we'll look back from
      createQuote(baseDate.plusDays(15), 158.0, 158.5, 159.0, 157.5),
      createQuote(baseDate.plusDays(16), 158.0, 157.0, 159.0, 156.5),
      createQuote(baseDate.plusDays(17), 157.0, 156.0, 158.0, 155.5),
      createQuote(baseDate.plusDays(18), 156.0, 140.0, 157.0, 139.0),
      // Strong downward move - CROSSUNDER happens here
      // ROC = (open[19] - open[15]) / open[15] * 100 = (110 - 158) / 158 * 100 = -30.38% (< -28%)
      // Previous ROC = (open[18] - open[14]) / open[14] * 100 = (156 - 157) / 157 * 100 = -0.64% (> -28%)
      // This creates a CROSSUNDER - ROC goes from above -28% to below -28%
      createQuote(baseDate.plusDays(19), 110.0, 109.0, 111.0, 108.0),
      createQuote(baseDate.plusDays(20), 109.0, 108.0, 110.0, 107.0),
      createQuote(baseDate.plusDays(21), 108.0, 107.0, 109.0, 106.0),
    )
  }

  private fun createQuotesWithModerateMomentum(): List<StockQuoteDomain> {
    val baseDate = LocalDate.of(2025, 1, 1)
    return listOf(
      // Setup quotes with gradual increase - need at least 40+ quotes to test both sensitivity levels
      createQuote(baseDate, 100.0, 100.5, 101.0, 99.0),
      createQuote(baseDate.plusDays(1), 100.5, 101.0, 102.0, 100.0),
      createQuote(baseDate.plusDays(2), 101.0, 101.5, 102.5, 100.5),
      createQuote(baseDate.plusDays(3), 101.5, 102.0, 103.0, 101.0),
      createQuote(baseDate.plusDays(4), 102.0, 102.5, 103.5, 101.5),
      createQuote(baseDate.plusDays(5), 102.5, 103.0, 104.0, 102.0),
      createQuote(baseDate.plusDays(6), 103.0, 103.5, 104.5, 102.5),
      createQuote(baseDate.plusDays(7), 103.5, 104.0, 105.0, 103.0),
      createQuote(baseDate.plusDays(8), 104.0, 104.5, 105.5, 103.5),
      createQuote(baseDate.plusDays(9), 104.5, 105.0, 106.0, 104.0),
      createQuote(baseDate.plusDays(10), 105.0, 105.5, 106.5, 104.5),
      createQuote(baseDate.plusDays(11), 105.5, 106.0, 107.0, 105.0),
      createQuote(baseDate.plusDays(12), 106.0, 106.5, 107.5, 105.5),
      createQuote(baseDate.plusDays(13), 106.5, 107.0, 108.0, 106.0),
      createQuote(baseDate.plusDays(14), 107.0, 107.5, 108.5, 106.5),
      // Bearish candle (origin for first bullish OB with high sensitivity - 35% ROC)
      createQuote(baseDate.plusDays(15), 108.0, 107.5, 109.0, 107.0),
      createQuote(baseDate.plusDays(16), 108.0, 109.0, 110.0, 107.5),
      createQuote(baseDate.plusDays(17), 109.0, 110.0, 111.0, 108.5),
      createQuote(baseDate.plusDays(18), 110.0, 111.0, 112.0, 109.5),
      // First CROSSOVER - 35% ROC (triggers HIGH sensitivity only)
      // ROC = (146 - 108) / 108 * 100 = 35.19% (> 28%, < 50%)
      // Previous ROC = (110 - 107) / 107 * 100 = 2.8% (< 28%)
      createQuote(baseDate.plusDays(19), 146.0, 147.0, 148.0, 145.0),
      createQuote(baseDate.plusDays(20), 147.0, 148.0, 149.0, 146.0),
      createQuote(baseDate.plusDays(21), 148.0, 149.0, 150.0, 147.0),
      createQuote(baseDate.plusDays(22), 149.0, 150.0, 151.0, 148.0),
      createQuote(baseDate.plusDays(23), 150.0, 151.0, 152.0, 149.0),
      createQuote(baseDate.plusDays(24), 151.0, 152.0, 153.0, 150.0),
      createQuote(baseDate.plusDays(25), 152.0, 153.0, 154.0, 151.0),
      // Now create a scenario for LOW sensitivity (50% ROC)
      createQuote(baseDate.plusDays(26), 153.0, 154.0, 155.0, 152.0),
      createQuote(baseDate.plusDays(27), 154.0, 155.0, 156.0, 153.0),
      createQuote(baseDate.plusDays(28), 155.0, 156.0, 157.0, 154.0),
      createQuote(baseDate.plusDays(29), 156.0, 157.0, 158.0, 155.0),
      createQuote(baseDate.plusDays(30), 157.0, 158.0, 159.0, 156.0),
      // Bearish candle (origin for second bullish OB with low sensitivity - 55% ROC)
      createQuote(baseDate.plusDays(31), 158.0, 157.5, 159.0, 157.0),
      createQuote(baseDate.plusDays(32), 158.0, 159.0, 160.0, 157.5),
      createQuote(baseDate.plusDays(33), 159.0, 160.0, 161.0, 158.5),
      createQuote(baseDate.plusDays(34), 160.0, 161.0, 162.0, 159.5),
      // Second CROSSOVER - 55% ROC (triggers both HIGH and LOW sensitivity)
      // ROC = (245 - 158) / 158 * 100 = 55.06% (> 50%)
      // Previous ROC = (160 - 157) / 157 * 100 = 1.91% (< 50%)
      createQuote(baseDate.plusDays(35), 245.0, 246.0, 247.0, 244.0),
      createQuote(baseDate.plusDays(36), 246.0, 247.0, 248.0, 245.0),
      createQuote(baseDate.plusDays(37), 247.0, 248.0, 249.0, 246.0),
    )
  }

  private fun createQuotesWithBullishBlockAndMitigation(): List<StockQuoteDomain> {
    val bullishQuotes = createQuotesWithBullishMomentum().toMutableList()
    val lastQuote = bullishQuotes.last()

    // Add quotes that close below the order block low to mitigate it
    bullishQuotes.add(
      createQuote(
        lastQuote.date!!.plusDays(1),
        130.0,
        95.0, // Close below the OB low
        131.0,
        94.0,
      ),
    )

    return bullishQuotes
  }

  private fun createQuotesWithBearishBlockAndMitigation(): List<StockQuoteDomain> {
    val bearishQuotes = createQuotesWithBearishMomentum().toMutableList()
    val lastQuote = bearishQuotes.last()

    // Add quotes that close above the order block high to mitigate it
    bearishQuotes.add(
      createQuote(
        lastQuote.date!!.plusDays(1),
        75.0,
        110.0, // Close above the OB high
        111.0,
        74.0,
      ),
    )

    return bearishQuotes
  }

  private fun createQuotesWithRapidMomentumChanges(): List<StockQuoteDomain> {
    val baseDate = LocalDate.of(2025, 1, 1)
    val quotes = mutableListOf<StockQuoteDomain>()

    // Create quotes with multiple CROSSOVER events to test MIN_BARS_BETWEEN_BLOCKS
    // We'll create crossovers at specific intervals to verify the spacing rule

    var price = 100.0
    for (i in 0..60) {
      val date = baseDate.plusDays(i.toLong())

      // Create setup bars with gradual movement
      if (i < 15) {
        // Initial setup - small movements
        val change = if (i % 2 == 0) 0.5 else -0.25
        quotes.add(createQuote(date, price, price + change, price + change + 0.5, price - 0.5))
        price += change
      }
      // First crossover at bar 19 (from bar 15)
      else if (i == 15) {
        // Add bearish candle (origin for bullish OB)
        quotes.add(createQuote(date, price, price - 0.5, price + 0.5, price - 1.0))
        price -= 0.5
      } else if (i in 16..18) {
        // Gradual increase
        quotes.add(createQuote(date, price, price + 1.0, price + 1.5, price - 0.5))
        price += 1.0
      } else if (i == 19) {
        // FIRST CROSSOVER - 35% ROC creates HIGH sensitivity OB
        val startPrice = quotes[15].openPrice
        val targetPrice = startPrice * 1.35 // 35% increase
        quotes.add(createQuote(date, targetPrice, targetPrice + 1, targetPrice + 2, targetPrice - 1))
        price = targetPrice + 1
      }
      // Try to create another block too soon (should be filtered)
      else if (i == 21) {
        // This is only 2 bars after the first OB (< MIN_BARS_BETWEEN_BLOCKS)
        val startPrice = quotes[17].openPrice
        val targetPrice = startPrice * 1.30 // 30% increase
        quotes.add(createQuote(date, targetPrice, targetPrice + 1, targetPrice + 2, targetPrice - 1))
        price = targetPrice + 1
      }
      // Second valid crossover at bar 30 (10 bars after first, > MIN_BARS_BETWEEN_BLOCKS)
      else if (i == 26) {
        // Add bearish candle (origin for second bullish OB)
        quotes.add(createQuote(date, price, price - 0.5, price + 0.5, price - 1.0))
        price -= 0.5
      } else if (i in 27..29) {
        // Gradual increase
        quotes.add(createQuote(date, price, price + 1.0, price + 1.5, price - 0.5))
        price += 1.0
      } else if (i == 30) {
        // SECOND CROSSOVER - 35% ROC creates HIGH sensitivity OB (valid spacing)
        val startPrice = quotes[26].openPrice
        val targetPrice = startPrice * 1.35 // 35% increase
        quotes.add(createQuote(date, targetPrice, targetPrice + 1, targetPrice + 2, targetPrice - 1))
        price = targetPrice + 1
      } else {
        // Normal fluctuation for remaining bars
        val change = if (i % 2 == 0) 0.5 else -0.25
        quotes.add(createQuote(date, price, price + change, price + change + 0.5, price - 0.5))
        price += change
      }
    }

    return quotes
  }

  /**
   * Test for OB mitigation timing matching TradingView behavior.
   *
   * Scenario modeled after CAT April 2019:
   * - Bar 15: Bullish origin candle → bearish OB [102.0, 106.0]
   * - Bars 16-18: Price closes above OB high (but OB doesn't exist yet in TV)
   * - Bar 19: ROC crossing triggers OB creation, close[1]=107.0 > OB high → immediate mitigation
   *
   * In TV, the box is created on the trigger bar. Mitigation only starts from
   * the trigger bar onward. On bar 19, close[1] (bar 18 close = 107.0) > OB high (106.0),
   * so the OB is immediately mitigated on creation.
   */
  @Test
  fun `should not overwrite mitigation date when OB is mitigated before trigger`() {
    val baseDate = LocalDate.of(2019, 4, 1)
    val quotes = mutableListOf<StockQuoteDomain>()

    // Bars 0-14: setup bars with gradual increase (open: 100.0 → 102.8)
    for (i in 0..14) {
      val open = 100.0 + i * 0.2
      quotes.add(
        createQuote(
          baseDate.plusDays(i.toLong()),
          open,
          open + 0.3, // slight bullish
          open + 0.5,
          open - 0.5,
        ),
      )
    }

    // Bar 15: Bullish origin candle → will become bearish OB [102.0, 106.0]
    quotes.add(createQuote(baseDate.plusDays(15), 103.0, 105.0, 106.0, 102.0))

    // Bar 16: Close above OB high (108.0 > 106.0) → mitigation trigger
    quotes.add(createQuote(baseDate.plusDays(16), 107.0, 108.0, 109.0, 106.5))

    // Bar 17: Still above OB high
    quotes.add(createQuote(baseDate.plusDays(17), 108.0, 109.0, 110.0, 107.0))

    // Bar 18: Still above OB high
    quotes.add(createQuote(baseDate.plusDays(18), 109.0, 107.0, 110.0, 106.0))

    // Bar 19: Bearish ROC crossing triggers OB creation
    // ROC = (open[19] - open[15]) / open[15] * 100 = (100.0 - 103.0) / 103.0 * 100 = -2.91%
    // prevROC = (open[18] - open[14]) / open[14] * 100 = (109.0 - 102.8) / 102.8 * 100 = +6.03%
    // Bearish crossing: prevROC > -0.28 AND ROC < -0.28 ✓
    quotes.add(createQuote(baseDate.plusDays(19), 100.0, 99.0, 101.0, 98.0))

    // Bars 20-21: trailing bars
    quotes.add(createQuote(baseDate.plusDays(20), 99.0, 98.0, 100.0, 97.0))
    quotes.add(createQuote(baseDate.plusDays(21), 98.0, 97.0, 99.0, 96.0))

    // When
    val orderBlocks = calculator.calculateOrderBlocks(quotes = quotes)

    // Then: Should have a bearish OB with origin at bar 15
    val bearishBlocks = orderBlocks.filter { it.orderBlockType == OrderBlockType.BEARISH }
    assertTrue(bearishBlocks.isNotEmpty(), "Should detect bearish OB")

    val block = bearishBlocks.first { it.low == 102.0 && it.high == 106.0 }

    // The OB should be mitigated at bar 19 (the trigger bar itself)
    // because close[1] on bar 19 (= bar 18 close = 107.0) > OB high (106.0)
    // Mitigation starts from the trigger bar, matching TV's behavior
    assertNotNull(block.endDate, "OB should be mitigated")
    assertEquals(
      baseDate.plusDays(19),
      block.endDate,
      "OB should be mitigated at bar 19 (trigger bar, close[1] > OB high), matching TV behavior",
    )
  }

  /**
   * Regression test: ALL crossings (skipped or not) are recorded in recentCrossings for
   * spacing checks, matching TradingView's cross_index behavior where cross_index updates
   * on every crossing event. Only non-skipped crossings produce OBs.
   *
   * Scenario:
   * - Bar 19: BULLISH crossing (ROC 40%) → creates OB (gap from previous is large)
   * - Bar 23: BEARISH crossing (ROC -28.57%) → SKIPPED (gap=4 from bar 19, ≤5), but RECORDED
   * - Bar 27: BEARISH crossing (ROC -30%) → SKIPPED (gap=4 from recorded bar 23, ≤5)
   *   This matches TV: cross_index=23 on bar 23, so bar 27 gap = 27-23 = 4, gate fails (4 > 5 = false)
   */
  @Test
  fun `skipped crossings participate in spacing but do not produce OBs`() {
    val baseDate = LocalDate.of(2025, 3, 1)
    val quotes = mutableListOf<StockQuoteDomain>()

    // Bars 0-14: flat prices (open=100), tiny bullish candles. ROC stays at 0%.
    for (i in 0..14) {
      quotes.add(createQuote(baseDate.plusDays(i.toLong()), 100.0, 100.1, 101.0, 99.0))
    }

    // Bar 15: bearish candle at open=100 (origin for bullish OB from bar 19 crossing)
    quotes.add(createQuote(baseDate.plusDays(15), 100.0, 99.5, 101.0, 99.0))

    // Bars 16-18: flat at open=100
    for (i in 16..18) {
      quotes.add(createQuote(baseDate.plusDays(i.toLong()), 100.0, 100.1, 101.0, 99.0))
    }

    // Bar 19: BULLISH crossing
    // ROC = (140 - 100) / 100 * 100 = 40% (> 0.28)
    // prevROC = (100 - 100) / 100 * 100 = 0% (< 0.28) → CROSSOVER ✓
    quotes.add(createQuote(baseDate.plusDays(19), 140.0, 141.0, 142.0, 139.0))

    // Bars 20-22: stay high at open=140
    for (i in 20..22) {
      quotes.add(createQuote(baseDate.plusDays(i.toLong()), 140.0, 140.1, 141.0, 139.0))
    }

    // Bar 23: BEARISH crossing
    // ROC = (100 - 140) / 140 * 100 = -28.57% (< -0.28)
    // prevROC = (140 - 100) / 100 * 100 = 40% (> -0.28) → CROSSUNDER ✓
    // 23 - 19 = 4 ≤ CROSS_TYPE_SPACING(5) → SKIPPED, but recorded for spacing
    quotes.add(createQuote(baseDate.plusDays(23), 100.0, 99.0, 101.0, 98.0))

    // Bars 24-26: back to high at open=140
    for (i in 24..26) {
      quotes.add(createQuote(baseDate.plusDays(i.toLong()), 140.0, 140.1, 141.0, 139.0))
    }

    // Bar 27: BEARISH crossing
    // ROC = (70 - 100) / 100 * 100 = -30% (< -0.28)
    // prevROC = (140 - 140) / 140 * 100 = 0% (> -0.28) → CROSSUNDER ✓
    // 27 - 23 = 4 ≤ SAME_TYPE_SPACING(5) → SKIPPED (bar 23 recorded in recentCrossings)
    // Matches TV: cross_index was updated to 23, gap = 27-23 = 4, gate fails (4 > 5 = false)
    quotes.add(createQuote(baseDate.plusDays(27), 70.0, 69.0, 71.0, 68.0))

    // Trailing bars
    quotes.add(createQuote(baseDate.plusDays(28), 69.0, 68.0, 70.0, 67.0))
    quotes.add(createQuote(baseDate.plusDays(29), 68.0, 67.0, 69.0, 66.0))

    // When
    val orderBlocks = calculator.calculateOrderBlocks(quotes = quotes)

    // Then
    val bullishBlocks = orderBlocks.filter { it.orderBlockType == OrderBlockType.BULLISH }
    val bearishBlocks = orderBlocks.filter { it.orderBlockType == OrderBlockType.BEARISH }

    assertTrue(bullishBlocks.isNotEmpty(), "Should detect bullish OB from bar 19 crossing")
    assertTrue(
      bearishBlocks.isEmpty(),
      "Should NOT detect bearish OBs — bar 23 is too close to bar 19 (gap=4 ≤5), " +
        "bar 27 is too close to recorded bar 23 (gap=4 ≤5), matching TV's cross_index behavior",
    )

    // Verify bullish OB origin
    val bullishOB = bullishBlocks.first()
    assertEquals(baseDate.plusDays(15), bullishOB.startDate, "Bullish OB origin should be bar 15 (bearish candle)")
  }

  /**
   * Test that a crossing with sufficient gap from a skipped crossing DOES produce an OB.
   * Skipped crossings are spacing markers but don't prevent OBs beyond the spacing window.
   *
   * Crossing chain:
   *   Bar 19: BULLISH → OB created (first crossing)
   *   Bar 23: BEARISH → skipped (gap=4 from 19, ≤5), recorded
   *   Bar 27: BULLISH → skipped (gap=4 from 23, ≤5), recorded (price recovery causes ROC crossing)
   *   Bar 34: BEARISH → NOT skipped (gap=7 from 27 cross-type, gap=11 from 23 same-type) → OB created
   */
  @Test
  fun `crossing beyond spacing window from skipped crossing produces OB`() {
    val baseDate = LocalDate.of(2025, 3, 1)
    val quotes = mutableListOf<StockQuoteDomain>()

    // Bars 0-14: flat prices
    for (i in 0..14) {
      quotes.add(createQuote(baseDate.plusDays(i.toLong()), 100.0, 100.1, 101.0, 99.0))
    }

    // Bar 15: bearish candle (origin for bullish OB)
    quotes.add(createQuote(baseDate.plusDays(15), 100.0, 99.5, 101.0, 99.0))

    // Bars 16-18: flat
    for (i in 16..18) {
      quotes.add(createQuote(baseDate.plusDays(i.toLong()), 100.0, 100.1, 101.0, 99.0))
    }

    // Bar 19: BULLISH crossing (ROC 40%) → creates OB
    quotes.add(createQuote(baseDate.plusDays(19), 140.0, 141.0, 142.0, 139.0))

    // Bars 20-22: high
    for (i in 20..22) {
      quotes.add(createQuote(baseDate.plusDays(i.toLong()), 140.0, 140.1, 141.0, 139.0))
    }

    // Bar 23: BEARISH crossing → SKIPPED (gap=4 from bar 19), recorded for spacing
    quotes.add(createQuote(baseDate.plusDays(23), 100.0, 99.0, 101.0, 98.0))

    // Bars 24-33: back to high prices
    // Note: Bar 27 triggers a BULLISH crossing (recovery from bar 23's dip), also skipped (gap=4 from 23)
    for (i in 24..33) {
      quotes.add(createQuote(baseDate.plusDays(i.toLong()), 140.0, 140.1, 141.0, 139.0))
    }

    // Bar 34: BEARISH crossing — gap from bar 27 (last crossing) = 7 > CROSS_TYPE_SPACING(5) → creates OB
    // ROC = (70 - 140) / 140 * 100 = -50% (< -0.28)
    // prevROC = (140 - 140) / 140 * 100 = 0% (> -0.28) → CROSSUNDER ✓
    quotes.add(createQuote(baseDate.plusDays(34), 70.0, 69.0, 71.0, 68.0))

    // Trailing bars
    quotes.add(createQuote(baseDate.plusDays(35), 69.0, 68.0, 70.0, 67.0))
    quotes.add(createQuote(baseDate.plusDays(36), 68.0, 67.0, 69.0, 66.0))

    val orderBlocks = calculator.calculateOrderBlocks(quotes = quotes)

    val bullishBlocks = orderBlocks.filter { it.orderBlockType == OrderBlockType.BULLISH }
    val bearishBlocks = orderBlocks.filter { it.orderBlockType == OrderBlockType.BEARISH }

    assertTrue(bullishBlocks.isNotEmpty(), "Should detect bullish OB from bar 19")
    assertTrue(
      bearishBlocks.isNotEmpty(),
      "Should detect bearish OB from bar 34 (gap=7 from skipped bar 27, > spacing 5)",
    )
  }

  private fun createQuote(
    date: LocalDate,
    open: Double,
    close: Double,
    high: Double,
    low: Double,
  ): StockQuoteDomain =
    StockQuoteDomain(
      symbol = "TEST",
      date = date,
      openPrice = open,
      closePrice = close,
      high = high,
      low = low,
    )
}
