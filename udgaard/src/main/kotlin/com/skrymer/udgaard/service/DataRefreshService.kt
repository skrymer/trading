package com.skrymer.udgaard.service

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
  private val stockService: StockService,
  private val breadthService: BreadthService,
) {
  private val logger = LoggerFactory.getLogger(DataRefreshService::class.java)
  private val refreshQueue = ConcurrentLinkedQueue<RefreshTask>()

  @Volatile
  private var isProcessing = false

  private var currentProgress = RefreshProgress()
  private var processingJob: Job? = null

  /**
   * Queue stock symbols for refresh
   */
  fun queueStockRefresh(symbols: List<String>) {
    symbols.forEach { symbol ->
      refreshQueue.add(
        RefreshTask(
          type = RefreshType.STOCK,
          identifier = symbol,
        ),
      )
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
      refreshQueue.add(
        RefreshTask(
          type = RefreshType.BREADTH,
          identifier = symbol,
        ),
      )
    }
    currentProgress = RefreshProgress(total = refreshQueue.size)
    logger.info("Queued ${symbols.size} breadth symbols for refresh. Total queue size: ${refreshQueue.size}")
    startProcessing()
  }

  /**
   * Start processing the refresh queue with parallel processing
   */
  @Synchronized
  private fun startProcessing() {
    if (isProcessing) {
      logger.info("Already processing, skipping startProcessing()")
      return
    }

    isProcessing = true

    processingJob =
      CoroutineScope(Dispatchers.IO).launch {
        logger.info("Started processing refresh queue with ${refreshQueue.size} items")

        // Process up to 10 stocks in parallel
        // Each stock makes ~5 API calls, but rate limiting is handled transparently by provider decorators
        // The decorators use suspend functions with Mutex backpressure to enforce 5 requests/second limit
        logger.info("Processing up to 10 stocks in parallel with automatic rate limiting")

        val limited = Dispatchers.IO.limitedParallelism(10)
        val jobs = mutableListOf<Deferred<Unit>>()

        while (refreshQueue.isNotEmpty()) {
          // Poll up to 10 tasks for parallel processing
          val batch = mutableListOf<RefreshTask>()
          repeat(10) {
            refreshQueue.poll()?.let { batch.add(it) }
          }

          if (batch.isEmpty()) break

          // Process batch in parallel
          jobs.addAll(
            batch.map { task ->
              async(limited) {
                try {
                  logger.info("Processing task: ${task.type} - ${task.identifier}")

                  // Rate limiting is handled transparently by provider decorators
                  when (task.type) {
                    RefreshType.STOCK -> {
                      // Each stock makes ~5 API calls (daily data, ATR, ADX, earnings, sector)
                      // Rate limiter will enforce per-second limit for each call
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
                  }

                  synchronized(currentProgress) {
                    currentProgress =
                      currentProgress.copy(
                        completed = currentProgress.completed + 1,
                        lastSuccess = task.identifier,
                      )
                  }
                  logger.info(
                    "Successfully processed ${task.identifier}. Progress: ${currentProgress.completed}/${currentProgress.total}",
                  )
                } catch (e: Exception) {
                  logger.error("Failed to refresh ${task.identifier}: ${e.message}", e)
                  synchronized(currentProgress) {
                    currentProgress =
                      currentProgress.copy(
                        failed = currentProgress.failed + 1,
                        lastError = "${task.identifier}: ${e.message}",
                      )
                  }
                }
              }
            },
          )

          // Wait for batch to complete before getting next batch
          jobs.forEach { it.await() }
          jobs.clear()
        }

        isProcessing = false
        logger.info("Finished processing refresh queue. Completed: ${currentProgress.completed}, Failed: ${currentProgress.failed}")

        // Reset progress after completion
        currentProgress = RefreshProgress()
      }
  }

  /**
   * Get current refresh progress
   */
  fun getProgress(): RefreshProgress = currentProgress

  /**
   * Clear the refresh queue and reset progress
   */
  fun clearQueue() {
    logger.info("Clearing refresh queue")
    refreshQueue.clear()
    currentProgress = RefreshProgress()
    processingJob?.cancel()
    isProcessing = false
  }
}
