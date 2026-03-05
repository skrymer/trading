package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.backtesting.model.BacktestReport
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class BacktestResultStore(
  private val cacheManager: CacheManager,
) {
  private val cache get() = cacheManager.getCache("backtestResults")!!

  fun store(report: BacktestReport): String {
    val id = UUID.randomUUID().toString()
    cache.put(id, report)
    return id
  }

  fun get(backtestId: String): BacktestReport? = cache.get(backtestId, BacktestReport::class.java)
}
