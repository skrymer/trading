package com.skrymer.udgaard.repository

import com.skrymer.udgaard.model.Stock
import org.springframework.data.mongodb.repository.MongoRepository
import java.util.*

interface StockRepository : MongoRepository<Stock, String> {
    fun findBySymbol(symbol: String): Stock?
}
