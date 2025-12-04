package com.skrymer.udgaard.service

import com.skrymer.udgaard.config.AlphaVantageRateLimitConfig
import com.skrymer.udgaard.controller.dto.RefreshProgress
import com.skrymer.udgaard.controller.dto.RefreshTask
import com.skrymer.udgaard.controller.dto.RefreshType
import com.skrymer.udgaard.model.BreadthSymbol
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentLinkedQueue

@Service
class DataRefreshService(
    private val rateLimiter: RateLimiterService,
    private val config: AlphaVantageRateLimitConfig,
    private val stockService: StockService,
    private val breadthService: BreadthService
) {
    private val logger = LoggerFactory.getLogger(DataRefreshService::class.java)
    private val refreshQueue = ConcurrentLinkedQueue<RefreshTask>()
    @Volatile
    private var isProcessing = false
    @Volatile
    private var shouldPause = false
    private var currentProgress = RefreshProgress()
    private var processingJob: Job? = null

    /**
     * Queue stock symbols for refresh
     */
    fun queueStockRefresh(symbols: List<String>) {
        symbols.forEach { symbol ->
            refreshQueue.add(RefreshTask(
                type = RefreshType.STOCK,
                identifier = symbol
            ))
        }
        currentProgress = RefreshProgress(total = refreshQueue.size)
        logger.info("Queued ${symbols.size} stocks for refresh. Total queue size: ${refreshQueue.size}")
        startProcessing()
    }

    /**
     * Queue breadth symbols for refresh
     */
    fun queueBreadthRefresh(symbols: List<String>) {
        symbols.forEach { symbol ->
            refreshQueue.add(RefreshTask(
                type = RefreshType.BREADTH,
                identifier = symbol
            ))
        }
        currentProgress = RefreshProgress(total = refreshQueue.size)
        logger.info("Queued ${symbols.size} breadth symbols for refresh. Total queue size: ${refreshQueue.size}")
        startProcessing()
    }

    /**
     * Start processing the refresh queue
     */
    @Synchronized
    private fun startProcessing() {
        if (isProcessing) {
            logger.info("Already processing, skipping startProcessing()")
            return
        }

        isProcessing = true
        shouldPause = false

        processingJob = CoroutineScope(Dispatchers.IO).launch {
            logger.info("Started processing refresh queue with ${refreshQueue.size} items")

            while (refreshQueue.isNotEmpty() && !shouldPause) {
                // Wait if rate limit is exceeded
                while (!rateLimiter.canMakeRequest() && !shouldPause) {
                    val delayMs = (60000.0 / config.requestsPerMinute).toLong()
                    logger.info("Rate limit reached, waiting ${delayMs}ms")
                    delay(delayMs)
                }

                if (shouldPause) {
                    logger.info("Processing paused")
                    break
                }

                val task = refreshQueue.poll() ?: break

                try {
                    logger.info("Processing task: ${task.type} - ${task.identifier}")
                    when (task.type) {
                        RefreshType.STOCK -> {
                            stockService.getStock(task.identifier, forceFetch = true)
                        }
                        RefreshType.BREADTH -> {
                            val breadthSymbol = BreadthSymbol.fromString(task.identifier)
                            if (breadthSymbol != null) {
                                breadthService.getBreadth(breadthSymbol, refresh = true)
                            } else {
                                logger.warn("Invalid breadth symbol: ${task.identifier}")
                            }
                        }
                        RefreshType.ETF -> {
                            // ETF refresh not yet implemented
                            logger.warn("ETF refresh not yet implemented for ${task.identifier}")
                        }
                    }
                    rateLimiter.recordRequest()
                    currentProgress = currentProgress.copy(
                        completed = currentProgress.completed + 1,
                        lastSuccess = task.identifier
                    )
                    logger.info("Successfully processed ${task.identifier}. Progress: ${currentProgress.completed}/${currentProgress.total}")
                } catch (e: Exception) {
                    logger.error("Failed to refresh ${task.identifier}: ${e.message}", e)
                    currentProgress = currentProgress.copy(
                        failed = currentProgress.failed + 1,
                        lastError = "${task.identifier}: ${e.message}"
                    )
                }
            }

            isProcessing = false
            logger.info("Finished processing refresh queue. Completed: ${currentProgress.completed}, Failed: ${currentProgress.failed}")
        }
    }

    /**
     * Get current refresh progress
     */
    fun getProgress(): RefreshProgress = currentProgress

    /**
     * Pause processing
     */
    fun pauseProcessing() {
        logger.info("Pause requested")
        shouldPause = true
    }

    /**
     * Resume processing
     */
    fun resumeProcessing() {
        logger.info("Resume requested")
        if (!isProcessing && refreshQueue.isNotEmpty()) {
            startProcessing()
        } else {
            shouldPause = false
        }
    }

    /**
     * Clear the refresh queue and reset progress
     */
    fun clearQueue() {
        logger.info("Clearing refresh queue")
        refreshQueue.clear()
        currentProgress = RefreshProgress()
        shouldPause = true
        processingJob?.cancel()
        isProcessing = false
    }
}
