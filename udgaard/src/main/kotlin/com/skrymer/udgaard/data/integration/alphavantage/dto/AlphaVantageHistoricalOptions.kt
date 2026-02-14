package com.skrymer.udgaard.data.integration.alphavantage.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.skrymer.udgaard.portfolio.integration.options.OptionContract
import com.skrymer.udgaard.portfolio.model.OptionType
import java.time.LocalDate

/**
 * Response from AlphaVantage HISTORICAL_OPTIONS API
 * Documentation: https://www.alphavantage.co/documentation/#historical-options
 *
 * Example response:
 * {
 *   "endpoint": "Historical Options",
 *   "message": "success",
 *   "data": [...]
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AlphaVantageHistoricalOptions(
  val endpoint: String? = null,
  val message: String? = null,
  val data: List<AlphaVantageOptionContract>? = null,
) {
  fun hasError(): Boolean = message != "success" || data == null

  fun getErrorDescription(): String = message ?: "Unknown error"

  fun isValid(): Boolean = message == "success" && data != null && data.isNotEmpty()

  fun toOptionContracts(): List<OptionContract> = data?.map { it.toOptionContract() } ?: emptyList()
}

/**
 * Individual option contract from AlphaVantage API
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AlphaVantageOptionContract(
  @JsonProperty("contractID")
  val contractID: String? = null,
  val symbol: String? = null,
  val expiration: String? = null,
  val strike: String? = null,
  val type: String? = null,
  val last: String? = null,
  val mark: String? = null,
  val bid: String? = null,
  @JsonProperty("bid_size")
  val bidSize: String? = null,
  val ask: String? = null,
  @JsonProperty("ask_size")
  val askSize: String? = null,
  val volume: String? = null,
  @JsonProperty("open_interest")
  val openInterest: String? = null,
  val date: String? = null,
  @JsonProperty("implied_volatility")
  val impliedVolatility: String? = null,
  val delta: String? = null,
  val gamma: String? = null,
  val theta: String? = null,
  val vega: String? = null,
  val rho: String? = null,
) {
  fun toOptionContract(): OptionContract =
    OptionContract(
      contractId = contractID ?: "",
      symbol = symbol ?: "",
      strike = strike?.toDoubleOrNull() ?: 0.0,
      expiration = parseDate(expiration),
      optionType = parseOptionType(type),
      date = parseDate(date),
      price = mark?.toDoubleOrNull() ?: last?.toDoubleOrNull() ?: 0.0,
      impliedVolatility = impliedVolatility?.toDoubleOrNull(),
      delta = delta?.toDoubleOrNull(),
      gamma = gamma?.toDoubleOrNull(),
      theta = theta?.toDoubleOrNull(),
      vega = vega?.toDoubleOrNull(),
    )

  private fun parseDate(dateStr: String?): LocalDate =
    try {
      LocalDate.parse(dateStr)
    } catch (e: Exception) {
      LocalDate.now()
    }

  private fun parseOptionType(typeStr: String?): OptionType =
    when (typeStr?.lowercase()) {
      "call" -> OptionType.CALL
      "put" -> OptionType.PUT
      else -> OptionType.CALL
    }
}
