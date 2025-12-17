package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.domain.StockQuoteDomain

interface EntryStrategy {
  fun description(): String

  fun test(
    stock: StockDomain,
    quote: StockQuoteDomain,
  ): Boolean
}
