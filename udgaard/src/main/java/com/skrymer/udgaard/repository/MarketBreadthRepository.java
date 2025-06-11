package com.skrymer.udgaard.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.skrymer.udgaard.model.MarketBreadth;
import com.skrymer.udgaard.model.MarketSymbol;
import com.skrymer.udgaard.model.Stock;

public interface MarketBreadthRepository extends MongoRepository<MarketBreadth, String> {
    Optional<Stock> findBySymbol(MarketSymbol symbol);
}
