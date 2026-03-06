package com.skrymer.udgaard.portfolio.integration.ibkr.dto

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

/**
 * IBKR Flex Query XML response DTOs
 */

@JacksonXmlRootElement(localName = "FlexQueryResponse")
@JsonIgnoreProperties(ignoreUnknown = true)
data class FlexQueryResponse
  @JsonCreator
  constructor(
    @param:JacksonXmlProperty(localName = "FlexStatements")
    val flexStatements: FlexStatements,
  )

@JsonIgnoreProperties(ignoreUnknown = true)
data class FlexStatements
  @JsonCreator
  constructor(
    @param:JacksonXmlProperty(localName = "FlexStatement")
    val flexStatement: FlexStatement,
  )

@JsonIgnoreProperties(ignoreUnknown = true)
data class FlexStatement
  @JsonCreator
  constructor(
    @param:JacksonXmlProperty(isAttribute = true, localName = "accountId")
    val accountId: String?,
    @param:JacksonXmlProperty(isAttribute = true, localName = "fromDate")
    val fromDate: String?,
    @param:JacksonXmlProperty(isAttribute = true, localName = "toDate")
    val toDate: String?,
    @param:JacksonXmlProperty(localName = "Trades")
    val trades: Trades?,
    @param:JacksonXmlProperty(localName = "AccountInformation")
    val accountInformation: AccountInformation?,
    @param:JacksonXmlProperty(localName = "FxPositions")
    val fxPositions: FxPositions?,
    @param:JacksonXmlProperty(localName = "CashTransactions")
    val cashTransactions: IBKRCashTransactions?,
  )

@JsonIgnoreProperties(ignoreUnknown = true)
data class Trades
  @JsonCreator
  constructor(
    @param:JacksonXmlElementWrapper(useWrapping = false)
    @param:JacksonXmlProperty(localName = "Trade")
    val trade: List<IBKRTrade>?,
  )

@JsonIgnoreProperties(ignoreUnknown = true)
data class IBKRTrade
  @JsonCreator
  constructor(
    @param:JacksonXmlProperty(isAttribute = true, localName = "tradeID")
    val tradeID: String,
    @param:JacksonXmlProperty(isAttribute = true, localName = "symbol")
    val symbol: String,
    @param:JacksonXmlProperty(isAttribute = true, localName = "tradeDate")
    val tradeDate: String,
    @param:JacksonXmlProperty(isAttribute = true, localName = "tradeTime")
    val tradeTime: String?,
    @param:JacksonXmlProperty(isAttribute = true, localName = "quantity")
    val quantity: String,
    @param:JacksonXmlProperty(isAttribute = true, localName = "tradePrice")
    val tradePrice: String,
    @param:JacksonXmlProperty(isAttribute = true, localName = "buySell")
    val buySell: String,
    @param:JacksonXmlProperty(isAttribute = true, localName = "openCloseIndicator")
    val openCloseIndicator: String?,
    @param:JacksonXmlProperty(isAttribute = true, localName = "assetCategory")
    val assetCategory: String,
    @param:JacksonXmlProperty(isAttribute = true, localName = "putCall")
    val putCall: String?,
    @param:JacksonXmlProperty(isAttribute = true, localName = "strike")
    val strike: String?,
    @param:JacksonXmlProperty(isAttribute = true, localName = "expiry")
    val expiry: String?,
    @param:JacksonXmlProperty(isAttribute = true, localName = "multiplier")
    val multiplier: String?,
    @param:JacksonXmlProperty(isAttribute = true, localName = "underlyingSymbol")
    val underlyingSymbol: String?,
    @param:JacksonXmlProperty(isAttribute = true, localName = "ibOrderID")
    val ibOrderID: String?,
    @param:JacksonXmlProperty(isAttribute = true, localName = "origTradeID")
    val origTradeID: String?,
    @param:JacksonXmlProperty(isAttribute = true, localName = "ibCommission")
    val ibCommission: String?,
    @param:JacksonXmlProperty(isAttribute = true, localName = "netCash")
    val netCash: String,
    @param:JacksonXmlProperty(isAttribute = true, localName = "currency")
    val currency: String?,
    @param:JacksonXmlProperty(isAttribute = true, localName = "levelOfDetail")
    val levelOfDetail: String?,
    @param:JacksonXmlProperty(isAttribute = true, localName = "dateTime")
    val dateTime: String?,
    @param:JacksonXmlProperty(isAttribute = true, localName = "fxRateToBase")
    val fxRateToBase: String?,
  )

@JsonIgnoreProperties(ignoreUnknown = true)
data class AccountInformation
  @JsonCreator
  constructor(
    @param:JacksonXmlProperty(isAttribute = true, localName = "currency")
    val currency: String?,
    @param:JacksonXmlProperty(isAttribute = true, localName = "accountType")
    val accountType: String?,
  )

@JsonIgnoreProperties(ignoreUnknown = true)
data class FxPositions
  @JsonCreator
  constructor(
    @param:JacksonXmlElementWrapper(useWrapping = false)
    @param:JacksonXmlProperty(localName = "FxPosition")
    val fxPosition: List<FxPosition>?,
  )

@JsonIgnoreProperties(ignoreUnknown = true)
data class FxPosition
  @JsonCreator
  constructor(
    @param:JacksonXmlProperty(isAttribute = true, localName = "functionalCurrency")
    val functionalCurrency: String?,
    @param:JacksonXmlProperty(isAttribute = true, localName = "fxCurrency")
    val fxCurrency: String?,
  )

@JsonIgnoreProperties(ignoreUnknown = true)
data class IBKRCashTransactions
  @JsonCreator
  constructor(
    @param:JacksonXmlElementWrapper(useWrapping = false)
    @param:JacksonXmlProperty(localName = "CashTransaction")
    val cashTransaction: List<IBKRCashTransaction>?,
  )

@JsonIgnoreProperties(ignoreUnknown = true)
data class IBKRCashTransaction
  @JsonCreator
  constructor(
    @param:JacksonXmlProperty(isAttribute = true, localName = "transactionID")
    val transactionID: String,
    @param:JacksonXmlProperty(isAttribute = true, localName = "type")
    val type: String,
    @param:JacksonXmlProperty(isAttribute = true, localName = "amount")
    val amount: String,
    @param:JacksonXmlProperty(isAttribute = true, localName = "currency")
    val currency: String?,
    @param:JacksonXmlProperty(isAttribute = true, localName = "fxRateToBase")
    val fxRateToBase: String?,
    @param:JacksonXmlProperty(isAttribute = true, localName = "reportDate")
    val reportDate: String,
    @param:JacksonXmlProperty(isAttribute = true, localName = "description")
    val description: String?,
  )
