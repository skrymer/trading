package com.skrymer.udgaard.util

/**
 * Maps leveraged and inverse ETFs to their underlying assets.
 * This is used when trading a leveraged ETF but want to use the underlying asset's
 * signals for entry/exit strategies.
 *
 * Example: Trade TQQQ (3x leveraged QQQ) but use QQQ's signals for strategy evaluation.
 */
object AssetMapper {

    /**
     * Map of leveraged/inverse ETF symbols to their underlying asset symbols
     */
    private val leveragedToUnderlying = mapOf(
        // Nasdaq QQQ
        "TQQQ" to "QQQ",  // 3x Bull
        "SQQQ" to "QQQ",  // 3x Bear
        "QLD" to "QQQ",   // 2x Bull
        "QID" to "QQQ",   // 2x Bear

        // S&P 500 SPY
        "UPRO" to "SPY",  // 3x Bull
        "SPXU" to "SPY",  // 3x Bear
        "SSO" to "SPY",   // 2x Bull
        "SDS" to "SPY",   // 2x Bear

        // Semiconductors
        "SOXL" to "SOXX", // 3x Bull
        "SOXS" to "SOXX", // 3x Bear

        // Russell 2000
        "TNA" to "IWM",   // 3x Bull
        "TZA" to "IWM",   // 3x Bear
        "UWM" to "IWM",   // 2x Bull
        "TWM" to "IWM",   // 2x Bear

        // Dow Jones
        "UDOW" to "DIA",  // 3x Bull
        "SDOW" to "DIA",  // 3x Bear

        // Financials
        "FAS" to "XLF",   // 3x Bull
        "FAZ" to "XLF",   // 3x Bear

        // Energy
        "ERX" to "XLE",   // 3x Bull
        "ERY" to "XLE",   // 3x Bear

        // Technology
        "TECL" to "XLK",  // 3x Bull
        "TECS" to "XLK",  // 3x Bear

        // Biotech
        "LABU" to "XBI",  // 3x Bull
        "LABD" to "XBI",  // 3x Bear

        // Gold
        "NUGT" to "GDX",  // 3x Bull
        "DUST" to "GDX",  // 3x Bear

        // Oil
        "GUSH" to "XOP",  // 3x Bull
        "DRIP" to "XOP",  // 3x Bear

        // Emerging Markets
        "EDC" to "EEM",   // 3x Bull
        "EDZ" to "EEM"    // 3x Bear
    )

    /**
     * Get the underlying symbol for a given symbol.
     * If the symbol is a leveraged ETF, returns the underlying asset.
     * Otherwise, returns the symbol itself.
     *
     * @param symbol The symbol to look up (e.g., "TQQQ")
     * @return The underlying symbol (e.g., "QQQ") or the original symbol if not mapped
     */
    fun getUnderlyingSymbol(symbol: String): String {
        return leveragedToUnderlying[symbol.uppercase()] ?: symbol.uppercase()
    }

    /**
     * Check if a symbol is a leveraged or inverse ETF.
     *
     * @param symbol The symbol to check
     * @return true if the symbol is in the leveraged ETF mapping
     */
    fun isLeveragedETF(symbol: String): Boolean {
        return leveragedToUnderlying.containsKey(symbol.uppercase())
    }

    /**
     * Get the leverage factor (positive for bull, negative for bear).
     * Returns null if not a leveraged ETF.
     *
     * @param symbol The symbol to check
     * @return Leverage factor (e.g., 3.0, -3.0, 2.0, -2.0) or null
     */
    fun getLeverageFactor(symbol: String): Double? {
        return when (symbol.uppercase()) {
            // 3x Bull
            "TQQQ", "UPRO", "SOXL", "TNA", "UDOW", "FAS", "ERX", "TECL", "LABU", "NUGT", "GUSH", "EDC" -> 3.0
            // 3x Bear
            "SQQQ", "SPXU", "SOXS", "TZA", "SDOW", "FAZ", "ERY", "TECS", "LABD", "DUST", "DRIP", "EDZ" -> -3.0
            // 2x Bull
            "QLD", "SSO", "UWM" -> 2.0
            // 2x Bear
            "QID", "SDS", "TWM" -> -2.0
            else -> null
        }
    }

    /**
     * Get all available mappings.
     *
     * @return Map of leveraged ETF symbols to their underlying assets
     */
    fun getAllMappings(): Map<String, String> {
        return leveragedToUnderlying.toMap()
    }
}
