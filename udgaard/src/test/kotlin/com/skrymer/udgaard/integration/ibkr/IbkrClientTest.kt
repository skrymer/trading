package com.skrymer.udgaard.integration.ibkr

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
internal class IbkrClientTest {
  private val logger = LoggerFactory.getLogger(IbkrClientTest::class.java)

  @Autowired
  lateinit var ibkrClient: IBKRClient

//  @Test
  fun `search contract`() {
    val result = ibkrClient.searchContract("pltr")
    logger.info("Search contract result: {}", result)
  }

//  @Test
  fun `get options chain`() {
    val contracts = ibkrClient.searchContract("pltr")
    val nasdaqPltr = contracts?.first { it.description == "NASDAQ" }
    val optSection = nasdaqPltr?.sections?.first { it.secType == "OPT" }
    val firstExpiryMonth = optSection?.months?.split(";")?.first()
    val result = ibkrClient.optionsChain(nasdaqPltr?.conid ?: "", SectionType.OPT, firstExpiryMonth ?: "", "")
    logger.info("Options chain result: {}", result)
  }
}
