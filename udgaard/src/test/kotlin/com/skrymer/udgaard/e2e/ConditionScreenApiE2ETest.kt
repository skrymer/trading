package com.skrymer.udgaard.e2e

import com.skrymer.udgaard.backtesting.dto.ConditionConfig
import com.skrymer.udgaard.backtesting.dto.ConditionScreenReport
import com.skrymer.udgaard.backtesting.dto.ConditionScreenRequest
import com.skrymer.udgaard.backtesting.dto.ScriptSweepSpec
import org.jooq.DSLContext
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import java.time.LocalDate

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConditionScreenApiE2ETest : AbstractIntegrationTest() {
  @Autowired
  private lateinit var dsl: DSLContext

  // Seed entirely inside the design-safe window (before Block C's 2021-01-01 start) so the screen
  // can run on the fixture without tripping the leakage guard.
  private val seedStart = LocalDate.of(2018, 1, 1)
  private val seedEnd = LocalDate.of(2020, 12, 31)

  @BeforeAll
  fun setupTestData() {
    BacktestTestDataGenerator.populate(dsl, seedStart, seedEnd)
  }

  @AfterAll
  fun cleanup() {
    BacktestTestDataGenerator.reset(dsl)
  }

  private fun post(request: ConditionScreenRequest) =
    restTemplate.exchange(
      "/api/conditions/screen",
      HttpMethod.POST,
      HttpEntity(request, HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }),
      ConditionScreenReport::class.java,
    )

  @Test
  fun `POST screen returns diagnostic report with forward returns, firing, sweep and regime stats`() {
    // Given a registered condition with a discrete tunable (emaPeriod) over the seeded universe
    val request =
      ConditionScreenRequest(
        conditions = listOf(ConditionConfig(type = "priceAboveEma", parameters = mapOf("emaPeriod" to 10))),
        operator = "AND",
        startDate = seedStart,
        endDate = seedEnd,
        horizons = listOf(5, 10, 20),
      )

    // When
    val response = post(request)

    // Then a 200 diagnostic report comes back, leading with the diagnostic-not-predictive notice
    assertEquals(HttpStatus.OK, response.statusCode)
    val body = requireNotNull(response.body)
    assertTrue(body.diagnosticNotice.contains("diagnostic", ignoreCase = true))
    assertTrue(body.diagnosticNotice.contains("not validated", ignoreCase = true))

    // And the window is the design-safe seeded range
    assertEquals(seedStart, body.window.startDate)
    assertEquals(seedEnd, body.window.endDate)

    // And the universe + baseline are populated
    assertTrue(body.universe.symbolCount > 0, "should resolve STOCK symbols")
    assertTrue(body.universe.totalEligibleBars > 0, "baseline should have eligible bars")

    // And there is one forward-return horizon report per requested N, each with a universe baseline
    assertEquals(listOf(5, 10, 20), body.forwardReturns.map { it.horizonDays })
    assertTrue(body.forwardReturns.all { it.universe.signalCount > 0 }, "universe baseline measured at every horizon")

    // And firing is reported per calendar year across the seeded years (2018-2020)
    assertTrue(body.firing.byYear.isNotEmpty())
    assertTrue(body.firing.byYear.all { it.firingRate in 0.0..1.0 })

    // And the discrete emaPeriod tunable was auto-swept to its adjacent allowed values (5 and 20)
    val emaSweep = body.parameterSweep.firstOrNull { it.parameterName == "emaPeriod" }
    assertNotNull(emaSweep, "registered numeric tunable should be auto-swept")
    val sweptValues = emaSweep!!.cells.map { it.parameterValue }.sorted()
    assertEquals(listOf(5.0, 10.0, 20.0), sweptValues, "center 10 plus adjacent allowed neighbours 5 and 20")
    assertTrue(emaSweep.cells.any { it.isCenter && it.parameterValue == 10.0 })

    // And the SPY regime breakdown is reported per horizon
    assertEquals(listOf(5, 10, 20), body.spyRegime.map { it.horizonDays })

    // And with no reference conditions, Jaccard is N/A (empty) and noted
    assertTrue(body.jaccard.isEmpty())
    assertTrue(body.notes.any { it.contains("Jaccard", ignoreCase = true) })
  }

  @Test
  fun `POST screen rejects an end date past Block C to prevent leakage`() {
    // Given a request whose window extends into Block C (past 2021-01-01)
    val request =
      ConditionScreenRequest(
        conditions = listOf(ConditionConfig(type = "priceAboveEma", parameters = mapOf("emaPeriod" to 10))),
        startDate = LocalDate.of(2020, 1, 1),
        endDate = LocalDate.of(2024, 1, 1),
      )

    // When
    val response = post(request)

    // Then the leakage guard rejects it with 400
    assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
  }

  @Test
  fun `POST screen accepts an inline script sweep and produces three sensitivity cells`() {
    // Given an inline script with a templated tunable (a multiplier that compiles at every cell)
    val request =
      ConditionScreenRequest(
        conditions =
          listOf(
            ConditionConfig(
              type = "script",
              parameters = mapOf("script" to "quote.closePrice > quote.closePriceEMA10 * {{factor}}"),
            ),
          ),
        startDate = seedStart,
        endDate = seedEnd,
        scriptSweeps = listOf(ScriptSweepSpec(name = "factor", center = 1.0, step = 0.1)),
      )

    // When
    val response = post(request)

    // Then the script sweep produced P-step / P / P+step cells (0.9, 1.0, 1.1)
    assertEquals(HttpStatus.OK, response.statusCode)
    val body = requireNotNull(response.body)
    val scriptSweep = body.parameterSweep.firstOrNull { it.source == "script:factor" }
    assertNotNull(scriptSweep, "declared script sweep should produce a swept parameter")
    assertEquals(3, scriptSweep!!.cells.size)
    assertTrue(scriptSweep.cells.any { it.isCenter && it.parameterValue == 1.0 })
  }
}
