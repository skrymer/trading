package com.skrymer.udgaard.portfolio.integration.ibkr

import com.skrymer.udgaard.portfolio.integration.ibkr.dto.OptionsChain
import com.skrymer.udgaard.portfolio.integration.ibkr.dto.SearchResult
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

@Component
class IBKRClient {
  companion object {
    const val BASE_URL = "https://localhost:5000/v1/api/iserver"
  }

  /**
   *
   */
  fun searchContract(symbol: String): List<SearchResult>? =
    createRestClientBuilder()
      .baseUrl("$BASE_URL/secdef/search")
      .build()
      .get()
      .uri { it.queryParam("symbol", symbol).build() }
      .retrieve()
      .body(object : ParameterizedTypeReference<List<SearchResult>>() {})

  /**
   * @param conid - Contract Identifier number for the underlying
   * @param sectionType - Security type of the derivatives you are looking for.
   * @param month - Expiration month and year for the given underlying
   *  Value Format: {3 character month}{2 character year}
   *  Example: AUG23
   */
  fun optionsChain(
    conid: String,
    sectionType: SectionType,
    month: String,
    exchange: String,
  ): OptionsChain? =
    createRestClientBuilder()
      .baseUrl("$BASE_URL/secdef/strikes")
      .build()
      .get()
      .uri {
        it
          .queryParam("conid", conid)
          .queryParam("sectype", sectionType.name)
          .queryParam("month", month)
          .build()
      }.retrieve()
      .toEntity(OptionsChain::class.java)
      .getBody()

  /**
   * Workaround to ignore ssl certificate error
   */
  fun createRestClientBuilder(): RestClient.Builder {
    // Create a trust manager that trusts all certificates
    val trustAllCerts =
      arrayOf<TrustManager>(
        object : X509TrustManager {
          override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

          override fun checkClientTrusted(
            certs: Array<X509Certificate>,
            authType: String,
          ) {
            // Trust all
          }

          override fun checkServerTrusted(
            certs: Array<X509Certificate>,
            authType: String,
          ) {
            // Trust all
          }
        },
      )

    // Create SSL context that uses the trust-all manager
    val sslContext =
      SSLContext.getInstance("TLS").apply {
        init(null, trustAllCerts, java.security.SecureRandom())
      }

    // Configure Apache HttpClient with the custom SSL context
    val httpClient =
      HttpClients
        .custom()
        .setConnectionManager(
          PoolingHttpClientConnectionManagerBuilder
            .create()
            .setSSLSocketFactory(
              SSLConnectionSocketFactoryBuilder
                .create()
                .setSslContext(sslContext)
                .build(),
            ).build(),
        ).build()

    // Create a request factory with the configured HttpClient
    val requestFactory = HttpComponentsClientHttpRequestFactory(httpClient)

    // Build and return the RestClient
    return RestClient
      .builder()
      .requestFactory(requestFactory)
  }
}

enum class SectionType {
  OPT,
  WAR,
}
