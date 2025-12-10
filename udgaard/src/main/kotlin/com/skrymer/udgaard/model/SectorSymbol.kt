package com.skrymer.udgaard.model

/**
 * Represents the 11 market sectors based on S&P sector ETFs.
 * Each sector groups stocks by industry category.
 */
enum class SectorSymbol(
  val description: String,
  val etfSymbol: String,
) {
  XLE("Energy", "XLE"),
  XLV("Health", "XLV"),
  XLB("Materials", "XLB"),
  XLC("Communications", "XLC"),
  XLK("Technology", "XLK"),
  XLRE("Real Estate", "XLRE"),
  XLI("Industrials", "XLI"),
  XLF("Financials", "XLF"),
  XLY("Discretionary", "XLY"),
  XLP("Staples", "XLP"),
  XLU("Utilities", "XLU"),
  ;

  companion object {
    /**
     * Parse a sector symbol from string, returns null if invalid.
     */
    fun fromString(value: String?): SectorSymbol? {
      if (value == null) return null
      return entries.firstOrNull {
        it.name.equals(value, ignoreCase = true) ||
          it.etfSymbol.equals(value, ignoreCase = true)
      }
    }
  }
}
