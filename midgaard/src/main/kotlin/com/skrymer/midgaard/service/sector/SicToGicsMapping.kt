package com.skrymer.midgaard.service.sector

/**
 * Maps SEC SIC codes to Midgaard's existing sector taxonomy (the AlphaVantage
 * sector names already used by `SectorMapping` and the `symbols.sector` column).
 *
 * Used for delisted-ticker ingestion: EODHD's fundamentals often return
 * `"Highlights": "NA"` for delisted issuers, so we fall back to SEC EDGAR's
 * `submissions/CIK##########.json` endpoint, which preserves SIC codes
 * indefinitely. Mapping table is frozen historical data — SIC codes don't
 * change once assigned.
 *
 * Coverage strategy: rules ordered by specificity, first match wins. Specific
 * sub-ranges (pharmaceuticals 2830-2839, semiconductors 3674) come before
 * the broader major-group ranges (chemicals 2800-2899, electronic equipment
 * 3600-3699). Unknown codes return null so callers can fall back to a default
 * (the ingest path uses "INDUSTRIALS").
 *
 * Sources: SEC SIC division headers + S&P GICS Methodology, mapped to the
 * 11 sector taxonomy already in use by `SectorMapping`.
 */
object SicToGicsMapping {
    private const val TECHNOLOGY = "TECHNOLOGY"
    private const val FINANCIAL_SERVICES = "FINANCIAL SERVICES"
    private const val HEALTHCARE = "HEALTHCARE"
    private const val ENERGY = "ENERGY"
    private const val INDUSTRIALS = "INDUSTRIALS"
    private const val CONSUMER_CYCLICAL = "CONSUMER CYCLICAL"
    private const val CONSUMER_DEFENSIVE = "CONSUMER DEFENSIVE"
    private const val COMMUNICATION_SERVICES = "COMMUNICATION SERVICES"
    private const val BASIC_MATERIALS = "BASIC MATERIALS"
    private const val REAL_ESTATE = "REAL ESTATE"
    private const val UTILITIES = "UTILITIES"

    // Order matters: more-specific ranges must come before broader ones.
    private val RULES: List<Pair<IntRange, String>> =
        listOf(
            // Specific overrides first
            2830..2839 to HEALTHCARE, // Pharmaceuticals
            3570..3579 to TECHNOLOGY, // Computer and office equipment
            3670..3679 to TECHNOLOGY, // Electronic components (incl. 3674 Semiconductors)
            3690..3699 to TECHNOLOGY, // Misc electrical machinery
            3711..3711 to CONSUMER_CYCLICAL, // Motor vehicles
            3712..3713 to CONSUMER_CYCLICAL, // Truck and bus bodies
            3714..3714 to CONSUMER_CYCLICAL, // Motor vehicle parts
            3815..3829 to HEALTHCARE, // Lab/medical instruments
            3841..3851 to HEALTHCARE, // Surgical/medical instruments
            5912..5912 to HEALTHCARE, // Drug stores
            6021..6022 to FINANCIAL_SERVICES, // National/state commercial banks
            7370..7379 to TECHNOLOGY, // Computer services & software
            7372..7372 to TECHNOLOGY, // Prepackaged software (kept for explicit lookup)
            // Major groups
            100..999 to BASIC_MATERIALS, // Agriculture, forestry, fishing
            1000..1299 to BASIC_MATERIALS, // Metal mining
            1300..1399 to ENERGY, // Oil and gas extraction
            1400..1499 to BASIC_MATERIALS, // Nonmetallic minerals
            1500..1799 to INDUSTRIALS, // Construction
            2000..2099 to CONSUMER_DEFENSIVE, // Food and kindred products
            2100..2199 to CONSUMER_DEFENSIVE, // Tobacco
            2200..2299 to CONSUMER_CYCLICAL, // Textile mill products
            2300..2399 to CONSUMER_CYCLICAL, // Apparel
            2400..2499 to BASIC_MATERIALS, // Lumber and wood
            2500..2599 to CONSUMER_CYCLICAL, // Furniture and fixtures
            2600..2699 to BASIC_MATERIALS, // Paper and allied products
            2700..2799 to COMMUNICATION_SERVICES, // Printing and publishing
            2800..2899 to BASIC_MATERIALS, // Chemicals (excluding drugs handled above)
            2900..2999 to ENERGY, // Petroleum refining
            3000..3099 to BASIC_MATERIALS, // Rubber and plastics
            3100..3199 to CONSUMER_CYCLICAL, // Leather
            3200..3299 to BASIC_MATERIALS, // Stone, clay, glass
            3300..3399 to BASIC_MATERIALS, // Primary metals
            3400..3499 to INDUSTRIALS, // Fabricated metals
            3500..3599 to INDUSTRIALS, // Industrial machinery (excludes computers handled above)
            3600..3699 to INDUSTRIALS, // Electrical equipment (excludes electronics handled above)
            3700..3799 to INDUSTRIALS, // Transportation equipment (excludes motor vehicles handled above)
            3800..3899 to INDUSTRIALS, // Instruments (excludes medical handled above)
            3900..3999 to CONSUMER_CYCLICAL, // Misc manufacturing
            4000..4499 to INDUSTRIALS, // Transportation
            4500..4599 to INDUSTRIALS, // Air transport
            4600..4799 to INDUSTRIALS, // Pipelines, transportation services
            4800..4829 to COMMUNICATION_SERVICES, // Telecommunications
            4830..4849 to COMMUNICATION_SERVICES, // Radio and TV broadcasting
            4850..4899 to COMMUNICATION_SERVICES, // Cable and other pay TV
            4900..4999 to UTILITIES, // Electric, gas, water, sanitary services
            5000..5199 to INDUSTRIALS, // Wholesale trade
            5200..5299 to CONSUMER_CYCLICAL, // Building materials retail
            5300..5399 to CONSUMER_CYCLICAL, // General merchandise stores
            5400..5499 to CONSUMER_DEFENSIVE, // Food stores (groceries)
            5500..5599 to CONSUMER_CYCLICAL, // Auto dealers
            5600..5699 to CONSUMER_CYCLICAL, // Apparel and accessory stores
            5700..5799 to CONSUMER_CYCLICAL, // Furniture/home furnishing stores
            5800..5899 to CONSUMER_CYCLICAL, // Eating and drinking places
            5900..5999 to CONSUMER_CYCLICAL, // Misc retail (excluding drug stores handled above)
            6000..6099 to FINANCIAL_SERVICES, // Depository institutions
            6100..6199 to FINANCIAL_SERVICES, // Non-depository credit
            6200..6299 to FINANCIAL_SERVICES, // Securities, brokers, dealers
            6300..6499 to FINANCIAL_SERVICES, // Insurance
            6500..6599 to REAL_ESTATE, // Real estate
            6700..6798 to FINANCIAL_SERVICES, // Holding/investment offices
            6798..6798 to REAL_ESTATE, // REITs
            6799..6799 to FINANCIAL_SERVICES, // Investors NEC
            7000..7099 to CONSUMER_CYCLICAL, // Hotels and lodging
            7200..7299 to CONSUMER_CYCLICAL, // Personal services
            7300..7369 to INDUSTRIALS, // Business services (excludes computer services handled above)
            7380..7399 to INDUSTRIALS, // Misc business services
            7500..7599 to CONSUMER_CYCLICAL, // Auto repair, services, parking
            7600..7699 to CONSUMER_CYCLICAL, // Misc repair services
            7700..7799 to CONSUMER_CYCLICAL,
            7800..7899 to COMMUNICATION_SERVICES, // Motion pictures
            7900..7999 to COMMUNICATION_SERVICES, // Amusement and recreation
            8000..8099 to HEALTHCARE, // Health services
            8200..8299 to CONSUMER_CYCLICAL, // Educational services
            8300..8399 to HEALTHCARE, // Social services
            8400..8499 to CONSUMER_CYCLICAL, // Museums, art galleries, gardens
            8600..8699 to CONSUMER_CYCLICAL, // Membership organizations
            8700..8799 to INDUSTRIALS, // Engineering, accounting, research
            8800..8899 to CONSUMER_CYCLICAL, // Private households
            8900..8999 to INDUSTRIALS, // Misc services
            9100..9999 to INDUSTRIALS, // Public administration / nonclassifiable
        )

    fun gicsSectorFor(sicCode: Int): String? = RULES.firstOrNull { sicCode in it.first }?.second
}
