package com.skrymer.udgaard.controller.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class PositionSizingSettingsDto(
  val enabled: Boolean = true,
  val portfolioValue: Double = 100000.0,
  val riskPercentage: Double = 1.5,
  @get:JsonProperty("nAtr")
  @param:JsonProperty("nAtr")
  val nAtr: Double = 2.0,
  val instrumentMode: String = "STOCK",
  val maxPositions: Int = 15,
)
