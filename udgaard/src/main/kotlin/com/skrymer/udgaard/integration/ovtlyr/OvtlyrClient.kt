package com.skrymer.udgaard.integration.ovtlyr

import com.skrymer.udgaard.integration.ovtlyr.dto.OvtlyrMarketBreadth
import com.skrymer.udgaard.integration.ovtlyr.dto.OvtlyrStockInformation
import com.skrymer.udgaard.integration.ovtlyr.dto.ScreenerResult
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class OvtlyrClient(
  @Value("\${ovtlyr.header.projectId}") val projectIdHeader: String,
  @Value("\${ovtlyr.cookies.token}") val cookieToken: String,
  @Value("\${ovtlyr.cookies.userid}") val cookieUserId: String,
  @Value("\${ovtlyr.stockinformation.token}") val stockinformationToken: String,
  @Value("\${ovtlyr.stockinformation.userid}") val stockinformationUserId: String,
  @Value("\${ovtlyr.stockinformation.baseUrl}") val stockInformationBaseUrl: String,
  @Value("\${ovtlyr.marketbreadth.baseUrl}") val marketBreadthBaseUrl: String,
  @Value("\${ovtlyr.screener.baseUrl}") val screenerBaseUrl: String,
) {

  /**
   * @param symbol
   */
  fun getStockInformation(symbol: String): OvtlyrStockInformation? {
    try {
      val restClient: RestClient = RestClient.builder()
        .baseUrl(stockInformationBaseUrl)
        .build()
      val requestBody = "{\"stockSymbol\":\"${symbol}\",\"period\":\"All\",\"page_index\":0,\"page_size\":20000}"

      println("Fetching stock information for $symbol")
      println("baseUrl: $stockInformationBaseUrl")
      println("User id $stockinformationUserId")
      println("Token: $stockinformationToken")
      println("requestBody: $requestBody")
      val response = restClient.post()
        .header("UserId", stockinformationUserId)
        .header("Token", stockinformationToken)
        .header("ProjectId", projectIdHeader)
        .contentType(MediaType.APPLICATION_JSON)
        .body(requestBody)
        .retrieve()
        .toEntity(OvtlyrStockInformation::class.java)
        .getBody()
      println("Response received: $response")
      return response
    } catch (e: Exception) {
      println("Exception occurred fetching stock: $symbol message: ${e.message} skipping")
      return null
    }
  }

  /**
   *
   * @param symbol - symbol of the market
   * @return
   */
  fun getMarketBreadth(symbol: String): OvtlyrMarketBreadth? {
    val restClient: RestClient = RestClient.builder()
      .baseUrl(marketBreadthBaseUrl)
      .build()
    val requestBody =
      "{\"page_size\":2000,\"page_index\":0,\"period\":\"All\",\"stockSymbol\":\"${symbol}\"}"

    println("Getting market breadth for $symbol")
    println("baseUrl: $marketBreadthBaseUrl")
    println("User id $cookieUserId")
    println("Token: $cookieToken")
    println("requestBody: $requestBody")

    return restClient.post()
      .cookie("UserId", cookieUserId)
      .cookie(
        "Token",
        cookieToken
      )
      .contentType(MediaType.APPLICATION_JSON)
      .body(requestBody)
      .retrieve()
      .toEntity(OvtlyrMarketBreadth::class.java)
      .getBody()
  }

  fun getScreenerStocks(): ScreenerResult? {
    val restClient: RestClient = RestClient.builder()
      .baseUrl(screenerBaseUrl)
      .build()
    val requestBody =
      "{\"searchKeyword\":null,\"filter_sectorIds\":null,\"filter_industryNames\":null,\"page_size\":200,\"page_index\":0,\"filter_OvtlyrSignalReturn\":null,\"filter_OvtlyrCapitalEfficency\":null,\"filter_min30DayAvgVol\":null,\"filter_max30DayAvgVol\":null,\"filter_CurrentBuySellStatus\":null,\"filter_PriceCorrectionPeriod\":null,\"filter_PriceCorrectionValue\":null,\"filter_minMarkerCap\":null,\"filter_maxMarkerCap\":null,\"filter_minClosePrice\":null,\"filter_maxClosePrice\":null,\"filter_minHeatMap\":null,\"filter_maxHeatMap\":null,\"isShowUpTreandIndicator\":null,\"isShowDownTreandIndicator\":null,\"isShowNeutralIndicator\":null,\"sortBy\":null,\"sortOrder\":null,\"filterByOscilatorMovingUpDown\":null,\"selectedFilterId\":null}"

    println("Getting screener details")
    println("baseUrl: $screenerBaseUrl")
    println("User id $cookieUserId")
    println("Token: $cookieToken")
    println("requestBody: $requestBody")

    return restClient.post()
      .cookie("UserId", cookieUserId)
      .cookie(
        "Token",
        cookieToken
      )
      .contentType(MediaType.APPLICATION_JSON)
      .body(requestBody)
      .retrieve()
      .toEntity(ScreenerResult::class.java)
      .getBody()
  }
}

