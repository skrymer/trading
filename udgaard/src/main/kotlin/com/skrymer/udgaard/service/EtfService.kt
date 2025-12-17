package com.skrymer.udgaard.service

import com.skrymer.udgaard.domain.EtfDomain
import com.skrymer.udgaard.domain.EtfHoldingDomain
import com.skrymer.udgaard.domain.EtfMetadataDomain
import com.skrymer.udgaard.domain.EtfQuoteDomain
import com.skrymer.udgaard.integration.EtfProvider
import com.skrymer.udgaard.model.EtfSymbol
import com.skrymer.udgaard.repository.jooq.EtfJooqRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Service for managing ETF entities.
 * Provides CRUD operations and business logic for ETF data.
 */
@Service
class EtfService(
  private val etfRepository: EtfJooqRepository,
  private val stockService: StockService,
  private val etfProvider: EtfProvider,
) {
  private val logger = LoggerFactory.getLogger(EtfService::class.java)

  /**
   * Get an ETF by symbol.
   * @param symbol The ETF symbol (e.g., "SPY", "QQQ")
   * @return The ETF domain model or null if not found
   */
  fun getEtf(symbol: String): EtfDomain? = etfRepository.findBySymbol(symbol.uppercase())

  /**
   * Get an ETF by EtfSymbol enum.
   * @param symbol The ETF symbol enum
   * @return The ETF domain model or null if not found
   */
  fun getEtf(symbol: EtfSymbol): EtfDomain? = getEtf(symbol.name)

  /**
   * Get all ETFs.
   * @return List of all ETF domain models
   */
  fun getAllEtfs(): List<EtfDomain> = etfRepository.findAll()

  /**
   * Save or update an ETF domain model.
   * @param etf The ETF domain model to save
   * @return The saved ETF domain model
   */
  fun saveEtf(etf: EtfDomain): EtfDomain {
    require(etf.symbol.isNotBlank()) { "ETF symbol cannot be blank" }
    val upperSymbol = etf.symbol.uppercase()
    logger.info("Saving ETF: $upperSymbol")
    return etfRepository.save(etf.copy(symbol = upperSymbol))
  }

  /**
   * Check if an ETF exists.
   * @param symbol The ETF symbol
   * @return true if exists, false otherwise
   */
  fun etfExists(symbol: String): Boolean = etfRepository.exists(symbol.uppercase())

  /**
   * Refresh ETF data from Ovtlyr and AlphaVantage.
   * Fetches quote data and technical indicators, then saves to database.
   * @param symbol The ETF symbol
   * @return The refreshed ETF domain model or null if fetch fails
   */
  fun refreshEtf(symbol: String): EtfDomain? {
    logger.info("Refreshing ETF data for: $symbol")

    // Fetch the stock as Stock (Ovtlyr doesn't distinguish between stocks and ETFs)
    // Use StockService which already handles all the conversion logic
    val stock = stockService.getStock(symbol, forceFetch = true)
    if (stock == null) {
      logger.warn("Could not fetch ETF data from Ovtlyr for $symbol")
      return null
    }

    // Get or create ETF domain
    val existingEtf = getEtf(symbol)

    // Convert StockQuote domain objects to EtfQuote domain objects
    val etfQuotes =
      stock.quotes.map { stockQuote ->
        EtfQuoteDomain(
          date = stockQuote.date,
          openPrice = stockQuote.openPrice,
          closePrice = stockQuote.closePrice,
          high = stockQuote.high,
          low = stockQuote.low,
          volume = stockQuote.volume,
          closePriceEMA5 = stockQuote.closePriceEMA5,
          closePriceEMA10 = stockQuote.closePriceEMA10,
          closePriceEMA20 = stockQuote.closePriceEMA20,
          closePriceEMA50 = stockQuote.closePriceEMA50,
          atr = stockQuote.atr,
          lastBuySignal = stockQuote.lastBuySignal,
          lastSellSignal = stockQuote.lastSellSignal,
        )
      }

    logger.info("Converted ${etfQuotes.size} StockQuote objects to EtfQuote format for $symbol")

    // Fetch ETF profile from EtfProvider for additional metadata and holdings
    var metadata = existingEtf?.metadata
    var holdings = existingEtf?.holdings ?: emptyList()

    val profile = etfProvider.getEtfProfile(symbol)
    if (profile != null) {
      logger.info("Fetched ETF profile from AlphaVantage for $symbol")

      // Map profile to metadata
      metadata =
        EtfMetadataDomain(
          expenseRatio = profile.getExpenseRatioAsDouble(),
          aum = profile.getNetAssetsAsDouble(),
          inceptionDate = profile.getInceptionDateAsLocalDate(),
          type = if (profile.isLeveraged()) "Leveraged" else null,
        )

      // Map holdings from profile
      if (!profile.holdings.isNullOrEmpty()) {
        val currentDate = LocalDate.now()
        holdings =
          profile.holdings.map { holding ->
            EtfHoldingDomain(
              stockSymbol = holding.symbol,
              weight = holding.getWeightAsDouble(),
              asOfDate = currentDate,
            )
          }
        logger.info("Mapped ${holdings.size} holdings from AlphaVantage profile for $symbol")
      }
    } else {
      logger.warn("Could not fetch ETF profile from AlphaVantage for $symbol - metadata and holdings not updated")
    }

    // Create ETF domain model
    val etf =
      EtfDomain(
        symbol = symbol.uppercase(),
        name = EtfSymbol.fromString(symbol)?.description ?: symbol,
        description = "ETF tracking ${stock.sectorSymbol ?: "market"} index",
        metadata = metadata,
        quotes = etfQuotes,
        holdings = holdings,
      )

    // Save and return
    logger.info("Saving refreshed ETF data for $symbol with ${etf.quotes.size} quotes")
    return saveEtf(etf)
  }

  /**
   * Create a new ETF domain model with basic information.
   * @param symbol The ETF symbol
   * @param name The ETF name
   * @param description Optional description
   * @return The created ETF domain model
   */
  fun createEtf(
    symbol: String,
    name: String,
    description: String = "",
  ): EtfDomain {
    val existingEtf = getEtf(symbol)
    if (existingEtf != null) {
      logger.warn("ETF $symbol already exists, returning existing domain model")
      return existingEtf
    }

    val etf =
      EtfDomain(
        symbol = symbol.uppercase(),
        name = name,
        description = description,
        metadata = null,
        quotes = emptyList(),
        holdings = emptyList(),
      )

    logger.info("Creating new ETF: $symbol")
    return saveEtf(etf)
  }
}
