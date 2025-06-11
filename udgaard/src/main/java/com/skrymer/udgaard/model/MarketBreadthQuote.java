package com.skrymer.udgaard.model;

import java.time.LocalDate;

public class MarketBreadthQuote {
    private String symbol;
    private LocalDate quoteDate;
    /**
     * Number of stocks in the market with a buys signal.
     */
    private Integer numberOfStocksWithABuySignal;
    /**
     * Number of stocks in the market with a sell signal
     */
    private Integer numberOfStocksWithASellSignal;
    /**
     * Number of stocks in the market in an uptrend - 10ema > 20ema and price over 50ema.
     */
    private Integer numberOfStocksInUptrend;
    /**
     * Number of stocks in the market that are not in an up/down trend.
     */
    private Integer numberOfStocksInNeutral;
    /**
     * Number of stocks in the market in an downtrend - 10ema < 20ema and price under 50ema.
     */
    private Integer numberOfStocksInDowntrend;
    /**
     * The percentage of stocks with a buy signal in this market.
     */
    private Double bullStocksPercentage;
    /**
     * The 5 ema percentage value of stocks with a buy signal in this market.
     */
    private Double ema_5;
    /**
     * The 10 ema percentage value of stocks with a buy signal in this market.
     */
    private Double ema_10;
    /**
     * The 20 ema percentage value of stocks with a buy signal in this market.
     */
    private Double ema_20;
    /**
     * The 50 ema percentage value of stocks with a buy signal in this market.
     */
    private Double ema_50;

    public MarketBreadthQuote() {}
    
    public MarketBreadthQuote(String symbol, LocalDate quoteDate, Integer numberOfStocksWithABuySignal,
            Integer numberOfStocksWithASellSignal, Integer numberOfStocksInUptrend, Integer numberOfStocksInNeutral,
            Integer numberOfStocksInDowntrend, Double ema_5, Double ema_10, Double ema_20, Double ema_50, Double bullStocksPercentage) {
        this.symbol = symbol;
        this.quoteDate = quoteDate;
        this.numberOfStocksWithABuySignal = numberOfStocksWithABuySignal;
        this.numberOfStocksWithASellSignal = numberOfStocksWithASellSignal;
        this.numberOfStocksInUptrend = numberOfStocksInUptrend;
        this.numberOfStocksInNeutral = numberOfStocksInNeutral;
        this.numberOfStocksInDowntrend = numberOfStocksInDowntrend;
        this.ema_5 = ema_5;
        this.ema_10 = ema_10;
        this.ema_20 = ema_20;
        this.ema_50 = ema_50;
        this.bullStocksPercentage = bullStocksPercentage;
    }

    public String getSymbol() {
        return symbol;
    }

    public LocalDate getQuoteDate() {
        return quoteDate;
    }

    public Integer getNumberOfStocksWithABuySignal() {
        return numberOfStocksWithABuySignal;
    }

    public Integer getNumberOfStocksWithASellSignal() {
        return numberOfStocksWithASellSignal;
    }

    public Integer getNumberOfStocksInUptrend() {
        return numberOfStocksInUptrend;
    }

    public Integer getNumberOfStocksInNeutral() {
        return numberOfStocksInNeutral;
    }

    public Integer getNumberOfStocksInDowntrend() {
        return numberOfStocksInDowntrend;
    }

    public Double getEma_5() {
        return ema_5;
    }

    public Double getEma_10() {
        return ema_10;
    }

    public Double getEma_20() {
        return ema_20;
    }

    public Double getEma_50() {
        return ema_50;
    }

    public Double getBullStocksPercentage() {
        return bullStocksPercentage;
    }

    /**
     * 
     * @return true if percentage of bullish stocks are higher than the 10ema.
     */
    public boolean isInUptrend(){
        return Double.compare(bullStocksPercentage, ema_10) > 0;
    }
}
