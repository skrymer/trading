package com.skrymer.midgaard.service

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicBoolean

@Service
@ConditionalOnProperty("app.scheduled-ingestion.enabled", havingValue = "true", matchIfMissing = false)
class ScheduledIngestionService(
    private val ingestionService: IngestionService,
) {
    private val logger = LoggerFactory.getLogger(ScheduledIngestionService::class.java)
    private val running = AtomicBoolean(false)

    @Scheduled(cron = "\${app.scheduled-ingestion.cron:0 30 16 * * MON-FRI}", zone = "America/New_York")
    fun scheduledDailyUpdate() {
        if (!running.compareAndSet(false, true)) {
            logger.warn("Previous scheduled ingestion still running, skipping")
            return
        }
        try {
            logger.info("Scheduled daily update triggered")
            ingestionService.updateAll()
        } finally {
            running.set(false)
        }
    }
}
