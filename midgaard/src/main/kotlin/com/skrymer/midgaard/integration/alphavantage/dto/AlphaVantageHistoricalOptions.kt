package com.skrymer.midgaard.integration.alphavantage.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.skrymer.midgaard.model.OptionContractDto
import org.slf4j.LoggerFactory
import java.time.LocalDate

@JsonIgnoreProperties(ignoreUnknown = true)
data class AlphaVantageHistoricalOptions(
    val endpoint: String? = null,
    val message: String? = null,
    val data: List<AlphaVantageOptionContract>? = null,
) : AlphaVantageApiResponse {
    override fun hasError(): Boolean = message != "success" || data == null

    override fun getErrorDescription(): String = message ?: "Unknown error"

    override fun isValid(): Boolean = message == "success" && data != null && data.isNotEmpty()

    fun toOptionContracts(): List<OptionContractDto> = data?.map { it.toOptionContract() } ?: emptyList()
}

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
    fun toOptionContract(): OptionContractDto =
        OptionContractDto(
            contractId = contractID ?: "",
            symbol = symbol ?: "",
            strike = strike?.toDoubleOrNull() ?: 0.0,
            expiration = parseDate(expiration),
            optionType = type ?: "call",
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
            logger.warn("Failed to parse date '$dateStr', defaulting to today: ${e.message}")
            LocalDate.now()
        }

    companion object {
        private val logger = LoggerFactory.getLogger(AlphaVantageOptionContract::class.java)
    }
}
