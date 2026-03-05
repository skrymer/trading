package com.skrymer.midgaard.integration.alphavantage

import com.skrymer.midgaard.integration.CompanyInfoProvider
import com.skrymer.midgaard.integration.EarningsProvider
import com.skrymer.midgaard.integration.IndicatorProvider
import com.skrymer.midgaard.integration.OhlcvProvider
import com.skrymer.midgaard.integration.OptionsProvider
import com.skrymer.midgaard.integration.alphavantage.dto.AlphaVantageADX
import com.skrymer.midgaard.integration.alphavantage.dto.AlphaVantageATR
import com.skrymer.midgaard.integration.alphavantage.dto.AlphaVantageApiResponse
import com.skrymer.midgaard.integration.alphavantage.dto.AlphaVantageCompanyOverview
import com.skrymer.midgaard.integration.alphavantage.dto.AlphaVantageCurrencyExchangeRate
import com.skrymer.midgaard.integration.alphavantage.dto.AlphaVantageEarnings
import com.skrymer.midgaard.integration.alphavantage.dto.AlphaVantageFxDaily
import com.skrymer.midgaard.integration.alphavantage.dto.AlphaVantageHistoricalOptions
import com.skrymer.midgaard.integration.alphavantage.dto.AlphaVantageTimeSeriesDailyAdjusted
import com.skrymer.midgaard.model.CompanyInfo
import com.skrymer.midgaard.model.Earning
import com.skrymer.midgaard.model.OptionContractDto
import com.skrymer.midgaard.model.RawBar
import com.skrymer.midgaard.service.ApiKeyService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Duration
import java.time.LocalDate

@Component
class AlphaVantageProvider(
    private val apiKeyService: ApiKeyService,
    @Value("\${alphavantage.api.baseUrl}") private val baseUrl: String,
) : OhlcvProvider,
    IndicatorProvider,
    EarningsProvider,
    CompanyInfoProvider,
    OptionsProvider {
    private val apiKey: String get() = apiKeyService.getAlphaVantageApiKey()

    private val restClient: RestClient =
        RestClient
            .builder()
            .baseUrl(baseUrl)
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(Duration.ofSeconds(5))
                    setReadTimeout(Duration.ofSeconds(30))
                },
            ).build()

    override suspend fun getDailyBars(
        symbol: String,
        outputSize: String,
        minDate: LocalDate,
    ): List<RawBar>? =
        withContext(Dispatchers.IO) {
            runCatching {
                val response =
                    restClient
                        .get()
                        .uri { uriBuilder ->
                            uriBuilder
                                .queryParam("function", FUNCTION_DAILY_ADJUSTED)
                                .queryParam("symbol", symbol)
                                .queryParam("outputsize", outputSize)
                                .queryParam("apikey", apiKey)
                                .build()
                        }.retrieve()
                        .toEntity(AlphaVantageTimeSeriesDailyAdjusted::class.java)
                        .body
                validateAndTransform(response, symbol, "daily adjusted") { it.toRawBars(minDate) }
            }.onFailure { e ->
                logger.error("Failed to fetch daily adjusted from AlphaVantage for $symbol: ${e.message}", e)
            }.getOrNull()
        }

    override suspend fun getATR(
        symbol: String,
        minDate: LocalDate,
    ): Map<LocalDate, Double>? =
        withContext(Dispatchers.IO) {
            runCatching {
                val response =
                    restClient
                        .get()
                        .uri { uriBuilder ->
                            uriBuilder
                                .queryParam("function", FUNCTION_ATR)
                                .queryParam("symbol", symbol)
                                .queryParam("interval", "daily")
                                .queryParam("time_period", 14)
                                .queryParam("apikey", apiKey)
                                .build()
                        }.retrieve()
                        .toEntity(AlphaVantageATR::class.java)
                        .body
                validateAndTransform(response, symbol, "ATR") { it.toATRMap(minDate) }
            }.onFailure { e ->
                logger.error("Failed to fetch ATR from AlphaVantage for $symbol: ${e.message}", e)
            }.getOrNull()
        }

    override suspend fun getADX(
        symbol: String,
        minDate: LocalDate,
    ): Map<LocalDate, Double>? =
        withContext(Dispatchers.IO) {
            runCatching {
                val response =
                    restClient
                        .get()
                        .uri { uriBuilder ->
                            uriBuilder
                                .queryParam("function", FUNCTION_ADX)
                                .queryParam("symbol", symbol)
                                .queryParam("interval", "daily")
                                .queryParam("time_period", 14)
                                .queryParam("apikey", apiKey)
                                .build()
                        }.retrieve()
                        .toEntity(AlphaVantageADX::class.java)
                        .body
                validateAndTransform(response, symbol, "ADX") { it.toADXMap(minDate) }
            }.onFailure { e ->
                logger.error("Failed to fetch ADX from AlphaVantage for $symbol: ${e.message}", e)
            }.getOrNull()
        }

    override suspend fun getEarnings(symbol: String): List<Earning>? =
        withContext(Dispatchers.IO) {
            runCatching {
                val response =
                    restClient
                        .get()
                        .uri { uriBuilder ->
                            uriBuilder
                                .queryParam("function", FUNCTION_EARNINGS)
                                .queryParam("symbol", symbol)
                                .queryParam("apikey", apiKey)
                                .build()
                        }.retrieve()
                        .toEntity(AlphaVantageEarnings::class.java)
                        .body
                validateAndTransform(response, symbol, "earnings") { it.toEarnings() }
            }.onFailure { e ->
                logger.error("Failed to fetch earnings from AlphaVantage for $symbol: ${e.message}", e)
            }.getOrNull()
        }

    override suspend fun getCompanyInfo(symbol: String): CompanyInfo? =
        withContext(Dispatchers.IO) {
            runCatching {
                val response =
                    restClient
                        .get()
                        .uri { uriBuilder ->
                            uriBuilder
                                .queryParam("function", FUNCTION_OVERVIEW)
                                .queryParam("symbol", symbol)
                                .queryParam("apikey", apiKey)
                                .build()
                        }.retrieve()
                        .toEntity(AlphaVantageCompanyOverview::class.java)
                        .body
                validateAndTransform(response, symbol, "overview") {
                    CompanyInfo(sector = it.toSector(), marketCap = it.toMarketCap())
                }
            }.onFailure { e ->
                logger.error("Failed to fetch company overview from AlphaVantage for $symbol: ${e.message}", e)
            }.getOrNull()
        }

    override fun getHistoricalOptions(
        symbol: String,
        date: String?,
    ): List<OptionContractDto>? =
        runCatching {
            val response =
                restClient
                    .get()
                    .uri { uriBuilder ->
                        val builder =
                            uriBuilder
                                .queryParam("function", FUNCTION_HISTORICAL_OPTIONS)
                                .queryParam("symbol", symbol)
                                .queryParam("apikey", apiKey)
                        if (date != null) builder.queryParam("date", date)
                        builder.build()
                    }.retrieve()
                    .toEntity(AlphaVantageHistoricalOptions::class.java)
                    .body
            validateAndTransform(response, symbol, "options") { it.toOptionContracts() }
        }.onFailure { e ->
            logger.error("Failed to fetch historical options for $symbol: ${e.message}", e)
        }.getOrNull()

    override fun findOptionContract(
        symbol: String,
        strike: Double,
        expiration: String,
        optionType: String,
        date: String,
    ): OptionContractDto? {
        val contracts = getHistoricalOptions(symbol, date) ?: return null
        return contracts.firstOrNull { contract ->
            contract.strike == strike &&
                contract.expiration.toString() == expiration &&
                contract.optionType.equals(optionType, ignoreCase = true)
        }
    }

    override fun getRealtimeOptions(symbol: String): List<OptionContractDto>? =
        runCatching {
            val response =
                restClient
                    .get()
                    .uri { uriBuilder ->
                        uriBuilder
                            .queryParam("function", FUNCTION_REALTIME_OPTIONS)
                            .queryParam("symbol", symbol)
                            .queryParam("require_greeks", "true")
                            .queryParam("apikey", apiKey)
                            .build()
                    }.retrieve()
                    .toEntity(AlphaVantageHistoricalOptions::class.java)
                    .body
            validateAndTransform(response, symbol, "realtime options") { it.toOptionContracts() }
        }.onFailure { e ->
            logger.error("Failed to fetch realtime options for $symbol: ${e.message}", e)
        }.getOrNull()

    override fun findRealtimeOptionContract(
        symbol: String,
        strike: Double,
        expiration: String,
        optionType: String,
    ): OptionContractDto? {
        val contracts = getRealtimeOptions(symbol) ?: return null
        return contracts.firstOrNull { contract ->
            contract.strike == strike &&
                contract.expiration.toString() == expiration &&
                contract.optionType.equals(optionType, ignoreCase = true)
        }
    }

    fun getExchangeRate(
        fromCurrency: String,
        toCurrency: String,
    ): Double? =
        runCatching {
            val response =
                restClient
                    .get()
                    .uri { uriBuilder ->
                        uriBuilder
                            .queryParam("function", FUNCTION_CURRENCY_EXCHANGE_RATE)
                            .queryParam("from_currency", fromCurrency)
                            .queryParam("to_currency", toCurrency)
                            .queryParam("apikey", apiKey)
                            .build()
                    }.retrieve()
                    .toEntity(AlphaVantageCurrencyExchangeRate::class.java)
                    .body
            validateAndTransform(response, "$fromCurrency/$toCurrency", "exchange rate") { it.toRate() }
        }.onFailure { e ->
            logger.error("Failed to fetch exchange rate $fromCurrency/$toCurrency: ${e.message}", e)
        }.getOrNull()

    fun getHistoricalExchangeRate(
        fromCurrency: String,
        toCurrency: String,
        date: LocalDate,
    ): Double? =
        runCatching {
            val response =
                restClient
                    .get()
                    .uri { uriBuilder ->
                        uriBuilder
                            .queryParam("function", FUNCTION_FX_DAILY)
                            .queryParam("from_symbol", fromCurrency)
                            .queryParam("to_symbol", toCurrency)
                            .queryParam("outputsize", "full")
                            .queryParam("apikey", apiKey)
                            .build()
                    }.retrieve()
                    .toEntity(AlphaVantageFxDaily::class.java)
                    .body
            validateAndTransform(response, "$fromCurrency/$toCurrency", "FX daily") { it.closestRateForDate(date) }
        }.onFailure { e ->
            logger.error("Failed to fetch historical FX rate $fromCurrency/$toCurrency for $date: ${e.message}", e)
        }.getOrNull()

    private fun <T : AlphaVantageApiResponse, R> validateAndTransform(
        response: T?,
        symbol: String,
        label: String,
        transform: (T) -> R,
    ): R? =
        when {
            response == null -> null.also { logger.warn("No response from AlphaVantage $label for $symbol") }
            response.hasError() -> null.also { logger.error("AlphaVantage $label error for $symbol: ${response.getErrorDescription()}") }
            !response.isValid() -> null.also { logger.error("AlphaVantage returned invalid $label for $symbol") }
            else -> transform(response)
        }

    companion object {
        private val logger = LoggerFactory.getLogger(AlphaVantageProvider::class.java)
        private const val FUNCTION_DAILY_ADJUSTED = "TIME_SERIES_DAILY_ADJUSTED"
        private const val FUNCTION_ATR = "ATR"
        private const val FUNCTION_ADX = "ADX"
        private const val FUNCTION_EARNINGS = "EARNINGS"
        private const val FUNCTION_OVERVIEW = "OVERVIEW"
        private const val FUNCTION_HISTORICAL_OPTIONS = "HISTORICAL_OPTIONS"
        private const val FUNCTION_REALTIME_OPTIONS = "REALTIME_OPTIONS"
        private const val FUNCTION_CURRENCY_EXCHANGE_RATE = "CURRENCY_EXCHANGE_RATE"
        private const val FUNCTION_FX_DAILY = "FX_DAILY"
    }
}
