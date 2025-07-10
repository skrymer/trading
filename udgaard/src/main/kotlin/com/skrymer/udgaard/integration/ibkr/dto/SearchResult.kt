package com.skrymer.udgaard.integration.ibkr.dto

/**
 *
 *   {
 *     "conid": "43645865",
 *     "companyHeader": "IBKR INTERACTIVE BROKERS GRO-CL A (NASDAQ) ",
 *     "companyName": "INTERACTIVE BROKERS GRO-CL A (NASDAQ)",
 *     "symbol": "IBKR",
 *     "description": null,
 *     "restricted": null,
 *     "sections": [],
 *     "secType": "STK"
 *   }
 *
 */
data class SearchResult(
  /**
   * Conid of the given contract.
   */
  val conid: String?,
  /**
   * Extended company name and primary exchange.
   */
  val companyHeader: String?,
  /**
   * Name of the company.
   */
  val companyName: String?,
  /**
   * Primary exchange of the contract.
   */
  val description: String?,
  /**
   * A string of exchanges, separated by semicolons.
   * Value Format: “EXCH;EXCH;EXCH”
   */
  val exchange: String?,
  /**
   * True if the contract is available for trading.
   */
  val restricted: String?,
  /**
   * Sections
   */
  val sections: List<Section>?,
  /**
   * Symbol of the instrument.
   */
  val symbol: String?
)

data class Section(
  /**
   * Given contracts security type.
   */
  val secType: String?,
  /**
   * A string of dates, separated by semicolons.
   * Value Format: “JANYY;FEBYY;MARYY”
   */
  val months: String?,
)
