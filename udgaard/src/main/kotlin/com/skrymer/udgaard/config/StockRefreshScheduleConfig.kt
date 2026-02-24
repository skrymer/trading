package com.skrymer.udgaard.config

import com.skrymer.udgaard.data.service.StockIngestionService
import com.skrymer.udgaard.data.service.SymbolService
import org.quartz.CronScheduleBuilder
import org.quartz.DisallowConcurrentExecution
import org.quartz.Job
import org.quartz.JobBuilder
import org.quartz.JobDetail
import org.quartz.JobExecutionContext
import org.quartz.Trigger
import org.quartz.TriggerBuilder
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.TimeZone

@Configuration
@ConditionalOnProperty("stock.refresh.schedule.enabled", havingValue = "true")
class StockRefreshScheduleConfig(
  private val properties: StockRefreshProperties,
) {
  @Bean
  fun stockRefreshJobDetail(): JobDetail =
    JobBuilder
      .newJob(StockRefreshJob::class.java)
      .withIdentity("stockRefreshJob")
      .storeDurably()
      .build()

  @Bean
  fun stockRefreshTrigger(jobDetail: JobDetail): Trigger =
    TriggerBuilder
      .newTrigger()
      .forJob(jobDetail)
      .withIdentity("stockRefreshTrigger")
      .withSchedule(
        CronScheduleBuilder
          .cronSchedule(properties.schedule.cron)
          .inTimeZone(TimeZone.getTimeZone(properties.schedule.timezone))
          .withMisfireHandlingInstructionFireAndProceed(),
      ).build()
}

@DisallowConcurrentExecution
class StockRefreshJob(
  private val stockIngestionService: StockIngestionService,
  private val symbolService: SymbolService,
) : Job {
  private val logger = LoggerFactory.getLogger(StockRefreshJob::class.java)

  override fun execute(context: JobExecutionContext) {
    val allSymbols = symbolService.getAll().map { it.symbol }
    logger.info("Scheduled stock refresh triggered — queueing ${allSymbols.size} symbols")
    stockIngestionService.queueStockRefresh(allSymbols)
  }
}
