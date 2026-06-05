package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.data.integration.midgaard.MidgaardClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Loads the treasury-yield series from Midgaard and builds the [RiskFreeRateProvider] the backtest
 * engine credits idle cash at (ADR 0016). When the series is unavailable it falls back **loudly** to
 * a 0% provider — a missing rate must never be silently filled with a wrong one.
 */
@Service
class RiskFreeRateService(
  private val midgaardClient: MidgaardClient,
) {
  fun loadProvider(expensePct: Double): RiskFreeRateProvider {
    val series = midgaardClient.getTreasuryYields(MATURITY)
    if (series == null) {
      logger.warn(
        "Treasury-yield series '$MATURITY' unavailable from Midgaard — idle cash will earn 0%. " +
          "Backtest results understate cash-heavy CAGR/Calmar until the series is ingested.",
      )
      return RiskFreeRateProvider(emptyMap(), expensePct)
    }
    return RiskFreeRateProvider(series, expensePct)
  }

  companion object {
    /** The 3-month T-bill series SGOV tracks — the idle-cash short rate. */
    const val MATURITY = "US3M"
    private val logger = LoggerFactory.getLogger(RiskFreeRateService::class.java)
  }
}
