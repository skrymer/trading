export interface BacktestReport {
    trades: Trade[]
    numberOfLosingTrades: number
    numberOfWinningTrades: number
    winRate: number
    averageWinAmount: number
    averageWinPercent: number
    lossRate: number
    averageLossAmount: number
    averageLossPercent: number
    totalTrades: number
    edge: number
    mostProfitable: any
    stockProfits: any
    tradesGroupedByDate: { date: string, profitPercentage: number, trades: Trade[] }[]
}

export interface Stock {
    symbol: string
    quotes: StockQuote[]
}
export interface StockQuote {
    atr: number
    lastBuySignal: string
    lastSellSignal: string
    date: string
    closePrice: number
    closePriceEMA10: number
    closePriceEMA20: number
    openPrice: number
    high: number
    low: number
    heatmap: number
    sectorHeatmap: number
}

export interface Trade {
    stockSymbol: string
    sector: string
    entryQuote: StockQuote
    exitQuote: StockQuote
    exitReason: string
    profitPercentage: number
    profit: number
    quotes: StockQuote[],
    tradingDays: number,
    startDate: string
}

export interface MarketBreadth {
    symbol: string
    name: string
    quotes: MarketBreadthQuote[]
    inUptrend: boolean
    heatmap: number
    previousHeatmap: number
    donkeyChannelScore: number
}

export interface MarketBreadthQuote {
    symbol: string
    quoteDate: string
    numberOfStocksWithABuySignal: number
    numberOfStocksWithASellSignal: number
    numberOfStocksInUptrend: number
    numberOfStocksInNeutral: number
    numberOfStocksInDowntrend: number
    bullStocksPercentage: number
    ema_10: number
    donchianUpperBand: number
    previousDonchianUpperBand: number
    donchianLowerBand: number
    previousDonchianLowerBand: number
    ema_10: number
}