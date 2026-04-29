package com.skrymer.midgaard.config

import com.skrymer.midgaard.integration.CompanyInfoProvider
import com.skrymer.midgaard.integration.EarningsProvider
import com.skrymer.midgaard.integration.IndicatorProvider
import com.skrymer.midgaard.integration.OhlcvProvider
import com.skrymer.midgaard.integration.OptionsProvider
import com.skrymer.midgaard.integration.ProviderIds
import com.skrymer.midgaard.integration.QuoteProvider
import com.skrymer.midgaard.integration.alphavantage.AlphaVantageProvider
import com.skrymer.midgaard.integration.eodhd.EodhdProvider
import com.skrymer.midgaard.integration.finnhub.FinnhubProvider
import com.skrymer.midgaard.service.RateLimiterService
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Wires the four data-provider interfaces (`OhlcvProvider`, `IndicatorProvider`,
 * `EarningsProvider`, `CompanyInfoProvider`) to a concrete implementation chosen
 * by the `app.ingest.provider` property:
 *
 *   `alphavantage` → AlphaVantage backs all four interfaces.
 *   `eodhd`        → EODHD backs all four interfaces.
 *
 * Defaults: bare `bootRun` (no env override) falls back to `alphavantage` via
 * the SpEL default below; both `udgaard/compose.yaml` (dev) and
 * `compose.prod.yaml` (prod) pin `APP_INGEST_PROVIDER=eodhd`.
 *
 * `IngestionService` injects with `@Qualifier`s matching the bean names below,
 * so swapping providers is a single config change with zero code edits. Both
 * initial ingest and daily updates flow through the same OHLCV bean.
 *
 * Live quotes always come from Finnhub; options always from AlphaVantage. Add
 * matching `@ConditionalOnProperty` blocks if/when those become toggleable.
 */
@Configuration
@Suppress("LongParameterList")
class ProviderConfiguration(
    private val alphaVantageProvider: AlphaVantageProvider,
    private val finnhubProvider: FinnhubProvider,
    private val eodhdProvider: EodhdProvider,
    private val rateLimiterService: RateLimiterService,
    @param:Value("\${provider.alphavantage.requestsPerSecond:5}") private val avReqPerSec: Int,
    @param:Value("\${provider.alphavantage.requestsPerMinute:75}") private val avReqPerMin: Int,
    @param:Value("\${provider.alphavantage.requestsPerDay:75000}") private val avReqPerDay: Int,
    @param:Value("\${provider.massive.requestsPerSecond:80}") private val massiveReqPerSec: Int,
    @param:Value("\${provider.massive.requestsPerMinute:1000}") private val massiveReqPerMin: Int,
    @param:Value("\${provider.massive.requestsPerDay:100000}") private val massiveReqPerDay: Int,
    @param:Value("\${provider.finnhub.requestsPerSecond:30}") private val finnhubReqPerSec: Int,
    @param:Value("\${provider.finnhub.requestsPerMinute:60}") private val finnhubReqPerMin: Int,
    @param:Value("\${provider.finnhub.requestsPerDay:50000}") private val finnhubReqPerDay: Int,
    @param:Value("\${provider.eodhd.requestsPerSecond:10}") private val eodhdReqPerSec: Int,
    @param:Value("\${provider.eodhd.requestsPerMinute:1000}") private val eodhdReqPerMin: Int,
    @param:Value("\${provider.eodhd.requestsPerDay:100000}") private val eodhdReqPerDay: Int,
    @param:Value("\${provider.edgar.requestsPerSecond:10}") private val edgarReqPerSec: Int,
    @param:Value("\${provider.edgar.requestsPerMinute:600}") private val edgarReqPerMin: Int,
    @param:Value("\${provider.edgar.requestsPerDay:100000}") private val edgarReqPerDay: Int,
) {
    @PostConstruct
    fun init() {
        rateLimiterService.registerProvider(ProviderIds.ALPHAVANTAGE, avReqPerSec, avReqPerMin, avReqPerDay)
        rateLimiterService.registerProvider(ProviderIds.MASSIVE, massiveReqPerSec, massiveReqPerMin, massiveReqPerDay)
        rateLimiterService.registerProvider(ProviderIds.FINNHUB, finnhubReqPerSec, finnhubReqPerMin, finnhubReqPerDay)
        rateLimiterService.registerProvider(ProviderIds.EODHD, eodhdReqPerSec, eodhdReqPerMin, eodhdReqPerDay)
        rateLimiterService.registerProvider(ProviderIds.EDGAR, edgarReqPerSec, edgarReqPerMin, edgarReqPerDay)
    }

    // ── Toggleable: app.ingest.provider picks which implementation backs each interface.
    //
    // Beans are named exactly the same as the corresponding `IngestionService`
    // constructor parameters (`ohlcv`, `indicators`, `earnings`, `companyInfo`),
    // so Spring's autowire-by-parameter-name resolves the right one. Avoids the
    // @Primary-with-multi-interface ambiguity that crops up when one provider
    // class implements several interfaces.

    @Bean("ohlcv")
    @ConditionalOnProperty(name = ["app.ingest.provider"], havingValue = ProviderIds.EODHD)
    fun ohlcvFromEodhd(): OhlcvProvider = eodhdProvider

    @Bean("ohlcv")
    @ConditionalOnExpression("'\${app.ingest.provider:alphavantage}' != 'eodhd'")
    fun ohlcvFromAlphaVantage(): OhlcvProvider = alphaVantageProvider

    @Bean("indicators")
    @ConditionalOnProperty(name = ["app.ingest.provider"], havingValue = ProviderIds.EODHD)
    fun indicatorsFromEodhd(): IndicatorProvider = eodhdProvider

    @Bean("indicators")
    @ConditionalOnExpression("'\${app.ingest.provider:alphavantage}' != 'eodhd'")
    fun indicatorsFromAlphaVantage(): IndicatorProvider = alphaVantageProvider

    @Bean("earnings")
    @ConditionalOnProperty(name = ["app.ingest.provider"], havingValue = ProviderIds.EODHD)
    fun earningsFromEodhd(): EarningsProvider = eodhdProvider

    @Bean("earnings")
    @ConditionalOnExpression("'\${app.ingest.provider:alphavantage}' != 'eodhd'")
    fun earningsFromAlphaVantage(): EarningsProvider = alphaVantageProvider

    @Bean("companyInfo")
    @ConditionalOnProperty(name = ["app.ingest.provider"], havingValue = ProviderIds.EODHD)
    fun companyInfoFromEodhd(): CompanyInfoProvider = eodhdProvider

    @Bean("companyInfo")
    @ConditionalOnExpression("'\${app.ingest.provider:alphavantage}' != 'eodhd'")
    fun companyInfoFromAlphaVantage(): CompanyInfoProvider = alphaVantageProvider

    // ── Non-toggleable: orthogonal concerns, kept under their own qualifiers.

    @Bean
    fun optionsProvider(): OptionsProvider = alphaVantageProvider

    @Bean
    fun quoteProvider(): QuoteProvider = finnhubProvider
}
