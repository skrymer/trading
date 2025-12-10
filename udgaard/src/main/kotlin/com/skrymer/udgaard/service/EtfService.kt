package com.skrymer.udgaard.service

import com.skrymer.udgaard.integration.EtfProvider
import com.skrymer.udgaard.model.EtfEntity
import com.skrymer.udgaard.model.EtfHolding
import com.skrymer.udgaard.model.EtfMetadata
import com.skrymer.udgaard.model.EtfQuote
import com.skrymer.udgaard.model.EtfSymbol
import com.skrymer.udgaard.repository.EtfRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Service for managing ETF entities.
 * Provides CRUD operations and business logic for ETF data.
 */
@Service
class EtfService(
    private val etfRepository: EtfRepository,
    private val stockService: StockService,
    private val etfProvider: EtfProvider
) {
    private val logger = LoggerFactory.getLogger(EtfService::class.java)

    /**
     * Get an ETF by symbol.
     * @param symbol The ETF symbol (e.g., "SPY", "QQQ")
     * @return The ETF entity or null if not found
     */
    fun getEtf(symbol: String): EtfEntity? {
        return etfRepository.findBySymbol(symbol.uppercase())
    }

    /**
     * Get an ETF by EtfSymbol enum.
     * @param symbol The ETF symbol enum
     * @return The ETF entity or null if not found
     */
    fun getEtf(symbol: EtfSymbol): EtfEntity? {
        return getEtf(symbol.name)
    }

    /**
     * Get all ETFs.
     * @return List of all ETF entities
     */
    fun getAllEtfs(): List<EtfEntity> {
        return etfRepository.findAll()
    }

    /**
     * Save or update an ETF entity.
     * @param etf The ETF entity to save
     * @return The saved ETF entity
     */
    fun saveEtf(etf: EtfEntity): EtfEntity {
        require(!etf.symbol.isNullOrBlank()) { "ETF symbol cannot be null or blank" }
        etf.symbol = etf.symbol?.uppercase()
        logger.info("Saving ETF: ${etf.symbol}")
        return etfRepository.save(etf)
    }

    /**
     * Check if an ETF exists.
     * @param symbol The ETF symbol
     * @return true if exists, false otherwise
     */
    fun etfExists(symbol: String): Boolean {
        return etfRepository.existsBySymbol(symbol.uppercase())
    }

    /**
     * Refresh ETF data from Ovtlyr and AlphaVantage.
     * Fetches quote data and technical indicators, then saves to database.
     * @param symbol The ETF symbol
     * @return The refreshed ETF entity or null if fetch fails
     */
    fun refreshEtf(symbol: String): EtfEntity? {
        logger.info("Refreshing ETF data for: $symbol")

        // Fetch the stock as Stock (Ovtlyr doesn't distinguish between stocks and ETFs)
        // Use StockService which already handles all the conversion logic
        val stock = stockService.getStock(symbol, forceFetch = true)
        if (stock == null) {
            logger.warn("Could not fetch ETF data from Ovtlyr for $symbol")
            return null
        }

        // Get or create ETF entity
        val etf = getEtf(symbol) ?: createEtf(
            symbol = symbol,
            name = EtfSymbol.fromString(symbol)?.description ?: symbol,
            description = "ETF tracking ${stock.sectorSymbol ?: "market"} index"
        )

        // Convert StockQuote objects to EtfQuote objects
        val etfQuotes = stock.quotes.map { stockQuote ->
            EtfQuote(
                date = stockQuote.date ?: LocalDate.now(),
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
                lastSellSignal = stockQuote.lastSellSignal
            )
        }

        logger.info("Converted ${etfQuotes.size} StockQuote objects to EtfQuote format for $symbol")

        // Clear existing quotes and add new ones (triggers orphan removal)
        etf.quotes.clear()
        etf.quotes.addAll(etfQuotes)

        // Fetch ETF profile from EtfProvider for additional metadata and holdings
        val profile = etfProvider.getEtfProfile(symbol)
        if (profile != null) {
            logger.info("Fetched ETF profile from AlphaVantage for $symbol")

            // Map profile to metadata
            etf.metadata = EtfMetadata(
                expenseRatio = profile.getExpenseRatioAsDouble(),
                aum = profile.getNetAssetsAsDouble(),
                inceptionDate = profile.getInceptionDateAsLocalDate(),
                type = if (profile.isLeveraged()) "Leveraged" else null
            )

            // Map holdings from profile
            if (!profile.holdings.isNullOrEmpty()) {
                val currentDate = LocalDate.now()
                val newHoldings = profile.holdings.map { holding ->
                    EtfHolding(
                        stockSymbol = holding.symbol,
                        weight = holding.getWeightAsDouble(),
                        asOfDate = currentDate
                    )
                }

                // Clear existing holdings and add new ones (triggers orphan removal)
                etf.holdings.clear()
                etf.holdings.addAll(newHoldings)
                logger.info("Mapped ${etf.holdings.size} holdings from AlphaVantage profile for $symbol")
            }
        } else {
            logger.warn("Could not fetch ETF profile from AlphaVantage for $symbol - metadata and holdings not updated")
        }

        // Save and return
        logger.info("Saving refreshed ETF data for $symbol with ${etf.quotes.size} quotes")
        return saveEtf(etf)
    }

    /**
     * Create a new ETF entity with basic information.
     * @param symbol The ETF symbol
     * @param name The ETF name
     * @param description Optional description
     * @return The created ETF entity
     */
    fun createEtf(symbol: String, name: String, description: String? = null): EtfEntity {
        val existingEtf = getEtf(symbol)
        if (existingEtf != null) {
            logger.warn("ETF $symbol already exists, returning existing entity")
            return existingEtf
        }

        val etf = EtfEntity().apply {
            this.symbol = symbol.uppercase()
            this.name = name
            this.description = description
        }

        logger.info("Creating new ETF: $symbol")
        return saveEtf(etf)
    }
}
