package com.skrymer.udgaard.model

/**
 * Represents the entire market (all stocks combined).
 * This is distinct from individual sectors.
 */
enum class MarketSymbol(
  val description: String,
) {
  /**
   * Represents the full stock market - all stocks across all sectors combined.
   */
  FULLSTOCK("All Stocks"),
  ;

  companion object {
    /**
     * Parse a market symbol from string, returns null if invalid.
     */
    fun fromString(value: String?): MarketSymbol? {
      if (value == null) return null
      return entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
  }
}
