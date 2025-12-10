package com.skrymer.udgaard.integration.ovtlyr.dto

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

@JsonInclude(JsonInclude.Include.NON_NULL)
data class StockPerformance(
  @JsonProperty("lst_performanceSummary")
  val performanceSummary: List<PerformanceSummaryItem>,
) {
  fun ovtlyrPerformance() =
    performanceSummary
      .first { it.metrics == "SIgnal_Return_per" }
      .ovtlyrPerformance
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class PerformanceSummaryItem(
  @JsonProperty("stockSymbol")
  val stockSymbol: String,
  @JsonProperty("quotedate")
  val quoteDate: String,
  @JsonProperty("matrics")
  val metrics: String,
  @JsonProperty("buyHold")
  val buyHold: String? = null,
  @JsonProperty("ovtlyr")
  val ovtlyrPerformance: Double? = null,
  @JsonProperty("period")
  val period: String,
)
