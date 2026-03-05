package com.skrymer.udgaard.portfolio.dto

import com.skrymer.udgaard.portfolio.model.InstrumentType
import com.skrymer.udgaard.portfolio.model.OptionType
import com.skrymer.udgaard.portfolio.model.Position
import com.skrymer.udgaard.portfolio.model.PositionWithExecutions
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import java.time.LocalDate

/**
 * Request to create a manual position
 */
data class CreatePositionRequest(
  @field:NotBlank(message = "Symbol is required")
  val symbol: String,
  val instrumentType: InstrumentType,
  @field:Positive(message = "Quantity must be positive")
  val quantity: Int,
  @field:Positive(message = "Entry price must be positive")
  val entryPrice: Double,
  val entryDate: LocalDate,
  val entryStrategy: String,
  val exitStrategy: String,
  val currency: String = "USD",
  val underlyingSymbol: String? = null,
  val optionType: OptionType? = null,
  val strikePrice: Double? = null,
  val expirationDate: LocalDate? = null,
  val multiplier: Int = 100,
)

/**
 * Request to close a position
 */
data class ClosePositionRequest(
  @field:Positive(message = "Exit price must be positive")
  val exitPrice: Double,
  val exitDate: LocalDate,
)

/**
 * Request to update position metadata
 */
data class UpdatePositionMetadataRequest(
  val entryStrategy: String? = null,
  val exitStrategy: String? = null,
  val notes: String? = null,
)

/**
 * Response containing position with executions
 */
data class PositionWithExecutionsResponse(
  val position: Position,
  val executions: List<com.skrymer.udgaard.portfolio.model.Execution>,
) {
  companion object {
    fun from(positionWithExecutions: PositionWithExecutions) =
      PositionWithExecutionsResponse(
        position = positionWithExecutions.position,
        executions = positionWithExecutions.executions,
      )
  }
}

/**
 * Response containing position with unrealized P&L
 */
data class PositionUnrealizedPnlResponse(
  val positionId: Long,
  val symbol: String,
  val currentPrice: Double?,
  val averageEntryPrice: Double,
  val unrealizedPnl: Double?,
  val unrealizedPnlPercentage: Double?,
)
