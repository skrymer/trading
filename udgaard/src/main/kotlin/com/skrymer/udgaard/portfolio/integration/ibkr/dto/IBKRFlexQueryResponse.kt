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
    @JacksonXmlProperty(localName = "FlexStatements")
    val flexStatements: FlexStatements,
  )

@JsonIgnoreProperties(ignoreUnknown = true)
data class FlexStatements
  @JsonCreator
  constructor(
    @JacksonXmlProperty(localName = "FlexStatement")
    val flexStatement: FlexStatement,
  )

@JsonIgnoreProperties(ignoreUnknown = true)
data class FlexStatement
  @JsonCreator
  constructor(
    @JacksonXmlProperty(isAttribute = true, localName = "accountId")
    val accountId: String?,
    @JacksonXmlProperty(isAttribute = true, localName = "fromDate")
    val fromDate: String?,
    @JacksonXmlProperty(isAttribute = true, localName = "toDate")
    val toDate: String?,
    @JacksonXmlProperty(localName = "Trades")
    val trades: Trades?,
    @JacksonXmlProperty(localName = "AccountInformation")
    val accountInformation: AccountInformation?,
  )

@JsonIgnoreProperties(ignoreUnknown = true)
data class Trades
  @JsonCreator
  constructor(
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Trade")
    val trade: List<IBKRTrade>?,
  )

@JsonIgnoreProperties(ignoreUnknown = true)
data class IBKRTrade
  @JsonCreator
  constructor(
    @JacksonXmlProperty(isAttribute = true, localName = "tradeID")
    val tradeID: String,
    @JacksonXmlProperty(isAttribute = true, localName = "symbol")
    val symbol: String,
    @JacksonXmlProperty(isAttribute = true, localName = "tradeDate")
    val tradeDate: String,
    @JacksonXmlProperty(isAttribute = true, localName = "tradeTime")
    val tradeTime: String?,
    @JacksonXmlProperty(isAttribute = true, localName = "quantity")
    val quantity: String,
    @JacksonXmlProperty(isAttribute = true, localName = "tradePrice")
    val tradePrice: String,
    @JacksonXmlProperty(isAttribute = true, localName = "buySell")
    val buySell: String,
    @JacksonXmlProperty(isAttribute = true, localName = "openCloseIndicator")
    val openCloseIndicator: String?,
    @JacksonXmlProperty(isAttribute = true, localName = "assetCategory")
    val assetCategory: String,
    @JacksonXmlProperty(isAttribute = true, localName = "putCall")
    val putCall: String?,
    @JacksonXmlProperty(isAttribute = true, localName = "strike")
    val strike: String?,
    @JacksonXmlProperty(isAttribute = true, localName = "expiry")
    val expiry: String?,
    @JacksonXmlProperty(isAttribute = true, localName = "multiplier")
    val multiplier: String?,
    @JacksonXmlProperty(isAttribute = true, localName = "underlyingSymbol")
    val underlyingSymbol: String?,
    @JacksonXmlProperty(isAttribute = true, localName = "ibOrderID")
    val ibOrderID: String?,
    @JacksonXmlProperty(isAttribute = true, localName = "origTradeID")
    val origTradeID: String?,
    @JacksonXmlProperty(isAttribute = true, localName = "ibCommission")
    val ibCommission: String?,
    @JacksonXmlProperty(isAttribute = true, localName = "netCash")
    val netCash: String,
    @JacksonXmlProperty(isAttribute = true, localName = "currency")
    val currency: String?,
  )

@JsonIgnoreProperties(ignoreUnknown = true)
data class AccountInformation
  @JsonCreator
  constructor(
    @JacksonXmlProperty(isAttribute = true, localName = "currency")
    val currency: String?,
    @JacksonXmlProperty(isAttribute = true, localName = "accountType")
    val accountType: String?,
  )
