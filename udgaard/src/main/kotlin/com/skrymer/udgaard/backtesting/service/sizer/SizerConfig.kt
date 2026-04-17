package com.skrymer.udgaard.backtesting.service.sizer

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
  JsonSubTypes.Type(value = AtrRiskSizerConfig::class, name = "atrRisk"),
  JsonSubTypes.Type(value = PercentEquitySizerConfig::class, name = "percentEquity"),
  JsonSubTypes.Type(value = KellySizerConfig::class, name = "kelly"),
  JsonSubTypes.Type(value = VolatilityTargetSizerConfig::class, name = "volTarget"),
)
sealed class SizerConfig {
  abstract fun toSizer(): PositionSizer
}

data class AtrRiskSizerConfig(
  val riskPercentage: Double,
  @param:JsonProperty("nAtr") @get:JsonProperty("nAtr") val nAtr: Double = 2.0,
) : SizerConfig() {
  init {
    require(riskPercentage > 0.0) { "riskPercentage must be positive, got $riskPercentage" }
    require(riskPercentage <= 100.0) { "riskPercentage must be <= 100, got $riskPercentage" }
    require(nAtr > 0.0) { "nAtr must be positive, got $nAtr" }
  }

  override fun toSizer(): PositionSizer = AtrRiskSizer(riskPercentage, nAtr)
}

data class PercentEquitySizerConfig(
  val percent: Double,
) : SizerConfig() {
  init {
    require(percent > 0.0) { "percent must be positive, got $percent" }
    require(percent <= 100.0) { "percent must be <= 100, got $percent" }
  }

  override fun toSizer(): PositionSizer = PercentEquitySizer(percent)
}

data class KellySizerConfig(
  val winRate: Double,
  val winLossRatio: Double,
  val fractionMultiplier: Double = KellySizer.DEFAULT_FRACTION_MULTIPLIER,
) : SizerConfig() {
  init {
    require(winRate in 0.0..1.0) { "winRate must be in [0, 1], got $winRate" }
    require(winLossRatio > 0.0) { "winLossRatio must be positive, got $winLossRatio" }
    require(fractionMultiplier in 0.0..1.0) { "fractionMultiplier must be in [0, 1], got $fractionMultiplier" }
  }

  override fun toSizer(): PositionSizer = KellySizer(winRate, winLossRatio, fractionMultiplier)
}

data class VolatilityTargetSizerConfig(
  val targetVolPct: Double,
  @param:JsonProperty("kAtr") @get:JsonProperty("kAtr") val kAtr: Double = 1.0,
) : SizerConfig() {
  init {
    require(targetVolPct > 0.0) { "targetVolPct must be positive, got $targetVolPct" }
    require(targetVolPct <= 100.0) { "targetVolPct must be <= 100, got $targetVolPct" }
    require(kAtr > 0.0) { "kAtr must be positive, got $kAtr" }
  }

  override fun toSizer(): PositionSizer = VolatilityTargetSizer(targetVolPct, kAtr)
}
