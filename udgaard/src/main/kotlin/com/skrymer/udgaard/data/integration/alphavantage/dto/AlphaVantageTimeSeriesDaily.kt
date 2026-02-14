package com.skrymer.udgaard.data.integration.alphavantage.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class MetaData(
  @JsonProperty("1. Information")
  val information: String,
  @JsonProperty("2. Symbol")
  val symbol: String,
  @JsonProperty("3. Last Refreshed")
  val lastRefreshed: String,
  @JsonProperty("4. Output Size")
  val outputSize: String,
  @JsonProperty("5. Time Zone")
  val timeZone: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DailyData(
  @JsonProperty("1. open")
  val open: String,
  @JsonProperty("2. high")
  val high: String,
  @JsonProperty("3. low")
  val low: String,
  @JsonProperty("4. close")
  val close: String,
  @JsonProperty("5. volume")
  val volume: String,
)
