package com.skrymer.udgaard.integration.ovtlyr.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.skrymer.udgaard.model.MarketBreadthQuote;

/**
 * Represents a market-breadth quote.
 * 
 * Ovtlyr payload:
 * {
 *   "StockSymbol": "FULLSTOCK",
 *   "Quotedate": "2025-06-05T00:00:00",
 *   "QuotedateStr": "Jun 05, 2025",
 *   "Total_ClosePrice": 980203.1443,
 *   "Bull_Total": 958,
 *   "Bear_Total": 1424,
 *   "Uptrend_DifferenceWithPrevious": -29,
 *   "Downtrend_DifferenceWithPrevious": 33,
 *   "Uptrend_DifferenceWithPrevious_str": "-29",
 *   "Downtrend_DifferenceWithPrevious_str": "+33",
 *   "Bull_per": 40.2183039462637,
 *   "Bull_EMA_5": 38.8829413699683,
 *   "Bull_EMA_10": 41.453098749149,
 *   "Bull_EMA_20": 46.8689974278856,
 *   "Bull_EMA_50": 47.1806479235534,
 *   "Lower": 25,
 *   "Midpoint": 50,
 *   "Upper": 75,
 *   "Uptrend": 1163,
 *   "Neutral": 571,
 *   "Downtrend": 648,
 *   "Total": 2382
 *   }
 */
public class OvtlyrMarketBreadthQuote {
    @JsonProperty("StockSymbol")
    private String symbol;
    @JsonProperty("Quotedate")
    private LocalDate quoteDate;
    @JsonProperty("Bull_Total")
    private Integer numberOfStocksWithABuySignal;
    @JsonProperty("Bear_Total")
    private Integer numberOfStocksWithASellSignal;
    @JsonProperty("Uptrend")
    private Integer numberOfStocksInUptrend;
    @JsonProperty("Neutral")
    private Integer numberOfStocksInNeutral;
    @JsonProperty("Downtrend")
    private Integer numberOfStocksInDowntrend;
    @JsonProperty("Bull_per")
    private Double bull_per;
    @JsonProperty("Bull_EMA_5")
    private Double ema_5;
    @JsonProperty("Bull_EMA_10")
    private Double ema_10;
    @JsonProperty("Bull_EMA_20")
    private Double ema_20;
    @JsonProperty("Bull_EMA_50")
    private Double ema_50;

    public OvtlyrMarketBreadthQuote(){}

    public MarketBreadthQuote toModel() {
        return new MarketBreadthQuote(
            symbol,
            quoteDate,
            numberOfStocksWithABuySignal,
            numberOfStocksWithASellSignal,
            numberOfStocksInUptrend,
            numberOfStocksInNeutral,
            numberOfStocksInDowntrend,
            ema_5, 
            ema_10,
            ema_20,
            ema_50,
            bull_per
        );
    }

    public Double getEma_10() {
        return ema_10;
    }

    public Double getEma_20() {
        return ema_20;
    }

    public Double getEma_5() {
        return ema_5;
    }

    public Double getEma_50() {
        return ema_50;
    }

    public Integer getNumberOfStocksInDowntrend() {
        return numberOfStocksInDowntrend;
    }

    public Integer getNumberOfStocksInNeutral() {
        return numberOfStocksInNeutral;
    }

    public Integer getNumberOfStocksInUptrend() {
        return numberOfStocksInUptrend;
    }

    public Integer getNumberOfStocksWithABuySignal() {
        return numberOfStocksWithABuySignal;
    }

    public Integer getNumberOfStocksWithASellSignal() {
        return numberOfStocksWithASellSignal;
    }

    public LocalDate getQuoteDate() {
        return quoteDate;
    }

    public String getSymbol() {
        return symbol;
    }
}