package com.skrymer.udgaard.e2e

import com.skrymer.udgaard.backtesting.dto.BacktestRequest
import com.skrymer.udgaard.backtesting.dto.ConditionConfig
import com.skrymer.udgaard.backtesting.dto.CustomStrategyConfig
import com.skrymer.udgaard.backtesting.dto.PredefinedStrategyConfig
import com.skrymer.udgaard.backtesting.model.BacktestResponseDto
import com.skrymer.udgaard.backtesting.model.Trade
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val DELTA = 1e-6

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BacktestApiE2ETest : AbstractIntegrationTest() {
  @Autowired
  private lateinit var dsl: DSLContext

  @BeforeAll
  fun setupTestData() {
    BacktestTestDataGenerator.populate(dsl)
  }

  @Test
  fun `POST backtest with TestEntry and TestExit should return valid response`() {
    val response = postBacktest(testBacktestRequest())
    assertEquals(HttpStatus.OK, response.statusCode)
    val body = response.body!!

    assertNotNull(body.backtestId)
    assertTradeMetrics(body)
    assertNoMissedOpportunities(body)
    assertStockProfits(body)
    assertTimeBasedStats(body)
    assertExitReasons(body)
    assertPerformanceBreakdowns(body)
    assertDrawdownAnalysis(body)
    assertMarketConditions(body)
    assertChartData(body)
    assertEquals(0, body.underlyingAssetTradeCount, "useUnderlyingAssets=false")
    assertNull(body.edgeConsistencyScore, "requires >= 2 years of data")
    assertNull(body.positionSizing, "not requested")
  }

  @Test
  fun `POST backtest with custom conditions covering all data types should work`() {
    val request = BacktestRequest(
      stockSymbols = BacktestTestDataGenerator.ALL_SYMBOLS,
      entryStrategy = CustomStrategyConfig(
        conditions = listOf(
          ConditionConfig(type = "uptrend"),
          ConditionConfig(type = "marketbreadthabove", parameters = mapOf("threshold" to 40.0)),
          ConditionConfig(type = "sectorbreadthabove", parameters = mapOf("threshold" to 20.0)),
          ConditionConfig(type = "noearningswithindays", parameters = mapOf("days" to 7)),
          ConditionConfig(type = "notinorderblock", parameters = mapOf("ageInDays" to 120)),
        ),
      ),
      exitStrategy = CustomStrategyConfig(
        conditions = listOf(
          ConditionConfig(type = "stoploss", parameters = mapOf("atrMultiplier" to 2.0)),
          ConditionConfig(type = "beforeearnings", parameters = mapOf("daysBeforeEarnings" to 1)),
          ConditionConfig(type = "bearishorderblock", parameters = mapOf("ageInDays" to 120)),
          ConditionConfig(type = "marketbreadthdeteriorating"),
          ConditionConfig(type = "sectorbreadthbelow", parameters = mapOf("threshold" to 15.0)),
        ),
      ),
      startDate = "2024-01-02",
      endDate = "2024-03-29",
      useUnderlyingAssets = false,
    )

    val response = postBacktest(request)

    assertEquals(HttpStatus.OK, response.statusCode)
    val body = response.body!!
    assertNotNull(body.backtestId)
    assertTrue(body.totalTrades >= 0)
  }

  @Test
  fun `POST backtest with maxPositions should cap concurrent trades`() {
    val maxPositions = 3
    val request = BacktestRequest(
      stockSymbols = BacktestTestDataGenerator.ALL_SYMBOLS,
      entryStrategy = PredefinedStrategyConfig(name = "TestEntry"),
      exitStrategy = PredefinedStrategyConfig(name = "TestExit"),
      startDate = "2024-01-02",
      endDate = "2024-03-29",
      maxPositions = maxPositions,
      ranker = "Adaptive",
      useUnderlyingAssets = false,
    )

    val response = postBacktest(request)

    assertEquals(HttpStatus.OK, response.statusCode)
    val body = response.body!!
    assertNotNull(body.backtestId)
    assertEquals(22, body.totalTrades)
    assertEquals(206, body.missedOpportunitiesCount)

    // Fetch trades and verify concurrent positions never exceed maxPositions
    val tradesResponse = restTemplate.exchange(
      "/api/backtest/${body.backtestId}/trades?startDate=2024-01-02&endDate=2024-03-29",
      HttpMethod.GET,
      null,
      object : ParameterizedTypeReference<List<Trade>>() {},
    )
    val trades = tradesResponse.body!!
    assertEquals(22, trades.size)

    // Build date ranges for each trade and check concurrent positions per day
    val tradeRanges = trades.map { trade ->
      val entryDate = trade.entryQuote.date
      val exitDate = trade.quotes.maxOf { it.date }
      entryDate to exitDate
    }
    val allDates = tradeRanges
      .flatMap { (entry, exit) ->
        generateSequence(entry) { it.plusDays(1) }.takeWhile { !it.isAfter(exit) }.toList()
      }.distinct()

    allDates.forEach { date ->
      val concurrentCount = tradeRanges.count { (entry, exit) ->
        !date.isBefore(entry) && !date.isAfter(exit)
      }
      assertTrue(
        concurrentCount <= maxPositions,
        "Concurrent positions on $date ($concurrentCount) should not exceed maxPositions ($maxPositions)",
      )
    }
  }

  @Test
  fun `POST backtest with sector filter should only include matching stocks`() {
    val includedSectors = listOf("XLK", "XLF")
    val request = BacktestRequest(
      stockSymbols = BacktestTestDataGenerator.ALL_SYMBOLS,
      includeSectors = includedSectors,
      entryStrategy = PredefinedStrategyConfig(name = "TestEntry"),
      exitStrategy = PredefinedStrategyConfig(name = "TestExit"),
      startDate = "2024-01-02",
      endDate = "2024-03-29",
      useUnderlyingAssets = false,
    )

    val response = postBacktest(request)

    assertEquals(HttpStatus.OK, response.statusCode)
    val body = response.body!!
    assertEquals(56, body.totalTrades)

    val xlkStocks = setOf("AAPL", "MSFT", "NVDA", "AVGO", "CRM")
    val xlfStocks = setOf("JPM", "BAC", "WFC", "GS", "MS")
    val allowedStocks = xlkStocks + xlfStocks

    body.stockProfits.forEach { (symbol, _) ->
      assertTrue(symbol in allowedStocks, "Trade for $symbol should not exist with sector filter $includedSectors")
    }

    // Sector stats should only contain the two included sectors
    assertEquals(2, body.sectorStats.size)
    val sectorStatsNames = body.sectorStats.map { it.sector }.toSet()
    assertEquals(setOf("XLK", "XLF"), sectorStatsNames)
  }

  @Test
  fun `POST backtest with excludeSectors should filter out matching stocks`() {
    val excludedSectors = listOf("XLE", "XLU")
    val request = BacktestRequest(
      stockSymbols = BacktestTestDataGenerator.ALL_SYMBOLS,
      excludeSectors = excludedSectors,
      entryStrategy = PredefinedStrategyConfig(name = "TestEntry"),
      exitStrategy = PredefinedStrategyConfig(name = "TestExit"),
      startDate = "2024-01-02",
      endDate = "2024-03-29",
      useUnderlyingAssets = false,
    )

    val response = postBacktest(request)

    assertEquals(HttpStatus.OK, response.statusCode)
    val body = response.body!!
    assertEquals(182, body.totalTrades)

    val excludedStocks = setOf("XOM", "CVX", "COP", "SLB", "EOG", "NEE", "DUK", "SO", "D", "AEP")
    body.stockProfits.forEach { (symbol, _) ->
      assertTrue(symbol !in excludedStocks, "Trade for $symbol should not exist with excluded sectors $excludedSectors")
    }

    // Sector stats should not contain excluded sectors
    assertEquals(9, body.sectorStats.size)
    val sectorStatsNames = body.sectorStats.map { it.sector }.toSet()
    assertEquals(
      setOf("XLY", "XLF", "XLV", "XLRE", "XLI", "XLK", "XLC", "XLB", "XLP"),
      sectorStatsNames,
    )
  }

  @Test
  fun `GET backtest trades should return trades for cached result`() {
    val request = testBacktestRequest()

    val backtestResponse = postBacktest(request)
    assertEquals(HttpStatus.OK, backtestResponse.statusCode)
    val backtestId = backtestResponse.body!!.backtestId

    val tradesResponse = restTemplate.exchange(
      "/api/backtest/$backtestId/trades?startDate=2024-01-02&endDate=2024-03-29",
      HttpMethod.GET,
      null,
      object : ParameterizedTypeReference<List<Trade>>() {},
    )

    assertEquals(HttpStatus.OK, tradesResponse.statusCode)
    val trades = tradesResponse.body!!
    assertEquals(227, trades.size)
    trades.forEach { trade ->
      assertTrue(trade.stockSymbol.isNotBlank(), "Trade stockSymbol should not be blank")
      assertTrue(trade.exitReason.isNotBlank(), "Trade exitReason should not be blank")
      assertTrue(trade.profitPercentage.isFinite(), "Trade profitPercentage should be finite")
    }
  }

  @Test
  fun `GET backtest trades with invalid backtestId should return 404`() {
    val response = restTemplate.exchange(
      "/api/backtest/nonexistent-id/trades?startDate=2024-01-02&endDate=2024-03-29",
      HttpMethod.GET,
      null,
      object : ParameterizedTypeReference<List<Trade>>() {},
    )

    assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
  }

  @Test
  fun `GET strategies should return available entry and exit strategies`() {
    val response = restTemplate.exchange(
      "/api/backtest/strategies",
      HttpMethod.GET,
      null,
      object : ParameterizedTypeReference<Map<String, List<String>>>() {},
    )

    assertEquals(HttpStatus.OK, response.statusCode)
    val body = response.body!!
    assertTrue(body.containsKey("entryStrategies"))
    assertTrue(body.containsKey("exitStrategies"))
    assertTrue(body["entryStrategies"]!!.contains("TestEntry"), "Should contain TestEntry strategy")
    assertTrue(body["exitStrategies"]!!.contains("TestExit"), "Should contain TestExit strategy")
  }

  @Test
  fun `GET rankers should return available rankers`() {
    val response = restTemplate.exchange(
      "/api/backtest/rankers",
      HttpMethod.GET,
      null,
      object : ParameterizedTypeReference<List<String>>() {},
    )

    assertEquals(HttpStatus.OK, response.statusCode)
    val body = response.body!!
    assertTrue(body.isNotEmpty(), "Should have at least one ranker")
    assertTrue(body.contains("Adaptive"), "Should contain Adaptive ranker")
    assertTrue(body.contains("Volatility"), "Should contain Volatility ranker")
  }

  @Test
  fun `GET conditions should return available entry and exit conditions`() {
    val response = restTemplate.getForEntity(
      "/api/backtest/conditions",
      Map::class.java,
    )

    assertEquals(HttpStatus.OK, response.statusCode)
    val body = response.body!!
    assertTrue(body.containsKey("entryConditions"))
    assertTrue(body.containsKey("exitConditions"))
  }

  @Test
  fun `POST backtest with cooldown days should limit re-entry`() {
    val request = BacktestRequest(
      stockSymbols = BacktestTestDataGenerator.ALL_SYMBOLS,
      entryStrategy = PredefinedStrategyConfig(name = "TestEntry"),
      exitStrategy = PredefinedStrategyConfig(name = "TestExit"),
      startDate = "2024-01-02",
      endDate = "2024-03-29",
      cooldownDays = 5,
      useUnderlyingAssets = false,
    )

    val response = postBacktest(request)

    assertEquals(HttpStatus.OK, response.statusCode)
    val body = response.body!!
    assertNotNull(body.backtestId)
    assertEquals(58, body.totalTrades)
  }

  @Test
  fun `POST backtest with invalid strategy name should return 400 with error message`() {
    val request = BacktestRequest(
      entryStrategy = PredefinedStrategyConfig(name = "NonExistentStrategy"),
      exitStrategy = PredefinedStrategyConfig(name = "TestExit"),
      startDate = "2024-01-02",
      endDate = "2024-03-29",
    )

    val response = restTemplate.exchange(
      "/api/backtest",
      HttpMethod.POST,
      HttpEntity(request, HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }),
      String::class.java,
    )

    assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    assertTrue(response.body?.contains("error") == true)
  }

  private fun testBacktestRequest() = BacktestRequest(
    stockSymbols = BacktestTestDataGenerator.ALL_SYMBOLS,
    entryStrategy = PredefinedStrategyConfig(name = "TestEntry"),
    exitStrategy = PredefinedStrategyConfig(name = "TestExit"),
    startDate = "2024-01-02",
    endDate = "2024-03-29",
    useUnderlyingAssets = false,
  )

  private fun postBacktest(request: BacktestRequest): ResponseEntity<BacktestResponseDto> =
    restTemplate.exchange(
      "/api/backtest",
      HttpMethod.POST,
      HttpEntity(request, HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }),
      BacktestResponseDto::class.java,
    )

  // -- Assertion helpers for default backtest (deterministic Random(42) seed) -----------------

  private fun assertTradeMetrics(body: BacktestResponseDto) {
    assertEquals(227, body.totalTrades)
    assertEquals(113, body.numberOfWinningTrades)
    assertEquals(114, body.numberOfLosingTrades)
    assertEquals(0.4978, body.winRate, 1e-4)
    assertEquals(0.5022, body.lossRate, 1e-4)
    assertEquals(1.0, body.winRate + body.lossRate, DELTA)
    assertEquals(5.2051, body.averageWinAmount, 1e-4)
    assertEquals(2.5496, body.averageWinPercent, 1e-4)
    assertEquals(3.4688, body.averageLossAmount, 1e-4)
    assertEquals(1.5231, body.averageLossPercent, 1e-4)
    assertEquals(0.5042, body.edge, 1e-4)
    assertEquals(1.4874, body.profitFactor!!, 1e-4)
  }

  private fun assertNoMissedOpportunities(body: BacktestResponseDto) {
    assertEquals(0, body.missedOpportunitiesCount)
    assertEquals(0.0, body.missedProfitPercentage, DELTA)
    assertEquals(0.0, body.missedAverageProfitPercentage, DELTA)
  }

  private fun assertStockProfits(body: BacktestResponseDto) {
    assertEquals(52, body.stockProfits.size)
    val validSymbols = BacktestTestDataGenerator.ALL_SYMBOLS.toSet()
    body.stockProfits.forEach { (symbol, _) ->
      assertTrue(symbol in validSymbols, "Unexpected symbol $symbol in stockProfits")
    }
  }

  private fun assertTimeBasedStats(body: BacktestResponseDto) {
    val stats = body.timeBasedStats!!
    // Yearly
    assertEquals(setOf(2024), stats.byYear.keys)
    assertEquals(227, stats.byYear[2024]!!.trades)
    assertEquals(49.78, stats.byYear[2024]!!.winRate, 0.01)
    assertEquals(0.504, stats.byYear[2024]!!.avgProfit, 0.001)
    // Monthly
    assertEquals(3, stats.byMonth.size)
    assertEquals(40, stats.byMonth["2024-01"]!!.trades)
    assertEquals(11, stats.byMonth["2024-02"]!!.trades)
    assertEquals(176, stats.byMonth["2024-03"]!!.trades)
    // Quarterly
    assertEquals(setOf("2024-Q1"), stats.byQuarter.keys)
    assertEquals(227, stats.byQuarter["2024-Q1"]!!.trades)
  }

  private fun assertExitReasons(body: BacktestResponseDto) {
    val byReason = body.exitReasonAnalysis!!.byReason
    assertEquals(3, byReason.size)
    // Earnings exit — high win rate, profitable
    val earnings = byReason["Exit before earnings"]!!
    assertEquals(13, earnings.count)
    assertEquals(84.62, earnings.winRate, 0.01)
    assertEquals(5.895, earnings.avgProfit, 0.001)
    // Breadth deteriorating — bulk of trades
    val breadth = byReason["Market breadth deteriorating (EMAs inverted)"]!!
    assertEquals(197, breadth.count)
    assertEquals(51.78, breadth.winRate, 0.01)
    // Stop loss — always a loser by definition
    val stopLoss = byReason["Stop loss triggered (2.0 ATR below entry)"]!!
    assertEquals(17, stopLoss.count)
    assertEquals(0.0, stopLoss.winRate, DELTA)
  }

  private fun assertPerformanceBreakdowns(body: BacktestResponseDto) {
    // All 11 sectors and 52 stocks represented
    assertEquals(11, body.sectorPerformance.size)
    assertEquals(52, body.stockPerformance.size)
    // Sector stats: wins + losses = total for every sector
    assertEquals(11, body.sectorStats.size)
    body.sectorStats.forEach { ss ->
      assertEquals(ss.winningTrades + ss.losingTrades, ss.totalTrades, "Invariant for ${ss.sector}")
    }
    // Spot-check two sectors
    val xlu = body.sectorStats.first { it.sector == "XLU" }
    assertEquals(27, xlu.totalTrades)
    assertEquals(17, xlu.winningTrades)
    val xlf = body.sectorStats.first { it.sector == "XLF" }
    assertEquals(34, xlf.totalTrades)
    assertEquals(19, xlf.winningTrades)
  }

  private fun assertDrawdownAnalysis(body: BacktestResponseDto) {
    val stats = body.atrDrawdownStats!!
    assertEquals(113, stats.totalWinningTrades)
    assertEquals(0.0, stats.medianDrawdown, DELTA)
    assertEquals(0.127, stats.meanDrawdown, 0.001)
  }

  private fun assertMarketConditions(body: BacktestResponseDto) {
    val stats = body.marketConditionStats!!
    assertEquals(148, stats.uptrendCount)
    assertEquals(79, stats.downtrendCount)
    assertEquals(47.97, stats.uptrendWinRate, 0.01)
    assertEquals(53.16, stats.downtrendWinRate, 0.01)
    assertEquals(227, stats.scatterPoints.size)
  }

  private fun assertChartData(body: BacktestResponseDto) {
    assertEquals(227, body.equityCurveData.size)
    assertEquals(227, body.excursionPoints.size)
    assertEquals(227, body.excursionSummary!!.totalTrades)
    assertEquals(59.03, body.excursionSummary!!.profitReachRate, 0.01)
    assertEquals(31, body.dailyProfitSummary.size)
    body.dailyProfitSummary.forEach { dps ->
      assertTrue(dps.tradeCount > 0, "No trades on ${dps.date}")
    }
  }
}
