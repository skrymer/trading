package com.skrymer.udgaard.portfolio.integration.options

import com.skrymer.udgaard.portfolio.model.OptionType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import java.time.LocalDate

/**
 * Options data provider that delegates to Midgaard's options API.
 * Midgaard in turn fetches from AlphaVantage.
 */
@Service
class MidgaardOptionsProvider(
  @param:Value("\${midgaard.base-url:http://localhost:8081}") private val baseUrl: String,
) : OptionsDataProvider {
  private val restClient: RestClient by lazy {
    RestClient
      .builder()
      .baseUrl(baseUrl)
      .build()
  }

  override fun getHistoricalOptions(
    symbol: String,
    date: String?,
  ): List<OptionContract>? = runCatching {
    val response = restClient
      .get()
      .uri { uriBuilder ->
        val builder = uriBuilder.path("/api/options/{symbol}")
        if (date != null) {
          builder.queryParam("date", date)
        }
        builder.build(symbol)
      }.retrieve()
      .body(object : ParameterizedTypeReference<List<MidgaardOptionContractDto>>() {})

    response?.map { it.toOptionContract() }
  }.onFailure { e ->
    if (e is HttpClientErrorException.NotFound) {
      logger.debug("No options data available for $symbol")
    } else {
      logger.error("Failed to fetch options from Midgaard for $symbol: ${e.message}", e)
    }
  }.getOrNull()

  override fun findOptionContract(
    symbol: String,
    strike: Double,
    expiration: String,
    optionType: OptionType,
    date: String,
  ): OptionContract? = runCatching {
    restClient
      .get()
      .uri { uriBuilder ->
        uriBuilder
          .path("/api/options/{symbol}/find")
          .queryParam("strike", strike)
          .queryParam("expiration", expiration)
          .queryParam("optionType", optionType.name.lowercase())
          .queryParam("date", date)
          .build(symbol)
      }.retrieve()
      .body(MidgaardOptionContractDto::class.java)
      ?.toOptionContract()
  }.onFailure { e ->
    if (e is HttpClientErrorException.NotFound) {
      logger.debug("No option contract found for $symbol")
    } else {
      logger.error("Failed to find option contract from Midgaard for $symbol: ${e.message}", e)
    }
  }.getOrNull()

  override fun getRealtimeOptions(symbol: String): List<OptionContract>? = runCatching {
    val response = restClient
      .get()
      .uri { uriBuilder ->
        uriBuilder.path("/api/options/{symbol}/realtime").build(symbol)
      }.retrieve()
      .body(object : ParameterizedTypeReference<List<MidgaardOptionContractDto>>() {})

    response?.map { it.toOptionContract() }
  }.onFailure { e ->
    if (e is HttpClientErrorException.NotFound) {
      logger.debug("No realtime options data available for $symbol")
    } else {
      logger.error("Failed to fetch realtime options from Midgaard for $symbol: ${e.message}", e)
    }
  }.getOrNull()

  override fun findRealtimeOptionContract(
    symbol: String,
    strike: Double,
    expiration: String,
    optionType: OptionType,
  ): OptionContract? = runCatching {
    restClient
      .get()
      .uri { uriBuilder ->
        uriBuilder
          .path("/api/options/{symbol}/realtime/find")
          .queryParam("strike", strike)
          .queryParam("expiration", expiration)
          .queryParam("optionType", optionType.name.lowercase())
          .build(symbol)
      }.retrieve()
      .body(MidgaardOptionContractDto::class.java)
      ?.toOptionContract()
  }.onFailure { e ->
    if (e is HttpClientErrorException.NotFound) {
      logger.debug("No realtime option contract found for $symbol")
    } else {
      logger.error("Failed to find realtime option contract from Midgaard for $symbol: ${e.message}", e)
    }
  }.getOrNull()

  companion object {
    private val logger = LoggerFactory.getLogger(MidgaardOptionsProvider::class.java)
  }
}

/**
 * DTO matching Midgaard's OptionContractDto response.
 */
private data class MidgaardOptionContractDto(
  val contractId: String,
  val symbol: String,
  val strike: Double,
  val expiration: LocalDate,
  val optionType: String,
  val date: LocalDate,
  val price: Double,
  val impliedVolatility: Double? = null,
  val delta: Double? = null,
  val gamma: Double? = null,
  val theta: Double? = null,
  val vega: Double? = null,
  val openInterest: Int? = null,
) {
  fun toOptionContract(): OptionContract =
    OptionContract(
      contractId = contractId,
      symbol = symbol,
      strike = strike,
      expiration = expiration,
      optionType = parseOptionType(optionType),
      date = date,
      price = price,
      impliedVolatility = impliedVolatility,
      delta = delta,
      gamma = gamma,
      theta = theta,
      vega = vega,
      openInterest = openInterest,
    )

  private fun parseOptionType(type: String): OptionType =
    when (type.lowercase()) {
      "call" -> OptionType.CALL
      "put" -> OptionType.PUT
      else -> OptionType.CALL
    }
}
