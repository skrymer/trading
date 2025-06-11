package com.skrymer.udgaard.model;

public enum MarketSymbol {
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

    private String description;

    private MarketSymbol(String description){
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
