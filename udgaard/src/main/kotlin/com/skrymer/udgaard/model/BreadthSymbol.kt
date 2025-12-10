package com.skrymer.udgaard.model

/**
 * Sealed class representing either market breadth (FULLSTOCK) or sector breadth (individual sectors).
 * This provides type-safe distinction between the two concepts.
 */
sealed class BreadthSymbol {
  /**
   * Market breadth for all stocks combined.
   */
  data class Market(
    val symbol: MarketSymbol = MarketSymbol.FULLSTOCK,
  ) : BreadthSymbol() {
    override fun toIdentifier(): String = symbol.name

    override fun toDescription(): String = symbol.description
  }

  /**
   * Sector breadth for a specific market sector.
   */
  data class Sector(
    val symbol: SectorSymbol,
  ) : BreadthSymbol() {
    override fun toIdentifier(): String = symbol.name

    override fun toDescription(): String = symbol.description
  }

  /**
   * Get the string identifier for this breadth symbol (used as database ID).
   */
  abstract fun toIdentifier(): String

  /**
   * Get the human-readable description for this breadth symbol.
   */
  abstract fun toDescription(): String

  companion object {
    /**
     * Parse a BreadthSymbol from a string identifier.
     * Returns null if the identifier doesn't match any market or sector.
     */
    fun fromString(value: String?): BreadthSymbol? {
      if (value == null) return null

      // Try to parse as market symbol first
      MarketSymbol.fromString(value)?.let { return Market(it) }

      // Try to parse as sector symbol
      SectorSymbol.fromString(value)?.let { return Sector(it) }

      return null
    }

    /**
     * Get all available breadth symbols (market + all sectors).
     */
    fun all(): List<BreadthSymbol> = listOf(Market()) + SectorSymbol.entries.map { Sector(it) }

    /**
     * Get all sector breadth symbols only.
     */
    fun allSectors(): List<BreadthSymbol.Sector> = SectorSymbol.entries.map { Sector(it) }
  }
}
