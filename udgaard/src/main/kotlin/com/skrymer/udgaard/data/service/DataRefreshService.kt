package com.skrymer.udgaard.data.service

import com.skrymer.udgaard.data.dto.RefreshProgress
import com.skrymer.udgaard.data.dto.RefreshTask
import com.skrymer.udgaard.data.dto.RefreshType
import com.skrymer.udgaard.data.model.BreadthSymbol
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.concurrent.ConcurrentLinkedQueue

@Service
class DataRefreshService(
  private val stockService: StockService,
  private val breadthService: BreadthService,
  private val stockRepository: com.skrymer.udgaard.data.repository.StockJooqRepository,
) {
  private val logger = LoggerFactory.getLogger(DataRefreshService::class.java)
  private val refreshQueue = ConcurrentLinkedQueue<RefreshTask>()

  @Volatile
  private var isProcessing = false

  private var currentProgress = RefreshProgress()
  private var processingJob: Job? = null

  // Flag to control Ovtlyr enrichment (shared across the current refresh session)
  @Volatile
  private var skipOvtlyrEnrichment = false

  // Minimum date for data filtering (shared across the current refresh session)
  @Volatile
  private var minDate: LocalDate = LocalDate.of(2020, 1, 1)

  /**
   * Queue stock symbols for refresh
   */
  fun queueStockRefresh(
    symbols: List<String>,
    skipOvtlyrEnrichment: Boolean = false,
    minDate: LocalDate = LocalDate.of(2020, 1, 1),
  ) {
    this.skipOvtlyrEnrichment = skipOvtlyrEnrichment
    this.minDate = minDate
    symbols.forEach { symbol ->
      refreshQueue.add(
        RefreshTask(
          type = RefreshType.STOCK,
          identifier = symbol,
        ),
      )
    }
    currentProgress = RefreshProgress(total = refreshQueue.size)
    logger.info("Queued ${symbols.size} stocks for refresh (skipOvtlyr=$skipOvtlyrEnrichment). Total queue size: ${refreshQueue.size}")
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
   * Start processing the refresh queue with parallel batch processing
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
        val errorDetails = mutableListOf<String>()
        var lastProgressLog = 0

        while (refreshQueue.isNotEmpty()) {
          // Separate stocks and breadth tasks
          val stockTasks = mutableListOf<RefreshTask>()
          val breadthTasks = mutableListOf<RefreshTask>()

          // Poll up to 10 tasks for batch processing (reduced to avoid OOM)
          repeat(10) {
            refreshQueue.poll()?.let { task ->
              when (task.type) {
                RefreshType.STOCK -> stockTasks.add(task)
                RefreshType.BREADTH -> breadthTasks.add(task)
              }
            }
          }

          if (stockTasks.isEmpty() && breadthTasks.isEmpty()) break

          // Process stock batch (leverages optimized getStocksBySymbols)
          if (stockTasks.isNotEmpty()) {
            try {
              val symbols = stockTasks.map { it.identifier }
              logger.info("Starting batch: ${symbols.size} stocks (skipOvtlyr=$skipOvtlyrEnrichment)")

              // This method fetches SPY ONCE for the entire batch and processes in parallel
              val stocks = stockService.getStocksBySymbols(symbols, forceFetch = true, skipOvtlyrEnrichment = skipOvtlyrEnrichment, minDate = minDate)

              // Update progress for successful stocks
              val successfulSymbols = stocks.map { it.symbol }.toSet()
              val failedSymbols = symbols.filterNot { it in successfulSymbols }

              // Collect error details for summary
              failedSymbols.forEach { symbol ->
                errorDetails.add("$symbol: Unknown error (not fetched)")
              }

              synchronized(currentProgress) {
                currentProgress =
                  currentProgress.copy(
                    completed = currentProgress.completed + stocks.size,
                    failed = currentProgress.failed + failedSymbols.size,
                    lastSuccess = stocks.lastOrNull()?.symbol ?: currentProgress.lastSuccess,
                    lastError =
                      if (failedSymbols.isNotEmpty()) {
                        "Failed symbols: ${failedSymbols.joinToString(", ")}"
                      } else {
                        currentProgress.lastError
                      },
                  )
              }

              // Log progress every 10 stocks or when reaching certain percentages
              val progressCompleted = currentProgress.completed + currentProgress.failed
              if (progressCompleted - lastProgressLog >= 10 || progressCompleted == currentProgress.total) {
                val percentage = String.format("%.1f", progressCompleted.toDouble() / currentProgress.total * 100)
                logger.info("Progress: $progressCompleted/${currentProgress.total} completed ($percentage%)")
                lastProgressLog = progressCompleted
              }
            } catch (e: Exception) {
              // Collect batch error details
              stockTasks.forEach { task ->
                errorDetails.add("${task.identifier}: Batch failure - ${e.message}")
              }

              synchronized(currentProgress) {
                currentProgress =
                  currentProgress.copy(
                    failed = currentProgress.failed + stockTasks.size,
                    lastError = "Batch failure: ${e.message}",
                  )
              }
            }
          }

          // Process breadth tasks individually (less frequent, no batch optimization needed)
          if (breadthTasks.isNotEmpty()) {
            breadthTasks.forEach { task ->
              try {
                val breadthSymbol = BreadthSymbol.fromString(task.identifier)
                if (breadthSymbol != null) {
                  breadthService.getBreadth(breadthSymbol, refresh = true)
                  synchronized(currentProgress) {
                    currentProgress =
                      currentProgress.copy(
                        completed = currentProgress.completed + 1,
                        lastSuccess = task.identifier,
                      )
                  }
                } else {
                  errorDetails.add("${task.identifier}: Invalid breadth symbol")
                  synchronized(currentProgress) {
                    currentProgress =
                      currentProgress.copy(
                        failed = currentProgress.failed + 1,
                        lastError = "${task.identifier}: Invalid breadth symbol",
                      )
                  }
                }
              } catch (e: Exception) {
                errorDetails.add("${task.identifier}: ${e.message}")
                synchronized(currentProgress) {
                  currentProgress =
                    currentProgress.copy(
                      failed = currentProgress.failed + 1,
                      lastError = "${task.identifier}: ${e.message}",
                    )
                }
              }
            }
          }
        }

        isProcessing = false

        // Recalculate market breadth from updated stock data
        try {
          stockRepository.refreshBreadthDaily()
        } catch (e: Exception) {
          logger.error("Failed to refresh market breadth daily: ${e.message}")
        }

        // Log completion summary
        val totalProcessed = currentProgress.completed + currentProgress.failed
        val successRate = if (totalProcessed > 0) {
          String.format("%.1f", currentProgress.completed.toDouble() / totalProcessed * 100)
        } else {
          "0.0"
        }

        logger.info("")
        logger.info("=== Refresh Complete ===")
        logger.info("Total: $totalProcessed | Succeeded: ${currentProgress.completed} | Failed: ${currentProgress.failed} ($successRate% success)")

        if (errorDetails.isNotEmpty()) {
          logger.info("")
          logger.info("Failed items (${errorDetails.size}):")
          errorDetails.forEach { error ->
            logger.info("  - $error")
          }
        }
        logger.info("========================")
        logger.info("")

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
