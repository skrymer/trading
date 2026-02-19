package com.skrymer.udgaard.backtesting.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EdgeConsistencyScoreTest {
  private fun periodStats(
    trades: Int = 10,
    edge: Double = 0.0,
  ) = PeriodStats(
    trades = trades,
    winRate = 50.0,
    avgProfit = 0.0,
    avgHoldingDays = 5.0,
    exitReasons = emptyMap(),
    edge = edge,
  )

  @Test
  fun `should return null for empty map`() {
    assertNull(calculateEdgeConsistency(emptyMap()))
  }

  @Test
  fun `should return null for single year`() {
    val stats = mapOf(2024 to periodStats(edge = 3.0))
    assertNull(calculateEdgeConsistency(stats))
  }

  @Test
  fun `should return null when fewer than 2 years have trades`() {
    val stats = mapOf(
      2023 to periodStats(trades = 0, edge = 0.0),
      2024 to periodStats(trades = 5, edge = 2.0),
      2025 to periodStats(trades = 0, edge = 0.0),
    )
    assertNull(calculateEdgeConsistency(stats))
  }

  @Test
  fun `should calculate perfect score for identical positive edges above threshold`() {
    val stats = mapOf(
      2020 to periodStats(edge = 3.0),
      2021 to periodStats(edge = 3.0),
      2022 to periodStats(edge = 3.0),
      2023 to periodStats(edge = 3.0),
      2024 to periodStats(edge = 3.0),
    )
    val result = calculateEdgeConsistency(stats)
    assertNotNull(result)
    // profitablePeriods: 5/5 = 100, tradeable (>=1.5%): 5/5 = 100, downside: 100
    // score = 100*0.4 + 100*0.4 + 100*0.2 = 100
    assertEquals(100.0, result!!.score, 0.01)
    assertEquals("Excellent", result.interpretation)
    assertEquals(5, result.yearsAnalyzed)
  }

  @Test
  fun `should calculate low score for one great year and four negative years`() {
    val stats = mapOf(
      2020 to periodStats(edge = 20.0),
      2021 to periodStats(edge = -2.0),
      2022 to periodStats(edge = -1.0),
      2023 to periodStats(edge = -3.0),
      2024 to periodStats(edge = -1.5),
    )
    val result = calculateEdgeConsistency(stats)
    assertNotNull(result)
    // profitablePeriods: 1/5 = 20
    // tradeable (>=1.5%): 1/5 = 20 (only 20.0)
    // worst = -3.0, downside = 100 * (1 + (-3.0/10)) = 70
    // score = 20*0.4 + 20*0.4 + 70*0.2 = 8 + 8 + 14 = 30
    assertEquals(30.0, result!!.score, 1.0)
    assertEquals("Poor", result.interpretation)
  }

  @Test
  fun `should handle all negative edges as Very Poor`() {
    val stats = mapOf(
      2020 to periodStats(edge = -5.0),
      2021 to periodStats(edge = -3.0),
      2022 to periodStats(edge = -4.0),
    )
    val result = calculateEdgeConsistency(stats)
    assertNotNull(result)
    // profitablePeriods: 0/3 = 0
    // tradeable: 0/3 = 0
    // worst = -5.0, downside = 100 * (1 + (-5.0/10)) = 50
    // score = 0*0.4 + 0*0.4 + 50*0.2 = 10
    assertEquals(10.0, result!!.score, 0.01)
    assertEquals("Very Poor", result.interpretation)
  }

  @Test
  fun `should handle two-year minimum case`() {
    val stats = mapOf(
      2023 to periodStats(edge = 2.0),
      2024 to periodStats(edge = 4.0),
    )
    val result = calculateEdgeConsistency(stats)
    assertNotNull(result)
    assertEquals(2, result!!.yearsAnalyzed)
    // profitablePeriods: 2/2 = 100
    // tradeable: 2/2 = 100 (both >= 1.5%)
    // worst=2.0>=0, downside=100
    // score = 100*0.4 + 100*0.4 + 100*0.2 = 100
    assertEquals(100.0, result.score, 0.01)
    assertEquals("Excellent", result.interpretation)
  }

  @Test
  fun `stability should be 0 when no years have tradeable edge`() {
    val stats = mapOf(
      2023 to periodStats(edge = 0.5),
      2024 to periodStats(edge = 1.0),
    )
    val result = calculateEdgeConsistency(stats)
    assertNotNull(result)
    // Both edges are positive but below 1.5% tradeable threshold
    assertEquals(0.0, result!!.stabilityScore, 0.01)
    // profitablePeriods: 2/2 = 100, tradeable: 0/2 = 0, downside: 100
    // score = 100*0.4 + 0*0.4 + 100*0.2 = 60
    assertEquals(60.0, result.score, 0.01)
    assertEquals("Good", result.interpretation)
  }

  @Test
  fun `stability should reflect tradeable edge percentage`() {
    val stats = mapOf(
      2022 to periodStats(edge = 0.5),
      2023 to periodStats(edge = 2.0),
      2024 to periodStats(edge = 3.0),
    )
    val result = calculateEdgeConsistency(stats)
    assertNotNull(result)
    // tradeable: 2/3 (2.0 and 3.0 >= 1.5%)
    assertEquals(66.67, result!!.stabilityScore, 0.5)
    // profitablePeriods: 3/3 = 100, downside: 100
    // score = 100*0.4 + 66.67*0.4 + 100*0.2 = 40 + 26.67 + 20 = 86.67
    assertEquals(86.67, result.score, 0.5)
    assertEquals("Excellent", result.interpretation)
  }

  @Test
  fun `should populate yearlyEdges map correctly`() {
    val stats = mapOf(
      2022 to periodStats(edge = 1.5),
      2023 to periodStats(edge = 2.5),
      2024 to periodStats(edge = 3.5),
    )
    val result = calculateEdgeConsistency(stats)
    assertNotNull(result)
    assertEquals(3, result!!.yearlyEdges.size)
    assertEquals(1.5, result.yearlyEdges[2022]!!, 0.001)
    assertEquals(2.5, result.yearlyEdges[2023]!!, 0.001)
    assertEquals(3.5, result.yearlyEdges[2024]!!, 0.001)
  }

  @Test
  fun `should return Excellent interpretation for score 80+`() {
    val stats = mapOf(
      2022 to periodStats(edge = 3.0),
      2023 to periodStats(edge = 3.0),
      2024 to periodStats(edge = 3.0),
    )
    val result = calculateEdgeConsistency(stats)
    assertEquals("Excellent", result!!.interpretation)
  }

  @Test
  fun `should return Very Poor interpretation for very low score`() {
    val stats = mapOf(
      2020 to periodStats(edge = -1.0),
      2021 to periodStats(edge = -1.0),
      2022 to periodStats(edge = -1.0),
      2023 to periodStats(edge = -1.0),
      2024 to periodStats(edge = -15.0),
    )
    val result = calculateEdgeConsistency(stats)
    assertNotNull(result)
    // profitablePeriods: 0, tradeable: 0, downside: 0 (worst=-15)
    // score = 0
    assertEquals("Very Poor", result!!.interpretation)
    assertTrue(result.score < 20)
  }

  @Test
  fun `should exclude years with zero trades`() {
    val stats = mapOf(
      2022 to periodStats(trades = 0, edge = 0.0),
      2023 to periodStats(trades = 10, edge = 3.0),
      2024 to periodStats(trades = 8, edge = 3.0),
    )
    val result = calculateEdgeConsistency(stats)
    assertNotNull(result)
    assertEquals(2, result!!.yearsAnalyzed)
    assertEquals(2, result.yearlyEdges.size)
  }

  @Test
  fun `downside should scale linearly - worst edge minus 5 gives downsideScore 50`() {
    val stats = mapOf(
      2023 to periodStats(edge = 3.0),
      2024 to periodStats(edge = -5.0),
    )
    val result = calculateEdgeConsistency(stats)
    assertNotNull(result)
    // worst = -5, downside = 100 * (1 + (-5/10)) = 100 * 0.5 = 50
    assertEquals(50.0, result!!.downsideScore, 0.01)
  }

  @Test
  fun `downside should floor at 0 - worst edge at or below minus 10`() {
    val stats = mapOf(
      2023 to periodStats(edge = 2.0),
      2024 to periodStats(edge = -10.0),
    )
    val result = calculateEdgeConsistency(stats)
    assertNotNull(result)
    // worst = -10, downside = 100 * (1 + (-10/10)) = 0
    assertEquals(0.0, result!!.downsideScore, 0.01)

    val stats2 = mapOf(
      2023 to periodStats(edge = 2.0),
      2024 to periodStats(edge = -15.0),
    )
    val result2 = calculateEdgeConsistency(stats2)
    assertNotNull(result2)
    // worst = -15, downside = 100 * (1 + (-15/10)) = -50 clamped to 0
    assertEquals(0.0, result2!!.downsideScore, 0.01)
  }

  @Test
  fun `downside should be 100 when worst edge is positive`() {
    val stats = mapOf(
      2023 to periodStats(edge = 1.0),
      2024 to periodStats(edge = 5.0),
    )
    val result = calculateEdgeConsistency(stats)
    assertNotNull(result)
    assertEquals(100.0, result!!.downsideScore, 0.01)
  }

  @Test
  fun `should handle Good interpretation range`() {
    // Target: score between 60-79
    val stats = mapOf(
      2020 to periodStats(edge = 4.0),
      2021 to periodStats(edge = 1.0),
      2022 to periodStats(edge = -2.0),
      2023 to periodStats(edge = 5.0),
      2024 to periodStats(edge = 0.5),
    )
    val result = calculateEdgeConsistency(stats)
    assertNotNull(result)
    // profitablePeriods: 4/5 = 80
    // tradeable (>=1.5%): 2/5 = 40 (4.0 and 5.0)
    // worst=-2.0, downside=100*(1+(-2/10))=80
    // score = 80*0.4 + 40*0.4 + 80*0.2 = 32 + 16 + 16 = 64
    assertTrue(result!!.score >= 60 && result.score < 80, "Score ${result.score} should be in Good range (60-79)")
    assertEquals("Good", result.interpretation)
  }

  @Test
  fun `edge exactly at threshold should count as tradeable`() {
    val stats = mapOf(
      2023 to periodStats(edge = 1.5),
      2024 to periodStats(edge = 1.5),
    )
    val result = calculateEdgeConsistency(stats)
    assertNotNull(result)
    // 1.5% is exactly at the threshold - should count
    assertEquals(100.0, result!!.stabilityScore, 0.01)
  }

  @Test
  fun `edge just below threshold should not count as tradeable`() {
    val stats = mapOf(
      2023 to periodStats(edge = 1.49),
      2024 to periodStats(edge = 1.49),
    )
    val result = calculateEdgeConsistency(stats)
    assertNotNull(result)
    assertEquals(0.0, result!!.stabilityScore, 0.01)
  }
}
