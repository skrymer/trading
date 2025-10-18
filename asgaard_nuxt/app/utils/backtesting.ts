import type { Trade } from "@/types"

export const numberOfWinningTrades = (trades: Trade[] | undefined) =>  trades?.filter(trade => trade.profitPercentage > 0).length || 0
export const numberOfLosingTrades = (trades: Trade[] | undefined) => trades?.filter(trade => trade.profitPercentage < 0).length || 0
export const winRate = (trades: Trade[] | undefined) => {
  const total = numberOfWinningTrades(trades) + numberOfLosingTrades(trades)
  return total > 0 ? numberOfWinningTrades(trades) / total : 0
}
export const averageWinPercent = (trades: Trade[] | undefined) => {
  const wins = trades?.filter(trade => trade.profitPercentage > 0) || []
  const total = wins.length
  const sum = wins.reduce((acc, trade) => acc + trade.profitPercentage, 0)
  return total > 0 ? sum / total : 0
}

export const averageLossPercent = (trades: Trade[] | undefined) => {
  const losses = trades?.filter(trade => trade.profitPercentage < 0) || []
  const total = losses.length
  const sum = losses.reduce((acc, trade) => acc + trade.profitPercentage, 0)
  return Math.abs(total > 0 ? sum / total : 0)
}

/**
 *  (AvgWinPercentage × WinRate) − ((1−WinRate) × AvgLossPercentage)
 */ 
export const edge = (trades: Trade[] | undefined) => (averageWinPercent(trades) * winRate(trades)) - ((1.0 - winRate(trades)) * averageLossPercent(trades))

