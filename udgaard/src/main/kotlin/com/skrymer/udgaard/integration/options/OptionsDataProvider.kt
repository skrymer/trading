package com.skrymer.udgaard.integration.options

import com.skrymer.udgaard.model.OptionType
import java.time.LocalDate

/**
 * Interface for fetching historical options data from various providers.
 * Allows switching between AlphaVantage, Polygon, or other data sources.
 */
interface OptionsDataProvider {
    /**
     * Get all option contracts available for a given symbol on a specific date.
     * If date is null, fetches all available historical options data.
     * Returns null if no data available (weekends, holidays, or data not found).
     */
    fun getHistoricalOptions(symbol: String, date: String? = null): List<OptionContract>?

    /**
     * Find a specific option contract matching the given parameters.
     * Returns null if no matching contract found.
     */
    fun findOptionContract(
        symbol: String,
        strike: Double,
        expiration: String,
        optionType: OptionType,
        date: String
    ): OptionContract?
}

/**
 * Represents an option contract with pricing and Greeks data.
 */
data class OptionContract(
    val contractId: String,
    val symbol: String,
    val strike: Double,
    val expiration: LocalDate,
    val optionType: OptionType,
    val date: LocalDate,
    val price: Double,
    val impliedVolatility: Double? = null,
    val delta: Double? = null,
    val gamma: Double? = null,
    val theta: Double? = null,
    val vega: Double? = null
)
