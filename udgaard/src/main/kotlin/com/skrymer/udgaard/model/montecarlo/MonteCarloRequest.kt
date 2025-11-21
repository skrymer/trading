package com.skrymer.udgaard.model.montecarlo

import com.skrymer.udgaard.model.BacktestReport

/**
 * Request for Monte Carlo simulation
 */
data class MonteCarloRequest(
    /**
     * The backtest result to run simulation on
     */
    val backtestResult: BacktestReport,

    /**
     * Type of Monte Carlo technique to use
     */
    val techniqueType: MonteCarloTechniqueType,

    /**
     * Number of simulation iterations to run
     */
    val iterations: Int = 10000,

    /**
     * Random seed for reproducibility (optional)
     */
    val seed: Long? = null,

    /**
     * Whether to include equity curves for all scenarios (can be large)
     * If false, only percentile curves will be included
     */
    val includeAllEquityCurves: Boolean = false
)
