package com.skrymer.udgaard.controller.dto

/**
 * DTOs for portfolio management endpoints
 * Position/Trade DTOs will be in PositionRequests.kt
 */

data class CreatePortfolioRequest(
  val name: String,
  val initialBalance: Double,
  val currency: String,
  val userId: String? = null,
)

data class UpdatePortfolioRequest(
  val currentBalance: Double,
)
