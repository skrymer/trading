package com.skrymer.udgaard.controller.dto

import com.skrymer.udgaard.model.PortfolioTrade

/**
 * Response DTO for portfolio trades that includes computed fields like exit signals
 */
data class PortfolioTradeResponse(
    val trade: PortfolioTrade,
    val hasExitSignal: Boolean = false,
    val exitSignalReason: String? = null
)
