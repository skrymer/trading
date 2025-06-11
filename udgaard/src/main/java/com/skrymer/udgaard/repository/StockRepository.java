package com.skrymer.udgaard.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.skrymer.udgaard.model.Stock;

public interface StockRepository extends MongoRepository<Stock, String>  {

    Optional<Stock> findBySymbol(String symbol);
}
