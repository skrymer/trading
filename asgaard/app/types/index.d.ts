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

export interface SectorStats {
  sector: string
  totalTrades: number
  winningTrades: number
  losingTrades: number
  winRate: number
  edge: number
  averageWinPercent: number
  averageLossPercent: number
  totalProfitPercentage: number
  maxDrawdown: number
  trades: Trade[]
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
  sectorStats: SectorStats[]
  // New diagnostic metrics
  timeBasedStats?: TimeBasedStats
  exitReasonAnalysis?: ExitReasonAnalysis
  sectorPerformance: SectorPerformance[]
  atrDrawdownStats?: ATRDrawdownStats
  marketConditionAverages?: Record<string, number>
}

export interface Stock {
  symbol: string
  quotes: StockQuote[]
  orderBlocks: OrderBlock[]
}

export interface StockQuote {
  atr: number
  signal?: string | null
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
export type OrderBlockSensitivity = 'HIGH' | 'LOW'

export interface OrderBlock {
  low: number
  high: number
  startDate: string
  endDate: string | null
  orderBlockType: OrderBlockType
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
  excursionMetrics?: ExcursionMetrics
  marketConditionAtEntry?: MarketConditionSnapshot
}

// Trade Performance Metrics - Diagnostic Data

/**
 * Trade excursion metrics - maximum profit/loss reached during trade
 */
export interface ExcursionMetrics {
  maxFavorableExcursion: number          // Highest % profit reached
  maxFavorableExcursionATR: number       // In ATR units
  maxAdverseExcursion: number            // Deepest % drawdown (negative)
  maxAdverseExcursionATR: number         // In ATR units (positive value)
  mfeReached: boolean                    // Did trade reach positive territory?
}

/**
 * Snapshot of market conditions at trade entry
 */
export interface MarketConditionSnapshot {
  spyClose: number
  spyHeatmap: number | null
  spyInUptrend: boolean
  marketBreadthBullPercent: number | null
  entryDate: string
}

/**
 * Performance statistics grouped by time periods (year, quarter, month)
 */
export interface TimeBasedStats {
  byYear: Record<number, PeriodStats>
  byQuarter: Record<string, PeriodStats>     // "2025-Q1"
  byMonth: Record<string, PeriodStats>       // "2025-01"
}

/**
 * Performance statistics for a specific time period
 */
export interface PeriodStats {
  trades: number
  winRate: number
  avgProfit: number
  avgHoldingDays: number
  exitReasons: Record<string, number>
}

/**
 * Exit reason analysis - breakdown by reason with stats
 */
export interface ExitReasonAnalysis {
  byReason: Record<string, ExitStats>
  byYearAndReason: Record<number, Record<string, number>>  // Year -> Reason -> Count
}

/**
 * Statistics for a specific exit reason
 */
export interface ExitStats {
  count: number
  avgProfit: number
  avgHoldingDays: number
  winRate: number
}

/**
 * Performance statistics for a specific sector
 */
export interface SectorPerformance {
  sector: string
  trades: number
  winRate: number
  avgProfit: number
  avgHoldingDays: number
}

/**
 * ATR drawdown statistics for winning trades
 */
export interface ATRDrawdownStats {
  medianDrawdown: number
  meanDrawdown: number
  percentile25: number
  percentile50: number                   // Same as median
  percentile75: number
  percentile90: number
  percentile95: number
  percentile99: number
  minDrawdown: number
  maxDrawdown: number
  distribution: Record<string, DrawdownBucket>
  totalWinningTrades: number
  // Losing trades stats (for comparison)
  losingTradesStats?: {
    medianLoss: number
    meanLoss: number
    percentile25: number
    percentile50: number
    percentile75: number
    percentile90: number
    percentile95: number
    percentile99: number
    minLoss: number
    maxLoss: number
    distribution: Record<string, DrawdownBucket>
    totalLosingTrades: number
  }
}

/**
 * Bucket for ATR drawdown distribution
 */
export interface DrawdownBucket {
  range: string                          // "0.0-0.5", "0.5-1.0", etc.
  count: number
  percentage: number
  cumulativePercentage: number           // Running total
}

/**
 * Breadth data for either market (all stocks) or sector (specific sector).
 * The symbol field will be either "FULLSTOCK" for market or a sector code like "XLK".
 */
export interface Breadth {
  id: string
  symbol: BreadthSymbol
  name: string
  quotes: BreadthQuote[]
  inUptrend: boolean
  heatmap: number
  previousHeatmap: number
  donkeyChannelScore: number
}

export interface BreadthQuote {
  symbol: string
  quoteDate: string
  numberOfStocksWithABuySignal: number
  numberOfStocksWithASellSignal: number
  numberOfStocksInUptrend: number
  numberOfStocksInNeutral: number
  numberOfStocksInDowntrend: number
  bullStocksPercentage: number
  ema_5: number
  ema_10: number
  ema_20: number
  ema_50: number
  heatmap: number
  previousHeatmap: number
  donchianUpperBand: number
  previousDonchianUpperBand: number
  donchianLowerBand: number
  previousDonchianLowerBand: number
  donkeyChannelScore: number
}

// Backward compatibility aliases (deprecated - use Breadth and BreadthQuote)
export type MarketBreadth = Breadth
export type MarketBreadthQuote = BreadthQuote

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

  // Rolling support - links trades in a roll chain
  parentTradeId?: string
  rolledToTradeId?: string
  rollNumber?: number

  // Cumulative tracking across the entire roll chain
  originalEntryDate?: string
  originalCostBasis?: number
  cumulativeRealizedProfit?: number
  totalRollCost?: number

  // Roll transaction details
  rollDate?: string
  rollCost?: number

  // Computed fields
  positionSize?: number
  daysToExpiration?: number
  timeDecay?: number
}

export interface RollTradeRequest {
  newSymbol: string
  newStrikePrice: number
  newExpirationDate: string
  newOptionType: OptionType
  newEntryPrice: number
  rollDate: string
  contracts: number
  exitPrice: number
}

export interface RollTradeResponse {
  closedTrade: PortfolioTrade
  newTrade: PortfolioTrade
  rollCost: number
}

export interface RollChainResponse {
  trades: PortfolioTrade[]
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

// ETF Entity Types (new architecture)
export interface EtfEntity {
  symbol: string
  name: string
  description: string
  quotes: EtfQuote[]
  holdings: EtfHolding[]
  metadata: EtfMetadata | null
}

export interface EtfQuote {
  date: string
  openPrice: number
  closePrice: number
  high: number
  low: number
  volume: number
  closePriceEMA5: number
  closePriceEMA10: number
  closePriceEMA20: number
  closePriceEMA50: number
  atr: number
  bullishPercentage: number
  stocksInUptrend: number
  stocksInDowntrend: number
  stocksInNeutral: number
  totalHoldings: number
  lastBuySignal: string | null
  lastSellSignal: string | null
}

export interface EtfHolding {
  stockSymbol: string
  weight: number
  shares: number | null
  marketValue: number | null
  asOfDate: string
  inUptrend: boolean
  trend: string | null
}

export interface EtfMetadata {
  expenseRatio: number | null
  aum: number | null
  inceptionDate: string | null
  issuer: string | null
  exchange: string | null
  currency: string
  type: string | null
  benchmark: string | null
  lastRebalanceDate: string | null
}

// Data Management Types
export interface RateLimitStats {
  requestsLastMinute: number
  requestsLastDay: number
  remainingMinute: number
  remainingDaily: number
  minuteLimit: number
  dailyLimit: number
  resetMinute: number
  resetDaily: number
}

export interface RateLimitConfig {
  requestsPerMinute: number
  requestsPerDay: number
  tier: 'FREE' | 'PREMIUM' | 'ULTIMATE'
}

export interface DatabaseStats {
  stockStats: StockDataStats
  breadthStats: BreadthDataStats
  etfStats: EtfDataStats
  totalDataPoints: number
  estimatedSizeKB: number
  generatedAt: string
}

export interface StockDataStats {
  totalStocks: number
  totalQuotes: number
  totalEarnings: number
  totalOrderBlocks: number
  dateRange: DateRange | null
  averageQuotesPerStock: number
  stocksWithEarnings: number
  stocksWithOrderBlocks: number
  lastUpdatedStock: StockUpdateInfo | null
  oldestDataStock: StockUpdateInfo | null
  recentlyUpdated: StockUpdateInfo[]
}

export interface DateRange {
  earliest: string
  latest: string
  days: number
}

export interface StockUpdateInfo {
  symbol: string
  lastQuoteDate: string
  quoteCount: number
  hasEarnings: boolean
  orderBlockCount: number
}

export interface BreadthDataStats {
  totalBreadthSymbols: number
  totalBreadthQuotes: number
  breadthSymbols: BreadthSymbolInfo[]
  dateRange: DateRange | null
}

export interface BreadthSymbolInfo {
  symbol: string
  quoteCount: number
  lastQuoteDate: string
}

export interface EtfDataStats {
  totalEtfs: number
  totalEtfQuotes: number
  totalHoldings: number
  dateRange: DateRange | null
  etfsWithHoldings: number
  averageHoldingsPerEtf: number
}

export interface RefreshProgress {
  total: number
  completed: number
  failed: number
  lastSuccess: string | null
  lastError: string | null
}

export interface RefreshResponse {
  queued: number
  message: string
}

export interface OptionPricePoint {
  date: string
  price: number
}
