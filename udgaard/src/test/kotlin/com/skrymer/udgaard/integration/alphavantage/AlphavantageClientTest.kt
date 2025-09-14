package com.skrymer.udgaard.integration.alphavantage

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
internal class AlphavantageClientTest {

  @Autowired
  lateinit var alphavantageClient: AlphavantageClient

  @Test
  fun `test get simple stock`() {
    val stock = alphavantageClient.getStock("SPY")

    Assertions.assertNotNull(stock)
  }
}