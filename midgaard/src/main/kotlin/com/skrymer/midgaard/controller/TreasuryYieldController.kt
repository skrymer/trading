package com.skrymer.midgaard.controller

import com.skrymer.midgaard.model.TreasuryYield
import com.skrymer.midgaard.repository.TreasuryYieldRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/treasury-yields")
class TreasuryYieldController(
    private val treasuryYieldRepository: TreasuryYieldRepository,
) {
    @GetMapping("/{maturity}")
    fun getYields(
        @PathVariable maturity: String,
    ): List<TreasuryYield> = treasuryYieldRepository.findByMaturity(maturity.uppercase())
}
