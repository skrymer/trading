package com.skrymer.udgaard.model

enum class MarketSymbol(val description: String) {
    FULLSTOCK("FULLSTOCK"),
    XLE("Energy"),
    XLV("Health"),
    XLB("Materials"),
    XLC("Communications"),
    XLK("Technology"),
    XLRE("Realestate"),
    XLI("Industrials"),
    XLF("Financials"),
    XLY("Discretionary"),
    XLP("Staples"),
    XLU("Utilities"),
    UNK("Unknown");

    companion object
}

fun MarketSymbol.Companion.valueOf(value: String?) =
    runCatching {
        if(value == null) MarketSymbol.UNK
        else MarketSymbol.valueOf(value)
    }.getOrDefault(MarketSymbol.UNK)