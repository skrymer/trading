package com.skrymer.midgaard.integration.ovtlyr

import com.skrymer.midgaard.model.OvtlyrSignal
import com.skrymer.midgaard.model.OvtlyrSignalType

/**
 * Extracts buy/sell signals from an ovtlyr payload. Days where ovtlyr emitted no call
 * (`final_calls` absent) are dropped — the stored signal set is sparse by design.
 */
object OvtlyrPayloadMapper {
    /**
     * @param symbol the requested symbol — used as the authoritative key. The payload's own
     *   per-row `stockSymbol` is not trusted: a casing mismatch would store rows under a key
     *   that never matches Midgaard's canonical `symbols` table.
     */
    fun toSignals(
        symbol: String,
        payload: OvtlyrPayloadDto,
    ): List<OvtlyrSignal> =
        payload.quotes.mapNotNull { quote ->
            parseSignalType(quote.finalCalls)?.let { type ->
                OvtlyrSignal(symbol, quote.date, type)
            }
        }

    private fun parseSignalType(finalCalls: String?): OvtlyrSignalType? =
        when (finalCalls?.lowercase()) {
            "buy" -> OvtlyrSignalType.BUY
            "sell" -> OvtlyrSignalType.SELL
            else -> null
        }
}
