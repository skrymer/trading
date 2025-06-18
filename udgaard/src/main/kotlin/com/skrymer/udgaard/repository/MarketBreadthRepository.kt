package com.skrymer.udgaard.repository

import com.skrymer.udgaard.model.MarketBreadth
import com.skrymer.udgaard.model.MarketSymbol
import org.springframework.data.mongodb.repository.MongoRepository

interface MarketBreadthRepository : MongoRepository<MarketBreadth, MarketSymbol>
