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
}

export interface EquityCurvePoint {
  date: string
  profitPercentage: number
}

export interface ExcursionPoint {
  mfe: number
  mae: number
  mfeATR: number
  maeATR: number
  mfeReached: boolean
  profitPercentage: number
  isWinner: boolean
}

export interface ExcursionSummary {
  totalTrades: number
  avgMFE: number
  avgMAE: number
  avgMFEATR: number
  avgMAEATR: number
  profitReachRate: number
  avgMFEEfficiency: number
  winnerCount: number
  winnerAvgMFE: number
  winnerAvgMAE: number
  winnerAvgFinalProfit: number
  loserCount: number
  loserAvgMFE: number
  loserAvgMAE: number
  loserAvgFinalLoss: number
  loserMissedWinRate: number
}

export interface DailyProfitSummary {
  date: string
  profitPercentage: number
  tradeCount: number
}

export interface MarketConditionPoint {
  breadth: number
  profitPercentage: number
  isWinner: boolean
  spyInUptrend: boolean
}

export interface MarketConditionStats {
  scatterPoints: MarketConditionPoint[]
  uptrendWinRate: number
  downtrendWinRate: number
  uptrendCount: number
  downtrendCount: number
}

export interface BacktestReport {
  backtestId: string
  // Scalar metrics
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
  profitFactor: number | null
  stockProfits: [string, number][]
  // Missed trades
  missedOpportunitiesCount: number
  missedProfitPercentage: number
  missedAverageProfitPercentage: number
  // Analytics
  timeBasedStats?: TimeBasedStats
  exitReasonAnalysis?: ExitReasonAnalysis
  sectorPerformance: SectorPerformance[]
  stockPerformance: StockPerformance[]
  atrDrawdownStats?: ATRDrawdownStats
  marketConditionAverages?: Record<string, number>
  edgeConsistencyScore?: EdgeConsistencyScore
  sectorStats: SectorStats[]
  // Pre-computed chart data
  equityCurveData: EquityCurvePoint[]
  excursionPoints: ExcursionPoint[]
  excursionSummary: ExcursionSummary | null
  dailyProfitSummary: DailyProfitSummary[]
  marketConditionStats: MarketConditionStats | null
  underlyingAssetTradeCount: number
  positionSizing?: PositionSizingResult
}

export interface Stock {
  symbol: string
  sectorSymbol?: string
  quotes: StockQuote[]
  orderBlocks: OrderBlock[]
}

export interface StockQuote {
  atr: number
  adx?: number | null
  date: string
  closePrice: number
  closePriceEMA5: number
  closePriceEMA10: number
  closePriceEMA20: number
  closePriceEMA50: number
  ema200: number
  openPrice: number
  high: number
  low: number
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
  ageInTradingDays?: number
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
  maxFavorableExcursion: number // Highest % profit reached
  maxFavorableExcursionATR: number // In ATR units
  maxAdverseExcursion: number // Deepest % drawdown (negative)
  maxAdverseExcursionATR: number // In ATR units (positive value)
  mfeReached: boolean // Did trade reach positive territory?
}

/**
 * Snapshot of market conditions at trade entry
 */
export interface MarketConditionSnapshot {
  spyClose: number
  spyInUptrend: boolean
  marketBreadthBullPercent: number | null
  entryDate: string
}

/**
 * Performance statistics grouped by time periods (year, quarter, month)
 */
export interface TimeBasedStats {
  byYear: Record<number, PeriodStats>
  byQuarter: Record<string, PeriodStats> // "2025-Q1"
  byMonth: Record<string, PeriodStats> // "2025-01"
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
  edge: number
}

/**
 * Exit reason analysis - breakdown by reason with stats
 */
export interface ExitReasonAnalysis {
  byReason: Record<string, ExitStats>
  byYearAndReason: Record<number, Record<string, number>> // Year -> Reason -> Count
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
 * Edge consistency score - measures how consistent a strategy's edge is across years
 * Score 0-100: 80+ Excellent, 60-79 Good, 40-59 Moderate, 20-39 Poor, <20 Very Poor
 */
export interface EdgeConsistencyScore {
  score: number
  profitablePeriodsScore: number
  stabilityScore: number
  downsideScore: number
  yearsAnalyzed: number
  yearlyEdges: Record<number, number>
  interpretation: string
}

/**
 * Performance statistics for a specific stock
 */
export interface StockPerformance {
  symbol: string
  trades: number
  winRate: number
  avgProfit: number
  avgHoldingDays: number
  totalProfitPercentage: number
  edge: number
  profitFactor: number | null
  maxDrawdown: number
}

/**
 * ATR drawdown statistics for winning trades
 */
export interface ATRDrawdownStats {
  medianDrawdown: number
  meanDrawdown: number
  percentile25: number
  percentile50: number // Same as median
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
  range: string // "0.0-0.5", "0.5-1.0", etc.
  count: number
  percentage: number
  cumulativePercentage: number // Running total
}

export interface MarketBreadthDaily {
  quoteDate: string
  breadthPercent: number
  ema5: number
  ema10: number
  ema20: number
  ema50: number
  donchianUpperBand: number
  donchianLowerBand: number
}

export interface SectorBreadthDaily {
  sectorSymbol: string
  quoteDate: string
  bullPercentage: number
  ema5: number
  ema10: number
  ema20: number
  ema50: number
  donchianUpperBand: number
  donchianLowerBand: number
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
  assetTypes?: string[]
  includeSectors?: string[]
  excludeSectors?: string[]
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
  entryDelayDays?: number
  positionSizing?: PositionSizingConfig
}

// Position Sizing Types
export interface PositionSizingConfig {
  startingCapital: number
  riskPercentage: number
  nAtr: number
  leverageRatio?: number
}

export interface PositionSizingResult {
  startingCapital: number
  finalCapital: number
  totalReturnPct: number
  maxDrawdownPct: number
  maxDrawdownDollars: number
  peakCapital: number
  trades: PositionSizedTrade[]
  equityCurve: PortfolioEquityPoint[]
}

export interface PositionSizedTrade {
  symbol: string
  entryDate: string
  exitDate: string
  shares: number
  entryPrice: number
  exitPrice: number
  dollarProfit: number
  portfolioValueAtEntry: number
  portfolioReturnPct: number
}

export interface PortfolioEquityPoint {
  date: string
  portfolioValue: number
}

// Monte Carlo Simulation Types
export type MonteCarloTechniqueType = 'TRADE_SHUFFLING' | 'BOOTSTRAP_RESAMPLING' | 'PRICE_PATH_RANDOMIZATION'

export interface MonteCarloRequest {
  backtestId: string
  technique: MonteCarloTechniqueType
  iterations: number
  seed?: number
  includeAllEquityCurves: boolean
  positionSizing?: PositionSizingConfig
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
export type PositionStatus = 'OPEN' | 'CLOSED'
export type PositionSource = 'BROKER' | 'MANUAL'

export interface Portfolio {
  id?: string
  userId?: string
  name: string
  initialBalance: number
  currentBalance: number
  currency: string
  createdDate?: string
  lastUpdated?: string
  broker?: string
  brokerAccountId?: string
  brokerConfig?: Record<string, string>
  lastSyncDate?: string
}

export interface Position {
  id: number
  portfolioId: number
  symbol: string
  underlyingSymbol?: string
  instrumentType: InstrumentType

  // Options-specific fields
  optionType?: OptionType
  strikePrice?: number
  expirationDate?: string
  multiplier: number

  // Position state (aggregated from executions)
  currentQuantity: number
  currentContracts?: number
  averageEntryPrice: number
  totalCost: number
  status: PositionStatus

  // Dates
  openedDate: string
  closedDate?: string

  // P&L
  realizedPnl?: number

  // Rolling (clean 1-to-1 relationship)
  rolledToPositionId?: number
  parentPositionId?: number
  rollNumber: number

  // Strategy (editable metadata)
  entryStrategy: string
  exitStrategy: string

  // Metadata
  notes?: string
  currency: string
  source: PositionSource

  createdAt: string
  updatedAt: string

  // Computed fields
  isOpen: boolean
  isClosed: boolean
  positionSize: number
  isBrokerImported: boolean
  isRolled: boolean
}

export interface Execution {
  id: number
  positionId: number
  brokerTradeId?: string
  linkedBrokerTradeId?: string

  // Execution details (signed quantity)
  quantity: number
  price: number
  executionDate: string
  executionTime?: string

  // Costs
  commission?: number

  // Metadata
  notes?: string
  createdAt: string
}

export interface PositionWithExecutions {
  position: Position
  executions: Execution[]
}

export interface PositionUnrealizedPnl {
  positionId: number
  symbol: string
  currentPrice: number | null
  averageEntryPrice: number
  unrealizedPnl: number | null
  unrealizedPnlPercentage: number | null
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

  // Broker integration fields
  brokerTradeId?: string
  linkedBrokerTradeId?: string

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
  profitFactor: number | null
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

export interface EtfDataStats {
  totalEtfs: number
  totalEtfQuotes: number
  totalHoldings: number
  dateRange: DateRange | null
  etfsWithHoldings: number
  averageHoldingsPerEtf: number
}

export interface BreadthCoverageStats {
  totalStocks: number
  sectors: SectorStockCount[]
}

export interface SectorStockCount {
  sectorSymbol: string
  totalStocks: number
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

// Broker Integration Types
export interface CreatePortfolioFromBrokerRequest {
  name: string
  broker: string
  credentials: Record<string, string>
  startDate?: string
  currency?: string
  initialBalance?: number
}

export interface SyncPortfolioRequest {
  credentials: Record<string, string>
}

export interface TestBrokerConnectionRequest {
  broker: string
  credentials: Record<string, string>
}

export interface TestBrokerConnectionResponse {
  success: boolean
  message: string
}

export interface CreateFromBrokerResult {
  portfolio: Portfolio
  tradesImported: number
  rollsDetected: number
  warnings: string[]
}

export interface PortfolioSyncResult {
  tradesAdded: number
  tradesUpdated: number
  rollsDetected: number
  lastSyncDate: string
  errors: string[]
}

// ========================================
// Scanner Types
// ========================================

export interface ScanRequest {
  entryStrategyName: string
  exitStrategyName: string
  stockSymbols?: string[]
  assetTypes?: string[]
  includeSectors?: string[]
  excludeSectors?: string[]
}

export interface ConditionEvaluationResult {
  conditionType: string
  description: string
  passed: boolean
  actualValue?: string
  threshold?: string
  message?: string
}

export interface EntrySignalDetails {
  strategyName: string
  strategyDescription: string
  conditions: ConditionEvaluationResult[]
  allConditionsMet: boolean
}

export interface ScanResult {
  symbol: string
  sectorSymbol?: string
  closePrice: number
  date: string
  entrySignalDetails?: EntrySignalDetails
  atr: number
  trend?: string
}

export interface ScanResponse {
  scanDate: string
  entryStrategyName: string
  exitStrategyName: string
  results: ScanResult[]
  totalStocksScanned: number
  executionTimeMs: number
}

export interface ExitCheckResult {
  tradeId: number
  symbol: string
  exitTriggered: boolean
  exitReason?: string
  currentPrice: number
  unrealizedPnlPercent: number
}

export interface ExitCheckResponse {
  results: ExitCheckResult[]
  checksPerformed: number
  exitsTriggered: number
}

export interface ScannerTrade {
  id: number
  symbol: string
  sectorSymbol?: string
  instrumentType: InstrumentType
  entryPrice: number
  entryDate: string
  quantity: number
  optionType?: OptionType
  strikePrice?: number
  expirationDate?: string
  multiplier: number
  entryStrategyName: string
  exitStrategyName: string
  rolledCredits: number
  rollCount: number
  notes?: string
  createdAt?: string
  updatedAt?: string
}

export interface AddScannerTradeRequest {
  symbol: string
  sectorSymbol?: string
  instrumentType: string
  entryPrice: number
  entryDate: string
  quantity: number
  optionType?: string
  strikePrice?: number
  expirationDate?: string
  multiplier?: number
  entryStrategyName: string
  exitStrategyName: string
  notes?: string
}

export interface RollScannerTradeRequest {
  closePrice: number
  newStrikePrice: number
  newExpirationDate: string
  newOptionType?: string
  newEntryPrice: number
  newEntryDate: string
  newQuantity: number
}

export interface UpdateScannerTradeRequest {
  notes?: string
}
