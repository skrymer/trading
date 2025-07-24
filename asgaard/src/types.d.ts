export interface BacktestReport {
    trades: Trade[]
    numberOfLosingTrades: number
    numberOfWinningTrades: number
    winRate: number
    averageWinAmount: number
    lossRate: number
    averageLossAmount: number
    totalTrades: number
    edge: number
    mostProfitable: any
    stockProfits: any
}

export interface StockQuote {
    lastBuySignal: string
    date: string
    closePrice: number
    closePriceEMA10: number
    openPrice: number
    high: number
    low: number
    heatmap: number
    sectorHeatmap: number
}

export interface Trade {
    entryQuote: StockQuote
    exitQuote: StockQuote
    exitReason: string
    profitPercentage: number
    profit: number
    quotes: StockQuote[]
}

export interface MarketBreadth {
    symbol: string
    quotes: MarketBreadthQuote[]
    inUptrend: boolean
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