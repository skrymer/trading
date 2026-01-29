package com.skrymer.udgaard.integration.ibkr

import com.skrymer.udgaard.domain.OptionTypeDomain
import com.skrymer.udgaard.integration.broker.*
import com.skrymer.udgaard.integration.ibkr.dto.IBKRTrade
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Mapper for converting IBKR Trade elements to standardized format
 */
@Component
class IBKRTradeMapper {
  companion object {
    private val logger: Logger = LoggerFactory.getLogger(IBKRTradeMapper::class.java)
  }

  /**
   * Convert IBKR Trade to StandardizedTrade
   */
  fun toStandardizedTrade(ibkrTrade: IBKRTrade): StandardizedTrade =
    StandardizedTrade(
      brokerTradeId = ibkrTrade.tradeID,
      symbol = ibkrTrade.symbol,
      tradeDate = parseDate(ibkrTrade.tradeDate),
      tradeTime = ibkrTrade.tradeTime?.let { parseTime(it) },
      quantity = Math.abs(parseIntSafe(ibkrTrade.quantity)), // Make quantity positive
      price = parseDoubleSafe(ibkrTrade.tradePrice),
      direction = parseDirection(ibkrTrade.buySell),
      openClose = parseOpenClose(ibkrTrade.openCloseIndicator?.takeIf { it.isNotBlank() } ?: "O"),
      assetType = parseAssetType(ibkrTrade.assetCategory),
      optionDetails =
        if (ibkrTrade.assetCategory == "OPT") {
          parseOptionDetails(ibkrTrade)
        } else {
          null
        },
      linkedTradeId = ibkrTrade.origTradeID,
      relatedOrderId = ibkrTrade.ibOrderID,
      commission = ibkrTrade.ibCommission?.let { parseDoubleSafe(it) },
      netAmount = parseDoubleSafe(ibkrTrade.netCash),
      currency = ibkrTrade.currency ?: "USD",
    )

  /**
   * Parse date string (format: yyyyMMdd or yyyy-MM-dd)
   */
  private fun parseDate(dateStr: String): LocalDate =
    try {
      when {
        dateStr.contains("-") -> LocalDate.parse(dateStr)
        dateStr.length == 8 -> LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd"))
        else -> throw IllegalArgumentException("Unknown date format: $dateStr")
      }
    } catch (e: Exception) {
      logger.error("Failed to parse date: $dateStr", e)
      throw IllegalArgumentException("Invalid date format: $dateStr", e)
    }

  /**
   * Parse integer safely
   */
  private fun parseIntSafe(str: String): Int =
    try {
      str.toInt()
    } catch (e: Exception) {
      logger.error("Failed to parse int: $str", e)
      throw IllegalArgumentException("Invalid integer: $str", e)
    }

  /**
   * Parse double safely
   */
  private fun parseDoubleSafe(str: String): Double =
    try {
      str.toDouble()
    } catch (e: Exception) {
      logger.error("Failed to parse double: $str", e)
      throw IllegalArgumentException("Invalid double: $str", e)
    }

  /**
   * Parse time string (format: HH:mm:ss or HHmmss)
   */
  private fun parseTime(timeStr: String): LocalTime? =
    try {
      when {
        timeStr.contains(":") -> LocalTime.parse(timeStr)
        timeStr.length == 6 -> LocalTime.parse("${timeStr.substring(0, 2)}:${timeStr.substring(2, 4)}:${timeStr.substring(4, 6)}")
        else -> null
      }
    } catch (e: Exception) {
      logger.warn("Failed to parse time: $timeStr", e)
      null
    }

  /**
   * Parse trade direction
   */
  private fun parseDirection(buySell: String): TradeDirection =
    when (buySell.uppercase()) {
      "BUY" -> TradeDirection.BUY
      "SELL" -> TradeDirection.SELL
      else -> throw IllegalArgumentException("Unknown direction: $buySell")
    }

  /**
   * Parse open/close indicator
   */
  private fun parseOpenClose(indicator: String): OpenCloseIndicator =
    when (indicator.uppercase()) {
      "OPEN", "O" -> OpenCloseIndicator.OPEN
      "CLOSE", "C" -> OpenCloseIndicator.CLOSE
      else -> throw IllegalArgumentException("Unknown open/close indicator: $indicator")
    }

  /**
   * Parse asset type
   */
  private fun parseAssetType(category: String): AssetType =
    when (category.uppercase()) {
      "STK" -> AssetType.STOCK
      "OPT" -> AssetType.OPTION
      "ETF" -> AssetType.ETF
      else -> {
        logger.warn("Unknown asset category: $category, defaulting to STOCK")
        AssetType.STOCK
      }
    }

  /**
   * Parse option details
   */
  private fun parseOptionDetails(ibkrTrade: IBKRTrade): OptionDetails {
    requireNotNull(ibkrTrade.putCall) { "Option type (PUT/CALL) is required for options" }
    requireNotNull(ibkrTrade.strike) { "Strike price is required for options" }
    requireNotNull(ibkrTrade.expiry) { "Expiry date is required for options" }

    return OptionDetails(
      underlyingSymbol = ibkrTrade.underlyingSymbol ?: ibkrTrade.symbol,
      optionType =
        when (ibkrTrade.putCall.uppercase()) {
          "PUT", "P" -> OptionTypeDomain.PUT
          "CALL", "C" -> OptionTypeDomain.CALL
          else -> throw IllegalArgumentException("Unknown option type: ${ibkrTrade.putCall}")
        },
      strike = parseDoubleSafe(ibkrTrade.strike),
      expiry = parseDate(ibkrTrade.expiry),
      multiplier = ibkrTrade.multiplier?.let { parseIntSafe(it) } ?: 100,
    )
  }
}
