package com.skrymer.udgaard.data.integration.ovtlyr.dto

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.skrymer.udgaard.data.model.OrderBlock
import com.skrymer.udgaard.data.model.OrderBlockType
import java.time.LocalDate

/**
 * Ovtlyr example payload
 * {
 *   "stockSymbol": "NVDA",
 *   "startdate": "2025-08-27",
 *   "startdate_timestamp": 1756252800000,
 *   "enddate": "NA",
 *   "enddate_timestamp": null,
 *   "oB_Type": "Bearish"
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class OvtlyrOrderBlock(
  @JsonProperty("stockSymbol")
  val stockSymbol: String,
  @JsonProperty("startdate")
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
  val startDate: LocalDate,
  @JsonProperty("startdate_timestamp")
  val startDateTimestamp: Long,
  // End date can be "NA" or null in your sample, so keep it as String?
  @JsonProperty("enddate")
  val endDate: String?,
  @JsonProperty("enddate_timestamp")
  val endDateTimestamp: Long?,
  @JsonProperty("oB_Type")
  val obType: ObType,
) {
  fun toModel(information: OvtlyrStockInformation): OrderBlock {
    val quote = information.getQuoteForDate(startDate)

    return OrderBlock(
      low = quote?.low ?: 0.0,
      high = quote?.high ?: 0.0,
      startDate = startDate,
      endDate = if (endDate.isNullOrBlank() || endDate == "NA") null else LocalDate.parse(endDate),
      orderBlockType = if (obType == ObType.BEARISH) OrderBlockType.BEARISH else OrderBlockType.BULLISH,
    )
  }
}

enum class ObType {
  @JsonProperty("Bearish")
  BEARISH,

  @JsonProperty("Bullish")
  BULLISH,
}
