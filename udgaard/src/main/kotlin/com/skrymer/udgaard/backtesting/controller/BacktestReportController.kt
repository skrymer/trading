package com.skrymer.udgaard.backtesting.controller

import com.skrymer.udgaard.backtesting.model.BacktestReportListItem
import com.skrymer.udgaard.backtesting.repository.BacktestReportJooqRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/backtest/reports")
class BacktestReportController(
  private val repository: BacktestReportJooqRepository
) {
  @GetMapping
  fun list(): ResponseEntity<List<BacktestReportListItem>> = ResponseEntity.ok(repository.listAll())

  @DeleteMapping("/{backtestId}")
  fun deleteOne(
    @PathVariable backtestId: UUID
  ): ResponseEntity<Void> {
    val deleted = repository.deleteById(backtestId)
    return if (deleted == 0) ResponseEntity.notFound().build() else ResponseEntity.noContent().build()
  }

  // POST + action-name path because HTTP spec discourages bodies on DELETE and some
  // proxies strip them.
  @PostMapping("/batch-delete")
  fun deleteBatch(
    @RequestBody ids: List<UUID>
  ): ResponseEntity<BatchDeleteResponse> {
    require(ids.size <= MAX_BATCH_DELETE) {
      "batch-delete accepts at most $MAX_BATCH_DELETE ids per request (got ${ids.size})"
    }
    if (ids.isEmpty()) return ResponseEntity.ok(BatchDeleteResponse(deleted = 0))
    val deleted = repository.deleteByIds(ids)
    logger.info("Batch-deleted $deleted backtest reports (requested ${ids.size})")
    return ResponseEntity.ok(BatchDeleteResponse(deleted = deleted))
  }

  companion object {
    // Bounded so a single request cannot blow past PostgreSQL's 65535 protocol-level
    // parameter limit and to keep planner work predictable under concurrent calls.
    private const val MAX_BATCH_DELETE = 500
    private val logger: Logger = LoggerFactory.getLogger(BacktestReportController::class.java)
  }
}

data class BatchDeleteResponse(
  val deleted: Int
)
