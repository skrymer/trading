package com.skrymer.udgaard.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.security")
data class SecurityProperties(
  val enabled: Boolean = true,
  val seed: SeedProperties = SeedProperties()
) {
  data class SeedProperties(
    val username: String = "admin",
    val password: String = "changeme",
    val apiKey: String = "changeme",
    val apiKeyExpiresInDays: Long? = null
  )
}
