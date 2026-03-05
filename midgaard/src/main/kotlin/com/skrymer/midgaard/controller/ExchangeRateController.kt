package com.skrymer.midgaard.controller

import com.skrymer.midgaard.integration.alphavantage.AlphaVantageProvider
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/fx")
class ExchangeRateController(
    private val alphaVantageProvider: AlphaVantageProvider,
) {
    @GetMapping("/rate")
    fun getCurrentRate(
        @RequestParam from: String,
        @RequestParam to: String,
    ): ResponseEntity<ExchangeRateResponse> {
        val rate =
            alphaVantageProvider.getExchangeRate(from.uppercase(), to.uppercase())
                ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(
            ExchangeRateResponse(
                from = from.uppercase(),
                to = to.uppercase(),
                rate = rate,
                date = LocalDate.now(),
            ),
        )
    }

    @GetMapping("/rate/historical")
    fun getHistoricalRate(
        @RequestParam from: String,
        @RequestParam to: String,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
    ): ResponseEntity<ExchangeRateResponse> {
        val rate =
            alphaVantageProvider.getHistoricalExchangeRate(from.uppercase(), to.uppercase(), date)
                ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(
            ExchangeRateResponse(
                from = from.uppercase(),
                to = to.uppercase(),
                rate = rate,
                date = date,
            ),
        )
    }
}

data class ExchangeRateResponse(
    val from: String,
    val to: String,
    val rate: Double,
    val date: LocalDate,
)
