package com.skrymer.udgaard.integration.ibkr

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.skrymer.udgaard.integration.ibkr.dto.FlexQueryResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * HTTP client for Interactive Brokers Flex Query API
 */
@Component
class IBKRFlexQueryClient(
  @Value("\${ibkr.flex.base.url:https://ndcdyn.interactivebrokers.com/AccountManagement/FlexWebService}")
  private val baseUrl: String,
  private val restTemplate: RestTemplate = RestTemplate(),
  private val xmlMapper: XmlMapper = XmlMapper(),
) {
  companion object {
    private val logger: Logger = LoggerFactory.getLogger(IBKRFlexQueryClient::class.java)
    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd")
  }

  /**
   * Send Flex Query request to IBKR
   *
   * @param token - Flex Query token (6hr expiry)
   * @param queryId - Flex Query template ID
   * @param startDate - Start date for data
   * @param endDate - End date for data
   * @return Reference code for retrieving the statement
   */
  fun sendRequest(
    token: String,
    queryId: String,
    startDate: LocalDate? = null,
    endDate: LocalDate? = null,
  ): String {
    if (startDate != null && endDate != null) {
      logger.info("Sending Flex Query request: queryId=$queryId, dateRange=$startDate to $endDate")
    } else {
      logger.info("Sending Flex Query request: queryId=$queryId (using template defaults)")
    }

    val url = "$baseUrl/SendRequest"
    val params =
      buildMap {
        put("t", token)
        put("q", queryId)
        put("v", "3")
        // Only include date parameters if provided (otherwise use template defaults)
        if (startDate != null) {
          put("fd", startDate.format(DATE_FORMATTER))
        }
        if (endDate != null) {
          put("td", endDate.format(DATE_FORMATTER))
        }
      }

    val response =
      try {
        restTemplate.getForObject(url + buildQueryString(params), String::class.java)
          ?: throw IllegalStateException("Empty response from IBKR")
      } catch (e: Exception) {
        logger.error("Failed to send Flex Query request", e)
        throw IBKRApiException("Failed to send Flex Query request: ${e.message}", e)
      }

    logger.debug("SendRequest response XML:\n{}", response)

    // Extract <ReferenceCode> from XML response
    val referenceCode = extractReferenceCode(response)
    logger.info("Flex Query request sent successfully, referenceCode=$referenceCode")
    return referenceCode
  }

  /**
   * Get Flex Query statement using reference code
   * Polls the API until the statement is ready (handles "Statement generation in progress" warnings)
   *
   * @param token - Flex Query token
   * @param referenceCode - Reference code from sendRequest
   * @return XML statement
   */
  fun getStatement(
    token: String,
    referenceCode: String,
  ): String {
    logger.info("Getting Flex Query statement: referenceCode=$referenceCode")

    val url = "$baseUrl/GetStatement"
    val params =
      mapOf(
        "t" to token,
        "q" to referenceCode,
        "v" to "3",
      )

    // Poll for up to 60 seconds (IBKR typically takes 1-10 seconds to generate)
    val maxRetries = 12
    val retryDelayMs = 5000L // 5 seconds between retries

    repeat(maxRetries) { attempt ->
      val response =
        try {
          restTemplate.getForObject(url + buildQueryString(params), String::class.java)
            ?: throw IllegalStateException("Empty response from IBKR")
        } catch (e: Exception) {
          logger.error("Failed to get Flex Query statement", e)
          throw IBKRApiException("Failed to get Flex Query statement: ${e.message}", e)
        }

      // Check if statement is still being generated (Warn status with 1019)
      if (response.contains("<Status>Warn</Status>") && response.contains("<ErrorCode>1019</ErrorCode>")) {
        val errorMessage = extractXmlTag(response, "ErrorMessage") ?: "Statement generation in progress"
        logger.info("Attempt ${attempt + 1}/$maxRetries: $errorMessage. Retrying in ${retryDelayMs}ms...")

        if (attempt < maxRetries - 1) {
          Thread.sleep(retryDelayMs)
          return@repeat // Continue to next retry
        } else {
          throw IBKRApiException("Statement generation timed out after ${maxRetries * retryDelayMs / 1000} seconds")
        }
      }

      // Check for error 1003 (Statement is not available) - also needs retry
      if (response.contains("<ErrorCode>1003</ErrorCode>")) {
        val errorMessage = extractXmlTag(response, "ErrorMessage") ?: "Statement is not available"
        logger.info("Attempt ${attempt + 1}/$maxRetries: $errorMessage. Retrying in ${retryDelayMs}ms...")

        if (attempt < maxRetries - 1) {
          Thread.sleep(retryDelayMs)
          return@repeat // Continue to next retry
        } else {
          throw IBKRApiException("Statement not available after ${maxRetries * retryDelayMs / 1000} seconds")
        }
      }

      // Check for other errors (non-retryable)
      if (response.contains("<Status>Fail</Status>") || response.contains("<Status>fail</Status>")) {
        val errorCode = extractXmlTag(response, "ErrorCode") ?: "UNKNOWN"
        val errorMessage = extractXmlTag(response, "ErrorMessage") ?: "Unknown error from IBKR"
        throw IBKRApiException("IBKR API Error [$errorCode]: $errorMessage")
      }

      // Statement is ready
      logger.info("Flex Query statement retrieved successfully (attempt ${attempt + 1})")
      logger.debug("Raw XML response from IBKR:\n{}", response)
      return response
    }

    throw IBKRApiException("Unexpected error: exceeded max retries without returning")
  }

  /**
   * Parse XML response to FlexQueryResponse
   */
  fun parseXml(xml: String): FlexQueryResponse =
    try {
      logger.debug("Parsing XML (first 500 chars): {}", xml.take(500))
      xmlMapper.readValue(xml, FlexQueryResponse::class.java)
    } catch (e: Exception) {
      logger.error("Failed to parse Flex Query XML. XML content (first 1000 chars):\n{}", xml.take(1000))
      logger.error("Parse error details", e)
      throw IBKRApiException("Failed to parse Flex Query XML: ${e.message}", e)
    }

  /**
   * Extract reference code from SendRequest XML response
   * Checks for error responses first
   */
  private fun extractReferenceCode(xml: String): String {
    // Check if response contains an error
    if (xml.contains("<Status>Fail</Status>") || xml.contains("<Status>fail</Status>")) {
      val errorCode = extractXmlTag(xml, "ErrorCode") ?: "UNKNOWN"
      val errorMessage = extractXmlTag(xml, "ErrorMessage") ?: "Unknown error from IBKR"
      logger.error("IBKR SendRequest failed - ErrorCode: $errorCode, Message: $errorMessage")
      logger.error("Full response XML:\n{}", xml)
      throw IBKRApiException("IBKR API Error [$errorCode]: $errorMessage")
    }

    // Extract reference code
    val referenceCode = extractXmlTag(xml, "ReferenceCode")
    return referenceCode
      ?: throw IBKRApiException("Reference code not found in response. Response: ${xml.take(500)}")
  }

  /**
   * Extract value from XML tag
   */
  private fun extractXmlTag(
    xml: String,
    tagName: String,
  ): String? {
    val regex = "<$tagName>([^<]+)</$tagName>".toRegex()
    return regex.find(xml)?.groupValues?.get(1)
  }

  /**
   * Build query string from parameters
   */
  private fun buildQueryString(params: Map<String, String>): String =
    params.entries.joinToString("&", "?") { "${it.key}=${it.value}" }
}

/**
 * IBKR API exception
 */
class IBKRApiException(
  message: String,
  cause: Throwable? = null,
) : RuntimeException(message, cause)
