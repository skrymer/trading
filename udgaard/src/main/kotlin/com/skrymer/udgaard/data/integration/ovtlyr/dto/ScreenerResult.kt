package com.skrymer.udgaard.data.integration.ovtlyr.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 */
class ScreenerResult {
  val resultDetail: String? = null

  @JsonProperty("lst_stk")
  val stocks: List<ScreenerStock> = emptyList()
}
