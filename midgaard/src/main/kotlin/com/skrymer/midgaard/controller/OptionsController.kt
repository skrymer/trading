package com.skrymer.midgaard.controller

import com.skrymer.midgaard.integration.OptionsProvider
import com.skrymer.midgaard.model.OptionContractDto
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/options")
class OptionsController(
    private val optionsProvider: OptionsProvider,
) {
    @GetMapping("/{symbol}")
    fun getHistoricalOptions(
        @PathVariable symbol: String,
        @RequestParam(required = false) date: String?,
    ): ResponseEntity<List<OptionContractDto>> {
        val contracts =
            optionsProvider.getHistoricalOptions(symbol.uppercase(), date)
                ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(contracts)
    }

    @GetMapping("/{symbol}/find")
    fun findOptionContract(
        @PathVariable symbol: String,
        @RequestParam strike: Double,
        @RequestParam expiration: String,
        @RequestParam optionType: String,
        @RequestParam date: String,
    ): ResponseEntity<OptionContractDto> {
        val contract =
            optionsProvider.findOptionContract(
                symbol = symbol.uppercase(),
                strike = strike,
                expiration = expiration,
                optionType = optionType,
                date = date,
            ) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(contract)
    }
}
