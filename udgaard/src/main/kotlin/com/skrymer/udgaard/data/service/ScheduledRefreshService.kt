package com.skrymer.udgaard.data.service

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicBoolean

@Service
@ConditionalOnProperty("app.scheduled-refresh.enabled", havingValue = "true", matchIfMissing = false)
class ScheduledRefreshService(
  private val stockIngestionService: StockIngestionService,
  private val symbolService: SymbolService,
) {
  private val logger = LoggerFactory.getLogger(ScheduledRefreshService::class.java)
  private val running = AtomicBoolean(false)

  @Scheduled(cron = "\${app.scheduled-refresh.cron:0 30 18 * * MON-FRI}", zone = "America/New_York")
  fun scheduledDailyRefresh() {
    if (!running.compareAndSet(false, true)) {
      logger.warn("Previous scheduled refresh still running, skipping")
      return
    }
    try {
      logger.info("Scheduled daily refresh triggered")
      val symbols = symbolService.getAll().map { it.symbol }
      if (symbols.isEmpty()) {
        logger.warn("No symbols found, skipping scheduled refresh")
        return
      }
      logger.info("Queuing ${symbols.size} symbols for refresh")
      stockIngestionService.queueStockRefresh(symbols)
    } finally {
      running.set(false)
    }
  }
}
