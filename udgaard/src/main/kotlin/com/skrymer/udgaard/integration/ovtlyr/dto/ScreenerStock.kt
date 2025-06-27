package com.skrymer.udgaard.integration.ovtlyr.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate

/**
 * Payload
 * {
 *   "Symbol": "AER",
 *   "symbolSEName": "AER",
 *   "marketCap": 22010696094.24,
 *   "scaling_sum6_version3": null,
 *   "CompanyName": "Aercap Holdings N.V.",
 *   "isFavourite": false,
 *   "isNotify": false,
 *   "buySellDate": "2025-06-25T00:00:00",
 *   "buySellDateStr": "Jun 25,2025",
 *   "buySellStatus": "Buy",
 *   "buySellFinalRegion": 1,
 *   "gics_Sector": "Industrials",
 *   "gics_Industry": "Trading Companies & Distributors",
 *   "notificationCount": 0,
 *   "averageVol30Days": 1234471.8,
 *   "averageVol30Days_str": "1,234,471",
 *   "closePrice": 116.01,
 *   "heatMap": 68.49390829808198,
 *   "heatMap_str": "68.49",
 *   "ovtlyrSignalReturn": 130.298198054537,
 *   "ovtlyrCapitalEfficiency": 2.67581784069589,
 *   "price_correction": 0.790616854908785,
 *   "oscilatorMovingUpDown": "up",
 *   "finalRegionChangedDate": "2025-04-24T00:00:00",
 *   "finalRegionChangedDate_str": "Apr 24,2025",
 *   "notificationCondition": null
 * }

 */
class ScreenerStock {

  @JsonProperty("Symbol")
  val symbol: String? = null

  @JsonProperty("buySellDate")
  val buySellDate: LocalDate? = null

  @JsonProperty("buySellStatus")
  val signal: String? = null

  @JsonProperty("gics_Sector")
  val sector: String? = null

  @JsonProperty("ovtlyrSignalReturn")
  val ovtlySignalReturn: Double = 0.0

  @JsonProperty("heatMap")
  val heatmap: Double  = 0.0

  @JsonProperty("closePrice")
  val closePrice: Double = 0.0

  override fun toString(): String {
    return "Symbol: $symbol Close price: $closePrice Signal date: $buySellDate".toString()
  }
}