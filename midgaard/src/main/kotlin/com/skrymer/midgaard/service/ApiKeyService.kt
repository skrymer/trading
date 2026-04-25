package com.skrymer.midgaard.service

import com.skrymer.midgaard.repository.ProviderConfigRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class ApiKeyService(
    private val providerConfigRepository: ProviderConfigRepository,
    @param:Value("\${alphavantage.api.key:}") private val defaultAvKey: String,
    @param:Value("\${massive.api.key:}") private val defaultMassiveKey: String,
    @param:Value("\${finnhub.api.key:}") private val defaultFinnhubKey: String,
    @param:Value("\${eodhd.api.key:}") private val defaultEodhdKey: String,
) {
    private val logger = LoggerFactory.getLogger(ApiKeyService::class.java)

    fun getAlphaVantageApiKey(): String = providerConfigRepository.findByKey(KEY_ALPHAVANTAGE) ?: defaultAvKey

    fun getMassiveApiKey(): String = providerConfigRepository.findByKey(KEY_MASSIVE) ?: defaultMassiveKey

    fun getFinnhubApiKey(): String = providerConfigRepository.findByKey(KEY_FINNHUB) ?: defaultFinnhubKey

    fun getEodhdApiKey(): String = providerConfigRepository.findByKey(KEY_EODHD) ?: defaultEodhdKey

    fun saveApiKeys(
        alphaVantageApiKey: String?,
        massiveApiKey: String?,
        finnhubApiKey: String? = null,
        eodhdApiKey: String? = null,
    ) {
        alphaVantageApiKey?.let {
            providerConfigRepository.upsert(KEY_ALPHAVANTAGE, it)
            logger.info("AlphaVantage API key updated")
        }
        massiveApiKey?.let {
            providerConfigRepository.upsert(KEY_MASSIVE, it)
            logger.info("Massive API key updated")
        }
        finnhubApiKey?.let {
            providerConfigRepository.upsert(KEY_FINNHUB, it)
            logger.info("Finnhub API key updated")
        }
        eodhdApiKey?.let {
            providerConfigRepository.upsert(KEY_EODHD, it)
            logger.info("EODHD API key updated")
        }
    }

    fun getStatus(): Map<String, Boolean> =
        mapOf(
            "alphaVantageConfigured" to getAlphaVantageApiKey().isNotBlank(),
            "massiveConfigured" to getMassiveApiKey().isNotBlank(),
            "finnhubConfigured" to getFinnhubApiKey().isNotBlank(),
            "eodhdConfigured" to getEodhdApiKey().isNotBlank(),
        )

    fun getMaskedKeys(): Map<String, String> =
        mapOf(
            "alphaVantage" to maskKey(getAlphaVantageApiKey()),
            "massive" to maskKey(getMassiveApiKey()),
            "finnhub" to maskKey(getFinnhubApiKey()),
            "eodhd" to maskKey(getEodhdApiKey()),
        )

    companion object {
        private const val KEY_ALPHAVANTAGE = "alphavantage_api_key"
        private const val KEY_MASSIVE = "massive_api_key"
        private const val KEY_FINNHUB = "finnhub_api_key"
        private const val KEY_EODHD = "eodhd_api_key"

        fun maskKey(key: String): String =
            when {
                key.length > 4 -> "${"•".repeat(key.length - 4)}${key.takeLast(4)}"
                key.isNotEmpty() -> "•".repeat(key.length)
                else -> "Not configured"
            }
    }
}
