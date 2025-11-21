package com.skrymer.udgaard.model

enum class Etf(val symbol: String, val description: String) {
    QQQ("QQQ", "Nasdaq-100"),
    SPY("SPY", "S&P 500"),
    IWM("IWM", "Russell 2000"),
    DIA("DIA", "Dow Jones Industrial Average");

    companion object
}

fun Etf.Companion.valueOf(value: String?) =
    runCatching {
        if (value == null) null
        else Etf.entries.find { it.symbol.equals(value, ignoreCase = true) }
    }.getOrNull()
