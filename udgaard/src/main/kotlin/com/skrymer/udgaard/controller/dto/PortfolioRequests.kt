package com.skrymer.udgaard.controller.dto

import com.skrymer.udgaard.model.InstrumentType
import com.skrymer.udgaard.model.OptionType
import java.time.LocalDate

data class CreatePortfolioRequest(
    val name: String,
    val initialBalance: Double,
    val currency: String,
    val userId: String? = null
)

data class UpdatePortfolioRequest(
    val currentBalance: Double
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
    val entryExtrinsicValue: Double? = null
)

data class CloseTradeRequest(
    val exitPrice: Double,
    val exitDate: LocalDate
)
