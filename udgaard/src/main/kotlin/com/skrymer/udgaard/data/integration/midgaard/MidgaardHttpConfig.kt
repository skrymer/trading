package com.skrymer.udgaard.data.integration.midgaard

import org.springframework.boot.web.client.RestClientCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import java.time.Duration

@Configuration
class MidgaardHttpConfig {
  // Bounded timeouts so a Midgaard outage fails fast with SocketTimeoutException instead of
  // blocking on the JVM's default infinite socket reads. 30s read timeout — Midgaard doesn't
  // cache FX rates persistently and proxies to upstream providers (AlphaVantage etc.) on
  // cache miss; a cold historical-FX-rate fetch can legitimately take 10–25s.
  @Bean
  fun restClientTimeoutCustomizer(): RestClientCustomizer =
    RestClientCustomizer { builder ->
      builder.requestFactory(
        SimpleClientHttpRequestFactory().apply {
          setConnectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
          setReadTimeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS))
        },
      )
    }

  private companion object {
    private const val CONNECT_TIMEOUT_SECONDS = 5L
    private const val READ_TIMEOUT_SECONDS = 30L
  }
}
