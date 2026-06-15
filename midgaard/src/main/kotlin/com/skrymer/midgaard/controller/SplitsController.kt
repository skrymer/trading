package com.skrymer.midgaard.controller

import com.skrymer.midgaard.model.Split
import com.skrymer.midgaard.repository.SplitRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/splits")
class SplitsController(
    private val splitRepository: SplitRepository,
) {
    @GetMapping("/{symbol}")
    fun getSplits(
        @PathVariable symbol: String,
    ): List<Split> = splitRepository.findBySymbol(symbol.uppercase())
}
