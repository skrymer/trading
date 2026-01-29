package com.skrymer.udgaard.controller.dto

import com.skrymer.udgaard.domain.InstrumentTypeDomain
import com.skrymer.udgaard.domain.OptionTypeDomain
import com.skrymer.udgaard.domain.PositionDomain
import com.skrymer.udgaard.domain.PositionWithExecutions
import java.time.LocalDate

/**
 * Request to create a manual position
 */
data class CreatePositionRequest(
  val symbol: String,
  val instrumentType: InstrumentTypeDomain,
  val quantity: Int,
  val entryPrice: Double,
  val entryDate: LocalDate,
  val entryStrategy: String,
  val exitStrategy: String,
  val currency: String = "USD",
  val underlyingSymbol: String? = null,
  val optionType: OptionTypeDomain? = null,
  val strikePrice: Double? = null,
  val expirationDate: LocalDate? = null,
  val multiplier: Int = 100,
)

/**
 * Request to close a position
 */
data class ClosePositionRequest(
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
  val position: PositionDomain,
  val executions: List<com.skrymer.udgaard.domain.ExecutionDomain>,
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
