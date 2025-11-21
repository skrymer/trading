package com.skrymer.udgaard.controller.dto

import java.time.LocalDate

data class EtfStatsResponse(
    val symbol: String,
    val name: String,
    val currentStats: EtfCurrentStats,
    val historicalData: List<EtfHistoricalDataPoint>,
    val warning: String? = null,
    val expectedStockCount: Int,
    val actualStockCount: Int
)

data class EtfCurrentStats(
    val bullishPercent: Double,
    val change: Double,
    val inUptrend: Boolean,
    val stocksInUptrend: Int,
    val stocksInDowntrend: Int,
    val stocksInNeutral: Int,
    val totalStocks: Int,
    val lastUpdated: LocalDate?
)

data class EtfHistoricalDataPoint(
    val date: LocalDate,
    val bullishPercent: Double,
    val stocksInUptrend: Int,
    val stocksInDowntrend: Int,
    val totalStocks: Int
)
