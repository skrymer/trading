package com.skrymer.udgaard.controller.dto

import com.skrymer.udgaard.domain.InstrumentTypeDomain
import com.skrymer.udgaard.domain.OptionTypeDomain
import com.skrymer.udgaard.domain.PortfolioTradeDomain
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
  val instrumentType: InstrumentTypeDomain = InstrumentTypeDomain.STOCK,
  val optionType: OptionTypeDomain? = null,
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
  val instrumentType: InstrumentTypeDomain? = null,
  val optionType: OptionTypeDomain? = null,
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
  val newOptionType: OptionTypeDomain,
  val newEntryPrice: Double,
  val rollDate: LocalDate,
  val contracts: Int,
  val exitPrice: Double,
)

data class RollTradeResponse(
  val closedTrade: PortfolioTradeDomain,
  val newTrade: PortfolioTradeDomain,
  val rollCost: Double,
)

data class RollChainResponse(
  val trades: List<PortfolioTradeDomain>,
)
