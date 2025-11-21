package com.skrymer.udgaard.model.montecarlo

/**
 * Types of Monte Carlo simulation techniques available
 */
enum class MonteCarloTechniqueType {
    /**
     * Randomly reorder trades while maintaining same trades
     */
    TRADE_SHUFFLING,

    /**
     * Randomly sample trades with replacement (bootstrap)
     */
    BOOTSTRAP_RESAMPLING,

    /**
     * Randomize price paths (future implementation)
     */
    PRICE_PATH_RANDOMIZATION
}
