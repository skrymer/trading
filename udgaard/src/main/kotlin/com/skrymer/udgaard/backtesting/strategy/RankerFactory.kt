package com.skrymer.udgaard.backtesting.strategy

import com.skrymer.udgaard.backtesting.dto.ParameterMetadata
import com.skrymer.udgaard.backtesting.dto.RankerConfig
import com.skrymer.udgaard.backtesting.dto.RankerMetadata

object RankerFactory {
  // Hardcoded catalog. Rankers aren't Spring components (some need runtime params from
  // RankerConfig), so the metadata lives here next to create() instead of on each class.
  private val catalog: List<RankerMetadata> =
    listOf(
      RankerMetadata(
        type = "Adaptive",
        displayName = "Adaptive",
        description = "Switches between Volatility and DistanceFrom10Ema based on the prevailing market regime.",
        parameters = emptyList(),
        category = "Score-Based",
        usesRandomTieBreaks = true,
      ),
      RankerMetadata(
        type = "Volatility",
        displayName = "Volatility",
        description = "Ranks stocks by ATR as percentage of price (higher volatility = larger potential moves).",
        parameters = emptyList(),
        category = "Score-Based",
        usesRandomTieBreaks = true,
      ),
      RankerMetadata(
        type = "DistanceFrom10Ema",
        displayName = "Distance From 10 EMA",
        description = "Ranks stocks by proximity to the 10 EMA (closer = less extended = better entry).",
        parameters = emptyList(),
        category = "Score-Based",
        usesRandomTieBreaks = true,
      ),
      RankerMetadata(
        type = "Composite",
        displayName = "Composite",
        description = "Weighted blend of Volatility (40%), DistanceFrom10Ema (30%), and SectorStrength (30%).",
        parameters = emptyList(),
        category = "Score-Based",
        usesRandomTieBreaks = true,
      ),
      RankerMetadata(
        type = "SectorStrength",
        displayName = "Sector Strength",
        description = "Ranks stocks by their sector's current bull percentage.",
        parameters = emptyList(),
        category = "Score-Based",
        usesRandomTieBreaks = true,
      ),
      RankerMetadata(
        type = "RollingSectorStrength",
        displayName = "Rolling Sector Strength",
        description = "Ranks stocks by their sector's average bull percentage over a rolling window (default 10 days).",
        parameters = emptyList(),
        category = "Score-Based",
        usesRandomTieBreaks = true,
      ),
      RankerMetadata(
        type = "SectorStrengthMomentum",
        displayName = "Sector Strength Momentum",
        description = "Ranks stocks by the recent change in their sector's bull percentage (default 10-day window).",
        parameters = emptyList(),
        category = "Score-Based",
        usesRandomTieBreaks = true,
      ),
      RankerMetadata(
        type = "SectorEdge",
        displayName = "Sector Edge",
        description = "Ranks stocks by user-supplied sector priority order (highest priority sector first).",
        parameters =
          listOf(
            ParameterMetadata(
              name = "sectorRanking",
              displayName = "Sector Ranking",
              type = "stringList",
              defaultValue = null,
            ),
          ),
        category = "Sector-Priority",
        usesRandomTieBreaks = false,
      ),
      RankerMetadata(
        type = "Random",
        displayName = "Random",
        description = "Random ordering. Use with `randomSeed` for reproducible runs.",
        parameters = emptyList(),
        category = "Random",
        usesRandomTieBreaks = true,
      ),
    )

  fun availableRankerMetadata(): List<RankerMetadata> = catalog

  fun availableRankers(): List<String> = catalog.map { it.type }

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
