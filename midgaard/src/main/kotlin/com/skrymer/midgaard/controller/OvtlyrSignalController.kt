package com.skrymer.midgaard.controller

import com.skrymer.midgaard.model.OvtlyrSignal
import com.skrymer.midgaard.repository.OvtlyrSignalRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/ovtlyr-signals")
class OvtlyrSignalController(
    private val ovtlyrSignalRepository: OvtlyrSignalRepository,
) {
    @GetMapping("/{symbol}")
    fun getSignals(
        @PathVariable symbol: String,
    ): List<OvtlyrSignal> = ovtlyrSignalRepository.findBySymbol(symbol.uppercase())
}
