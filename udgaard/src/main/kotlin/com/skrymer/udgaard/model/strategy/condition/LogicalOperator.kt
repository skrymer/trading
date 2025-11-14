package com.skrymer.udgaard.model.strategy.condition

/**
 * Logical operators for combining trading conditions.
 */
enum class LogicalOperator {
    /**
     * All conditions must be true (logical AND).
     */
    AND,

    /**
     * At least one condition must be true (logical OR).
     */
    OR,

    /**
     * Inverts the result of the condition (logical NOT).
     */
    NOT
}
