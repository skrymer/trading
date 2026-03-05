package com.skrymer.udgaard.portfolio.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive

/**
 * DTOs for portfolio management endpoints
 * Position/Trade DTOs will be in PositionRequests.kt
 */

data class CreatePortfolioRequest(
  @field:NotBlank(message = "Portfolio name is required")
  val name: String,
  @field:Positive(message = "Initial balance must be positive")
  val initialBalance: Double,
  @field:NotBlank(message = "Currency is required")
  val currency: String,
  val userId: String? = null,
)

data class UpdatePortfolioRequest(
  @field:Positive(message = "Balance must be positive")
  val currentBalance: Double,
)
