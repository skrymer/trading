package com.skrymer.udgaard.portfolio.service

import com.skrymer.udgaard.portfolio.integration.options.OptionContract
import com.skrymer.udgaard.portfolio.integration.options.OptionsDataProvider
import com.skrymer.udgaard.portfolio.model.InstrumentType
import com.skrymer.udgaard.portfolio.model.OptionType
import com.skrymer.udgaard.portfolio.model.Position
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Service for fetching historical option prices and Greeks.
 * Uses OptionsDataClient interface to allow switching between providers.
 */
@Service
class OptionPriceService(
  private val optionsDataProvider: OptionsDataProvider,
) {
  /**
   * Get historical option prices for a date range.
   * Used for visualization - displays option premium movement over time.
   *
   * @param underlyingSymbol Underlying stock symbol (e.g., "SPY")
   * @param strike Strike price
   * @param expiration Expiration date
   * @param optionType CALL or PUT
   * @param startDate Start of date range
   * @param endDate End of date range
   * @return List of prices for each trading day in the range
   */
  fun getHistoricalOptionPrices(
    underlyingSymbol: String,
    strike: Double,
    expiration: LocalDate,
    optionType: OptionType,
    startDate: LocalDate,
    endDate: LocalDate,
  ): List<OptionPricePoint> {
    logger.info(
      "Fetching historical option prices for $underlyingSymbol $strike $optionType " +
        "exp=$expiration from $startDate to $endDate",
    )

    val prices = mutableListOf<OptionPricePoint>()
    var currentDate = startDate

    while (!currentDate.isAfter(endDate)) {
      // Format date in YYYY-MM-DD format (ISO 8601) for AlphaVantage API
      val dateString = currentDate.toString() // LocalDate.toString() returns YYYY-MM-DD format
      logger.debug("Fetching option data for $underlyingSymbol on $dateString")

      val contract =
        optionsDataProvider.findOptionContract(
          symbol = underlyingSymbol,
          strike = strike,
          expiration = expiration.toString(), // Also YYYY-MM-DD format
          optionType = optionType,
          date = dateString,
        )

      if (contract != null) {
        prices.add(
          OptionPricePoint(
            date = contract.date,
            price = contract.price,
          ),
        )
        logger.debug("Found price ${contract.price} for $dateString")
      } else {
        logger.debug("No data found for $dateString (weekend/holiday/no trading)")
      }

      currentDate = currentDate.plusDays(1)
    }

    logger.info("Fetched ${prices.size} price points for $underlyingSymbol $strike $optionType")
    return prices
  }

  /**
   * Get historical option price for a specific date.
   * Used for price verification and historical analysis.
   */
  fun getHistoricalOptionPrice(
    underlyingSymbol: String,
    strike: Double,
    expiration: LocalDate,
    optionType: OptionType,
    date: LocalDate,
  ): OptionContract? =
    optionsDataProvider.findOptionContract(
      symbol = underlyingSymbol,
      strike = strike,
      expiration = expiration.toString(),
      optionType = optionType,
      date = date.toString(),
    )

  /**
   * Get option price and Greeks for a position at a specific date.
   * Returns full contract details including Greeks.
   */
  fun getOptionDataForPosition(
    position: Position,
    date: LocalDate,
  ): OptionContract? {
    if (position.instrumentType != InstrumentType.OPTION) return null

    val underlying = position.underlyingSymbol ?: position.symbol

    return getHistoricalOptionPrice(
      underlyingSymbol = underlying,
      strike = position.strikePrice!!,
      expiration = position.expirationDate!!,
      optionType = position.optionType!!,
      date = date,
    )
  }

  companion object {
    private val logger: Logger = LoggerFactory.getLogger(OptionPriceService::class.java)
  }
}

/**
 * Simple DTO for option price at a specific date.
 * Used for chart visualization.
 */
data class OptionPricePoint(
  val date: LocalDate,
  val price: Double,
)
