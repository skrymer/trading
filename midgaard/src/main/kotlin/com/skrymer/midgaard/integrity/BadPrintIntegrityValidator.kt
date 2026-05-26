package com.skrymer.midgaard.integrity

import com.skrymer.midgaard.jooq.tables.references.QUOTES
import org.jooq.DSLContext
import org.jooq.impl.DSL.lag
import org.jooq.impl.DSL.lead
import org.springframework.stereotype.Component
import java.math.BigDecimal

/**
 * Detects the bad-print V-shape pattern in OHLCV data: a single bar whose close
 * is >= 5x the prior bar AND the next bar's close reverts to <= 20% of the
 * spike. This is the canonical data-corruption signature, almost always on
 * microscopic volume. A single such bar poisons any backtest that enters near
 * it or exits at it.
 *
 * - V1: no symbol should contain a V-shape bar. Thresholds are conservative —
 *       legitimate +100% earnings moves do not revert by 80% the next day.
 *       Sample symbols are ordered alphabetically so the violation row is
 *       stable across runs given identical inputs.
 */
@Component
class BadPrintIntegrityValidator(
    private val dsl: DSLContext,
) : DataIntegrityValidator {
    override val name: String = "BadPrintIntegrityValidator"

    override fun validate(): List<Violation> = listOfNotNull(checkV1BadPrintVShape())

    private fun checkV1BadPrintVShape(): Violation? {
        val prevClose = lag(QUOTES.CLOSE_PRICE).over().partitionBy(QUOTES.SYMBOL).orderBy(QUOTES.QUOTE_DATE)
        val nextClose = lead(QUOTES.CLOSE_PRICE).over().partitionBy(QUOTES.SYMBOL).orderBy(QUOTES.QUOTE_DATE)

        val neighboured =
            dsl
                .select(
                    QUOTES.SYMBOL,
                    QUOTES.CLOSE_PRICE,
                    prevClose.`as`("prev_close"),
                    nextClose.`as`("next_close"),
                ).from(QUOTES)
                .asTable("neighboured")

        val prev = neighboured.field("prev_close", BigDecimal::class.java)!!
        val next = neighboured.field("next_close", BigDecimal::class.java)!!
        val close = neighboured.field(QUOTES.CLOSE_PRICE)!!
        val symbol = neighboured.field(QUOTES.SYMBOL)!!

        val offenders: List<String> =
            dsl
                .selectDistinct(symbol)
                .from(neighboured)
                .where(prev.gt(BigDecimal.ZERO))
                .and(next.gt(BigDecimal.ZERO))
                .and(close.div(prev).ge(JUMP_RATIO))
                .and(next.div(close).le(REVERSION_RATIO))
                .orderBy(symbol)
                .fetch { it.value1() }
                .filterNotNull()

        if (offenders.isEmpty()) return null

        val samples = offenders.take(SAMPLE_LIMIT)
        return Violation(
            validator = name,
            invariant = "V1",
            severity = Severity.CRITICAL,
            description = "${offenders.size} symbols contain a bad-print V-shape bar. Samples: ${samples.joinToString(", ")}",
            count = offenders.size,
            sampleSymbols = samples,
        )
    }

    companion object {
        private val JUMP_RATIO = BigDecimal("5")
        private val REVERSION_RATIO = BigDecimal("0.20")
        private const val SAMPLE_LIMIT = 10
    }
}
