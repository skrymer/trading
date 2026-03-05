package com.skrymer.udgaard.data.integration.ovtlyr.dto

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

@JsonInclude(JsonInclude.Include.NON_NULL)
data class StockPerformance(
  @param:JsonProperty("lst_performanceSummary")
  val performanceSummary: List<PerformanceSummaryItem>,
) {
  fun ovtlyrPerformance() =
    performanceSummary
      .first { it.metrics == "SIgnal_Return_per" }
      .ovtlyrPerformance
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class PerformanceSummaryItem(
  @param:JsonProperty("stockSymbol")
  val stockSymbol: String,
  @param:JsonProperty("quotedate")
  val quoteDate: String,
  @param:JsonProperty("matrics")
  val metrics: String,
  @param:JsonProperty("buyHold")
  val buyHold: String? = null,
  @param:JsonProperty("ovtlyr")
  val ovtlyrPerformance: Double? = null,
  @param:JsonProperty("period")
  val period: String,
)
