package com.skrymer.udgaard.integration.ovtlyr.dto

import com.fasterxml.jackson.annotation.JsonProperty


/**
 */
class ScreenerResult {
  @JsonProperty("result")
  val result: String? = null

  @JsonProperty("lst_stk")
  val stocks: List<ScreenerStock> = emptyList()

}