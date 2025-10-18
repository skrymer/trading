package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote

class WithinOrderBlockExitStrategy(val orderBlockAgeInDays: Int) : ExitStrategy {
  override fun match(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote
  ) = stock.withinOrderBlock(quote, orderBlockAgeInDays)

  override fun reason(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote
  ) =  "Quote is within an order block older than $orderBlockAgeInDays days"

  override fun description() = "Quote is within an order block older than $orderBlockAgeInDays days"
}