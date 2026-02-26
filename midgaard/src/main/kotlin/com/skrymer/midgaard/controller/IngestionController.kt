package com.skrymer.midgaard.controller

import com.skrymer.midgaard.model.IngestionResult
import com.skrymer.midgaard.model.IngestionStatus
import com.skrymer.midgaard.repository.IngestionStatusRepository
import com.skrymer.midgaard.service.IngestionService
import kotlinx.coroutines.runBlocking
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/ingestion")
class IngestionController(
    private val ingestionService: IngestionService,
    private val ingestionStatusRepository: IngestionStatusRepository,
) {
    @GetMapping("/status")
    fun getIngestionStatus(): List<IngestionStatus> = ingestionStatusRepository.findAll()

    @PostMapping("/initial/{symbol}")
    fun triggerInitialIngest(
        @PathVariable symbol: String,
    ): IngestionResult = runBlocking { ingestionService.initialIngest(symbol.uppercase()) }

    @PostMapping("/initial/all")
    fun triggerInitialIngestAll(): ResponseEntity<Map<String, String>> {
        ingestionService.initialIngestAll()
        return ResponseEntity.ok(mapOf("message" to "Bulk initial ingest started"))
    }

    @PostMapping("/update/{symbol}")
    fun triggerUpdate(
        @PathVariable symbol: String,
    ): IngestionResult = runBlocking { ingestionService.updateSymbol(symbol.uppercase()) }

    @PostMapping("/update/all")
    fun triggerUpdateAll(): ResponseEntity<Map<String, String>> {
        ingestionService.updateAll()
        return ResponseEntity.ok(mapOf("message" to "Bulk update started"))
    }

    @GetMapping("/progress")
    fun getProgress(): ResponseEntity<Map<String, Any>> {
        val progress =
            ingestionService.bulkProgress
                ?: return ResponseEntity.ok(mapOf("active" to false))

        return ResponseEntity.ok(
            mapOf(
                "active" to true,
                "total" to progress.total,
                "completed" to progress.completed.get(),
                "succeeded" to progress.succeeded.get(),
                "failed" to progress.failed.get(),
                "errors" to progress.errors.toMap(),
            ),
        )
    }
}
