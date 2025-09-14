package com.skrymer.udgaard.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

/**
 * Simple stock info.
 */
@Document(collection = "simple_stocks")
data class SimpleStock(
  /**
   * The stock symbol
   */
  @Id
  var symbol: String = "",

  /**
   * The stock quotes.
   */
  var quote: List<SimpleStockQuote> = emptyList()
)
