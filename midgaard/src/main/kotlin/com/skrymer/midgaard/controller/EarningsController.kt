package com.skrymer.midgaard.controller

import com.skrymer.midgaard.model.Earning
import com.skrymer.midgaard.repository.EarningsRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/earnings")
class EarningsController(
    private val earningsRepository: EarningsRepository,
) {
    @GetMapping("/{symbol}")
    fun getEarnings(
        @PathVariable symbol: String,
    ): List<Earning> = earningsRepository.findBySymbol(symbol.uppercase())
}
