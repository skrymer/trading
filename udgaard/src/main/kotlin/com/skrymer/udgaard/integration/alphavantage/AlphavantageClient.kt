package com.skrymer.udgaard.integration.alphavantage

import com.skrymer.udgaard.model.SimpleStock
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * Client for the alphavantage API.
 * API key WEG4U78HKED4LMRV
 */
@Component
class AlphavantageClient {

  /**
   * https://www.alphavantage.co/documentation/#stochrsi
   *  Would be interesting to test if closing when FastK crosses under the FastD is viable. Also RSI value (Similar to heatmap)
   */
  fun getStochasticRSI(){}

  /**
   * https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&symbol=SPY&apikey=WEG4U78HKED4LMRV
   */
  fun getStock(symbol: String = "SPY"): SimpleStock? {
    val url = "https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&symbol=$symbol&apikey=WEG4U78HKED4LMRV"
    val restClient: RestClient = RestClient.builder()
      .baseUrl(url)
      .build()

    return restClient
      .get()
      .retrieve()
      .toEntity(SimpleStock::class.java)
      .body
  }
}