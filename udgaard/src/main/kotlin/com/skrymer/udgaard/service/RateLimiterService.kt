package com.skrymer.udgaard.service

import com.skrymer.udgaard.config.AlphaVantageRateLimitConfig
import com.skrymer.udgaard.controller.dto.RateLimitStats
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentLinkedQueue

@Service
class RateLimiterService(
  private val config: AlphaVantageRateLimitConfig,
) {
  private val requestQueue = ConcurrentLinkedQueue<Instant>()

  /**
   * Check if a new API request can be made without exceeding rate limits
   */
  fun canMakeRequest(): Boolean {
    val now = Instant.now()
    cleanOldRequests(now)

    val lastMinute = requestQueue.count { it.isAfter(now.minus(1, ChronoUnit.MINUTES)) }
    val lastDay = requestQueue.count { it.isAfter(now.minus(24, ChronoUnit.HOURS)) }

    return lastMinute < config.requestsPerMinute && lastDay < config.requestsPerDay
  }

  /**
   * Record a new API request
   */
  fun recordRequest() {
    requestQueue.add(Instant.now())
  }

  /**
   * Get current usage statistics
   */
  fun getUsageStats(): RateLimitStats {
    val now = Instant.now()
    cleanOldRequests(now)

    val lastMinute = requestQueue.count { it.isAfter(now.minus(1, ChronoUnit.MINUTES)) }
    val lastDay = requestQueue.count { it.isAfter(now.minus(24, ChronoUnit.HOURS)) }

    return RateLimitStats(
      requestsLastMinute = lastMinute,
      requestsLastDay = lastDay,
      remainingMinute = config.requestsPerMinute - lastMinute,
      remainingDaily = config.requestsPerDay - lastDay,
      minuteLimit = config.requestsPerMinute,
      dailyLimit = config.requestsPerDay,
      resetMinute = calculateResetTime(1, ChronoUnit.MINUTES),
      resetDaily = calculateResetTime(24, ChronoUnit.HOURS),
    )
  }

  /**
   * Remove requests older than 24 hours from the queue
   */
  private fun cleanOldRequests(now: Instant) {
    val cutoff = now.minus(24, ChronoUnit.HOURS)
    while (requestQueue.peek()?.isBefore(cutoff) == true) {
      requestQueue.poll()
    }
  }

  /**
   * Calculate seconds until the rate limit window resets
   */
  private fun calculateResetTime(
    amount: Long,
    unit: ChronoUnit,
  ): Long {
    val now = Instant.now()
    val oldestInWindow = requestQueue.firstOrNull { it.isAfter(now.minus(amount, unit)) }

    return if (oldestInWindow != null) {
      val resetTime = oldestInWindow.plus(amount, unit)
      ChronoUnit.SECONDS.between(now, resetTime).coerceAtLeast(0)
    } else {
      0
    }
  }
}
