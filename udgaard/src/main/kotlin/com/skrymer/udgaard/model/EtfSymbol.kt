package com.skrymer.udgaard.model

/**
 * Enum representing ETF symbols supported by the system.
 * This is the new type-safe enum for ETF symbols, replacing the older Etf enum.
 *
 * Includes both standard index ETFs and leveraged/inverse ETFs.
 */
enum class EtfSymbol(
  val description: String,
) {
  SPY("SPDR S&P 500 ETF Trust"),
  QQQ("Invesco QQQ Trust"),
  IWM("iShares Russell 2000 ETF"),
  DIA("SPDR Dow Jones Industrial Average ETF"),

  // Leveraged ETFs
  TQQQ("ProShares UltraPro QQQ (3x Leveraged)"),
  SQQQ("ProShares UltraPro Short QQQ (-3x Inverse)"),
  UPRO("ProShares UltraPro S&P500 (3x Leveraged)"),
  SPXU("ProShares UltraPro Short S&P500 (-3x Inverse)"),
  ;

  companion object {
    /**
     * Find an EtfSymbol by its string representation (case-insensitive).
     * @param value The string to convert to EtfSymbol
     * @return The matching EtfSymbol or null if not found
     */
    fun fromString(value: String?): EtfSymbol? {
      if (value == null) return null
      return entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }

    /**
     * Get all standard (non-leveraged) ETF symbols.
     * @return List of standard ETF symbols
     */
    fun getStandardEtfs(): List<EtfSymbol> = listOf(SPY, QQQ, IWM, DIA)

    /**
     * Get all leveraged ETF symbols.
     * @return List of leveraged ETF symbols
     */
    fun getLeveragedEtfs(): List<EtfSymbol> = listOf(TQQQ, SQQQ, UPRO, SPXU)
  }

  /**
   * Check if this is a leveraged ETF (2x, 3x, or inverse).
   * @return true if leveraged, false otherwise
   */
  fun isLeveraged(): Boolean = this in listOf(TQQQ, SQQQ, UPRO, SPXU)

  /**
   * Check if this is an inverse ETF (negative multiplier).
   * @return true if inverse, false otherwise
   */
  fun isInverse(): Boolean = this in listOf(SQQQ, SPXU)

  /**
   * Get the leverage multiplier for this ETF.
   * @return The multiplier (1.0 for standard, 3.0 for 3x, -3.0 for -3x)
   */
  fun getLeverageMultiplier(): Double =
    when (this) {
      TQQQ, UPRO -> 3.0
      SQQQ, SPXU -> -3.0
      else -> 1.0
    }

  /**
   * Get the underlying ETF that this leveraged ETF tracks.
   * @return The underlying ETF symbol or null if not leveraged
   */
  fun getUnderlyingEtf(): EtfSymbol? =
    when (this) {
      TQQQ, SQQQ -> QQQ
      UPRO, SPXU -> SPY
      else -> null
    }
}
