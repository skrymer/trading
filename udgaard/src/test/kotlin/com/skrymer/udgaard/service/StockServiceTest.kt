package com.skrymer.udgaard.service

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.EntryStrategy
import com.skrymer.udgaard.model.strategy.ExitStrategy
import com.skrymer.udgaard.model.strategy.MainExitStrategy
import com.skrymer.udgaard.model.strategy.Ovtlyr9EntryStrategy
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate

@SpringBootTest
class StockServiceTest() {

  @Autowired
  private lateinit var stockService: StockService

  @Test
  fun `should do something`() {

    // given some stock quotes
    val quote1 = StockQuote(closePrice = 99.9, date = LocalDate.of(2025, 7, 1))
    val quote2 = StockQuote(closePrice = 100.0, date = LocalDate.of(2025, 7, 2))
    val quote3 = StockQuote(closePrice = 100.1, date = LocalDate.of(2025, 7, 3))
    val quote4 = StockQuote(closePrice = 100.2, date = LocalDate.of(2025, 7, 4))
    val quote5 = StockQuote(closePrice = 99.9, date = LocalDate.of(2025, 7, 7))

    val stock = Stock("TEST", "TEST_SECTOR", listOf(quote1, quote2, quote3, quote4, quote5))

    val backtestReport =
      stockService.backtest(
        closePriceIsGreaterThanOrEqualTo100,
        openPriceIsLessThan100,
        listOf(stock),
        LocalDate.of(2024, 1, 1),
        LocalDate.now()
      )
    println(backtestReport)
  }

  @Test
  fun `should calculate report results`() {
    // given some stock quotes
    val quote1 = StockQuote(closePrice = 99.9, date = LocalDate.of(2025, 7, 1))
    // Trade 1 win 3$ 3%
    val quote2 = StockQuote(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 7, 2))
    val quote3 = StockQuote(closePrice = 102.0, openPrice = 100.0, date = LocalDate.of(2025, 7, 3))
    val quote4 = StockQuote(closePrice = 103.0, openPrice = 99.9, date = LocalDate.of(2025, 7, 4))
    // Trade 2 loss 2$ 2%
    val quote5 = StockQuote(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 7, 8))
    val quote6 = StockQuote(closePrice = 101.0, openPrice = 100.0, date = LocalDate.of(2025, 7, 9))
    val quote7 = StockQuote(closePrice = 98.0, openPrice = 99.9, date = LocalDate.of(2025, 7, 10))
    // Trade 3 win 5$ 5%
    val quote8 = StockQuote(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 7, 11))
    val quote9 = StockQuote(closePrice = 102.0, openPrice = 100.0, date = LocalDate.of(2025, 7, 14))
    val quote10 = StockQuote(closePrice = 105.0, openPrice = 99.9, date = LocalDate.of(2025, 7, 15))
    // Trade 4 loss 4$ 4%
    val quote11 = StockQuote(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 7, 16))
    val quote12 = StockQuote(closePrice = 101.0, openPrice = 100.0, date = LocalDate.of(2025, 7, 17))
    val quote13 = StockQuote(closePrice = 96.0, openPrice = 99.9, date = LocalDate.of(2025, 7, 18))
    // Trade 5 win 8$ 8%
    val quote14 = StockQuote(closePrice = 100.0, openPrice = 100.0, date = LocalDate.of(2025, 7, 21))
    val quote15 = StockQuote(closePrice = 102.0, openPrice = 100.0, date = LocalDate.of(2025, 7, 22))
    val quote16 = StockQuote(closePrice = 108.0, openPrice = 99.9, date = LocalDate.of(2025, 7, 23))

    val stock = Stock(
      "TEST",
      "TEST_SECTOR",
      listOf(
        quote1,
        quote2,
        quote3,
        quote4,
        quote5,
        quote6,
        quote7,
        quote8,
        quote9,
        quote10,
        quote11,
        quote12,
        quote13,
        quote14,
        quote15,
        quote16
      )
    )

    val report = stockService.backtest(
      closePriceIsGreaterThanOrEqualTo100,
      openPriceIsLessThan100,
      listOf(stock),
      LocalDate.of(2024, 1, 1),
      LocalDate.now()
    )
    Assertions.assertEquals(3, report.numberOfWinningTrades)
    Assertions.assertEquals(0.6, report.winRate)
    Assertions.assertEquals(5.33, report.averageWinPercent, 0.01)
    Assertions.assertEquals(5.33, report.averageWinAmount, 0.01)

    Assertions.assertEquals(2, report.numberOfLosingTrades)
    Assertions.assertEquals(0.4, report.lossRate)
    Assertions.assertEquals(3.0, report.averageLossPercent)
    Assertions.assertEquals(3.0, report.averageLossAmount)

    Assertions.assertEquals(1.99, report.edge, 0.01)
  }

  @Test
  fun `calculate something`() {

    // PYPL
    val pyplQuotes = listOf(
      StockQuote(
        symbol = "PYPL",
        date = LocalDate.of(2025, 6, 26),
        openPrice = 73.245,
        heatmap = 54.80961587991226,
        previousHeatmap = 53.52984474799194,
        sectorHeatmap = 55.99994791780286,
        previousSectorHeatmap = 55.63197145642206,
        sectorIsInUptrend = true,
        signal = null,
        closePrice = 73.17,
        closePriceEMA10 = 72.2948671533986,
        closePriceEMA5 = 72.5910269810038,
        closePriceEMA20 = 71.960388379967,
        closePriceEMA50 = 70.943468577458,
        trend = "Uptrend",
        lastBuySignal = LocalDate.of(2025, 6, 25),
        lastSellSignal = LocalDate.of(2025, 6, 18),
        spySignal = "Buy",
        spyIsInUptrend = true,
        marketIsInUptrend = true,
        previousQuoteDate = LocalDate.of(2025, 6, 25),
        atr = 1.933485714285712,
        sectorStocksInUptrend = 280,
        sectorStocksInDowntrend = 280,
        sectorBullPercentage = 63.2241813602015,
        high = 73.38,
        low = 71.61
      ),
      StockQuote(
        symbol = "PYPL",
        date = LocalDate.of(2025, 6, 27),
        openPrice = 73.27,
        heatmap = 56.34823923081387,
        previousHeatmap = 54.80961587991226,
        sectorHeatmap = 56.7179565520566,
        previousSectorHeatmap = 55.99994791780286,
        sectorIsInUptrend = true,
        signal = null,
        closePrice = 73.64,
        closePriceEMA10 = 72.5394367618716,
        closePriceEMA5 = 72.9406846540025,
        closePriceEMA20 = 72.1203513913987,
        closePriceEMA50 = 71.0492149077538,
        trend = "Uptrend",
        lastBuySignal = LocalDate.of(2025, 6, 25),
        lastSellSignal = LocalDate.of(2025, 6, 18),
        spySignal = "Buy",
        spyIsInUptrend = true,
        marketIsInUptrend = true,
        previousQuoteDate = LocalDate.of(2025, 6, 26),
        atr = 1.9152714285714256,
        sectorStocksInUptrend = 295,
        sectorStocksInDowntrend = 295,
        sectorBullPercentage = 72.7959697732998,
        high = 73.75,
        low = 72.855
      ),
      StockQuote(
        symbol = "PYPL",
        date = LocalDate.of(2025, 6, 30),
        openPrice = 73.94,
        heatmap = 58.08355294280994,
        previousHeatmap = 56.34823923081387,
        sectorHeatmap = 57.50370316184567,
        previousSectorHeatmap = 56.7179565520566,
        sectorIsInUptrend = true,
        signal = null,
        closePrice = 74.32,
        closePriceEMA10 = 72.8631755324404,
        closePriceEMA5 = 73.4004564360017,
        closePriceEMA20 = 72.329841735075,
        closePriceEMA50 = 71.1774809898027,
        trend = "Uptrend",
        lastBuySignal = LocalDate.of(2025, 6, 25),
        lastSellSignal = LocalDate.of(2025, 6, 18),
        spySignal = "Buy",
        spyIsInUptrend = true,
        marketIsInUptrend = true,
        previousQuoteDate = LocalDate.of(2025, 6, 27),
        atr = 1.9066999999999976,
        sectorStocksInUptrend = 299,
        sectorStocksInDowntrend = 299,
        sectorBullPercentage = 72.544080604534,
        high = 74.54,
        low = 73.66
      ),
      StockQuote(
        symbol = "PYPL",
        date = LocalDate.of(2025, 7, 1),
        openPrice = 73.95,
        heatmap = 59.97029580738159,
        previousHeatmap = 58.08355294280994,
        sectorHeatmap = 58.32056059501168,
        previousSectorHeatmap = 57.50370316184567,
        sectorIsInUptrend = true,
        signal = null,
        closePrice = 75.29,
        closePriceEMA10 = 73.304416344724,
        closePriceEMA5 = 74.0303042906678,
        closePriceEMA20 = 72.6117615698298,
        closePriceEMA50 = 71.3387562451046,
        trend = "Uptrend",
        lastBuySignal = LocalDate.of(2025, 6, 25),
        lastSellSignal = LocalDate.of(2025, 6, 18),
        spySignal = "Buy",
        spyIsInUptrend = true,
        marketIsInUptrend = true,
        previousQuoteDate = LocalDate.of(2025, 6, 30),
        atr = 1.9027714285714257,
        sectorStocksInUptrend = 311,
        sectorStocksInDowntrend = 311,
        sectorBullPercentage = 79.0931989924433,
        high = 75.695,
        low = 73.8
      ),
      StockQuote(
        symbol = "PYPL",
        date = LocalDate.of(2025, 7, 2),
        openPrice = 75.01,
        heatmap = 61.978046985952126,
        previousHeatmap = 59.97029580738159,
        sectorHeatmap = 59.32933258360609,
        previousSectorHeatmap = 58.32056059501168,
        sectorIsInUptrend = true,
        signal = null,
        closePrice = 76.31,
        closePriceEMA10 = 73.8508861002287,
        closePriceEMA5 = 74.7902028604452,
        closePriceEMA20 = 72.9639747536555,
        closePriceEMA50 = 71.5337069805907,
        trend = "Uptrend",
        lastBuySignal = LocalDate.of(2025, 6, 25),
        lastSellSignal = LocalDate.of(2025, 6, 18),
        spySignal = "Buy",
        spyIsInUptrend = true,
        marketIsInUptrend = true,
        previousQuoteDate = LocalDate.of(2025, 7, 1),
        atr = 1.9445571428571407,
        sectorStocksInUptrend = 306,
        sectorStocksInDowntrend = 306,
        sectorBullPercentage = 81.1083123425693,
        high = 76.865,
        low = 75.0
      ),
      StockQuote(
        symbol = "PYPL",
        date = LocalDate.of(2025, 7, 3),
        openPrice = 76.71,
        heatmap = 64.98208372059856,
        previousHeatmap = 61.978046985952126,
        sectorHeatmap = 60.3715569600269,
        previousSectorHeatmap = 59.32933258360609,
        sectorIsInUptrend = true,
        signal = null,
        closePrice = 76.59,
        closePriceEMA10 = 74.348906809278,
        closePriceEMA5 = 75.3901352402968,
        closePriceEMA20 = 73.3093104914026,
        closePriceEMA50 = 71.7319929813518,
        trend = "Uptrend",
        lastBuySignal = LocalDate.of(2025, 6, 25),
        lastSellSignal = LocalDate.of(2025, 6, 18),
        spySignal = "Buy",
        spyIsInUptrend = true,
        marketIsInUptrend = true,
        previousQuoteDate = LocalDate.of(2025, 7, 2),
        atr = 1.9262142857142837,
        sectorStocksInUptrend = 321,
        sectorStocksInDowntrend = 321,
        sectorBullPercentage = 78.3375314861461,
        high = 77.36,
        low = 76.475
      )
    )
    val pypl = Stock(symbol = "PYPL", sectorSymbol = "", quotes = pyplQuotes)
    // SMCI
    val smciQuotes = listOf(
      StockQuote(
        symbol = "SMCI",
        date = LocalDate.of(2025, 6, 26),
        openPrice = 47.055,
        heatmap = 41.65785107980844,
        previousHeatmap = 41.65182536050492,
        sectorHeatmap = 57.74039620049885,
        previousSectorHeatmap = 57.36321773643856,
        sectorIsInUptrend = true,
        signal = null,
        closePrice = 49.27,
        closePriceEMA10 = 44.6348505124788,
        closePriceEMA5 = 45.8736147820046,
        closePriceEMA20 = 43.3206515282924,
        closePriceEMA50 = 40.8484602688507,
        trend = "Uptrend",
        lastBuySignal = LocalDate.of(2025, 6, 25),
        lastSellSignal = LocalDate.of(2025, 6, 24),
        spySignal = "Buy",
        spyIsInUptrend = true,
        marketIsInUptrend = true,
        previousQuoteDate = LocalDate.of(2025, 6, 25),
        atr = 4.267485714285715,
        sectorStocksInUptrend = 214,
        sectorStocksInDowntrend = 214,
        sectorBullPercentage = 51.044776119403,
        high = 49.52,
        low = 46.07
      ),
      StockQuote(
        symbol = "SMCI",
        date = LocalDate.of(2025, 6, 27),
        openPrice = 49.48,
        heatmap = 42.42915181957912,
        previousHeatmap = 41.65785107980844,
        sectorHeatmap = 58.34866491192026,
        previousSectorHeatmap = 57.74039620049885,
        sectorIsInUptrend = true,
        signal = null,
        closePrice = 47.1,
        closePriceEMA10 = 45.1703322374827,
        closePriceEMA5 = 46.4424098546697,
        closePriceEMA20 = 43.7263037636931,
        closePriceEMA50 = 41.1124422190918,
        trend = "Uptrend",
        lastBuySignal = LocalDate.of(2025, 6, 25),
        lastSellSignal = LocalDate.of(2025, 6, 24),
        spySignal = "Buy",
        spyIsInUptrend = true,
        marketIsInUptrend = true,
        previousQuoteDate = LocalDate.of(2025, 6, 26),
        atr = 2.3717000000000006,
        sectorStocksInUptrend = 221,
        sectorStocksInDowntrend = 221,
        sectorBullPercentage = 59.4029850746269,
        high = 49.97,
        low = 46.861
      )
    )
    val smci = Stock(symbol = "SMCI", sectorSymbol = "", quotes = smciQuotes)

    val smtcQuotes = listOf(
      StockQuote(
        symbol = "SMTC",
        date = LocalDate.of(2025, 6, 26),
        openPrice = 44.74,
        heatmap = 62.7544813125464,
        previousHeatmap = 62.30479001339747,
        sectorHeatmap = 57.74039620049885,
        previousSectorHeatmap = 57.36321773643856,
        sectorIsInUptrend = true,
        signal = null,
        closePrice = 45.62,
        closePriceEMA10 = 42.5435048697277,
        closePriceEMA5 = 43.7661977954878,
        closePriceEMA20 = 40.9954072400008,
        closePriceEMA50 = 38.8642051504154,
        trend = "Uptrend",
        lastBuySignal = LocalDate.of(2025, 6, 25),
        lastSellSignal = LocalDate.of(2025, 6, 24),
        spySignal = "Buy",
        spyIsInUptrend = true,
        marketIsInUptrend = true,
        previousQuoteDate = LocalDate.of(2025, 6, 25),
        atr = 1.977142857142857,
        sectorStocksInUptrend = 214,
        sectorStocksInDowntrend = 214,
        sectorBullPercentage = 51.044776119403,
        high = 45.7,
        low = 44.6
      ),
      StockQuote(
        symbol = "SMTC",
        date = LocalDate.of(2025, 6, 27),
        openPrice = 45.87,
        heatmap = 63.56809228455731,
        previousHeatmap = 62.7544813125464,
        sectorHeatmap = 58.34866491192026,
        previousSectorHeatmap = 57.74039620049885,
        sectorIsInUptrend = true,
        signal = null,
        closePrice = 44.91,
        closePriceEMA10 = 42.9737767115954,
        closePriceEMA5 = 44.1474651969919,
        closePriceEMA20 = 41.368225598096,
        closePriceEMA50 = 39.1012951445168,
        trend = "Uptrend",
        lastBuySignal = LocalDate.of(2025, 6, 25),
        lastSellSignal = LocalDate.of(2025, 6, 24),
        spySignal = "Buy",
        spyIsInUptrend = true,
        marketIsInUptrend = true,
        previousQuoteDate = LocalDate.of(2025, 6, 26),
        atr = 2.0007142857142854,
        sectorStocksInUptrend = 221,
        sectorStocksInDowntrend = 221,
        sectorBullPercentage = 59.4029850746269,
        high = 45.98,
        low = 44.25
      ),
      StockQuote(
        symbol = "SMTC",
        date = LocalDate.of(2025, 6, 30),
        openPrice = 45.22,
        heatmap = 64.35753393698333,
        previousHeatmap = 63.56809228455731,
        sectorHeatmap = 58.9124858697415,
        previousSectorHeatmap = 58.34866491192026,
        sectorIsInUptrend = true,
        signal = null,
        closePrice = 45.14,
        closePriceEMA10 = 43.3676354913053,
        closePriceEMA5 = 44.4783101313279,
        closePriceEMA20 = 41.7274422078011,
        closePriceEMA50 = 39.3381070996338,
        trend = "Uptrend",
        lastBuySignal = LocalDate.of(2025, 6, 25),
        lastSellSignal = LocalDate.of(2025, 6, 24),
        spySignal = "Buy",
        spyIsInUptrend = true,
        marketIsInUptrend = true,
        previousQuoteDate = LocalDate.of(2025, 6, 27),
        atr = 1.836428571428571,
        sectorStocksInUptrend = 231,
        sectorStocksInDowntrend = 231,
        sectorBullPercentage = 60.2985074626866,
        high = 45.69,
        low = 44.27
      ),
      StockQuote(
        symbol = "SMTC",
        date = LocalDate.of(2025, 7, 1),
        openPrice = 44.44,
        heatmap = 65.12732005695125,
        previousHeatmap = 64.35753393698333,
        sectorHeatmap = 59.56146983120528,
        previousSectorHeatmap = 58.9124858697415,
        sectorIsInUptrend = true,
        signal = null,
        closePrice = 43.47,
        closePriceEMA10 = 43.3862472201589,
        closePriceEMA5 = 44.1422067542186,
        closePriceEMA20 = 41.8934000927724,
        closePriceEMA50 = 39.5001421153344,
        trend = "Uptrend",
        lastBuySignal = LocalDate.of(2025, 6, 25),
        lastSellSignal = LocalDate.of(2025, 6, 24),
        spySignal = "Buy",
        spyIsInUptrend = true,
        marketIsInUptrend = true,
        previousQuoteDate = LocalDate.of(2025, 6, 30),
        atr = 1.8899999999999995,
        sectorStocksInUptrend = 237,
        sectorStocksInDowntrend = 237,
        sectorBullPercentage = 68.0597014925373,
        high = 44.705,
        low = 42.375
      )
    )
    val smtc = Stock(symbol = "SMTC", sectorSymbol = "", quotes = smtcQuotes)
    val eqnrQuotes = listOf(
      StockQuote(
        symbol = "EQNR",
        date = LocalDate.of(2025, 7, 9),
        openPrice = 26.28,
        heatmap = 60.49634920870701,
        previousHeatmap = 53.009581003958964,
        sectorHeatmap = 58.29380185128821,
        previousSectorHeatmap = 57.96175913916965,
        sectorIsInUptrend = true,
        signal = "Buy",
        closePrice = 26.36,
        closePriceEMA10 = 25.8436488625729,
        closePriceEMA5 = 25.943062860486,
        closePriceEMA20 = 25.6890647844315,
        closePriceEMA50 = 25.0375340654047,
        trend = "Uptrend",
        lastBuySignal = LocalDate.of(2025, 7, 9),
        lastSellSignal = LocalDate.of(2025, 6, 24),
        spySignal = "Buy",
        spyIsInUptrend = true,
        marketIsInUptrend = true,
        previousQuoteDate = LocalDate.of(2025, 7, 8),
        atr = 0.4707071428571426,
        sectorStocksInUptrend = 85,
        sectorStocksInDowntrend = 85,
        sectorBullPercentage = 59.8591549295775,
        high = 26.435,
        low = 26.16
      ),
      StockQuote(
        symbol = "EQNR",
        date = LocalDate.of(2025, 7, 10),
        openPrice = 26.28,
        heatmap = 67.77400726288916,
        previousHeatmap = 60.49634920870701,
        sectorHeatmap = 58.35697010621501,
        previousSectorHeatmap = 58.29380185128821,
        sectorIsInUptrend = true,
        signal = null,
        closePrice = 26.68,
        closePriceEMA10 = 25.9957127057415,
        closePriceEMA5 = 26.1887085736573,
        closePriceEMA20 = 25.7834395668666,
        closePriceEMA50 = 25.1019444942124,
        trend = "Uptrend",
        lastBuySignal = LocalDate.of(2025, 7, 9),
        lastSellSignal = LocalDate.of(2025, 6, 24),
        spySignal = "Buy",
        spyIsInUptrend = true,
        marketIsInUptrend = true,
        previousQuoteDate = LocalDate.of(2025, 7, 9),
        atr = 0.4596285714285709,
        sectorStocksInUptrend = 93,
        sectorStocksInDowntrend = 93,
        sectorBullPercentage = 59.8591549295775,
        high = 26.685,
        low = 26.25
      ),
      StockQuote(
        symbol = "EQNR",
        date = LocalDate.of(2025, 7, 11),
        openPrice = 26.74,
        heatmap = 73.96104951236157,
        previousHeatmap = 67.77400726288916,
        sectorHeatmap = 58.513208993677424,
        previousSectorHeatmap = 58.35697010621501,
        sectorIsInUptrend = true,
        signal = null,
        closePrice = 27.1,
        closePriceEMA10 = 26.1964922137885,
        closePriceEMA5 = 26.4924723824382,
        closePriceEMA20 = 25.9088262747841,
        closePriceEMA50 = 25.1802996120864,
        trend = "Uptrend",
        lastBuySignal = LocalDate.of(2025, 7, 9),
        lastSellSignal = LocalDate.of(2025, 6, 24),
        spySignal = "Buy",
        spyIsInUptrend = true,
        marketIsInUptrend = false,
        previousQuoteDate = LocalDate.of(2025, 7, 10),
        atr = 0.47284285714285673,
        sectorStocksInUptrend = 95,
        sectorStocksInDowntrend = 95,
        sectorBullPercentage = 61.2676056338028,
        high = 27.175,
        low = 26.685
      )
    )
    val eqnr = Stock(symbol = "EQNR", sectorSymbol = "", quotes = eqnrQuotes)
    val report = stockService.backtest(
      Ovtlyr9EntryStrategy(),
      MainExitStrategy(),
      listOf(pypl, smci, smtc, eqnr),
      LocalDate.of(2024, 1, 1),
      LocalDate.now()
    )

    println(report.edge)
  }


  val closePriceIsGreaterThanOrEqualTo100 = object : EntryStrategy {
    override fun description() = "Test entry strategy"
    override fun test(quote: StockQuote, previousQuote: StockQuote?) = quote.closePrice >= 100.0
  }

  val openPriceIsLessThan100 = object : ExitStrategy {
    override fun match(entryQuote: StockQuote?, quote: StockQuote, previousQuote: StockQuote?) = quote.openPrice < 100.0
    override fun reason(entryQuote: StockQuote?, quote: StockQuote, previousQuote: StockQuote?) =
      "Because stone cold said so!"

    override fun description() = ""
  }
}