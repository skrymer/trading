package com.skrymer.midgaard.integration.eodhd.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * One row from `GET /api/exchange-symbol-list/US?delisted=1&fmt=json`.
 *
 * EODHD's delisted-symbol list returns a flat array. Each row identifies a
 * once-listed US ticker plus enough metadata for downstream filtering — we
 * keep `type` so the ingest path can drop ETFs / preferred shares / warrants
 * before paying the per-symbol enrichment cost (CIK lookup + bar fetches).
 *
 * Other fields in the response (Country, Currency, Isin, previousTickers)
 * are ignored — we don't currently use them.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class EodhdDelistedSymbolDto(
    @param:JsonProperty("Code") val code: String,
    @param:JsonProperty("Name") val name: String? = null,
    @param:JsonProperty("Type") val type: String? = null,
    @param:JsonProperty("Exchange") val exchange: String? = null,
)
