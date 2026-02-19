package com.skrymer.udgaard.backtesting.service

import com.github.benmanes.caffeine.cache.Caffeine
import com.skrymer.udgaard.backtesting.model.BacktestReport
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.TimeUnit

@Component
class BacktestResultStore {
  private val cache =
    Caffeine
      .newBuilder()
      .maximumSize(10)
      .expireAfterAccess(1, TimeUnit.HOURS)
      .build<String, BacktestReport>()

  fun store(report: BacktestReport): String {
    val id = UUID.randomUUID().toString()
    cache.put(id, report)
    return id
  }

  fun get(backtestId: String): BacktestReport? = cache.getIfPresent(backtestId)
}
