package com.skrymer.udgaard.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.skrymer.udgaard.model.Stock;
import com.skrymer.udgaard.repository.StockRepository;

@Service
public class StockService {
    
    private StockRepository stockRepository;

    public StockService(StockRepository stockRepository){
        this.stockRepository = stockRepository;
    }
    
    public Optional<Stock> getStock(String symbol){
        return stockRepository.findBySymbol(symbol);
    }
}
