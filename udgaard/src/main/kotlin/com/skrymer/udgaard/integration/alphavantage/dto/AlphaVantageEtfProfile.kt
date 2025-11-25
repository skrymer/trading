package com.skrymer.udgaard.integration.alphavantage.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate

/**
 * Response from AlphaVantage ETF_PROFILE endpoint
 * https://www.alphavantage.co/documentation/#etf-profile
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AlphaVantageEtfProfile(
    @JsonProperty("net_assets")
    val netAssets: String? = null,  // AUM in string format like "410800000000"

    @JsonProperty("net_expense_ratio")
    val netExpenseRatio: String? = null,  // Expense ratio like "0.002" (0.2%)

    @JsonProperty("portfolio_turnover")
    val portfolioTurnover: String? = null,  // Like "0.05" or "n/a"

    @JsonProperty("dividend_yield")
    val dividendYield: String? = null,  // Like "0.0043" (0.43%)

    @JsonProperty("inception_date")
    val inceptionDate: String? = null,  // Like "1999-03-10"

    @JsonProperty("leveraged")
    val leveraged: String? = null,  // "YES" or "NO"

    @JsonProperty("sectors")
    val sectors: List<EtfSectorAllocation>? = null,

    @JsonProperty("holdings")
    val holdings: List<EtfHoldingInfo>? = null,

    // Error fields
    @JsonProperty("Error Message")
    val errorMessage: String? = null,

    @JsonProperty("Note")
    val note: String? = null,

    @JsonProperty("Information")
    val information: String? = null
) {
    fun hasError(): Boolean = errorMessage != null || note != null || information != null

    fun getErrorDescription(): String {
        return errorMessage ?: note ?: information ?: "Unknown error"
    }

    fun isValid(): Boolean = netAssets != null && !hasError()

    /**
     * Parse net assets (AUM) from string to double
     */
    fun getNetAssetsAsDouble(): Double? {
        return netAssets?.toDoubleOrNull()
    }

    /**
     * Parse expense ratio from string to double (as percentage)
     */
    fun getExpenseRatioAsDouble(): Double? {
        return netExpenseRatio?.toDoubleOrNull()?.times(100)  // Convert to percentage
    }

    /**
     * Parse dividend yield from string to double (as percentage)
     */
    fun getDividendYieldAsDouble(): Double? {
        return dividendYield?.toDoubleOrNull()?.times(100)  // Convert to percentage
    }

    /**
     * Parse inception date from string to LocalDate
     */
    fun getInceptionDateAsLocalDate(): LocalDate? {
        return try {
            inceptionDate?.let { LocalDate.parse(it) }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Check if the ETF is leveraged
     */
    fun isLeveraged(): Boolean {
        return leveraged?.uppercase() == "YES"
    }
}

/**
 * Sector allocation within an ETF
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class EtfSectorAllocation(
    @JsonProperty("sector")
    val sector: String,

    @JsonProperty("weight")
    val weight: String  // Like "0.534" for 53.4%
) {
    fun getWeightAsDouble(): Double {
        return weight.toDoubleOrNull()?.times(100) ?: 0.0  // Convert to percentage
    }
}

/**
 * Individual holding information within an ETF
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class EtfHoldingInfo(
    @JsonProperty("symbol")
    val symbol: String,

    @JsonProperty("description")
    val description: String? = null,  // Company name

    @JsonProperty("weight")
    val weight: String  // Like "0.0967" for 9.67%
) {
    fun getWeightAsDouble(): Double {
        return weight.toDoubleOrNull()?.times(100) ?: 0.0  // Convert to percentage
    }
}
