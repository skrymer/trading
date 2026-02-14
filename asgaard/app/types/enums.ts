export enum Sector {
  XLK = 'Technology',
  XLF = 'Financials',
  XLV = 'Healthcare',
  XLE = 'Energy',
  XLI = 'Industrials',
  XLY = 'Consumer Discretionary',
  XLP = 'Consumer Staples',
  XLB = 'Materials',
  XLRE = 'Real Estate',
  XLU = 'Utilities',
  XLC = 'Communication Services'
}

export function getSectorName(sectorCode: string): string {
  return Sector[sectorCode as keyof typeof Sector] || sectorCode
}

/**
 * Market symbol - represents the entire stock market (all stocks combined)
 */
export enum MarketSymbol {
  FULLSTOCK = 'FULLSTOCK'
}

export const MarketSymbolDescriptions: Record<MarketSymbol, string> = {
  [MarketSymbol.FULLSTOCK]: 'All Stocks'
}

/**
 * Sector symbols - represent individual market sectors
 */
export enum SectorSymbol {
  XLE = 'XLE', // Energy
  XLV = 'XLV', // Health
  XLB = 'XLB', // Materials
  XLC = 'XLC', // Communications
  XLK = 'XLK', // Technology
  XLRE = 'XLRE', // Real Estate
  XLI = 'XLI', // Industrials
  XLF = 'XLF', // Financials
  XLY = 'XLY', // Discretionary
  XLP = 'XLP', // Staples
  XLU = 'XLU' // Utilities
}

export const SectorSymbolDescriptions: Record<SectorSymbol, string> = {
  [SectorSymbol.XLE]: 'Energy',
  [SectorSymbol.XLV]: 'Health',
  [SectorSymbol.XLB]: 'Materials',
  [SectorSymbol.XLC]: 'Communications',
  [SectorSymbol.XLK]: 'Technology',
  [SectorSymbol.XLRE]: 'Real Estate',
  [SectorSymbol.XLI]: 'Industrials',
  [SectorSymbol.XLF]: 'Financials',
  [SectorSymbol.XLY]: 'Discretionary',
  [SectorSymbol.XLP]: 'Staples',
  [SectorSymbol.XLU]: 'Utilities'
}

/**
 * Type representing either market or sector breadth symbol
 */
export type BreadthSymbol
  = | { type: 'market', symbol: MarketSymbol }
    | { type: 'sector', symbol: SectorSymbol }

/**
 * ETF symbols with support for leveraged and inverse ETFs
 */
export enum EtfSymbol {
  SPY = 'SPY',
  QQQ = 'QQQ',
  IWM = 'IWM',
  DIA = 'DIA',
  // Leveraged ETFs
  TQQQ = 'TQQQ',
  SQQQ = 'SQQQ',
  UPRO = 'UPRO',
  SPXU = 'SPXU'
}

export const EtfSymbolDescriptions: Record<EtfSymbol, string> = {
  [EtfSymbol.SPY]: 'SPDR S&P 500 ETF Trust',
  [EtfSymbol.QQQ]: 'Invesco QQQ Trust',
  [EtfSymbol.IWM]: 'iShares Russell 2000 ETF',
  [EtfSymbol.DIA]: 'SPDR Dow Jones Industrial Average ETF',
  [EtfSymbol.TQQQ]: 'ProShares UltraPro QQQ (3x Leveraged)',
  [EtfSymbol.SQQQ]: 'ProShares UltraPro Short QQQ (-3x Inverse)',
  [EtfSymbol.UPRO]: 'ProShares UltraPro S&P500 (3x Leveraged)',
  [EtfSymbol.SPXU]: 'ProShares UltraPro Short S&P500 (-3x Inverse)'
}

export enum MonteCarloTechnique {
  TRADE_SHUFFLING = 'TRADE_SHUFFLING',
  BOOTSTRAP_RESAMPLING = 'BOOTSTRAP_RESAMPLING',
  PRICE_PATH_RANDOMIZATION = 'PRICE_PATH_RANDOMIZATION'
}

export const MonteCarloTechniqueDescriptions: Record<MonteCarloTechnique, { name: string, description: string }> = {
  [MonteCarloTechnique.TRADE_SHUFFLING]: {
    name: 'Trade Shuffling',
    description: 'Randomly reorders trades to test if edge holds regardless of sequence'
  },
  [MonteCarloTechnique.BOOTSTRAP_RESAMPLING]: {
    name: 'Bootstrap Resampling',
    description: 'Randomly samples trades with replacement to test edge consistency'
  },
  [MonteCarloTechnique.PRICE_PATH_RANDOMIZATION]: {
    name: 'Price Path Randomization',
    description: 'Randomizes price paths while maintaining statistical properties (not yet implemented)'
  }
}

export const AssetTypeOptions = [
  { label: 'Stock', value: 'STOCK' },
  { label: 'ETF', value: 'ETF' },
  { label: 'Leveraged ETF', value: 'LEVERAGED_ETF' },
  { label: 'Index', value: 'INDEX' },
  { label: 'Bond ETF', value: 'BOND_ETF' },
  { label: 'Commodity ETF', value: 'COMMODITY_ETF' }
]

export enum BrokerType {
  MANUAL = 'MANUAL',
  IBKR = 'IBKR'
}

export const BrokerTypeDescriptions: Record<BrokerType, string> = {
  [BrokerType.MANUAL]: 'Manual Entry',
  [BrokerType.IBKR]: 'Interactive Brokers'
}

export enum PositionStatus {
  OPEN = 'OPEN',
  CLOSED = 'CLOSED'
}

export const PositionStatusDescriptions: Record<PositionStatus, string> = {
  [PositionStatus.OPEN]: 'Open',
  [PositionStatus.CLOSED]: 'Closed'
}

export enum PositionSource {
  BROKER = 'BROKER',
  MANUAL = 'MANUAL'
}

export const PositionSourceDescriptions: Record<PositionSource, string> = {
  [PositionSource.BROKER]: 'Broker Import',
  [PositionSource.MANUAL]: 'Manual Entry'
}
