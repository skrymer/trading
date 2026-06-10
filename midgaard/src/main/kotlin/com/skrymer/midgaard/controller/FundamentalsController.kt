package com.skrymer.midgaard.controller

import com.skrymer.midgaard.model.Fundamental
import com.skrymer.midgaard.repository.FundamentalsRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/fundamentals")
class FundamentalsController(
    private val fundamentalsRepository: FundamentalsRepository,
) {
    @GetMapping("/{symbol}")
    fun getFundamentals(
        @PathVariable symbol: String,
    ): List<Fundamental> = fundamentalsRepository.findBySymbol(symbol.uppercase())
}
