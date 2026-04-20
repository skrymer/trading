package com.skrymer.udgaard.backtesting.strategy

import com.skrymer.udgaard.backtesting.dto.RankerConfig

object RankerFactory {
  private val rankerNames = listOf(
    "Adaptive",
    "Volatility",
    "DistanceFrom10Ema",
    "Composite",
    "SectorStrength",
    "RollingSectorStrength",
    "SectorStrengthMomentum",
    "SectorEdge",
    "Random",
  )

  fun availableRankers(): List<String> = rankerNames

  fun create(name: String, rankerConfig: RankerConfig? = null): StockRanker? =
    when (name.lowercase()) {
      "volatility" -> VolatilityRanker()
      "distancefrom10ema" -> DistanceFrom10EmaRanker()
      "composite" -> CompositeRanker()
      "sectorstrength" -> SectorStrengthRanker()
      "rollingsectorstrength" -> RollingSectorStrengthRanker()
      "sectorstrengthmomentum" -> SectorStrengthMomentumRanker()
      "sectoredge" -> {
        val ranking = rankerConfig?.sectorRanking
        if (ranking.isNullOrEmpty()) null else SectorEdgeRanker(ranking)
      }
      "random" -> RandomRanker()
      "adaptive" -> AdaptiveRanker()
      else -> null
    }
}
