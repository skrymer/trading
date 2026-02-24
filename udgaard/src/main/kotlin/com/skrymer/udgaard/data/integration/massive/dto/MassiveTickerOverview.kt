package com.skrymer.udgaard.data.integration.massive.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.skrymer.udgaard.data.model.CompanyInfo
import com.skrymer.udgaard.data.model.SectorSymbol

/**
 * Massive API Ticker Overview response
 *
 * Endpoint: GET /v3/reference/tickers/{ticker}?apiKey={key}
 *
 * Example response:
 * {
 *   "results": {
 *     "ticker": "AAPL",
 *     "name": "Apple Inc.",
 *     "sic_code": "3571",
 *     "market_cap": 3000000000000,
 *     ...
 *   },
 *   "status": "OK"
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class MassiveTickerOverview(
  @JsonProperty("results")
  val results: MassiveTickerResults? = null,
  @JsonProperty("status")
  val status: String? = null,
  @JsonProperty("error")
  val error: String? = null,
  @JsonProperty("message")
  val message: String? = null,
) {
  fun hasError(): Boolean = status == "ERROR" || error != null || message != null

  fun getErrorDescription(): String =
    when {
      error != null -> error
      message != null -> message
      else -> "Unknown error"
    }

  fun isValid(): Boolean = results != null && results.ticker != null

  fun toCompanyInfo(): CompanyInfo = CompanyInfo(
    sectorSymbol = results?.toSectorSymbol(),
    marketCap = results?.marketCap,
  )
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class MassiveTickerResults(
  @JsonProperty("ticker")
  val ticker: String? = null,
  @JsonProperty("name")
  val name: String? = null,
  @JsonProperty("sic_code")
  val sicCode: String? = null,
  @JsonProperty("market_cap")
  val marketCap: Long? = null,
) {
  /**
   * Map 4-digit SIC code to S&P sector ETF symbol.
   *
   * SIC (Standard Industrial Classification) codes are 4-digit numbers
   * assigned by the US government to classify industries.
   *
   * Mapping based on SIC Division structure:
   * - Division B (10-14): Mining → XLB/XLE
   * - Division C (15-17): Construction → XLI
   * - Division D (20-39): Manufacturing → varies by industry
   * - Division E (40-49): Transportation/Communications/Utilities
   * - Division F (50-51): Wholesale → XLI
   * - Division G (52-59): Retail → XLY/XLP
   * - Division H (60-67): Finance/Insurance/Real Estate
   * - Division I (70-89): Services → varies by industry
   */
  fun toSectorSymbol(): SectorSymbol? {
    val code = sicCode?.toIntOrNull() ?: return null
    return SIC_MAPPINGS.firstOrNull { code in it.first }?.second
  }

  companion object {
    /**
     * SIC code range to SectorSymbol mapping table.
     * Each entry maps a range of SIC codes to its corresponding S&P sector ETF.
     */
    private val SIC_MAPPINGS: List<Pair<IntRange, SectorSymbol>> = listOf(
      // Mining (metals, minerals) → Materials
      1000..1099 to SectorSymbol.XLB,
      1400..1499 to SectorSymbol.XLB,
      // Mining (coal, oil & gas extraction, oil services) → Energy
      1200..1399 to SectorSymbol.XLE,
      // Construction → Industrials
      1500..1799 to SectorSymbol.XLI,
      // Food, tobacco → Consumer Staples
      2000..2199 to SectorSymbol.XLP,
      // Textiles, apparel, lumber, furniture, paper → Consumer Discretionary
      2200..2599 to SectorSymbol.XLY,
      // Printing, publishing → Communications (media)
      2700..2799 to SectorSymbol.XLC,
      // Chemicals → Materials
      2800..2835 to SectorSymbol.XLB,
      // Biological products → Health Care
      2836..2836 to SectorSymbol.XLV,
      // Soap, cleaning, cosmetics → Consumer Staples
      2840..2899 to SectorSymbol.XLP,
      // Petroleum refining → Energy
      2900..2999 to SectorSymbol.XLE,
      // Rubber, plastics, glass, stone, metals → Materials
      3000..3499 to SectorSymbol.XLB,
      // Industrial machinery → Industrials
      3500..3569 to SectorSymbol.XLI,
      // Computer hardware → Technology
      3570..3579 to SectorSymbol.XLK,
      // Other industrial machinery → Industrials
      3580..3599 to SectorSymbol.XLI,
      // Electronics, computers, semiconductors → Technology
      3600..3699 to SectorSymbol.XLK,
      // Transportation equipment → Industrials
      3700..3799 to SectorSymbol.XLI,
      // Medical instruments, measuring devices → Health Care
      3800..3851 to SectorSymbol.XLV,
      // Photographic, watches, misc manufacturing → Industrials
      3852..3999 to SectorSymbol.XLI,
      // Railroad, trucking, air transport, pipelines → Industrials
      4000..4799 to SectorSymbol.XLI,
      // Communications (telephone, broadcasting, cable) → Communications
      4800..4899 to SectorSymbol.XLC,
      // Utilities (electric, gas, water, sanitary) → Utilities
      4900..4999 to SectorSymbol.XLU,
      // Wholesale (durable and non-durable) → Industrials
      5000..5199 to SectorSymbol.XLI,
      // Retail (building materials, general merchandise, food stores) → Consumer Staples
      5200..5399 to SectorSymbol.XLP,
      // Retail (auto, apparel, furniture, electronics, restaurants) → Consumer Discretionary
      5400..5999 to SectorSymbol.XLY,
      // Banking, credit, securities, commodities → Financials
      6000..6499 to SectorSymbol.XLF,
      // Real estate → Real Estate
      6500..6599 to SectorSymbol.XLRE,
      // Insurance, holding companies → Financials
      6600..6799 to SectorSymbol.XLF,
      // Hotels, personal services → Consumer Discretionary
      7000..7099 to SectorSymbol.XLY,
      // Business services (including software) → Technology
      7300..7399 to SectorSymbol.XLK,
      // Auto repair, misc repair → Consumer Discretionary
      7500..7699 to SectorSymbol.XLY,
      // Motion pictures, amusement, recreation → Communications
      7800..7999 to SectorSymbol.XLC,
      // Health services (hospitals, nursing, labs) → Health Care
      8000..8099 to SectorSymbol.XLV,
      // Legal services → Industrials
      8100..8199 to SectorSymbol.XLI,
      // Educational services → Consumer Discretionary
      8200..8299 to SectorSymbol.XLY,
      // Social services → Health Care
      8300..8399 to SectorSymbol.XLV,
      // Engineering, accounting, research, management → Technology
      8700..8799 to SectorSymbol.XLK,
      // Misc services → Industrials
      8900..8999 to SectorSymbol.XLI,
      // Public administration → Industrials
      9000..9999 to SectorSymbol.XLI,
    )
  }
}
