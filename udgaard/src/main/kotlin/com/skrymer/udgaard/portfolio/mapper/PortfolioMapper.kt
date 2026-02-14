package com.skrymer.udgaard.portfolio.mapper

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.skrymer.udgaard.jooq.tables.pojos.Portfolios
import com.skrymer.udgaard.portfolio.integration.broker.BrokerType
import com.skrymer.udgaard.portfolio.model.Portfolio
import org.springframework.stereotype.Component

/**
 * Mapper between jOOQ Portfolio POJOs and domain models
 */
@Component
class PortfolioMapper {
  private val objectMapper = jacksonObjectMapper()

  /**
   * Convert jOOQ Portfolio POJO to domain model
   */
  fun toDomain(portfolio: Portfolios): Portfolio {
    val brokerConfig = parseBrokerConfig(portfolio.brokerConfig)
    return Portfolio(
      id = portfolio.id,
      userId = portfolio.userId,
      name = portfolio.name ?: "",
      initialBalance = portfolio.initialBalance?.toDouble() ?: 0.0,
      currentBalance = portfolio.currentBalance?.toDouble() ?: 0.0,
      currency = portfolio.currency ?: "USD",
      createdDate = portfolio.createdAt ?: java.time.LocalDateTime.now(),
      lastUpdated = portfolio.updatedAt ?: java.time.LocalDateTime.now(),
      broker = portfolio.broker?.let { BrokerType.valueOf(it) } ?: BrokerType.MANUAL,
      brokerAccountId = brokerConfig["accountId"], // Stored in brokerConfig JSON
      brokerConfig = brokerConfig,
      lastSyncDate = portfolio.lastSyncDate,
    )
  }

  /**
   * Convert domain model to jOOQ Portfolio POJO
   */
  fun toPojo(portfolio: Portfolio): Portfolios {
    // Merge brokerAccountId into brokerConfig
    val configWithAccountId =
      if (portfolio.brokerAccountId != null) {
        portfolio.brokerConfig + ("accountId" to portfolio.brokerAccountId)
      } else {
        portfolio.brokerConfig
      }

    return Portfolios(
      name = portfolio.name,
      initialBalance = portfolio.initialBalance.toBigDecimal(),
      currentBalance = portfolio.currentBalance.toBigDecimal(),
      currency = portfolio.currency,
      userId = portfolio.userId,
      createdAt = portfolio.createdDate,
      updatedAt = portfolio.lastUpdated,
      broker = portfolio.broker.name,
      brokerConfig = serializeBrokerConfig(configWithAccountId),
      lastSyncDate = portfolio.lastSyncDate,
    ).apply {
      id = portfolio.id
    }
  }

  /**
   * Parse broker config from JSON string
   */
  private fun parseBrokerConfig(json: String?): Map<String, String> =
    if (json.isNullOrBlank()) {
      emptyMap()
    } else {
      try {
        objectMapper.readValue(json)
      } catch (e: Exception) {
        emptyMap()
      }
    }

  /**
   * Serialize broker config to JSON string
   */
  private fun serializeBrokerConfig(config: Map<String, String>): String? =
    if (config.isEmpty()) {
      null
    } else {
      try {
        objectMapper.writeValueAsString(config)
      } catch (e: Exception) {
        null
      }
    }
}
