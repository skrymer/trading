package com.skrymer.udgaard.data.integration.alphavantage.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.skrymer.udgaard.data.model.CompanyInfo
import com.skrymer.udgaard.data.model.SectorSymbol

/**
 * Alpha Vantage OVERVIEW response (Company Overview)
 *
 * This endpoint returns company information, financial ratios, and other key metrics.
 * Most importantly for our use case, it provides the sector classification.
 *
 * Example response:
 * {
 *   "Symbol": "IBM",
 *   "Name": "International Business Machines",
 *   "Sector": "TECHNOLOGY",
 *   "Industry": "Computer & Technology",
 *   "MarketCapitalization": "153879871488",
 *   "PERatio": "19.63",
 *   ...
 * }
 *
 * API Documentation: https://www.alphavantage.co/documentation/#company-overview
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AlphaVantageCompanyOverview(
  @JsonProperty("Symbol")
  val symbol: String? = null,
  @JsonProperty("Name")
  val name: String? = null,
  @JsonProperty("Sector")
  val sector: String? = null,
  @JsonProperty("Industry")
  val industry: String? = null,
  @JsonProperty("MarketCapitalization")
  val marketCapitalization: String? = null,
  @JsonProperty("Error Message")
  val errorMessage: String? = null,
  @JsonProperty("Note")
  val note: String? = null,
  @JsonProperty("Information")
  val information: String? = null,
) {
  /**
   * Check if the response contains an error
   */
  fun hasError(): Boolean = errorMessage != null || note != null || information != null

  /**
   * Get human-readable error description
   */
  fun getErrorDescription(): String =
    when {
      errorMessage != null -> errorMessage
      note != null -> note
      information != null -> information
      else -> "Unknown error"
    }

  /**
   * Check if response is valid (has required data fields)
   */
  fun isValid(): Boolean = symbol != null && sector != null

  /**
   * Convert Alpha Vantage sector string to SectorSymbol enum
   *
   * Maps Alpha Vantage sector names to our internal sector ETF representation:
   * - "TECHNOLOGY" -> XLK
   * - "FINANCIALS" -> XLF
   * - "HEALTH CARE" -> XLV
   * - "ENERGY" -> XLE
   * - "INDUSTRIALS" -> XLI
   * - "CONSUMER DISCRETIONARY" -> XLY
   * - "CONSUMER STAPLES" -> XLP
   * - "UTILITIES" -> XLU
   * - "MATERIALS" -> XLB
   * - "REAL ESTATE" -> XLRE
   * - "COMMUNICATION SERVICES" -> XLC
   *
   * @return SectorSymbol enum, or null if sector cannot be mapped
   */
  fun toSectorSymbol(): SectorSymbol? {
    if (sector == null) return null

    return when (sector.uppercase().trim()) {
      "TECHNOLOGY" -> SectorSymbol.XLK
      "FINANCIAL SERVICES" -> SectorSymbol.XLF
      "HEALTHCARE" -> SectorSymbol.XLV
      "ENERGY" -> SectorSymbol.XLE
      "INDUSTRIALS" -> SectorSymbol.XLI
      "CONSUMER DISCRETIONARY", "CONSUMER CYCLICAL" -> SectorSymbol.XLY
      "CONSUMER STAPLES", "CONSUMER DEFENSIVE" -> SectorSymbol.XLP
      "UTILITIES" -> SectorSymbol.XLU
      "MATERIALS", "BASIC MATERIALS" -> SectorSymbol.XLB
      "REAL ESTATE" -> SectorSymbol.XLRE
      "COMMUNICATION SERVICES", "COMMUNICATIONS", "TELECOMMUNICATION SERVICES" -> SectorSymbol.XLC
      else -> null
    }
  }

  /**
   * Parse market capitalization string to Long
   *
   * @return Market cap as Long, or null if not available or not parseable
   */
  fun toMarketCap(): Long? = marketCapitalization?.toLongOrNull()

  /**
   * Convert to CompanyInfo containing both sector and market cap
   */
  fun toCompanyInfo(): CompanyInfo = CompanyInfo(
    sectorSymbol = toSectorSymbol(),
    marketCap = toMarketCap(),
  )
}
