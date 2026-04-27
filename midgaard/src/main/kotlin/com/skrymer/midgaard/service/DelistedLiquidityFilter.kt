package com.skrymer.midgaard.service

import com.skrymer.midgaard.integration.OhlcvProvider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Narrows a delisted-symbol candidate list down to the names that traded with
 * meaningful liquidity in the months leading up to delisting.
 *
 * Bias-correction studies show that including every delisted ticker in a
 * backtest universe over-represents penny-stock noise: thinly-traded names
 * have wide spreads and unreliable closing prices that don't reflect anything
 * a real strategy could have traded. The conventional fix — and what quant
 * guidance for this branch recommends — is to keep only the top 80 % by
 * 6-month pre-delist median dollar volume.
 *
 * For each candidate we fetch up to 180 trading days ending at the delisting
 * date, compute median(close × volume), and drop:
 *   - candidates with fewer than `minBars` bars in the window (180-day
 *     enforcement is too strict for symbols that delisted partway through a
 *     quarter; 90 is a reasonable floor)
 *   - candidates whose median dollar volume is zero (no real activity)
 *   - the bottom `dropBottomPercent` of survivors by median dollar volume
 */
@Service
class DelistedLiquidityFilter(
    private val ohlcv: OhlcvProvider,
) {
    suspend fun filter(
        candidates: List<DelistedCandidate>,
        config: Config = Config(),
    ): List<DelistedCandidate> {
        val scored =
            candidates.mapNotNull { candidate ->
                val medianDollarVolume = medianDollarVolume(candidate, config) ?: return@mapNotNull null
                if (medianDollarVolume <= 0.0) return@mapNotNull null
                ScoredCandidate(candidate, medianDollarVolume)
            }
        if (scored.isEmpty()) {
            logger.warn("No delisted candidates survived bar/volume filtering")
            return emptyList()
        }
        val survivors = dropBottomPercent(scored, config.dropBottomPercent).map { it.candidate }
        logger.info("Liquidity filter: kept ${survivors.size} of ${candidates.size} delisted candidates")
        return survivors
    }

    private suspend fun medianDollarVolume(
        candidate: DelistedCandidate,
        config: Config,
    ): Double? {
        val windowStart = candidate.delistedDate.minusDays(config.lookbackDays)
        val bars =
            ohlcv
                .getDailyBars(candidate.symbol, "compact", windowStart)
                ?.filter { !it.date.isAfter(candidate.delistedDate) }
                ?: return null
        if (bars.size < config.minBars) return null
        val dollarVolumes = bars.map { it.close * it.volume }
        return median(dollarVolumes)
    }

    private fun dropBottomPercent(
        scored: List<ScoredCandidate>,
        dropBottomPercent: Double,
    ): List<ScoredCandidate> {
        val sorted = scored.sortedByDescending { it.medianDollarVolume }
        val keepCount = (sorted.size * (1.0 - dropBottomPercent)).toInt().coerceAtLeast(1)
        return sorted.take(keepCount)
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid]
    }

    data class Config(
        val lookbackDays: Long = 365,
        val minBars: Int = 90,
        val dropBottomPercent: Double = 0.20,
    )

    private data class ScoredCandidate(
        val candidate: DelistedCandidate,
        val medianDollarVolume: Double,
    )

    companion object {
        private val logger = LoggerFactory.getLogger(DelistedLiquidityFilter::class.java)
    }
}

/**
 * Minimum information needed to score a delisted candidate. Held outside the
 * filter so the ingest pipeline can construct it from EODHD's symbol list +
 * fundamentals without dragging the whole `Symbol` model in.
 */
data class DelistedCandidate(
    val symbol: String,
    val delistedDate: LocalDate,
    val name: String? = null,
)
