package com.skrymer.udgaard.integration.alphavantage

import org.springframework.stereotype.Component

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
}