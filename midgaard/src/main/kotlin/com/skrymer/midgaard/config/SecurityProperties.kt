package com.skrymer.midgaard.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app.security")
data class SecurityProperties(
    val enabled: Boolean = false,
    val apiKeyHash: String = "",
)
