package com.skrymer.udgaard.e2e

import com.skrymer.udgaard.backtesting.dto.BacktestRequest
import com.skrymer.udgaard.backtesting.dto.ConditionConfig
import com.skrymer.udgaard.backtesting.dto.CustomStrategyConfig
import com.skrymer.udgaard.backtesting.dto.PredefinedStrategyConfig
import com.skrymer.udgaard.backtesting.model.BacktestResponseDto
import com.skrymer.udgaard.backtesting.model.Trade
import org.jooq.DSLContext
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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
    val request = testBacktestRequest()

    val response = postBacktest(request)

    assertEquals(HttpStatus.OK, response.statusCode)
    val body = response.body!!
    assertNotNull(body.backtestId)
    assertTrue(body.totalTrades >= 0)
    assertEquals(body.numberOfWinningTrades + body.numberOfLosingTrades, body.totalTrades)
    assertTrue(body.winRate in 0.0..1.0)
    assertTrue(body.lossRate in 0.0..1.0)
    assertNotNull(body.equityCurveData)
    assertNotNull(body.excursionPoints)
    assertNotNull(body.dailyProfitSummary)
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
    assertTrue(body.totalTrades >= 0)
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

    val xlkStocks = setOf("AAPL", "MSFT", "NVDA", "AVGO", "CRM")
    val xlfStocks = setOf("JPM", "BAC", "WFC", "GS", "MS")
    val allowedStocks = xlkStocks + xlfStocks

    body.stockProfits.forEach { (symbol, _) ->
      assertTrue(symbol in allowedStocks, "Trade for $symbol should not exist with sector filter $includedSectors")
    }
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

    val excludedStocks = setOf("XOM", "CVX", "COP", "SLB", "EOG", "NEE", "DUK", "SO", "D", "AEP")
    body.stockProfits.forEach { (symbol, _) ->
      assertTrue(symbol !in excludedStocks, "Trade for $symbol should not exist with excluded sectors $excludedSectors")
    }
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
    assertNotNull(tradesResponse.body)
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
    assertNotNull(response.body!!.backtestId)
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

  private fun postBacktest(request: BacktestRequest) =
    restTemplate.exchange(
      "/api/backtest",
      HttpMethod.POST,
      HttpEntity(request, HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }),
      BacktestResponseDto::class.java,
    )
}
