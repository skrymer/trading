package com.skrymer.udgaard.integration.alphavantage

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
internal class AlphavantageClientTest {

  @Autowired
  lateinit var alphaVantageClient: AlphaVantageClient

  @Test
  fun `test get daily time series for stock`() {
    val quotes = alphaVantageClient.getDailyTimeSeriesCompact("SPY")

    Assertions.assertNotNull(quotes)
    Assertions.assertTrue(quotes!!.isNotEmpty(), "Should return quotes for SPY")
  }
}