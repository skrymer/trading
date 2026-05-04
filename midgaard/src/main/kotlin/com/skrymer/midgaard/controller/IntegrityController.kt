package com.skrymer.midgaard.controller

import com.skrymer.midgaard.integrity.DataIntegrityService
import com.skrymer.midgaard.integrity.Violation
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/integrity")
class IntegrityController(
    private val service: DataIntegrityService,
) {
    /**
     * Run every registered validator and return the resulting snapshot. Replaces
     * the previously-persisted snapshot. Use after manual writes (Adminer UPDATE,
     * `psql` shell) when the auto-run-on-refresh path was bypassed.
     */
    @PostMapping("/validate")
    fun runValidation(): List<Violation> = service.runAll()

    /** Returns the latest persisted snapshot from the most recent `runAll()`. */
    @GetMapping("/violations")
    fun getLatest(): List<Violation> = service.latestViolations()
}
