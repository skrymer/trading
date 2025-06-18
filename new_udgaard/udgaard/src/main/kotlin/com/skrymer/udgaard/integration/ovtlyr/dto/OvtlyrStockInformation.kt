package com.skrymer.udgaard.integration.ovtlyr.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.skrymer.udgaard.model.MarketBreadth
import com.skrymer.udgaard.model.Stock
import java.time.LocalDate
import java.util.*

/**
 * Represents stock information comming from the Ovtlyr service.
 */
class OvtlyrStockInformation {
    @JsonProperty("resultDetail")
    val result: String? = null
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
                val sectorBreadthQuote =
                    sectorMarketBreadth?.getQuoteForDate(quote.getDate())
                val marketBreadthQuote = marketBreadth?.getQuoteForDate(quote.getDate())
                quote.toModel(this, marketBreadthQuote, sectorBreadthQuote, spy)
            }.toList()
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
        this.stockName = stkDetail.get("stockSymbol") as String?
        this.sectorSymbol = stkDetail.get("sectorSymbol") as String?
    }

    /**
     *
     * @param date
     * @return the last buy signal before the given date
     */
    fun getLastBuySignal(date: LocalDate): LocalDate? {
        return quotes!!.stream() // Only care about quotes with a buy signal
            .filter { it: OvtlyrStockQuote? -> it!!.hasBuySignal() }  // Sort by quote date desc
            .sorted { a: OvtlyrStockQuote?, b: OvtlyrStockQuote? ->
                b!!.getDate().compareTo(a!!.getDate())
            }  // Only look at quotes that are before this
            .filter { it: OvtlyrStockQuote? -> it!!.getDate().isBefore(date) || it.getDate().isEqual(date) }
            .map<LocalDate?> { it: OvtlyrStockQuote? -> it!!.getDate() }
            .findFirst()
            .orElse(null)
    }

    /**
     *
     * @param date
     * @return the last sell signal before the given date
     */
    fun getLastSellSignal(date: LocalDate): LocalDate? {
        return quotes!!.stream() // Only care about quotes with a buy signal
            .filter { it: OvtlyrStockQuote? -> it!!.hasSellSignal() }  // Sort by quote date desc
            .sorted { a: OvtlyrStockQuote?, b: OvtlyrStockQuote? ->
                b!!.getDate().compareTo(a!!.getDate())
            }  // Only look at quotes that are before this
            .filter { it: OvtlyrStockQuote? -> it!!.getDate().isBefore(date) || it.getDate().isEqual(date) }
            .map<LocalDate?> { it: OvtlyrStockQuote? -> it!!.getDate() }
            .findFirst()
            .orElse(null)
    }

    fun getCurrentSignalFrom(from: LocalDate): String {
        val lastSellSignal = getLastSellSignal(from)
        val lastBuySignal = getLastBuySignal(from)

        return if (Objects.compare<LocalDate?>(
                lastBuySignal,
                lastSellSignal,
                Comparator.nullsLast<LocalDate?>(Comparator { obj: LocalDate?, other: LocalDate? ->
                    obj!!.compareTo(other)
                })
            ) > 0
        ) "Buy" else "Sell"
    }
}
