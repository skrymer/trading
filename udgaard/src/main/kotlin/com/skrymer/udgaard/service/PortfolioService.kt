package com.skrymer.udgaard.service

import com.skrymer.udgaard.model.*
import com.skrymer.udgaard.repository.PortfolioRepository
import com.skrymer.udgaard.repository.PortfolioTradeRepository
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.pow

@Service
class PortfolioService(
    private val portfolioRepository: PortfolioRepository,
    private val portfolioTradeRepository: PortfolioTradeRepository,
    private val stockService: StockService,
    private val strategyRegistry: StrategyRegistry
) {

    /**
     * Get all portfolios, optionally filtered by userId
     */
    fun getAllPortfolios(userId: String? = null): List<Portfolio> {
        return if (userId != null) {
            portfolioRepository.findByUserId(userId)
        } else {
            portfolioRepository.findAll()
        }
    }

    /**
     * Create a new portfolio
     */
    fun createPortfolio(name: String, initialBalance: Double, currency: String, userId: String? = null): Portfolio {
        val portfolio = Portfolio(
            userId = userId,
            name = name,
            initialBalance = initialBalance,
            currentBalance = initialBalance,
            currency = currency
        )
        return portfolioRepository.save(portfolio)
    }

    /**
     * Get portfolio by ID
     */
    fun getPortfolio(portfolioId: String): Portfolio? {
        return portfolioRepository.findById(portfolioId).orElse(null)
    }

    /**
     * Update portfolio balance
     */
    fun updatePortfolio(portfolioId: String, currentBalance: Double): Portfolio? {
        val portfolio = getPortfolio(portfolioId) ?: return null
        val updated = portfolio.copy(
            currentBalance = currentBalance,
            lastUpdated = LocalDateTime.now()
        )
        return portfolioRepository.save(updated)
    }

    /**
     * Delete portfolio and all associated trades
     */
    fun deletePortfolio(portfolioId: String) {
        // Delete all trades associated with the portfolio
        portfolioTradeRepository.deleteByPortfolioId(portfolioId)

        // Delete the portfolio itself
        portfolioRepository.deleteById(portfolioId)
    }

    /**
     * Open a new trade
     */
    fun openTrade(
        portfolioId: String,
        symbol: String,
        entryPrice: Double,
        entryDate: LocalDate,
        quantity: Int,
        entryStrategy: String,
        exitStrategy: String,
        currency: String,
        underlyingSymbol: String? = null,
        instrumentType: InstrumentType = InstrumentType.STOCK,
        optionType: OptionType? = null,
        strikePrice: Double? = null,
        expirationDate: LocalDate? = null,
        contracts: Int? = null,
        multiplier: Int = 100,
        entryIntrinsicValue: Double? = null,
        entryExtrinsicValue: Double? = null
    ): PortfolioTrade {
        val portfolio = getPortfolio(portfolioId)
            ?: throw IllegalArgumentException("Portfolio not found")

        // Validate entry date is not before portfolio creation
        if (entryDate.isBefore(portfolio.createdDate.toLocalDate())) {
            throw IllegalArgumentException("Entry date cannot be before portfolio creation date (${portfolio.createdDate.toLocalDate()})")
        }

        // Validate options-specific fields
        if (instrumentType == InstrumentType.OPTION) {
            requireNotNull(optionType) { "Option type is required for option trades" }
            requireNotNull(strikePrice) { "Strike price is required for option trades" }
            requireNotNull(expirationDate) { "Expiration date is required for option trades" }
            requireNotNull(contracts) { "Number of contracts is required for option trades" }
        }

        val trade = PortfolioTrade(
            portfolioId = portfolioId,
            symbol = symbol,
            instrumentType = instrumentType,
            optionType = optionType,
            strikePrice = strikePrice,
            expirationDate = expirationDate,
            contracts = contracts,
            multiplier = multiplier,
            entryIntrinsicValue = entryIntrinsicValue,
            entryExtrinsicValue = entryExtrinsicValue,
            entryPrice = entryPrice,
            entryDate = entryDate,
            quantity = quantity,
            entryStrategy = entryStrategy,
            exitStrategy = exitStrategy,
            currency = currency,
            status = TradeStatus.OPEN,
            underlyingSymbol = underlyingSymbol
        )
        return portfolioTradeRepository.save(trade)
    }

    /**
     * Close an existing trade and update portfolio balance
     */
    fun closeTrade(
        tradeId: String,
        exitPrice: Double,
        exitDate: LocalDate,
        exitIntrinsicValue: Double? = null,
        exitExtrinsicValue: Double? = null
    ): PortfolioTrade? {
        val trade = portfolioTradeRepository.findById(tradeId).orElse(null) ?: return null

        // Validate exit date is not before entry date
        if (exitDate.isBefore(trade.entryDate)) {
            throw IllegalArgumentException("Exit date cannot be before entry date (${trade.entryDate})")
        }

        val closedTrade = trade.copy(
            exitPrice = exitPrice,
            exitDate = exitDate,
            exitIntrinsicValue = exitIntrinsicValue,
            exitExtrinsicValue = exitExtrinsicValue,
            status = TradeStatus.CLOSED
        )

        // Update portfolio balance
        val profit = closedTrade.profit ?: 0.0
        val portfolio = getPortfolio(trade.portfolioId)
        portfolio?.let {
            updatePortfolio(it.id!!, it.currentBalance + profit)
        }

        return portfolioTradeRepository.save(closedTrade)
    }

    /**
     * Get all trades for a portfolio
     */
    fun getTrades(portfolioId: String, status: TradeStatus? = null): List<PortfolioTrade> {
        return if (status != null) {
            portfolioTradeRepository.findByPortfolioIdAndStatus(portfolioId, status)
        } else {
            portfolioTradeRepository.findByPortfolioId(portfolioId)
        }
    }

    /**
     * Get a specific trade
     */
    fun getTrade(tradeId: String): PortfolioTrade? {
        return portfolioTradeRepository.findById(tradeId).orElse(null)
    }

    /**
     * Calculate portfolio statistics
     */
    fun calculateStats(portfolioId: String): PortfolioStats {
        val portfolio = getPortfolio(portfolioId) ?: return createEmptyStats()
        val allTrades = getTrades(portfolioId)
        val closedTrades = allTrades.filter { it.status == TradeStatus.CLOSED }
        val openTrades = allTrades.filter { it.status == TradeStatus.OPEN }

        if (closedTrades.isEmpty()) {
            return createEmptyStats().copy(
                totalTrades = allTrades.size,
                openTrades = openTrades.size
            )
        }

        // Calculate wins and losses
        val wins = closedTrades.filter { (it.profit ?: 0.0) > 0 }
        val losses = closedTrades.filter { (it.profit ?: 0.0) < 0 }

        val numberOfWins = wins.size
        val numberOfLosses = losses.size
        val winRate = if (closedTrades.isNotEmpty()) {
            (numberOfWins.toDouble() / closedTrades.size) * 100.0
        } else 0.0

        val avgWin = if (wins.isNotEmpty()) {
            wins.mapNotNull { it.profitPercentage }.average()
        } else 0.0

        val avgLoss = if (losses.isNotEmpty()) {
            losses.mapNotNull { it.profitPercentage }.average()
        } else 0.0

        // Calculate proven edge
        val lossRate = 100.0 - winRate
        val provenEdge = (winRate / 100.0 * avgWin) - (lossRate / 100.0 * kotlin.math.abs(avgLoss))

        // Calculate total profit
        val totalProfit = closedTrades.mapNotNull { it.profit }.sum()
        val totalProfitPercentage = (totalProfit / portfolio.initialBalance) * 100.0

        // Calculate YTD return
        val ytdReturn = calculateYTDReturn(portfolio, closedTrades)

        // Calculate annualized return
        val annualizedReturn = calculateAnnualizedReturn(portfolio)

        // Find the largest win and loss
        val largestWin = wins.maxOfOrNull { it.profitPercentage ?: 0.0 }
        val largestLoss = losses.minOfOrNull { it.profitPercentage ?: 0.0 }

        return PortfolioStats(
            totalTrades = allTrades.size,
            openTrades = openTrades.size,
            closedTrades = closedTrades.size,
            ytdReturn = ytdReturn,
            annualizedReturn = annualizedReturn,
            avgWin = avgWin,
            avgLoss = avgLoss,
            winRate = winRate,
            provenEdge = provenEdge,
            totalProfit = totalProfit,
            totalProfitPercentage = totalProfitPercentage,
            largestWin = largestWin,
            largestLoss = largestLoss,
            numberOfWins = numberOfWins,
            numberOfLosses = numberOfLosses
        )
    }

    /**
     * Calculate YTD return
     */
    private fun calculateYTDReturn(portfolio: Portfolio, closedTrades: List<PortfolioTrade>): Double {
        val startOfYear = LocalDate.now().withDayOfYear(1)
        val ytdTrades = closedTrades.filter {
            it.exitDate?.isAfter(startOfYear.minusDays(1)) == true
        }

        val ytdProfit = ytdTrades.mapNotNull { it.profit }.sum()
        return (ytdProfit / portfolio.initialBalance) * 100.0
    }

    /**
     * Calculate annualized return using CAGR formula
     */
    private fun calculateAnnualizedReturn(portfolio: Portfolio): Double {
        val daysSinceCreation = ChronoUnit.DAYS.between(
            portfolio.createdDate.toLocalDate(),
            LocalDate.now()
        )

        if (daysSinceCreation == 0L) return 0.0

        val years = daysSinceCreation / 365.0
        return ((portfolio.currentBalance / portfolio.initialBalance).pow(1.0 / years) - 1.0) * 100.0
    }

    /**
     * Generate equity curve data
     * Groups trades by exit date to show end-of-day balance
     */
    fun getEquityCurve(portfolioId: String): EquityCurveData {
        val portfolio = getPortfolio(portfolioId) ?: return EquityCurveData(emptyList())
        val trades = getTrades(portfolioId, TradeStatus.CLOSED)
            .sortedBy { it.exitDate }

        val dataPoints = mutableListOf<EquityDataPoint>()
        var runningBalance = portfolio.initialBalance

        // Add starting point
        dataPoints.add(EquityDataPoint(
            date = portfolio.createdDate.toLocalDate(),
            balance = runningBalance,
            returnPercentage = 0.0
        ))

        // Group trades by exit date and aggregate
        val tradesByDate = trades.groupBy { it.exitDate }

        tradesByDate.keys.filterNotNull().sorted().forEach { exitDate ->
            val dayTrades = tradesByDate[exitDate] ?: emptyList()

            // Add all profits from trades on this date
            val dayProfit = dayTrades.mapNotNull { it.profit }.sum()
            runningBalance += dayProfit

            val returnPct = ((runningBalance - portfolio.initialBalance) / portfolio.initialBalance) * 100.0

            dataPoints.add(EquityDataPoint(
                date = exitDate,
                balance = runningBalance,
                returnPercentage = returnPct
            ))
        }

        return EquityCurveData(dataPoints)
    }

    /**
     * Check if a trade currently has an exit signal
     */
    fun hasExitSignal(tradeId: String): Pair<Boolean, String?> {
        val trade = getTrade(tradeId) ?: return Pair(false, null)
        if (trade.status != TradeStatus.OPEN) return Pair(false, null)

        // Determine which symbol to use for strategy evaluation
        val symbolForStrategy = trade.underlyingSymbol ?: trade.symbol
        val stock = stockService.getStock(symbolForStrategy) ?: return Pair(false, null)

        // Get the exit strategy
        val exitStrategy = strategyRegistry.createExitStrategy(trade.exitStrategy)
            ?: return Pair(false, null)

        // Get entry quote
        val entryQuote = stock.quotes.firstOrNull { it.date == trade.entryDate }

        // Get latest quote
        val latestQuote = stock.quotes.mapNotNull { quote -> quote.date?.let { quote } }
            .maxByOrNull { it.date!! } ?: return Pair(false, null)

        // Check if exit signal is present
        val hasSignal = exitStrategy.match(stock, entryQuote, latestQuote)
        val reason = if (hasSignal) exitStrategy.reason(stock, entryQuote, latestQuote) else null

        return Pair(hasSignal, reason)
    }

    private fun createEmptyStats() = PortfolioStats(
        totalTrades = 0,
        openTrades = 0,
        closedTrades = 0,
        ytdReturn = 0.0,
        annualizedReturn = 0.0,
        avgWin = 0.0,
        avgLoss = 0.0,
        winRate = 0.0,
        provenEdge = 0.0,
        totalProfit = 0.0,
        totalProfitPercentage = 0.0
    )
}
