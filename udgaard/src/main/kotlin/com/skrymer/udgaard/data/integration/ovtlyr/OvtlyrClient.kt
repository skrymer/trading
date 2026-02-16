package com.skrymer.udgaard.data.integration.ovtlyr

import com.skrymer.udgaard.data.integration.ovtlyr.dto.OvtlyrStockInformation
import com.skrymer.udgaard.data.integration.ovtlyr.dto.ScreenerResult
import com.skrymer.udgaard.data.integration.ovtlyr.dto.StockPerformance
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpResponse
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class OvtlyrClient(
  @Value("\${ovtlyr.header.projectId}") val projectIdHeader: String,
  @Value("\${ovtlyr.cookies.token:}") val cookieToken: String,
  @Value("\${ovtlyr.cookies.userid:}") val cookieUserId: String,
  @Value("\${ovtlyr.stockinformation.baseUrl}") val stockInformationBaseUrl: String,
  @Value("\${ovtlyr.marketbreadth.baseUrl}") val marketBreadthBaseUrl: String,
  @Value("\${ovtlyr.screener.baseUrl}") val screenerBaseUrl: String,
) {
  /**
   * @param symbol
   */
  fun getStockInformation(symbol: String): OvtlyrStockInformation? {
    return runCatching {
      val restClient: RestClient =
        RestClient
          .builder()
          .requestInterceptor { request, body, execution ->
            val response = execution.execute(request, body)
            logResponse(response)
            response
          }.baseUrl(stockInformationBaseUrl)
          .build()
      val requestBody = "{\"stockSymbol\":\"${symbol}\",\"period\":\"All\",\"page_index\":0,\"page_size\":20000}"

      val response =
        restClient
          .post()
          .header("ProjectId", projectIdHeader)
          .header("Accept", "application/json")
          .cookie("UserId", cookieUserId)
          .cookie("Token", cookieToken)
          .contentType(MediaType.APPLICATION_JSON)
          .body(requestBody)
          .retrieve()
          .toEntity(OvtlyrStockInformation::class.java)
          .getBody()

      return response
    }.onFailure { e ->
      logger.error("Exception occurred fetching stock: $symbol message: ${e.message} skipping", e)
    }.getOrNull()
  }

  /**
   * get ovtlyr stock performance
   */
  fun getStockPerformance(symbol: String): StockPerformance? {
    return runCatching {
      val restClient: RestClient =
        RestClient
          .builder()
          .baseUrl(marketBreadthBaseUrl)
          .build()

      val requestBody =
        "{\"page_size\":2000,\"page_index\":0,\"period\":\"All\",\"stockSymbol\":\"${symbol}\"}"

      return restClient
        .post()
        .cookie("UserId", cookieUserId)
        .cookie("Token", cookieToken)
        .contentType(MediaType.APPLICATION_JSON)
        .body(requestBody)
        .retrieve()
        .toEntity(StockPerformance::class.java)
        .getBody()
    }.onFailure { e ->
      logger.error("Exception occurred fetching market breadth: $symbol message: ${e.message} skipping", e)
    }.getOrNull()
  }

  fun getScreenerStocks(): ScreenerResult? {
    val restClient: RestClient =
      RestClient
        .builder()
        .baseUrl(screenerBaseUrl)
        .build()
    val requestBody =
      "{\"searchKeyword\":null,\"filter_sectorIds\":null,\"filter_industryNames\":null,\"page_size\":200,\"page_index\":0,\"filter_OvtlyrSignalReturn\":null,\"filter_OvtlyrCapitalEfficency\":null,\"filter_min30DayAvgVol\":null,\"filter_max30DayAvgVol\":null,\"filter_CurrentBuySellStatus\":null,\"filter_PriceCorrectionPeriod\":null,\"filter_PriceCorrectionValue\":null,\"filter_minMarkerCap\":null,\"filter_maxMarkerCap\":null,\"filter_minClosePrice\":null,\"filter_maxClosePrice\":null,\"filter_minHeatMap\":null,\"filter_maxHeatMap\":null,\"isShowUpTreandIndicator\":null,\"isShowDownTreandIndicator\":null,\"isShowNeutralIndicator\":null,\"sortBy\":null,\"sortOrder\":null,\"filterByOscilatorMovingUpDown\":null,\"selectedFilterId\":null}"

    return restClient
      .post()
      .cookie("UserId", cookieUserId)
      .cookie("Token", cookieToken)
      .contentType(MediaType.APPLICATION_JSON)
      .body(requestBody)
      .retrieve()
      .toEntity(ScreenerResult::class.java)
      .getBody()
  }

  fun logResponse(response: ClientHttpResponse) {
    // Intentionally empty - hook for subclasses to override for response logging
  }

  companion object {
    private val logger: Logger = LoggerFactory.getLogger("Ovtlyr client")
  }
}
