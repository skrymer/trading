package com.skrymer.midgaard.config

import com.skrymer.midgaard.integration.CompanyInfoProvider
import com.skrymer.midgaard.integration.EarningsProvider
import com.skrymer.midgaard.integration.IndicatorProvider
import com.skrymer.midgaard.integration.OhlcvProvider
import com.skrymer.midgaard.integration.OptionsProvider
import com.skrymer.midgaard.integration.alphavantage.AlphaVantageProvider
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
    private val rateLimiterService: RateLimiterService,
    @Value("\${provider.alphavantage.requestsPerSecond:5}") private val avReqPerSec: Int,
    @Value("\${provider.alphavantage.requestsPerMinute:75}") private val avReqPerMin: Int,
    @Value("\${provider.alphavantage.requestsPerDay:75000}") private val avReqPerDay: Int,
    @Value("\${provider.massive.requestsPerSecond:80}") private val massiveReqPerSec: Int,
    @Value("\${provider.massive.requestsPerMinute:1000}") private val massiveReqPerMin: Int,
    @Value("\${provider.massive.requestsPerDay:100000}") private val massiveReqPerDay: Int,
) {
    @PostConstruct
    fun init() {
        rateLimiterService.registerProvider("alphavantage", avReqPerSec, avReqPerMin, avReqPerDay)
        rateLimiterService.registerProvider("massive", massiveReqPerSec, massiveReqPerMin, massiveReqPerDay)
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
}
