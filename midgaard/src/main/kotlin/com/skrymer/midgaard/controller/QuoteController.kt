package com.skrymer.midgaard.controller

import com.skrymer.midgaard.model.Quote
import com.skrymer.midgaard.repository.QuoteRepository
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/quotes")
class QuoteController(
    private val quoteRepository: QuoteRepository,
) {
    @GetMapping("/{symbol}")
    fun getQuotes(
        @PathVariable symbol: String,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?,
    ): List<Quote> = quoteRepository.findBySymbol(symbol.uppercase(), startDate, endDate)

    @GetMapping("/bulk")
    fun getQuotesBulk(
        @RequestParam symbols: List<String>,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?,
    ): Map<String, List<Quote>> = quoteRepository.findBySymbols(symbols.map { it.uppercase() }, startDate, endDate)
}
