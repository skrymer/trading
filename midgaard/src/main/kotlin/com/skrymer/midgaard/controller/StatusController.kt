package com.skrymer.midgaard.controller

import com.skrymer.midgaard.repository.QuoteRepository
import com.skrymer.midgaard.repository.SymbolRepository
import com.skrymer.midgaard.service.RateLimiterService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/status")
class StatusController(
    private val rateLimiterService: RateLimiterService,
    private val quoteRepository: QuoteRepository,
    private val symbolRepository: SymbolRepository,
) {
    @GetMapping
    fun getStatus(): Map<String, Any> =
        mapOf(
            "totalSymbols" to symbolRepository.count(),
            "totalQuotes" to quoteRepository.getTotalQuoteCount(),
            "rateLimits" to rateLimiterService.getAllProviderStats(),
        )
}
