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
  XLC = 'Communication Services',
  TECH = 'Technology',
  FINC = 'Financials',
  HLTH = 'Healthcare',
  ENGY = 'Energy',
  INDU = 'Industrials',
  COND = 'Consumer Discretionary',
  CONS = 'Consumer Staples',
  MATL = 'Materials',
  REAL = 'Real Estate',
  UTIL = 'Utilities',
  COMM = 'Communication Services'
}

export function getSectorName(sectorCode: string): string {
  return Sector[sectorCode as keyof typeof Sector] || sectorCode
}

export enum MarketSymbol {
  FULLSTOCK = 'FULLSTOCK',
  XLE = 'XLE', // Energy
  XLV = 'XLV', // Health
  XLB = 'XLB', // Materials
  XLC = 'XLC', // Communications
  XLK = 'XLK', // Technology
  XLRE = 'XLRE', // Realestate
  XLI = 'XLI', // Industrials
  XLF = 'XLF', // Financials
  XLY = 'XLY', // Discretionary
  XLP = 'XLP', // Staples
  XLU = 'XLU', // Utilities
  UNK = 'UNK' // Unknown
}

export const MarketSymbolDescriptions: Record<MarketSymbol, string> = {
  [MarketSymbol.FULLSTOCK]: 'Full Stock Market',
  [MarketSymbol.XLE]: 'Energy',
  [MarketSymbol.XLV]: 'Health',
  [MarketSymbol.XLB]: 'Materials',
  [MarketSymbol.XLC]: 'Communications',
  [MarketSymbol.XLK]: 'Technology',
  [MarketSymbol.XLRE]: 'Real Estate',
  [MarketSymbol.XLI]: 'Industrials',
  [MarketSymbol.XLF]: 'Financials',
  [MarketSymbol.XLY]: 'Discretionary',
  [MarketSymbol.XLP]: 'Staples',
  [MarketSymbol.XLU]: 'Utilities',
  [MarketSymbol.UNK]: 'Unknown'
}

export enum Etf {
  QQQ = 'QQQ',
  SPY = 'SPY',
  IWM = 'IWM',
  DIA = 'DIA'
}

export const EtfDescriptions: Record<Etf, string> = {
  [Etf.QQQ]: 'Nasdaq-100',
  [Etf.SPY]: 'S&P 500',
  [Etf.IWM]: 'Russell 2000',
  [Etf.DIA]: 'Dow Jones Industrial Average'
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
