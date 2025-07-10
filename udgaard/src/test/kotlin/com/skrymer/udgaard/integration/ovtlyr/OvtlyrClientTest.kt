package com.skrymer.udgaard.integration.ovtlyr

import com.skrymer.udgaard.model.MarketSymbol
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
internal class OvtlyrClientTest {

  @Autowired
  lateinit var ovtlyrClient: OvtlyrClient

  @Test
  fun `GET stock information`(){
    val spyStockInformation = ovtlyrClient.getStockInformation("SPY")
    Assertions.assertEquals(spyStockInformation?.resultDetail, "Success")
  }

  @Test
  fun `GET market breadth`() {
    val fullstockBreadth = ovtlyrClient.getMarketBreadth(MarketSymbol.FULLSTOCK.name)
    Assertions.assertEquals(fullstockBreadth?.resultDetail,  "Success")
  }

  @Test
  fun `GET screener values`() {
    val screenerResult = ovtlyrClient.getScreenerStocks()
    Assertions.assertEquals(screenerResult?.resultDetail, "Success")
  }
}