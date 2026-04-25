package com.skrymer.midgaard.integration.eodhd

import com.skrymer.midgaard.integration.CompanyInfoProvider
import com.skrymer.midgaard.integration.EarningsProvider
import com.skrymer.midgaard.integration.IndicatorProvider
import com.skrymer.midgaard.integration.OhlcvProvider
import com.skrymer.midgaard.integration.eodhd.dto.EodhdAdxResponse
import com.skrymer.midgaard.integration.eodhd.dto.EodhdAdxRowDto
import com.skrymer.midgaard.integration.eodhd.dto.EodhdApiResponse
import com.skrymer.midgaard.integration.eodhd.dto.EodhdAtrResponse
import com.skrymer.midgaard.integration.eodhd.dto.EodhdAtrRowDto
import com.skrymer.midgaard.integration.eodhd.dto.EodhdBarDto
import com.skrymer.midgaard.integration.eodhd.dto.EodhdEodResponse
import com.skrymer.midgaard.integration.eodhd.dto.EodhdFundamentalsResponse
import com.skrymer.midgaard.model.CompanyInfo
import com.skrymer.midgaard.model.Earning
import com.skrymer.midgaard.model.RawBar
import com.skrymer.midgaard.service.ApiKeyService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Duration
import java.time.LocalDate

@Component
class EodhdProvider(
    private val apiKeyService: ApiKeyService,
    @param:Value("\${eodhd.api.baseUrl}") private val baseUrl: String,
) : OhlcvProvider,
    IndicatorProvider,
    EarningsProvider,
    CompanyInfoProvider {
    private val apiKey: String get() = apiKeyService.getEodhdApiKey()

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
                val bars =
                    restClient
                        .get()
                        .uri { uriBuilder ->
                            uriBuilder
                                .path(PATH_EOD)
                                .queryParam(QUERY_API_TOKEN, apiKey)
                                .queryParam(QUERY_FMT, FMT_JSON)
                                .queryParam(QUERY_FROM, minDate.toString())
                                .build(symbol.toEodhdSymbol())
                        }.retrieve()
                        .body(BAR_LIST_TYPE)
                val response = EodhdEodResponse(bars = bars ?: emptyList())
                validateAndTransform(response, symbol, "EOD") { it.toRawBars(symbol, minDate) }
            }.onFailure { e -> logSafe("EOD bars", symbol, e) }.getOrNull()
        }

    override suspend fun getATR(
        symbol: String,
        minDate: LocalDate,
    ): Map<LocalDate, Double>? =
        withContext(Dispatchers.IO) {
            runCatching {
                val rows = fetchTechnical(symbol, FUNCTION_ATR, minDate, ATR_LIST_TYPE)
                val response = EodhdAtrResponse(rows = rows ?: emptyList())
                validateAndTransform(response, symbol, "ATR") { it.toAtrMap(minDate) }
            }.onFailure { e -> logSafe("ATR", symbol, e) }.getOrNull()
        }

    override suspend fun getADX(
        symbol: String,
        minDate: LocalDate,
    ): Map<LocalDate, Double>? =
        withContext(Dispatchers.IO) {
            runCatching {
                val rows = fetchTechnical(symbol, FUNCTION_ADX, minDate, ADX_LIST_TYPE)
                val response = EodhdAdxResponse(rows = rows ?: emptyList())
                validateAndTransform(response, symbol, "ADX") { it.toAdxMap(minDate) }
            }.onFailure { e -> logSafe("ADX", symbol, e) }.getOrNull()
        }

    private fun <T> fetchTechnical(
        symbol: String,
        function: String,
        minDate: LocalDate,
        type: ParameterizedTypeReference<List<T>>,
    ): List<T>? =
        restClient
            .get()
            .uri { uriBuilder ->
                uriBuilder
                    .path(PATH_TECHNICAL)
                    .queryParam(QUERY_API_TOKEN, apiKey)
                    .queryParam(QUERY_FUNCTION, function)
                    .queryParam(QUERY_PERIOD, INDICATOR_PERIOD)
                    .queryParam(QUERY_FMT, FMT_JSON)
                    .queryParam(QUERY_FROM, minDate.toString())
                    .build(symbol.toEodhdSymbol())
            }.retrieve()
            .body(type)

    override suspend fun getEarnings(symbol: String): List<Earning>? =
        withContext(Dispatchers.IO) {
            runCatching {
                fetchFundamentals(symbol)?.let { response ->
                    validateAndTransform(response, symbol, "earnings") { it.toEarnings(symbol) }
                }
            }.onFailure { e -> logSafe("earnings", symbol, e) }.getOrNull()
        }

    override suspend fun getCompanyInfo(symbol: String): CompanyInfo? =
        withContext(Dispatchers.IO) {
            runCatching {
                fetchFundamentals(symbol)?.let { response ->
                    validateAndTransform(response, symbol, "company info") { it.toCompanyInfo() }
                }
            }.onFailure { e -> logSafe("company info", symbol, e) }.getOrNull()
        }

    private fun fetchFundamentals(symbol: String): EodhdFundamentalsResponse? =
        restClient
            .get()
            .uri { uriBuilder ->
                uriBuilder
                    .path(PATH_FUNDAMENTALS)
                    .queryParam(QUERY_API_TOKEN, apiKey)
                    .queryParam(QUERY_FILTER, FUNDAMENTALS_FILTER)
                    .build(symbol.toEodhdSymbol())
            }.retrieve()
            .body(EodhdFundamentalsResponse::class.java)

    private fun <T : EodhdApiResponse, R> validateAndTransform(
        response: T?,
        symbol: String,
        label: String,
        transform: (T) -> R,
    ): R? =
        when {
            response == null -> null.also { logger.warn("No response from EODHD $label for $symbol") }
            response.hasError() ->
                null.also {
                    logger.error("EODHD $label error for $symbol: ${response.getErrorDescription()}")
                }
            !response.isValid() -> null.also { logger.error("EODHD returned invalid $label for $symbol") }
            else -> transform(response)
        }

    /**
     * Map a Midgaard ticker to the form EODHD expects (`{ticker}.{exchange}`).
     *
     * If the input already ends with a known exchange suffix, leave it alone.
     * Otherwise treat it as a US ticker, replacing `.` with `-` for class
     * shares (`BRK.B` → `BRK-B.US`, matching EODHD's convention).
     */
    private fun String.toEodhdSymbol(): String {
        if (KNOWN_EXCHANGE_SUFFIXES.any { endsWith(it) }) return this
        return replace('.', '-') + ".US"
    }

    /**
     * Log fetch failures without leaking the API key.
     *
     * Spring's `RestClientResponseException` embeds the request URI (with the
     * `api_token` query param) in both `message` and stacktrace lines, so we
     * scrub the message and skip passing the throwable as the second logger
     * argument (which would print the unscrubbed stack). Stack lines are
     * available at DEBUG for local debugging.
     */
    private fun logSafe(
        label: String,
        symbol: String,
        e: Throwable,
    ) {
        val sanitized = (e.message ?: e.javaClass.simpleName).replace(API_TOKEN_PATTERN, "api_token=***")
        logger.error("Failed to fetch $label from EODHD for $symbol: $sanitized")
        logger.debug("Stack trace for EODHD $label fetch failure ($symbol)", e)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(EodhdProvider::class.java)
        private val BAR_LIST_TYPE = object : ParameterizedTypeReference<List<EodhdBarDto>>() {}
        private val ATR_LIST_TYPE = object : ParameterizedTypeReference<List<EodhdAtrRowDto>>() {}
        private val ADX_LIST_TYPE = object : ParameterizedTypeReference<List<EodhdAdxRowDto>>() {}
        private const val PATH_EOD = "/eod/{symbol}"
        private const val PATH_TECHNICAL = "/technical/{symbol}"
        private const val PATH_FUNDAMENTALS = "/fundamentals/{symbol}"
        private const val QUERY_API_TOKEN = "api_token"
        private const val QUERY_FMT = "fmt"
        private const val QUERY_FROM = "from"
        private const val QUERY_FUNCTION = "function"
        private const val QUERY_PERIOD = "period"
        private const val QUERY_FILTER = "filter"
        private const val FMT_JSON = "json"
        private const val FUNCTION_ATR = "atr"
        private const val FUNCTION_ADX = "adx"
        private const val INDICATOR_PERIOD = 14
        private const val FUNDAMENTALS_FILTER = "General,Highlights,Earnings"
        private val KNOWN_EXCHANGE_SUFFIXES =
            setOf(".US", ".LSE", ".XETRA", ".PA", ".HK", ".TO", ".AX", ".V", ".F", ".FOREX")
        private val API_TOKEN_PATTERN = Regex("api_token=[^&\\s\"]*")
    }
}
