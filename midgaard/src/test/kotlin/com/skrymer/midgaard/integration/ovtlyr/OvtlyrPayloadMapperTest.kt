package com.skrymer.midgaard.integration.ovtlyr

import com.skrymer.midgaard.model.OvtlyrSignal
import com.skrymer.midgaard.model.OvtlyrSignalType
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals

class OvtlyrPayloadMapperTest {
    @Test
    fun `toSignals keeps only Buy and Sell entries, dropping days with no call`() {
        // Given: a payload with a Buy, a no-call day, and a Sell
        val payload =
            OvtlyrPayloadDto(
                quotes =
                    listOf(
                        quote("2026-05-11", "Buy"),
                        quote("2026-05-14", null),
                        quote("2026-05-18", "Sell"),
                    ),
            )

        // When
        val signals = OvtlyrPayloadMapper.toSignals("AAPL", payload)

        // Then: only the two real calls survive
        assertEquals(
            listOf(
                OvtlyrSignal("AAPL", LocalDate.of(2026, 5, 11), OvtlyrSignalType.BUY),
                OvtlyrSignal("AAPL", LocalDate.of(2026, 5, 18), OvtlyrSignalType.SELL),
            ),
            signals,
        )
    }

    @Test
    fun `toSignals keys on the requested symbol, not the payload's echoed stockSymbol`() {
        // Given: ovtlyr echoes a differently-cased symbol in the row
        val payload =
            OvtlyrPayloadDto(
                quotes = listOf(OvtlyrQuoteDto(symbol = "aapl", date = LocalDate.of(2026, 5, 11), finalCalls = "Buy")),
            )

        // When: the caller requests the canonical "AAPL"
        val signals = OvtlyrPayloadMapper.toSignals("AAPL", payload)

        // Then: the stored signal carries the requested symbol, not the echoed one
        assertEquals("AAPL", signals.single().symbol)
    }

    private fun quote(
        date: String,
        finalCalls: String?,
    ) = OvtlyrQuoteDto(symbol = "AAPL", date = LocalDate.parse(date), finalCalls = finalCalls)
}
