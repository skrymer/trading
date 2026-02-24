package com.skrymer.udgaard.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "stock.refresh")
data class StockRefreshProperties(
  val schedule: ScheduleProperties = ScheduleProperties(),
  val retry: RetryProperties = RetryProperties(),
  val requeue: RequeueProperties = RequeueProperties(),
) {
  data class ScheduleProperties(
    val cron: String = "0 30 16 ? * MON-FRI",
    val timezone: String = "America/New_York",
    val enabled: Boolean = false,
  )

  data class RetryProperties(
    val maxAttempts: Int = 3,
    val initialDelayMs: Long = 2000,
    val multiplier: Double = 2.0,
    val maxDelayMs: Long = 30000,
  )

  data class RequeueProperties(
    val enabled: Boolean = true,
    val maxRounds: Int = 1,
  )
}
