package com.skrymer.udgaard.backtesting.strategy

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins VCP's preferred-ranker contract. The ranker is the only structural choice
 * VCP exposes at scan time and the one that decides 60–80% of fills on signal-dense
 * days — see ADR / sweep writeup in `strategy_exploration/VCP_STRATEGY_DEVELOPMENT.md`.
 */
class VcpEntryStrategyTest {
  @Test
  fun `preferredRanker uses base-tightness tie-breaker, not the random-jitter Sector Edge`() {
    // Given
    val strategy = VcpEntryStrategy()

    // When
    val ranker = strategy.preferredRanker()

    // Then: deterministic tightness ranker — random tie-breaks within a sector were swept
    // out in favour of ATR/close (tighter base ranks higher). See sweep results in
    // strategy_exploration/VCP_STRATEGY_DEVELOPMENT.md § Sector Edge Tightness Ranker.
    assertTrue(
      ranker is SectorEdgeWithTightnessRanker,
      "expected SectorEdgeWithTightnessRanker, got ${ranker?.javaClass?.simpleName}",
    )
  }
}
