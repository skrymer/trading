package com.skrymer.midgaard.integration.ovtlyr

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate

/**
 * Slim view of ovtlyr.com's per-symbol `getStockInformation` response. ovtlyr returns
 * dozens of fields per daily entry; only the three needed to derive a buy/sell signal
 * are modelled — the rest are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class OvtlyrPayloadDto(
    @param:JsonProperty("lst_h")
    val quotes: List<OvtlyrQuoteDto> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OvtlyrQuoteDto(
    @param:JsonProperty("stockSymbol")
    val symbol: String,
    // `quotedateStr` is the clean date-only form ("2026-05-18"); the sibling `quotedate`
    // field is a date-time string and parses less reliably into LocalDate.
    @param:JsonProperty("quotedateStr")
    val date: LocalDate,
    @param:JsonProperty("final_calls")
    val finalCalls: String?,
)
