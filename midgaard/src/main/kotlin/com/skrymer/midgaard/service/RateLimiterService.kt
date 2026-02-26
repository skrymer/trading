package com.skrymer.midgaard.service

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

@Service
class RateLimiterService {
    private val logger = LoggerFactory.getLogger(RateLimiterService::class.java)
    private val providers = ConcurrentHashMap<String, ProviderRateLimiter>()

    fun registerProvider(
        providerId: String,
        requestsPerSecond: Int,
        requestsPerMinute: Int,
        requestsPerDay: Int,
    ) {
        providers[providerId] =
            ProviderRateLimiter(
                providerId = providerId,
                requestsPerSecond = requestsPerSecond,
                requestsPerMinute = requestsPerMinute,
                requestsPerDay = requestsPerDay,
            )
        logger.info(
            "Registered provider $providerId with limits: $requestsPerSecond/sec, $requestsPerMinute/min, $requestsPerDay/day",
        )
    }

    suspend fun acquirePermit(providerId: String) {
        val rateLimiter =
            providers[providerId]
                ?: throw IllegalStateException("Provider $providerId not registered")
        rateLimiter.acquirePermit()
    }

    fun getProviderStats(providerId: String): ProviderRateLimitStats? = providers[providerId]?.getUsageStats()

    fun getAllProviderStats(): Map<String, ProviderRateLimitStats> = providers.mapValues { it.value.getUsageStats() }
}

class ProviderRateLimiter(
    val providerId: String,
    val requestsPerSecond: Int,
    val requestsPerMinute: Int,
    val requestsPerDay: Int,
) {
    private val requestQueue = ConcurrentLinkedQueue<Instant>()
    private val mutex = Mutex()

    suspend fun acquirePermit() {
        mutex.withLock {
            while (true) {
                cleanOldRequests()
                val now = Instant.now()

                val lastSecond = requestQueue.count { it.isAfter(now.minus(1, ChronoUnit.SECONDS)) }
                val lastMinute = requestQueue.count { it.isAfter(now.minus(1, ChronoUnit.MINUTES)) }
                val lastDay = requestQueue.count { it.isAfter(now.minus(24, ChronoUnit.HOURS)) }

                if (lastSecond < requestsPerSecond &&
                    lastMinute < requestsPerMinute &&
                    lastDay < requestsPerDay
                ) {
                    requestQueue.add(Instant.now())
                    return
                }

                val waitMs = calculateWaitTime(now, lastSecond, lastMinute, lastDay)
                kotlinx.coroutines.delay(waitMs)
            }
        }
    }

    private fun calculateWaitTime(
        now: Instant,
        lastSecond: Int,
        lastMinute: Int,
        lastDay: Int,
    ): Long {
        val waitTimes = mutableListOf<Long>()

        if (lastSecond >= requestsPerSecond) {
            val oldestInSecond = requestQueue.firstOrNull { it.isAfter(now.minus(1, ChronoUnit.SECONDS)) }
            oldestInSecond?.let {
                waitTimes.add(ChronoUnit.MILLIS.between(now, it.plus(1, ChronoUnit.SECONDS)))
            }
        }

        if (lastMinute >= requestsPerMinute) {
            val oldestInMinute = requestQueue.firstOrNull { it.isAfter(now.minus(1, ChronoUnit.MINUTES)) }
            oldestInMinute?.let {
                waitTimes.add(ChronoUnit.MILLIS.between(now, it.plus(1, ChronoUnit.MINUTES)))
            }
        }

        if (lastDay >= requestsPerDay) {
            val oldestInDay = requestQueue.firstOrNull { it.isAfter(now.minus(24, ChronoUnit.HOURS)) }
            oldestInDay?.let {
                waitTimes.add(ChronoUnit.MILLIS.between(now, it.plus(24, ChronoUnit.HOURS)))
            }
        }

        return waitTimes.maxOrNull()?.coerceAtLeast(100) ?: 100
    }

    private fun cleanOldRequests() {
        val cutoff = Instant.now().minus(24, ChronoUnit.HOURS)
        while (requestQueue.peek()?.isBefore(cutoff) == true) {
            requestQueue.poll()
        }
    }

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
