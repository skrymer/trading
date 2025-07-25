package com.skrymer.udgaard.controller

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class UdgaardControllerTest {

  @Autowired
  private lateinit var mockMvc: MockMvc

  @Test
  fun `get backtest report for tsla`() {
    mockMvc.perform(get("/api/report")
      .param("stock", "TSLA")
      .contentType(MediaType.APPLICATION_JSON))
      .andExpect(status().isOk)
  }
}