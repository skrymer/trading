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
  winningTrades: Trade[]
  losingTrades: Trade[]
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
  orderBlocks: OrderBlock[]
}

export interface StockQuote {
  atr: number
  lastBuySignal: string
  lastSellSignal: string
  date: string
  closePrice: number
  closePriceEMA5: number
  closePriceEMA10: number
  closePriceEMA20: number
  closePriceEMA50: number
  openPrice: number
  high: number
  low: number
  heatmap: number
  sectorHeatmap: number
  trend?: string
  volume?: number
}

export type OrderBlockType = 'BULLISH' | 'BEARISH'
export type OrderBlockSource = 'OVTLYR' | 'CALCULATED'
export type OrderBlockSensitivity = 'HIGH' | 'LOW'

export interface OrderBlock {
  low: number
  high: number
  startDate: string
  endDate: string | null
  orderBlockType: OrderBlockType
  source: OrderBlockSource
  volume: number
  volumeStrength: number
  sensitivity?: OrderBlockSensitivity
  rateOfChange?: number
}

export interface Trade {
  stockSymbol: string
  underlyingSymbol?: string
  sector: string
  entryQuote: StockQuote
  exitReason: string
  profitPercentage: number
  profit: number
  quotes: StockQuote[]
  tradingDays: number
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

// Dynamic Strategy Types
export type StrategyType = 'predefined' | 'custom'

export interface ParameterMetadata {
  name: string
  displayName: string
  type: 'number' | 'boolean' | 'string'
  defaultValue: any
  min?: number
  max?: number
  options?: string[]
}

export interface ConditionMetadata {
  type: string
  displayName: string
  description: string
  parameters: ParameterMetadata[]
  category: string
}

export interface AvailableConditions {
  entryConditions: ConditionMetadata[]
  exitConditions: ConditionMetadata[]
}

export interface ConditionConfig {
  type: string
  parameters?: Record<string, any>
}

export interface PredefinedStrategyConfig {
  type: 'predefined'
  name: string
}

export interface CustomStrategyConfig {
  type: 'custom'
  operator: 'AND' | 'OR' | 'NOT'
  description?: string
  conditions: ConditionConfig[]
}

export type StrategyConfig = PredefinedStrategyConfig | CustomStrategyConfig

export interface BacktestRequest {
  stockSymbols?: string[]
  entryStrategy: StrategyConfig
  exitStrategy: StrategyConfig
  startDate?: string
  endDate?: string
  maxPositions?: number
  ranker?: string
  refresh?: boolean
  useUnderlyingAssets?: boolean
  customUnderlyingMap?: Record<string, string>
  cooldownDays?: number
}

// Monte Carlo Simulation Types
export type MonteCarloTechniqueType = 'TRADE_SHUFFLING' | 'BOOTSTRAP_RESAMPLING' | 'PRICE_PATH_RANDOMIZATION'

export interface MonteCarloRequest {
  trades: Trade[]
  technique: MonteCarloTechniqueType
  iterations: number
  seed?: number
  includeAllEquityCurves: boolean
}

export interface MonteCarloEquityPoint {
  date: string
  cumulativeReturnPercentage: number
  tradeNumber: number
}

export interface MonteCarloScenario {
  scenarioNumber: number
  equityCurve: MonteCarloEquityPoint[]
  trades: Trade[]
  totalReturnPercentage: number
  winRate: number
  edge: number
  maxDrawdown: number
  winningTrades: number
  losingTrades: number
}

export interface Percentiles {
  p5: number
  p25: number
  p50: number
  p75: number
  p95: number
}

export interface ConfidenceInterval {
  lower: number
  upper: number
}

export interface MonteCarloStatistics {
  meanReturnPercentage: number
  medianReturnPercentage: number
  stdDevReturnPercentage: number
  returnPercentiles: Percentiles
  meanMaxDrawdown: number
  medianMaxDrawdown: number
  drawdownPercentiles: Percentiles
  meanWinRate: number
  medianWinRate: number
  winRatePercentiles: Percentiles
  meanEdge: number
  medianEdge: number
  edgePercentiles: Percentiles
  probabilityOfProfit: number
  returnConfidenceInterval95: ConfidenceInterval
  drawdownConfidenceInterval95: ConfidenceInterval
  bestCaseReturnPercentage: number
  worstCaseReturnPercentage: number
}

export interface PercentileEquityCurves {
  p5: MonteCarloEquityPoint[]
  p25: MonteCarloEquityPoint[]
  p50: MonteCarloEquityPoint[]
  p75: MonteCarloEquityPoint[]
  p95: MonteCarloEquityPoint[]
}

export interface MonteCarloResult {
  technique: string
  iterations: number
  statistics: MonteCarloStatistics
  scenarios: MonteCarloScenario[]
  percentileEquityCurves: PercentileEquityCurves
  originalReturnPercentage: number
  originalEdge: number
  originalWinRate: number
  executionTimeMs: number
}

// Portfolio Manager Types
export type TradeStatus = 'OPEN' | 'CLOSED'
export type InstrumentType = 'STOCK' | 'OPTION' | 'LEVERAGED_ETF'
export type OptionType = 'CALL' | 'PUT'

export interface Portfolio {
  id?: string
  userId?: string
  name: string
  initialBalance: number
  currentBalance: number
  currency: string
  createdDate?: string
  lastUpdated?: string
}

export interface PortfolioTrade {
  id?: string
  portfolioId: string
  symbol: string

  // Instrument type
  instrumentType: InstrumentType

  // Options-specific fields
  optionType?: OptionType
  strikePrice?: number
  expirationDate?: string
  contracts?: number
  multiplier?: number

  // Option value components (optional)
  entryIntrinsicValue?: number
  entryExtrinsicValue?: number
  exitIntrinsicValue?: number
  exitExtrinsicValue?: number

  // Common fields
  entryPrice: number
  entryDate: string
  exitPrice?: number
  exitDate?: string
  quantity: number
  entryStrategy: string
  exitStrategy: string
  currency: string
  status: TradeStatus
  profit?: number
  profitPercentage?: number
  underlyingSymbol?: string

  // Computed fields
  positionSize?: number
  daysToExpiration?: number
  timeDecay?: number
}

export interface PortfolioStats {
  totalTrades: number
  openTrades: number
  closedTrades: number
  ytdReturn: number
  annualizedReturn: number
  avgWin: number
  avgLoss: number
  winRate: number
  provenEdge: number
  totalProfit: number
  totalProfitPercentage: number
  largestWin?: number
  largestLoss?: number
  numberOfWins?: number
  numberOfLosses?: number
}

export interface EquityDataPoint {
  date: string
  balance: number
  returnPercentage: number
}

export interface EquityCurveData {
  dataPoints: EquityDataPoint[]
}

export interface PortfolioTradeResponse {
  trade: PortfolioTrade
  hasExitSignal: boolean
  exitSignalReason?: string
}

// ETF Stats Types
export interface EtfStatsResponse {
  symbol: string
  name: string
  currentStats: EtfCurrentStats
  historicalData: EtfHistoricalDataPoint[]
  warning?: string | null
  expectedStockCount: number
  actualStockCount: number
}

export interface EtfCurrentStats {
  bullishPercent: number
  change: number
  inUptrend: boolean
  stocksInUptrend: number
  stocksInDowntrend: number
  stocksInNeutral: number
  totalStocks: number
  lastUpdated: string | null
}

export interface EtfHistoricalDataPoint {
  date: string
  bullishPercent: number
  stocksInUptrend: number
  stocksInDowntrend: number
  totalStocks: number
}
