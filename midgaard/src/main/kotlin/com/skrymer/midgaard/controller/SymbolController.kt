package com.skrymer.midgaard.controller

import com.skrymer.midgaard.model.Symbol
import com.skrymer.midgaard.repository.SymbolRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/symbols")
class SymbolController(
    private val symbolRepository: SymbolRepository,
) {
    @GetMapping
    fun getAllSymbols(): List<Symbol> = symbolRepository.findAll()

    @GetMapping("/{symbol}")
    fun getSymbol(
        @PathVariable symbol: String,
    ): ResponseEntity<Symbol> {
        val found = symbolRepository.findBySymbol(symbol.uppercase())
        return if (found != null) ResponseEntity.ok(found) else ResponseEntity.notFound().build()
    }
}
