package com.skrymer.udgaard.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.skrymer.udgaard.controller.dto.ApiCredentialsDto
import com.skrymer.udgaard.controller.dto.PositionSizingSettingsDto
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SettingsService(
  private val repository: UserSettingsJooqRepository,
) {
  private val logger = LoggerFactory.getLogger(SettingsService::class.java)
  private val mapper = jacksonObjectMapper()

  fun getCredentials(): ApiCredentialsDto {
    val json = repository.findByKey(KEY_CREDENTIALS) ?: return ApiCredentialsDto()
    return mapper.readValue<ApiCredentialsDto>(json)
  }

  fun saveCredentials(credentials: ApiCredentialsDto) {
    repository.upsert(KEY_CREDENTIALS, mapper.writeValueAsString(credentials))
    logger.info("Credentials saved to database")
  }

  fun getPositionSizingSettings(): PositionSizingSettingsDto {
    val json = repository.findByKey(KEY_POSITION_SIZING) ?: return PositionSizingSettingsDto()
    return mapper.readValue<PositionSizingSettingsDto>(json)
  }

  fun savePositionSizingSettings(settings: PositionSizingSettingsDto) {
    repository.upsert(KEY_POSITION_SIZING, mapper.writeValueAsString(settings))
    logger.info("Position sizing settings saved to database")
  }

  fun getCredentialsStatus(): Map<String, Boolean> {
    val credentials = getCredentials()
    return mapOf(
      "ibkrConfigured" to (
        credentials.ibkrAccountId.isNotBlank() &&
          credentials.ibkrFlexQueryId.isNotBlank() &&
          credentials.ibkrFlexQueryToken.isNotBlank()
      ),
    )
  }

  companion object {
    private const val KEY_CREDENTIALS = "api_credentials"
    private const val KEY_POSITION_SIZING = "position_sizing"
  }
}
