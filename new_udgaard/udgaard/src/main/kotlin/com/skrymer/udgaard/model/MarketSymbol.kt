package com.skrymer.udgaard.model

enum class MarketSymbol(description: String) {
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

    val description: String?

    init {
        this.description = description
    }
}
