package com.skrymer.udgaard.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "alphavantage.ratelimit")
data class AlphaVantageRateLimitConfig(
    var requestsPerMinute: Int = 5,
    var requestsPerDay: Int = 500
)
