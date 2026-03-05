package com.skrymer.udgaard.config

import com.skrymer.udgaard.data.integration.midgaard.MidgaardClient
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component

@Component
class MidgaardHealthIndicator(
  private val midgaardClient: MidgaardClient,
) : HealthIndicator {
  override fun health(): Health =
    try {
      val symbols = midgaardClient.getAllSymbols()
      if (symbols != null) {
        Health.up().withDetail("symbols", symbols.size).build()
      } else {
        Health.down().withDetail("reason", "No response from Midgaard").build()
      }
    } catch (e: Exception) {
      Health.down().withDetail("reason", e.message ?: "Unknown error").build()
    }
}
