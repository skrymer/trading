package com.skrymer.midgaard.config

import com.skrymer.midgaard.integration.CompanyInfoProvider
import com.skrymer.midgaard.integration.EarningsProvider
import com.skrymer.midgaard.integration.IndicatorProvider
import com.skrymer.midgaard.integration.OhlcvProvider
import com.skrymer.midgaard.integration.OptionsProvider
import com.skrymer.midgaard.integration.QuoteProvider
import com.skrymer.midgaard.integration.alphavantage.AlphaVantageProvider
import com.skrymer.midgaard.integration.finnhub.FinnhubProvider
import com.skrymer.midgaard.integration.massive.MassiveProvider
import com.skrymer.midgaard.service.RateLimiterService
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ProviderConfiguration(
    private val alphaVantageProvider: AlphaVantageProvider,
    private val massiveProvider: MassiveProvider,
    private val finnhubProvider: FinnhubProvider,
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
) {
    @PostConstruct
    fun init() {
        rateLimiterService.registerProvider("alphavantage", avReqPerSec, avReqPerMin, avReqPerDay)
        rateLimiterService.registerProvider("massive", massiveReqPerSec, massiveReqPerMin, massiveReqPerDay)
        rateLimiterService.registerProvider("finnhub", finnhubReqPerSec, finnhubReqPerMin, finnhubReqPerDay)
    }

    @Bean("alphaVantageOhlcv")
    fun alphaVantageOhlcv(): OhlcvProvider = alphaVantageProvider

    @Bean("massiveOhlcv")
    fun massiveOhlcv(): OhlcvProvider = massiveProvider

    @Bean
    fun indicatorProvider(): IndicatorProvider = alphaVantageProvider

    @Bean
    fun earningsProvider(): EarningsProvider = alphaVantageProvider

    @Bean
    fun companyInfoProvider(): CompanyInfoProvider = alphaVantageProvider

    @Bean
    fun optionsProvider(): OptionsProvider = alphaVantageProvider

    @Bean
    fun quoteProvider(): QuoteProvider = finnhubProvider
}
