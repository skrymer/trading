package com.skrymer.udgaard.integration.ovtlyr.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.skrymer.udgaard.model.MarketBreadth
import com.skrymer.udgaard.model.Stock
import java.time.LocalDate

/**
 * Represents stock information comming from the Ovtlyr service.
 */
class OvtlyrStockInformation {
    val resultDetail: String? = null
    var stockName: String? = null
        private set
    var sectorSymbol: String? = null
        private set
    @JsonProperty("lst_h")
    private val quotes: List<OvtlyrStockQuote> = emptyList()

    fun toModel(
        marketBreadth: MarketBreadth?,
        sectorMarketBreadth: MarketBreadth?,
        spy: OvtlyrStockInformation
    ): Stock {
        return Stock(
            this.stockName,
            this.sectorSymbol,
            this.quotes.map { quote ->
                val sectorBreadthQuote = sectorMarketBreadth?.getQuoteForDate(quote.getDate())
                val marketBreadthQuote = marketBreadth?.getQuoteForDate(quote.getDate())
                quote.toModel(this, marketBreadthQuote, sectorBreadthQuote, spy)
            }
        )
    }

    fun getQuotes(): List<OvtlyrStockQuote?> {
        return quotes
    }

    override fun toString(): String {
        return stockName!!
    }

    /**
     * Map nested objct stkDetail
     * @param stkDetail
     *
     * Ovtlyr example payload:
     * {
     * "stockSymbol": "NVDA",
     * "symbolSEName": "NVDA",
     * "stockName": "NVIDIA Corp",
     * "sectorName": "Technology",
     * "sectorSymbol": "XLK",
     * "sectorHeatMap": 82.69744781359672,
     * "stockExchange": "NASDAQ",
     * "stockDesc": "NVIDIA is the world leader in accelerated computing.",
     * "stockTypeID": 1,
     * "isFavourite": true,
     * "quotedate": "2025-06-05T00:00:00",
     * "quotedateStr": "06-05-2025",
     * "avgVolume": 216735185,
     * "weekRange52_high": null,
     * "weekRange52_low": null,
     * "lastPrice": 139.99,
     * "marketCap": 3415756000000,
     * "peRatio": 44.4910516581,
     * "gics_IndexTracked": null,
     * "gics_ExpenseRation": null,
     * "buySellDate": "2025-06-03T00:00:00",
     * "buySellDateStr": "Jun 03, 2025",
     * "buySellStatus": "Sell",
     * "heatMap": 63.41708828753126,
     * "oscilatorMovingUpDown": "Down",
     * "sectorOscilatorMovingUpDown": "Down",
     * "oscilator": 0.6341708828753125,
     * "buySellFinalRegion": 1,
     * "buySellFinalRegionLastChangedDate": "2025-05-02T00:00:00",
     * "buySellFinalRegionLastChangedDateStr": "May 02, 2025",
     * "isNotify": false,
     * "masterKey_SectorSymbol": "XLK",
     * "masterKey_Quotedate": "2025-06-05T00:00:00",
     * "masterKey_Bull_per": 41.7910447761194,
     * "masterKey_MarketBreadthArrow": "up",
     * "masterKey_BuySellStatus_Spy": "Sell",
     * "masterKey_BuySellQuoteDate_Spy": "2025-06-02T00:00:00",
     * "masterKey_MarketTrend_Spy": "Bullish",
     * "masterKey_Bull_per_FullStock": 40.2183039462637,
     * "masterKey_MarketBreadthArrow_FullStock": "down",
     * "notificationCondition": null
     * }
     */
    @JsonProperty("stkDetail")
    private fun unpackNested(stkDetail: MutableMap<String?, Any?>) {
        this.stockName = stkDetail["stockSymbol"] as String?
        this.sectorSymbol = stkDetail["sectorSymbol"] as String?
    }

    /**
     *
     * @param date
     * @return the closest buy signal to the given date
     */
    fun getLastBuySignal(date: LocalDate): LocalDate? {
        return quotes
            // Only care about quotes with a buy signal
            .filter { it.hasBuySignal() }
            // Sort by quote date desc
            .sortedByDescending { it.getDate()}
            // Only look at quotes that are before or equal to the given date
            .firstOrNull { it.getDate().isBefore(date) || it.getDate().isEqual(date) }
            ?.getDate()
    }

    /**
     *
     * @param date
     * @return the closest sell signal to the given date
     */
    fun getLastSellSignal(date: LocalDate): LocalDate? {
        return quotes
            // Only care about quotes with a sell signal
            .filter { it.hasSellSignal() }
            // Sort by quote date desc
            .sortedByDescending { it.getDate()}
            // Only look at quotes that are before or equal to the given date
            .firstOrNull { it.getDate().isBefore(date) || it.getDate().isEqual(date) }
            ?.getDate()
    }

    fun getCurrentSignalFrom(from: LocalDate): String {
        val lastSellSignal = getLastSellSignal(from)
        val lastBuySignal = getLastBuySignal(from)

        if(lastBuySignal != null && lastSellSignal == null){
            return "Buy"
        }

        return if (lastBuySignal?.isAfter(lastSellSignal) == true) "Buy" else "Sell"
    }

    fun getQuoteForDate(date: LocalDate) = getQuotes().first { it?.getDate() == date }

    fun getPreviousQuote(quote: OvtlyrStockQuote) =
        getQuotes().sortedByDescending { it?.getDate() }.firstOrNull { quote.getDate().isAfter(it!!.getDate()) }
}
