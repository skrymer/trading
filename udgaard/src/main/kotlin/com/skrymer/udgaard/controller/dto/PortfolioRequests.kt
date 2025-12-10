package com.skrymer.udgaard.controller.dto

import com.skrymer.udgaard.model.InstrumentType
import com.skrymer.udgaard.model.OptionType
import com.skrymer.udgaard.model.PortfolioTrade
import java.time.LocalDate

data class CreatePortfolioRequest(
  val name: String,
  val initialBalance: Double,
  val currency: String,
  val userId: String? = null,
)

data class UpdatePortfolioRequest(
  val currentBalance: Double,
)

data class OpenTradeRequest(
  val symbol: String,
  val entryPrice: Double,
  val entryDate: LocalDate,
  val quantity: Int,
  val entryStrategy: String,
  val exitStrategy: String,
  val currency: String,
  val underlyingSymbol: String? = null,
  val instrumentType: InstrumentType = InstrumentType.STOCK,
  val optionType: OptionType? = null,
  val strikePrice: Double? = null,
  val expirationDate: LocalDate? = null,
  val contracts: Int? = null,
  val multiplier: Int = 100,
  val entryIntrinsicValue: Double? = null,
  val entryExtrinsicValue: Double? = null,
)

data class CloseTradeRequest(
  val exitPrice: Double,
  val exitDate: LocalDate,
  val exitIntrinsicValue: Double? = null,
  val exitExtrinsicValue: Double? = null,
)

data class UpdateTradeRequest(
  val symbol: String? = null,
  val entryPrice: Double? = null,
  val entryDate: LocalDate? = null,
  val quantity: Int? = null,
  val entryStrategy: String? = null,
  val exitStrategy: String? = null,
  val underlyingSymbol: String? = null,
  val instrumentType: InstrumentType? = null,
  val optionType: OptionType? = null,
  val strikePrice: Double? = null,
  val expirationDate: LocalDate? = null,
  val contracts: Int? = null,
  val multiplier: Int? = null,
  val entryIntrinsicValue: Double? = null,
  val entryExtrinsicValue: Double? = null,
)

data class RollTradeRequest(
  val newSymbol: String,
  val newStrikePrice: Double,
  val newExpirationDate: LocalDate,
  val newOptionType: OptionType,
  val newEntryPrice: Double,
  val rollDate: LocalDate,
  val contracts: Int,
  val exitPrice: Double,
)

data class RollTradeResponse(
  val closedTrade: PortfolioTrade,
  val newTrade: PortfolioTrade,
  val rollCost: Double,
)

data class RollChainResponse(
  val trades: List<PortfolioTrade>,
)
