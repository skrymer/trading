package com.skrymer.midgaard.integrity

import com.skrymer.midgaard.jooq.tables.references.QUOTES
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.impl.DSL.lag
import org.jooq.impl.DSL.lead
import org.springframework.stereotype.Component
import java.math.BigDecimal

/**
 * Detects two classes of bar-level OHLCV anomalies via close-neighbour ratios:
 *
 * - V1: bad-print V-shape — close >= 5x prev AND next reverts to <= 20% of the
 *       spike. Canonical data-corruption signature on a single bar, almost
 *       always with microscopic volume. CRITICAL.
 * - V2: split-adjustment failure — close >= 5x prev AND next holds at >= 50%
 *       of the spike. The provider (EODHD) carried only a partial adjustment
 *       factor back to historical bars, leaving a real corporate-action jump
 *       that the engine treats as a phantom return. HIGH (recoverable data
 *       issue, not corruption).
 *
 * Sample symbols are ordered alphabetically so the violation row is stable
 * across runs given identical inputs.
 */
@Component
class BadPrintIntegrityValidator(
    private val dsl: DSLContext,
) : DataIntegrityValidator {
    override val name: String = "BadPrintIntegrityValidator"

    override fun validate(): List<Violation> =
        listOfNotNull(
            checkV1BadPrintVShape(),
            checkV2SplitAdjustmentFailure(),
        )

    private fun checkV1BadPrintVShape(): Violation? =
        runJumpQuery(
            invariant = "V1",
            severity = Severity.CRITICAL,
            describe = { count, samples ->
                "$count symbols contain a bad-print V-shape bar " +
                    "(close >= ${JUMP_RATIO}x prev, next <= ${V1_REVERSION_RATIO}x spike). " +
                    "Samples: $samples"
            },
        ) { prev, close, next ->
            close.div(prev).ge(JUMP_RATIO).and(next.div(close).le(V1_REVERSION_RATIO))
        }

    private fun checkV2SplitAdjustmentFailure(): Violation? =
        runJumpQuery(
            invariant = "V2",
            severity = Severity.HIGH,
            describe = { count, samples ->
                "$count symbols contain a split-adjustment-failure bar " +
                    "(close >= ${JUMP_RATIO}x prev, next >= ${V2_HOLD_RATIO}x close — " +
                    "provider did not back-adjust). Samples: $samples"
            },
        ) { prev, close, next ->
            close.div(prev).ge(JUMP_RATIO).and(next.div(close).ge(V2_HOLD_RATIO))
        }

    private fun runJumpQuery(
        invariant: String,
        severity: Severity,
        describe: (Int, String) -> String,
        predicate: (
            prev: Field<BigDecimal?>,
            close: Field<BigDecimal?>,
            next: Field<BigDecimal?>,
        ) -> Condition,
    ): Violation? {
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
                .where(prev.ge(MIN_PRICE))
                .and(next.gt(BigDecimal.ZERO))
                .and(predicate(prev, close, next))
                .orderBy(symbol)
                .fetch { it.value1() }
                .filterNotNull()

        if (offenders.isEmpty()) return null

        val samples = offenders.take(SAMPLE_LIMIT)
        return Violation(
            validator = name,
            invariant = invariant,
            severity = severity,
            description = describe(offenders.size, samples.joinToString(", ")),
            count = offenders.size,
            sampleSymbols = samples,
        )
    }

    companion object {
        private val JUMP_RATIO = BigDecimal("5")
        private val V1_REVERSION_RATIO = BigDecimal("0.20")
        private val V2_HOLD_RATIO = BigDecimal("0.50")

        // Floor applied to prev_close only — the spike bar itself can
        // legitimately be any price. Sub-cent prev_close usually marks a
        // stub/placeholder bar at the symbol's listing date, not real
        // contamination — see `BadPrintIntegrityValidatorE2ETest.validate
        // ignores a stub first bar...`.
        private val MIN_PRICE = BigDecimal("0.01")

        private const val SAMPLE_LIMIT = 10
    }
}
