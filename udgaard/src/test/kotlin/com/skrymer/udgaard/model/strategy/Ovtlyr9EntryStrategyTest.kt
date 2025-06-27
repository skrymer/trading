package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.StockQuote
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class Ovtlyr9EntryStrategyTest {

    @Test
    fun `should pass Ovtlyr9-EntryStrategy when all criteria are true`(){
        val ovtlyr9EntryStrategy = Ovtlyr9EntryStrategy()

        // given stock quote matches the ovtlyr 9 entry strategy
        val stockQuote = StockQuote(
            symbol = "TEST",
            date = LocalDate.of(2025, 6, 16),
            openPrice = 110.0,
            // Heatmap value is going up
            heatmap = 2.0,
            previousHeatmap = 1.0,
            // Sector heatmap value is going up
            sectorHeatmap = 2.0,
            previousSectorHeatmap = 1.0,
            // Sector is in an uptrend
            sectorIsInUptrend = true,
            signal = "Buy",
            // Close price is higher than 10 EMA
            closePrice = 100.0,
            closePriceEMA10 = 99.0,
            closePriceEMA5 = 10.0,
            closePriceEMA20 = 10.0,
            closePriceEMA50 = 99.0,
            // Stock is in an uptrend
            trend = "Uptrend",
            // Last buy signal is more recent than last sell signal
            lastBuySignal = LocalDate.of(2025, 6, 16),
            lastSellSignal = LocalDate.of(2025, 6, 15),
            // SPY has a buy signal
            spySignal = "Buy",
            // Spy is in an uptrend
            spyIsInUptrend = true,
            // Market is in an uptrend
            marketIsInUptrend = true,
            previousQuoteDate = LocalDate.now(),
            atr = 1.0
        )

        // then quote matches the strategy
        assertTrue(ovtlyr9EntryStrategy.test(stockQuote))
    }

    @Test
    fun shouldPassOvtlyr9EntryStrategyWhenAFridayAndItsMonday(){
        val ovtlyr9EntryStrategy = Ovtlyr9EntryStrategy()

        // given stock quote matches the ovtlyr 9 entry strategy
        val stockQuote = StockQuote(
            symbol = "TEST",
            // The quote was from a Monday
            date = LocalDate.of(2025, 6, 16),
            // Last buy signal was from the previous Friday
            lastBuySignal = LocalDate.of(2025, 6, 13),
            // and the Sell signal is older than buy signal
            lastSellSignal = LocalDate.of(2025, 6, 12),
            openPrice = 110.0,
            // Heatmap value is going up
            heatmap = 2.0,
            previousHeatmap = 1.0,
            // Sector heatmap value is going up
            sectorHeatmap = 2.0,
            previousSectorHeatmap = 1.0,
            // Sector is in an uptrend
            sectorIsInUptrend = true,
            signal = "Buy",
            // Close price is higher than 10 EMA
            closePrice = 100.0,
            closePriceEMA10 = 99.0,
            closePriceEMA5 = 10.0,
            closePriceEMA20 = 10.0,
            closePriceEMA50 = 99.0,
            // Stock is in an uptrend
            trend = "Uptrend",
            // SPY has a buy signal
            spySignal = "Buy",
            // Spy is in an uptrend
            spyIsInUptrend = true,
            // Market is in an uptrend
            marketIsInUptrend = true,
            previousQuoteDate = LocalDate.now(),
            atr = 1.0
        )

        // then quote matches the strategy
        assertTrue(ovtlyr9EntryStrategy.test(stockQuote))
    }

    @Test
    fun shouldNotMatchStrategyWhenInADownTrend(){
        val ovtlyr9EntryStrategy = Ovtlyr9EntryStrategy()

        // given stock quote matches the ovtlyr 9 entry strategy apart from it being in a downtrend
        val stockQuote = StockQuote(
            symbol = "TEST",
            date = LocalDate.of(2025, 6, 16),
            openPrice = 110.0,
            // Heatmap value is going up
            heatmap = 2.0,
            previousHeatmap = 1.0,
            // Sector heatmap value is going up
            sectorHeatmap = 2.0,
            previousSectorHeatmap = 1.0,
            // Sector is in an uptrend
            sectorIsInUptrend = true,
            signal = "Buy",
            // Close price is higher than 10 EMA
            closePrice = 100.0,
            closePriceEMA10 = 99.0,
            closePriceEMA5 = 10.0,
            closePriceEMA20 = 10.0,
            closePriceEMA50 = 99.0,
            // Stock is in a downtrend
            trend = "Downtrend",
            // Last buy signal is more recent than last sell signal
            lastBuySignal = LocalDate.of(2025, 6, 16),
            lastSellSignal = LocalDate.of(2025, 6, 15),
            // SPY has a buy signal
            spySignal = "Buy",
            // Spy is in an uptrend
            spyIsInUptrend = true,
            // Market is in an uptrend
            marketIsInUptrend = true,
            previousQuoteDate = LocalDate.now(),
            atr = 1.0
        )

        // then does not quote matches the strategy
        assertFalse(ovtlyr9EntryStrategy.test(stockQuote))
    }

    @Test
    fun shouldNotMatchStrategyWhenSpyIsInADowntrend(){
        val ovtlyr9EntryStrategy = Ovtlyr9EntryStrategy()

        // given SPY is in a downtrend
        val stockQuote = StockQuote(
            symbol = "TEST",
            date = LocalDate.of(2025, 6, 16),
            openPrice = 110.0,
            // Heatmap value is going up
            heatmap = 2.0,
            previousHeatmap = 1.0,
            // Sector heatmap value is going up
            sectorHeatmap = 2.0,
            previousSectorHeatmap = 1.0,
            // Sector is in an uptrend
            sectorIsInUptrend = true,
            signal = "Buy",
            // Close price is higher than 10 EMA
            closePrice = 100.0,
            closePriceEMA10 = 99.0,
            closePriceEMA5 = 10.0,
            closePriceEMA20 = 10.0,
            closePriceEMA50 = 99.0,
            // Stock is in an uptrend
            trend = "Uptrend",
            // Last buy signal is more recent than last sell signal
            lastBuySignal = LocalDate.of(2025, 6, 16),
            lastSellSignal = LocalDate.of(2025, 6, 15),
            // SPY has a buy signal
            spySignal = "Buy",
            // Spy is in a downtrend
            spyIsInUptrend = false,
            // Market is in an uptrend
            marketIsInUptrend = true,
            previousQuoteDate = LocalDate.now(),
            atr = 1.0
        )

        // then does not quote matches the strategy
        assertFalse(ovtlyr9EntryStrategy.test(stockQuote))
    }

    @Test
    fun shouldNotMatchStrategyWhenSpyHasNoBuySignal(){
        val ovtlyr9EntryStrategy = Ovtlyr9EntryStrategy()

        // given SPY has no Buy signal
        val stockQuote = StockQuote(
            symbol = "TEST",
            date = LocalDate.of(2025, 6, 16),
            openPrice = 110.0,
            // Heatmap value is going up
            heatmap = 2.0,
            previousHeatmap = 1.0,
            // Sector heatmap value is going up
            sectorHeatmap = 2.0,
            previousSectorHeatmap = 1.0,
            // Sector is in an uptrend
            sectorIsInUptrend = true,
            signal = "Buy",
            // Close price is higher than 10 EMA
            closePrice = 100.0,
            closePriceEMA10 = 99.0,
            closePriceEMA5 = 10.0,
            closePriceEMA20 = 10.0,
            closePriceEMA50 = 99.0,
            // Stock is in an uptrend
            trend = "Uptrend",
            // Last buy signal is more recent than last sell signal
            lastBuySignal = LocalDate.of(2025, 6, 16),
            lastSellSignal = LocalDate.of(2025, 6, 15),
            // SPY has a Sell signal
            spySignal = "Sell",
            // Spy is in an uptrend
            spyIsInUptrend = true,
            // Market is in an uptrend
            marketIsInUptrend = true,
            previousQuoteDate = LocalDate.now(),
            atr = 1.0
        )

        // then does not quote matches the strategy
        assertFalse(ovtlyr9EntryStrategy.test(stockQuote))
    }

    @Test
    fun shouldNotMatchStrategyWhenSectorIsInADowntrend(){
        val ovtlyr9EntryStrategy = Ovtlyr9EntryStrategy()

        // given sector is in downtrend
        val stockQuote = StockQuote(
            symbol = "TEST",
            date = LocalDate.of(2025, 6, 16),
            openPrice = 110.0,
            // Heatmap value is going up
            heatmap = 2.0,
            previousHeatmap = 1.0,
            // Sector heatmap value is going up
            sectorHeatmap = 2.0,
            previousSectorHeatmap = 1.0,
            // Sector is in a downtrend
            sectorIsInUptrend = false,
            signal = "Buy",
            // Close price is higher than 10 EMA
            closePrice = 100.0,
            closePriceEMA10 = 99.0,
            closePriceEMA5 = 10.0,
            closePriceEMA20 = 10.0,
            closePriceEMA50 = 99.0,
            // Stock is in an uptrend
            trend = "Uptrend",
            // Last buy signal is more recent than last sell signal
            lastBuySignal = LocalDate.of(2025, 6, 16),
            lastSellSignal = LocalDate.of(2025, 6, 15),
            // SPY has a buy signal
            spySignal = "Buy",
            // Spy is in an uptrend
            spyIsInUptrend = true,
            // Market is in an uptrend
            marketIsInUptrend = true,
            previousQuoteDate = LocalDate.now(),
            atr = 1.0
        )

        // then does not quote matches the strategy
        assertFalse(ovtlyr9EntryStrategy.test(stockQuote))
    }

    @Test
    fun shouldNotMatchStrategyWhenSectorHeatmapIsGettingMoreFearful(){
        val ovtlyr9EntryStrategy = Ovtlyr9EntryStrategy()

        // given sector heatmap is getting more fearful
        val stockQuote = StockQuote(
            symbol = "TEST",
            date = LocalDate.of(2025, 6, 16),
            openPrice = 110.0,
            // Heatmap value is going up
            heatmap = 2.0,
            previousHeatmap = 1.0,
            // Sector heatmap is getting more fearful
            sectorHeatmap = 2.0,
            previousSectorHeatmap = 3.0,
            // Sector is in an uptrend
            sectorIsInUptrend = true,
            signal = "Buy",
            // Close price is higher than 10 EMA
            closePrice = 100.0,
            closePriceEMA10 = 99.0,
            closePriceEMA5 = 10.0,
            closePriceEMA20 = 10.0,
            closePriceEMA50 = 99.0,
            // Stock is in an uptrend
            trend = "Uptrend",
            // Last buy signal is more recent than last sell signal
            lastBuySignal = LocalDate.of(2025, 6, 16),
            lastSellSignal = LocalDate.of(2025, 6, 15),
            // SPY has a buy signal
            spySignal = "Buy",
            // Spy is in an uptrend
            spyIsInUptrend = true,
            // Market is in an uptrend
            marketIsInUptrend = true,
            previousQuoteDate = LocalDate.now(),
            atr = 1.0
        )

        // then does not quote matches the strategy
        assertFalse(ovtlyr9EntryStrategy.test(stockQuote))
    }

    @Test
    fun shouldNotMatchStrategyWhenStockIsInADowntrend(){
        val ovtlyr9EntryStrategy = Ovtlyr9EntryStrategy()

        // given stock is in a downtrend
        val stockQuote = StockQuote(
            symbol = "TEST",
            date = LocalDate.of(2025, 6, 16),
            openPrice = 110.0,
            // Heatmap value is going up
            heatmap = 2.0,
            previousHeatmap = 1.0,
            // Sector heatmap value is going up
            sectorHeatmap = 2.0,
            previousSectorHeatmap = 1.0,
            // Sector is in an uptrend
            sectorIsInUptrend = true,
            signal = "Buy",
            // Close price is higher than 10 EMA
            closePrice = 100.0,
            closePriceEMA10 = 99.0,
            closePriceEMA5 = 10.0,
            closePriceEMA20 = 10.0,
            closePriceEMA50 = 99.0,
            // Stock is in a downtrend
            trend = "Downtrend",
            // Last buy signal is more recent than last sell signal
            lastBuySignal = LocalDate.of(2025, 6, 16),
            lastSellSignal = LocalDate.of(2025, 6, 15),
            // SPY has a buy signal
            spySignal = "Buy",
            // Spy is in an uptrend
            spyIsInUptrend = true,
            // Market is in an uptrend
            marketIsInUptrend = true,
            previousQuoteDate = LocalDate.now(),
            atr = 1.0
        )

        // then does not quote matches the strategy
        assertFalse(ovtlyr9EntryStrategy.test(stockQuote))
    }

    // What if there is a buy signal on a Friday and the quote is from the following Monday?
    @Test
    fun shouldNotMatchStrategyWhenStockHasNoCurrentBuySignalWithinTheLast2Days(){
        val ovtlyr9EntryStrategy = Ovtlyr9EntryStrategy()

        // given current buy signal is not within the last 2 days of the quote date
        val stockQuote = StockQuote(
            symbol = "TEST",
            date = LocalDate.of(2025, 6, 16),
            // Last buy signal is more recent than last sell but older than 2 days
            lastBuySignal = LocalDate.of(2025, 6, 14),
            lastSellSignal = LocalDate.of(2025, 6, 13),
            openPrice = 110.0,
            // Heatmap value is going up
            heatmap = 2.0,
            previousHeatmap = 1.0,
            // Sector heatmap value is going up
            sectorHeatmap = 2.0,
            previousSectorHeatmap = 1.0,
            // Sector is in an uptrend
            sectorIsInUptrend = true,
            signal = "Buy",
            // Close price is higher than 10 EMA
            closePrice = 100.0,
            closePriceEMA10 = 99.0,
            closePriceEMA5 = 10.0,
            closePriceEMA20 = 10.0,
            closePriceEMA50 = 99.0,
            // Stock is in an uptrend
            trend = "Uptrend",
            // SPY has a buy signal
            spySignal = "Buy",
            // Spy is in an uptrend
            spyIsInUptrend = true,
            // Market is in an uptrend
            marketIsInUptrend = true,
            previousQuoteDate = LocalDate.now(),
            atr = 1.0
        )

        // then does not quote matches the strategy
        assertFalse(ovtlyr9EntryStrategy.test(stockQuote))
    }

    @Test
    fun shouldNotMatchStrategyWhenStockWhenCurrentSellSignalIsMoreRecentThanTheBuySignal(){
        val ovtlyr9EntryStrategy = Ovtlyr9EntryStrategy()

        // given a buy signal one day before quote date and a sell signal on quote day.
        val stockQuote = StockQuote(
            symbol = "TEST",
            date = LocalDate.of(2025, 6, 16),
            // Last buy signal was the day before the quote date
            lastBuySignal = LocalDate.of(2025, 6, 15),
            // Last sell signal was same day as quote date
            lastSellSignal = LocalDate.of(2025, 6, 16),
            openPrice = 110.0,
            // Heatmap value is going up
            heatmap = 2.0,
            previousHeatmap = 1.0,
            // Sector heatmap value is going up
            sectorHeatmap = 2.0,
            previousSectorHeatmap = 1.0,
            // Sector is in an uptrend
            sectorIsInUptrend = true,
            signal = "Buy",
            // Close price is higher than 10 EMA
            closePrice = 100.0,
            closePriceEMA10 = 99.0,
            closePriceEMA5 = 10.0,
            closePriceEMA20 = 10.0,
            closePriceEMA50 = 99.0,
            // Stock is in an uptrend
            trend = "Uptrend",
            // SPY has a buy signal
            spySignal = "Buy",
            // Spy is in an uptrend
            spyIsInUptrend = true,
            // Market is in an uptrend
            marketIsInUptrend = true,
            previousQuoteDate = LocalDate.now(),
            atr = 1.0
        )

        // then does not quote matches the strategy
        assertFalse(ovtlyr9EntryStrategy.test(stockQuote))
    }

    @Test
    fun shouldNotMatchStrategyWhenStockHeatmapIsGettingMoreFearful(){
        val ovtlyr9EntryStrategy = Ovtlyr9EntryStrategy()

        // given stock quote matches the ovtlyr 9 entry strategy
        val stockQuote = StockQuote(
            symbol = "TEST",
            date = LocalDate.of(2025, 6, 16),
            openPrice = 110.0,
            // Heatmap value is going down
            heatmap = 2.0,
            previousHeatmap = 3.0,
            // Sector heatmap value is going up
            sectorHeatmap = 2.0,
            previousSectorHeatmap = 1.0,
            // Sector is in an uptrend
            sectorIsInUptrend = true,
            signal = "Buy",
            // Close price is higher than 10 EMA
            closePrice = 100.0,
            closePriceEMA10 = 99.0,
            closePriceEMA5 = 10.0,
            closePriceEMA20 = 10.0,
            closePriceEMA50 = 99.0,
            // Stock is in an uptrend
            trend = "Uptrend",
            // Last buy signal is more recent than last sell signal
            lastBuySignal = LocalDate.of(2025, 6, 16),
            lastSellSignal = LocalDate.of(2025, 6, 15),
            // SPY has a buy signal
            spySignal = "Buy",
            // Spy is in an uptrend
            spyIsInUptrend = true,
            // Market is in an uptrend
            marketIsInUptrend = true,
            previousQuoteDate = LocalDate.now(),
            atr = 1.0
        )

        // then does not quote matches the strategy
        assertFalse(ovtlyr9EntryStrategy.test(stockQuote))
    }

    @Test
    fun shouldNotMatchStrategyWhenPriceIsBelowThe10EMA(){
        val ovtlyr9EntryStrategy = Ovtlyr9EntryStrategy()

        // given stock quote close price is below the 10 EMA
        val stockQuote = StockQuote(
            symbol = "TEST",
            date = LocalDate.of(2025, 6, 16),
            openPrice = 110.0,
            // Heatmap value is going up
            heatmap = 2.0,
            previousHeatmap = 1.0,
            // Sector heatmap value is going up
            sectorHeatmap = 2.0,
            previousSectorHeatmap = 1.0,
            // Sector is in an uptrend
            sectorIsInUptrend = true,
            signal = "Buy",
            // Close price is lower than 10 EMA
            closePrice = 100.0,
            closePriceEMA10 = 101.0,
            closePriceEMA5 = 10.0,
            closePriceEMA20 = 10.0,
            closePriceEMA50 = 99.0,
            // Stock is in an uptrend
            trend = "Uptrend",
            // Last buy signal is more recent than last sell signal
            lastBuySignal = LocalDate.of(2025, 6, 16),
            lastSellSignal = LocalDate.of(2025, 6, 15),
            // SPY has a buy signal
            spySignal = "Buy",
            // Spy is in an uptrend
            spyIsInUptrend = true,
            // Market is in an uptrend
            marketIsInUptrend = true,
            previousQuoteDate = LocalDate.now(),
            atr = 1.0
        )

        // then does not quote matches the strategy
        assertFalse(ovtlyr9EntryStrategy.test(stockQuote))
    }

}