import type { AvatarProps } from '@nuxt/ui'

export type UserStatus = 'subscribed' | 'unsubscribed' | 'bounced'
export type SaleStatus = 'paid' | 'failed' | 'refunded'

export interface User {
  id: number
  name: string
  email: string
  avatar?: AvatarProps
  status: UserStatus
  location: string
}

export interface Mail {
  id: number
  unread?: boolean
  from: User
  subject: string
  body: string
  date: string
}

export interface Member {
  name: string
  username: string
  role: 'member' | 'owner'
  avatar: AvatarProps
}

export interface Stat {
  title: string
  icon: string
  value: number | string
  variation: number
  formatter?: (value: number) => string
}

export interface Sale {
  id: string
  date: string
  status: SaleStatus
  email: string
  amount: number
}

export interface Notification {
  id: number
  unread?: boolean
  sender: User
  body: string
  date: string
}

export type Period = 'daily' | 'weekly' | 'monthly'

export interface Range {
  start: Date
  end: Date
}

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