package com.skrymer.udgaard.data.service

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

/**
 * Provider-specific rate limiter with true backpressure using Kotlin coroutines.
 *
 * Tracks API requests per provider and enforces per-second, per-minute, and per-day limits.
 * Uses Mutex for non-busy-waiting suspension when rate limits are reached.
 *
 * IMPORTANT: Enforces minimum delay between consecutive requests to prevent burst detection.
 * AlphaVantage requires requests to be "spread evenly" across time windows, not just counted.
 *
 * @param providerId Unique identifier for the provider (e.g., "alphavantage", "yahoo")
 * @param requestsPerSecond Maximum requests per second
 * @param requestsPerMinute Maximum requests per minute
 * @param requestsPerDay Maximum requests per day
 */
class ProviderRateLimiter(
  val providerId: String,
  val requestsPerSecond: Int,
  val requestsPerMinute: Int,
  val requestsPerDay: Int,
) {
  companion object {
    private val logger = LoggerFactory.getLogger(ProviderRateLimiter::class.java)
  }

  private val requestQueue = ConcurrentLinkedQueue<Instant>()
  private val mutex = Mutex()

  // Track last request time to enforce minimum inter-request delay
  @Volatile
  private var lastRequestTime: Instant? = null

  // Atomic sequence counter to ensure strict request ordering
  private val requestSequence = AtomicLong(0)

  // Minimum delay between requests to spread them evenly and avoid burst detection
  // Using 1000ms (1 second) to ensure strict spacing - 5 req/sec max becomes effectively 1 req/sec
  // This prevents AlphaVantage burst detection which looks at request distribution across the minute window
  private val minInterRequestDelayMs = 1000L

  /**
   * Acquire a permit to make an API request.
   * Suspends the coroutine until rate limits allow the request.
   *
   * This provides true backpressure - no busy-waiting or CPU spinning.
   * Enforces minimum inter-request delay to prevent burst detection by API providers.
   */
  suspend fun acquirePermit() {
    // Acquire sequence number atomically - ensures strict ordering
    val mySequence = requestSequence.incrementAndGet()

    mutex.withLock {
      // Wait until rate limits allow the request
      while (true) {
        cleanOldRequests()

        val now = Instant.now()

        // Check minimum inter-request delay (prevents burst detection)
        val lastRequest = lastRequestTime
        if (lastRequest != null) {
          val msSinceLastRequest = ChronoUnit.MILLIS.between(lastRequest, now)

          if (msSinceLastRequest < minInterRequestDelayMs) {
            val waitMs = minInterRequestDelayMs - msSinceLastRequest
            // Delay while holding the lock to prevent concurrent permits
            kotlinx.coroutines.delay(waitMs)
            continue // Re-check all conditions after waiting
          }
        }

        val lastSecond = requestQueue.count { it.isAfter(now.minus(1, ChronoUnit.SECONDS)) }
        val lastMinute = requestQueue.count { it.isAfter(now.minus(1, ChronoUnit.MINUTES)) }
        val lastDay = requestQueue.count { it.isAfter(now.minus(24, ChronoUnit.HOURS)) }

        if (lastSecond < requestsPerSecond &&
          lastMinute < requestsPerMinute &&
          lastDay < requestsPerDay
        ) {
          // Permit available - record request with atomic sequence and return
          val requestTime = Instant.now()
          requestQueue.add(requestTime)
          lastRequestTime = requestTime
          return
        }

        // Rate limit exceeded - calculate minimum wait time and delay while holding lock
        val waitMs = calculateWaitTime(now, lastSecond, lastMinute, lastDay)
        kotlinx.coroutines.delay(waitMs)
      }
    }
  }

  /**
   * Calculate minimum wait time before next request can be made
   */
  private fun calculateWaitTime(
    now: Instant,
    lastSecond: Int,
    lastMinute: Int,
    lastDay: Int,
  ): Long {
    val waitTimes = mutableListOf<Long>()

    // Check per-second limit
    if (lastSecond >= requestsPerSecond) {
      val oldestInSecond = requestQueue.firstOrNull { it.isAfter(now.minus(1, ChronoUnit.SECONDS)) }
      oldestInSecond?.let {
        val resetTime = it.plus(1, ChronoUnit.SECONDS)
        waitTimes.add(ChronoUnit.MILLIS.between(now, resetTime))
      }
    }

    // Check per-minute limit
    if (lastMinute >= requestsPerMinute) {
      val oldestInMinute = requestQueue.firstOrNull { it.isAfter(now.minus(1, ChronoUnit.MINUTES)) }
      oldestInMinute?.let {
        val resetTime = it.plus(1, ChronoUnit.MINUTES)
        waitTimes.add(ChronoUnit.MILLIS.between(now, resetTime))
      }
    }

    // Check per-day limit
    if (lastDay >= requestsPerDay) {
      val oldestInDay = requestQueue.firstOrNull { it.isAfter(now.minus(24, ChronoUnit.HOURS)) }
      oldestInDay?.let {
        val resetTime = it.plus(24, ChronoUnit.HOURS)
        waitTimes.add(ChronoUnit.MILLIS.between(now, resetTime))
      }
    }

    return waitTimes.maxOrNull()?.coerceAtLeast(100) ?: 100
  }

  /**
   * Remove requests older than 24 hours from the queue
   */
  private fun cleanOldRequests() {
    val cutoff = Instant.now().minus(24, ChronoUnit.HOURS)
    while (requestQueue.peek()?.isBefore(cutoff) == true) {
      requestQueue.poll()
    }
  }

  /**
   * Get current usage statistics for monitoring
   */
  fun getUsageStats(): ProviderRateLimitStats {
    val now = Instant.now()
    cleanOldRequests()

    val lastSecond = requestQueue.count { it.isAfter(now.minus(1, ChronoUnit.SECONDS)) }
    val lastMinute = requestQueue.count { it.isAfter(now.minus(1, ChronoUnit.MINUTES)) }
    val lastDay = requestQueue.count { it.isAfter(now.minus(24, ChronoUnit.HOURS)) }

    return ProviderRateLimitStats(
      providerId = providerId,
      requestsLastSecond = lastSecond,
      requestsLastMinute = lastMinute,
      requestsLastDay = lastDay,
      remainingSecond = requestsPerSecond - lastSecond,
      remainingMinute = requestsPerMinute - lastMinute,
      remainingDaily = requestsPerDay - lastDay,
      secondLimit = requestsPerSecond,
      minuteLimit = requestsPerMinute,
      dailyLimit = requestsPerDay,
    )
  }
}

/**
 * Rate limit statistics for a specific provider
 */
data class ProviderRateLimitStats(
  val providerId: String,
  val requestsLastSecond: Int,
  val requestsLastMinute: Int,
  val requestsLastDay: Int,
  val remainingSecond: Int,
  val remainingMinute: Int,
  val remainingDaily: Int,
  val secondLimit: Int,
  val minuteLimit: Int,
  val dailyLimit: Int,
)
