import type { Trade } from '~/types'

export default eventHandler(async (event): Promise<Trade[]> => {
  const query = getQuery(event)
  const stockSymbol = query.stockSymbol as string | undefined
  const refresh = query.refresh === 'true'

  try {
    // If stockSymbol is provided, fetch backtest report for that stock
    if (stockSymbol) {
      const url = `http://localhost:8080/api/report?stockSymbol=${encodeURIComponent(stockSymbol)}&refresh=${refresh}`
      const response = await $fetch<{ trades: Trade[] }>(url)
      return response.trades || []
    }

    // Otherwise, fetch all trades from all stocks
    const url = `http://localhost:8080/api/report/all?refresh=${refresh}`
    const response = await $fetch<{ trades: Trade[] }>(url)
    return response.trades || []
  } catch (error) {
    console.error('Error fetching trades:', error)
    throw createError({
      statusCode: 500,
      message: 'Failed to fetch trades from backend service'
    })
  }
})
