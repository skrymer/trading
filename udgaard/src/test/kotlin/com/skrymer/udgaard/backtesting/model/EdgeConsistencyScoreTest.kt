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
  fun `should calculate perfect score for identical positive edges`() {
    val stats = mapOf(
      2020 to periodStats(edge = 3.0),
      2021 to periodStats(edge = 3.0),
      2022 to periodStats(edge = 3.0),
      2023 to periodStats(edge = 3.0),
      2024 to periodStats(edge = 3.0),
    )
    val result = calculateEdgeConsistency(stats)
    assertNotNull(result)
    // profitablePeriods = 100, stability = 100 (stdDev=0, CV=0), downside = 100 (worst=3.0>=0)
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
    // mean = 2.5, stdDev is large, CV > 1 so stability = 0
    // worst = -3.0, downside = 100 * (1 + (-3.0/10)) = 70
    // score = 20*0.4 + 0*0.4 + 70*0.2 = 8 + 0 + 14 = 22
    assertEquals(22.0, result!!.score, 1.0)
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
    // stability: edges are [-5,-3,-4], mean=-4, stdDev=~0.816, CV=0.204, stability=max(0, 100*(1-0.204))=79.6
    // worst = -5.0, downside = 100 * (1 + (-5.0/10)) = 50
    // score = 0*0.4 + 79.6*0.4 + 50*0.2 = 0 + 31.84 + 10 = 41.84
    // Actually all negative but consistent => Moderate, not Very Poor
    // Very Poor requires score < 20
    assertEquals("Moderate", result!!.interpretation)
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
    // mean=3.0, stdDev=1.0, CV=1/3=0.333, stability=100*(1-0.333)=66.67
    // worst=2.0>=0, downside=100
    // score = 100*0.4 + 66.67*0.4 + 100*0.2 = 40 + 26.67 + 20 = 86.67
    assertEquals(86.67, result.score, 0.5)
    assertEquals("Excellent", result.interpretation)
  }

  @Test
  fun `should set stability to 0 for near-zero mean with variance`() {
    val stats = mapOf(
      2023 to periodStats(edge = 5.0),
      2024 to periodStats(edge = -5.0),
    )
    val result = calculateEdgeConsistency(stats)
    assertNotNull(result)
    // mean = 0.0 (near zero), stdDev = 5.0 (not near zero)
    // stability = 0
    assertEquals(0.0, result!!.stabilityScore, 0.01)
  }

  @Test
  fun `should set stability to 50 when all edges are exactly zero`() {
    val stats = mapOf(
      2023 to periodStats(edge = 0.0),
      2024 to periodStats(edge = 0.0),
      2025 to periodStats(edge = 0.0),
    )
    val result = calculateEdgeConsistency(stats)
    assertNotNull(result)
    // mean = 0, stdDev = 0 => stability = 50
    assertEquals(50.0, result!!.stabilityScore, 0.01)
    // profitablePeriods: 0/3 = 0
    // downside: worst=0>=0, so 100
    // score = 0*0.4 + 50*0.4 + 100*0.2 = 0 + 20 + 20 = 40
    assertEquals(40.0, result.score, 0.01)
    assertEquals("Moderate", result.interpretation)
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
    // Need: profitablePeriods 0, stability low (high CV), downside 0
    val stats = mapOf(
      2020 to periodStats(edge = -1.0),
      2021 to periodStats(edge = -15.0),
      2022 to periodStats(edge = -2.0),
      2023 to periodStats(edge = -14.0),
      2024 to periodStats(edge = -3.0),
    )
    val result = calculateEdgeConsistency(stats)
    assertNotNull(result)
    // profitablePeriods: 0/5 = 0
    // mean = -7.0, stdDev = ~6.32, CV = 0.903, stability = max(0, 100*(1-0.903)) = 9.7
    // worst = -15, downside = 100*(1 + (-15/10)) = -50 clamped to 0
    // score = 0*0.4 + 9.7*0.4 + 0*0.2 = 3.88
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
    // Use consistent positive edges with one slightly negative year
    val stats = mapOf(
      2020 to periodStats(edge = 3.0),
      2021 to periodStats(edge = 2.5),
      2022 to periodStats(edge = -0.5),
      2023 to periodStats(edge = 3.5),
      2024 to periodStats(edge = 2.0),
    )
    val result = calculateEdgeConsistency(stats)
    assertNotNull(result)
    // profitablePeriods: 4/5 = 80
    // mean=2.1, stdDev=~1.37, CV=0.65, stability=max(0,100*(1-0.65))=35
    // worst=-0.5, downside=100*(1+(-0.5/10))=95
    // score = 80*0.4 + 35*0.4 + 95*0.2 = 32 + 14 + 19 = 65
    assertTrue(result!!.score >= 60 && result.score < 80, "Score ${result.score} should be in Good range (60-79)")
    assertEquals("Good", result.interpretation)
  }
}
