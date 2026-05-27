package com.skrymer.midgaard.integrity

import com.skrymer.midgaard.jooq.tables.references.QUOTES
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.impl.DSL.lag
import org.jooq.impl.DSL.lead
import org.jooq.impl.DSL.rowNumber
import org.springframework.stereotype.Component
import java.math.BigDecimal

/**
 * Detects three classes of bar-level OHLCV anomalies via close-neighbour ratios:
 *
 * - V1: bad-print V-shape — close >= 5x prev AND next reverts to <= 20% of the
 *       spike. Canonical data-corruption signature on a single bar, almost
 *       always with microscopic volume. CRITICAL.
 * - V2: split-adjustment failure with normal-priced prev — close >= 5x prev
 *       AND next holds at >= 50% of the spike AND prev >= 0.01. The provider
 *       (EODHD) carried only a partial adjustment factor back to historical
 *       bars. HIGH.
 * - V3: split-adjustment failure with sub-cent prev confirmed by real history
 *       — same shape as V2 but prev < 0.01 AND bar_position >= 5 (at least 4
 *       prior bars on the symbol). The bar-position guard separates real
 *       low-price pre-split history (AT-class) from stub-first-bar artifacts
 *       (IHG-class, where prev is sub-cent only because it's the symbol's
 *       very first bar). HIGH.
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
            checkV3SubCentPrevWithHistory(),
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
            barFilter = { prev, next, _ -> prev.ge(MIN_PRICE).and(next.gt(BigDecimal.ZERO)) },
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
            barFilter = { prev, next, _ -> prev.ge(MIN_PRICE).and(next.gt(BigDecimal.ZERO)) },
        ) { prev, close, next ->
            close.div(prev).ge(JUMP_RATIO).and(next.div(close).ge(V2_HOLD_RATIO))
        }

    private fun checkV3SubCentPrevWithHistory(): Violation? =
        runJumpQuery(
            invariant = "V3",
            severity = Severity.HIGH,
            describe = { count, samples ->
                "$count symbols contain a split-adjustment-failure bar with sub-cent prev " +
                    "(close >= ${JUMP_RATIO}x prev, next >= ${V2_HOLD_RATIO}x close, prev < ${MIN_PRICE}, " +
                    "bar_position >= $BAR_POSITION_FLOOR — real low-price pre-split history, " +
                    "not a stub first bar). Samples: $samples"
            },
            barFilter = { prev, next, barPos ->
                // `prev.gt(ZERO)` is the explicit divide-by-zero guard for the
                // `close.div(prev)` evaluation below — V1/V2 fold this into
                // `prev.ge(MIN_PRICE)`; V3 separates them because the upper
                // bound is `prev < MIN_PRICE`.
                prev
                    .gt(BigDecimal.ZERO)
                    .and(prev.lt(MIN_PRICE))
                    .and(next.gt(BigDecimal.ZERO))
                    .and(barPos.ge(BAR_POSITION_FLOOR))
            },
        ) { prev, close, next ->
            close.div(prev).ge(JUMP_RATIO).and(next.div(close).ge(V2_HOLD_RATIO))
        }

    private fun runJumpQuery(
        invariant: String,
        severity: Severity,
        describe: (Int, String) -> String,
        barFilter: (
            prev: Field<BigDecimal?>,
            next: Field<BigDecimal?>,
            barPosition: Field<Long?>,
        ) -> Condition,
        predicate: (
            prev: Field<BigDecimal?>,
            close: Field<BigDecimal?>,
            next: Field<BigDecimal?>,
        ) -> Condition,
    ): Violation? {
        val prevClose = lag(QUOTES.CLOSE_PRICE).over().partitionBy(QUOTES.SYMBOL).orderBy(QUOTES.QUOTE_DATE)
        val nextClose = lead(QUOTES.CLOSE_PRICE).over().partitionBy(QUOTES.SYMBOL).orderBy(QUOTES.QUOTE_DATE)
        val barPositionExpr = rowNumber().over().partitionBy(QUOTES.SYMBOL).orderBy(QUOTES.QUOTE_DATE)

        val neighboured =
            dsl
                .select(
                    QUOTES.SYMBOL,
                    QUOTES.CLOSE_PRICE,
                    prevClose.`as`("prev_close"),
                    nextClose.`as`("next_close"),
                    barPositionExpr.cast(Long::class.java).`as`("bar_position"),
                ).from(QUOTES)
                .asTable("neighboured")

        val prev = neighboured.field("prev_close", BigDecimal::class.java)!!
        val next = neighboured.field("next_close", BigDecimal::class.java)!!
        val close = neighboured.field(QUOTES.CLOSE_PRICE)!!
        val symbol = neighboured.field(QUOTES.SYMBOL)!!
        val barPosition = neighboured.field("bar_position", Long::class.java)!!

        val offenders: List<String> =
            dsl
                .selectDistinct(symbol)
                .from(neighboured)
                .where(barFilter(prev, next, barPosition))
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
        // legitimately be any price. V1/V2 require prev >= MIN_PRICE to avoid
        // stub-first-bar false positives. V3 inverts this and requires prev <
        // MIN_PRICE but adds BAR_POSITION_FLOOR to separate stubs from real
        // pre-split history.
        private val MIN_PRICE = BigDecimal("0.01")

        // Minimum bar position (1-indexed) for V3 — at least 4 prior bars must
        // exist on the symbol. Excludes IHG-class stub artifacts (where the
        // symbol's very first bar happens to be sub-cent) without restricting
        // the price level.
        private const val BAR_POSITION_FLOOR = 5L

        private const val SAMPLE_LIMIT = 10
    }
}
