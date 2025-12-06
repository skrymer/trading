package com.skrymer.udgaard.service

import com.skrymer.udgaard.model.*
import com.skrymer.udgaard.repository.PortfolioRepository
import com.skrymer.udgaard.repository.PortfolioTradeRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
    fun getPortfolio(portfolioId: Long): Portfolio? {
        return portfolioRepository.findById(portfolioId).orElse(null)
    }

    /**
     * Update portfolio balance
     */
    fun updatePortfolio(portfolioId: Long, currentBalance: Double): Portfolio? {
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
    @Transactional
    fun deletePortfolio(portfolioId: Long) {
        // Delete all trades associated with the portfolio
        portfolioTradeRepository.deleteByPortfolioId(portfolioId)

        // Delete the portfolio itself
        portfolioRepository.deleteById(portfolioId)
    }

    /**
     * Open a new trade
     */
    fun openTrade(
        portfolioId: Long,
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

        // Validate options-specific fields
        if (instrumentType == InstrumentType.OPTION) {
            requireNotNull(optionType) { "Option type is required for option trades" }
            requireNotNull(strikePrice) { "Strike price is required for option trades" }
            requireNotNull(expirationDate) { "Expiration date is required for option trades" }
            requireNotNull(contracts) { "Number of contracts is required for option trades" }
        }

        // Calculate the cost of opening this trade
        val tradeCost = if (instrumentType == InstrumentType.OPTION) {
            entryPrice * (contracts ?: 1) * multiplier
        } else {
            entryPrice * quantity
        }

        // Deduct trade cost from portfolio balance
        val updatedBalance = portfolio.currentBalance - tradeCost
        updatePortfolio(portfolioId, updatedBalance)

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
     * Update an existing open trade
     */
    fun updateTrade(
        tradeId: Long,
        symbol: String? = null,
        entryPrice: Double? = null,
        entryDate: LocalDate? = null,
        quantity: Int? = null,
        entryStrategy: String? = null,
        exitStrategy: String? = null,
        underlyingSymbol: String? = null,
        instrumentType: InstrumentType? = null,
        optionType: OptionType? = null,
        strikePrice: Double? = null,
        expirationDate: LocalDate? = null,
        contracts: Int? = null,
        multiplier: Int? = null,
        entryIntrinsicValue: Double? = null,
        entryExtrinsicValue: Double? = null
    ): PortfolioTrade? {
        val trade = portfolioTradeRepository.findById(tradeId).orElse(null) ?: return null

        // Only allow updating open trades
        if (trade.status != TradeStatus.OPEN) {
            throw IllegalArgumentException("Can only update open trades")
        }

        // Calculate old entry cost
        val oldEntryCost = if (trade.instrumentType == InstrumentType.OPTION) {
            trade.entryPrice * (trade.contracts ?: trade.quantity) * trade.multiplier
        } else {
            trade.entryPrice * trade.quantity
        }

        // Update trade with provided values (keep existing if null)
        val updatedTrade = trade.copy(
            symbol = symbol ?: trade.symbol,
            entryPrice = entryPrice ?: trade.entryPrice,
            entryDate = entryDate ?: trade.entryDate,
            quantity = quantity ?: trade.quantity,
            entryStrategy = entryStrategy ?: trade.entryStrategy,
            exitStrategy = exitStrategy ?: trade.exitStrategy,
            underlyingSymbol = underlyingSymbol ?: trade.underlyingSymbol,
            instrumentType = instrumentType ?: trade.instrumentType,
            optionType = optionType ?: trade.optionType,
            strikePrice = strikePrice ?: trade.strikePrice,
            expirationDate = expirationDate ?: trade.expirationDate,
            contracts = contracts ?: trade.contracts,
            multiplier = multiplier ?: trade.multiplier,
            entryIntrinsicValue = entryIntrinsicValue ?: trade.entryIntrinsicValue,
            entryExtrinsicValue = entryExtrinsicValue ?: trade.entryExtrinsicValue
        )

        // Calculate new entry cost
        val newEntryCost = if (updatedTrade.instrumentType == InstrumentType.OPTION) {
            updatedTrade.entryPrice * (updatedTrade.contracts ?: updatedTrade.quantity) * updatedTrade.multiplier
        } else {
            updatedTrade.entryPrice * updatedTrade.quantity
        }

        // Adjust portfolio balance based on the difference
        val costDifference = newEntryCost - oldEntryCost
        if (costDifference != 0.0) {
            val portfolio = getPortfolio(trade.portfolioId)
            portfolio?.let {
                updatePortfolio(it.id!!, it.currentBalance - costDifference)
            }
        }

        return portfolioTradeRepository.save(updatedTrade)
    }

    /**
     * Close an existing trade and update portfolio balance
     */
    fun closeTrade(
        tradeId: Long,
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

        // Calculate the exit value (amount received from closing the position)
        val exitValue = if (trade.instrumentType == InstrumentType.OPTION) {
            exitPrice * (trade.contracts ?: trade.quantity) * trade.multiplier
        } else {
            exitPrice * trade.quantity
        }

        // Update portfolio balance by adding back the exit value
        val portfolio = getPortfolio(trade.portfolioId)
        portfolio?.let {
            updatePortfolio(it.id!!, it.currentBalance + exitValue)
        }

        return portfolioTradeRepository.save(closedTrade)
    }

    /**
     * Get all trades for a portfolio
     */
    fun getTrades(portfolioId: Long, status: TradeStatus? = null): List<PortfolioTrade> {
        return if (status != null) {
            portfolioTradeRepository.findByPortfolioIdAndStatus(portfolioId, status)
        } else {
            portfolioTradeRepository.findByPortfolioId(portfolioId)
        }
    }

    /**
     * Get a specific trade
     */
    fun getTrade(tradeId: Long): PortfolioTrade? {
        return portfolioTradeRepository.findById(tradeId).orElse(null)
    }

    /**
     * Delete a trade (only open trades can be deleted)
     */
    @Transactional
    fun deleteTrade(tradeId: Long): Boolean {
        val trade = portfolioTradeRepository.findById(tradeId).orElse(null) ?: return false

        // Only allow deleting open trades
        if (trade.status != TradeStatus.OPEN) {
            throw IllegalArgumentException("Can only delete open trades")
        }

        // Calculate the entry cost that was deducted when opening the trade
        val entryCost = if (trade.instrumentType == InstrumentType.OPTION) {
            trade.entryPrice * (trade.contracts ?: trade.quantity) * trade.multiplier
        } else {
            trade.entryPrice * trade.quantity
        }

        // Refund the entry cost back to the portfolio balance
        val portfolio = getPortfolio(trade.portfolioId)
        portfolio?.let {
            updatePortfolio(it.id!!, it.currentBalance + entryCost)
        }

        portfolioTradeRepository.deleteById(tradeId)
        return true
    }

    /**
     * Roll an options position to a new strike/expiration
     *
     * @param tradeId - The ID of the open trade to roll
     * @param newSymbol - Symbol for new position (could be different strike/expiration)
     * @param newStrikePrice - New strike price
     * @param newExpirationDate - New expiration date
     * @param newOptionType - New option type (usually same as original)
     * @param newEntryPrice - Entry price for new position
     * @param rollDate - Date of the roll
     * @param contracts - Number of contracts for new position
     * @param exitPrice - Current value of old position
     * @return Pair of (closed original trade, new rolled trade)
     */
    fun rollTrade(
        tradeId: Long,
        newSymbol: String,
        newStrikePrice: Double,
        newExpirationDate: LocalDate,
        newOptionType: OptionType,
        newEntryPrice: Double,
        rollDate: LocalDate,
        contracts: Int,
        exitPrice: Double
    ): Pair<PortfolioTrade, PortfolioTrade> {
        // 1. Get the original trade
        val originalTrade = getTrade(tradeId)
            ?: throw IllegalArgumentException("Trade not found")

        if (originalTrade.status != TradeStatus.OPEN) {
            throw IllegalArgumentException("Can only roll open trades")
        }

        if (originalTrade.instrumentType != InstrumentType.OPTION) {
            throw IllegalArgumentException("Can only roll option trades")
        }

        // 2. Close the original position
        val closedTrade = closeTrade(tradeId, exitPrice, rollDate)
            ?: throw IllegalStateException("Failed to close trade")

        // 3. Calculate roll cost/credit
        val exitValue = exitPrice * (originalTrade.contracts ?: 1) * originalTrade.multiplier
        val newEntryCost = newEntryPrice * contracts * originalTrade.multiplier
        val rollCost = newEntryCost - exitValue  // Positive = paid debit, negative = received credit

        // 4. Calculate cumulative values for new position
        val originalEntryDate = originalTrade.originalEntryDate ?: originalTrade.entryDate
        val originalCostBasis = originalTrade.originalCostBasis ?: originalTrade.positionSize
        val cumulativeProfit = (closedTrade.profit ?: 0.0) + (originalTrade.cumulativeRealizedProfit ?: 0.0)
        val totalRollCost = rollCost + (originalTrade.totalRollCost ?: 0.0)
        val rollNumber = originalTrade.rollNumber + 1

        // 5. Create new rolled position
        val newTrade = PortfolioTrade(
            portfolioId = originalTrade.portfolioId,
            symbol = newSymbol,
            instrumentType = InstrumentType.OPTION,
            optionType = newOptionType,
            strikePrice = newStrikePrice,
            expirationDate = newExpirationDate,
            contracts = contracts,
            multiplier = originalTrade.multiplier,
            entryPrice = newEntryPrice,
            entryDate = rollDate,
            quantity = contracts,
            entryStrategy = originalTrade.entryStrategy,
            exitStrategy = originalTrade.exitStrategy,
            currency = originalTrade.currency,
            status = TradeStatus.OPEN,
            underlyingSymbol = originalTrade.underlyingSymbol,

            // Rolling fields
            parentTradeId = tradeId,
            rollNumber = rollNumber,
            rollDate = rollDate,
            rollCost = rollCost,
            originalEntryDate = originalEntryDate,
            originalCostBasis = originalCostBasis,
            cumulativeRealizedProfit = cumulativeProfit,
            totalRollCost = totalRollCost
        )

        val savedNewTrade = portfolioTradeRepository.save(newTrade)

        // 6. Update the closed trade to link to new trade
        val updatedClosedTrade = closedTrade.copy(rolledToTradeId = savedNewTrade.id)
        portfolioTradeRepository.save(updatedClosedTrade)

        // 7. Adjust portfolio balance for roll cost (if any)
        // Note: closeTrade() already added exitValue back, now deduct newEntryCost
        val portfolio = getPortfolio(originalTrade.portfolioId)
        portfolio?.let {
            updatePortfolio(it.id!!, it.currentBalance - newEntryCost)
        }

        return Pair(updatedClosedTrade, savedNewTrade)
    }

    /**
     * Get the complete roll chain for a trade
     * Returns all trades in the chain from original to current
     */
    fun getRollChain(tradeId: Long): List<PortfolioTrade> {
        val trade = getTrade(tradeId) ?: return emptyList()

        // Find the original trade in the chain by walking backwards
        var originalTrade = trade
        while (originalTrade.parentTradeId != null) {
            originalTrade = getTrade(originalTrade.parentTradeId!!) ?: break
        }

        // Build the chain from original to current by walking forwards
        val chain = mutableListOf(originalTrade)
        var currentTrade = originalTrade
        while (currentTrade.rolledToTradeId != null) {
            val nextTrade = getTrade(currentTrade.rolledToTradeId!!)
            if (nextTrade != null) {
                chain.add(nextTrade)
                currentTrade = nextTrade
            } else break
        }

        return chain
    }

    /**
     * Calculate portfolio statistics
     *
     * @param portfolioId - The portfolio to calculate stats for
     * @param groupRolledTrades - If true, treat roll chains as single trades (use cumulative P&L)
     *                            If false, count each leg as a separate trade
     */
    fun calculateStats(portfolioId: Long, groupRolledTrades: Boolean = false): PortfolioStats {
        val portfolio = getPortfolio(portfolioId) ?: return createEmptyStats()
        val allTrades = getTrades(portfolioId)

        // When grouping, only count final trades in roll chains (not intermediate rolled-out positions)
        val closedTrades = if (groupRolledTrades) {
            allTrades.filter { it.status == TradeStatus.CLOSED && it.rolledToTradeId == null }
        } else {
            allTrades.filter { it.status == TradeStatus.CLOSED }
        }

        val openTrades = allTrades.filter { it.status == TradeStatus.OPEN }

        if (closedTrades.isEmpty()) {
            return createEmptyStats().copy(
                totalTrades = allTrades.size,
                openTrades = openTrades.size
            )
        }

        // Calculate wins and losses - use cumulative profit for rolled trades if grouping
        val wins = if (groupRolledTrades) {
            closedTrades.filter { (it.getCumulativeProfit() ?: 0.0) > 0 }
        } else {
            closedTrades.filter { (it.profit ?: 0.0) > 0 }
        }

        val losses = if (groupRolledTrades) {
            closedTrades.filter { (it.getCumulativeProfit() ?: 0.0) < 0 }
        } else {
            closedTrades.filter { (it.profit ?: 0.0) < 0 }
        }

        val numberOfWins = wins.size
        val numberOfLosses = losses.size
        val winRate = if (closedTrades.isNotEmpty()) {
            (numberOfWins.toDouble() / closedTrades.size) * 100.0
        } else 0.0

        // Use cumulative return percentages when grouping rolled trades
        val avgWin = if (wins.isNotEmpty()) {
            if (groupRolledTrades) {
                wins.mapNotNull { it.getCumulativeReturnPercentage() }.average()
            } else {
                wins.mapNotNull { it.profitPercentage }.average()
            }
        } else 0.0

        val avgLoss = if (losses.isNotEmpty()) {
            if (groupRolledTrades) {
                losses.mapNotNull { it.getCumulativeReturnPercentage() }.average()
            } else {
                losses.mapNotNull { it.profitPercentage }.average()
            }
        } else 0.0

        // Calculate proven edge
        val lossRate = 100.0 - winRate
        val provenEdge = (winRate / 100.0 * avgWin) - (lossRate / 100.0 * kotlin.math.abs(avgLoss))

        // Calculate total profit - use cumulative when grouping
        val totalProfit = if (groupRolledTrades) {
            closedTrades.mapNotNull { it.getCumulativeProfit() }.sum()
        } else {
            closedTrades.mapNotNull { it.profit }.sum()
        }
        val totalProfitPercentage = (totalProfit / portfolio.initialBalance) * 100.0

        // Calculate YTD return
        val ytdReturn = calculateYTDReturn(portfolio, closedTrades, groupRolledTrades)

        // Calculate annualized return
        val annualizedReturn = calculateAnnualizedReturn(portfolio)

        // Find the largest win and loss - use cumulative when grouping
        val largestWin = if (groupRolledTrades) {
            wins.maxOfOrNull { it.getCumulativeReturnPercentage() ?: 0.0 }
        } else {
            wins.maxOfOrNull { it.profitPercentage ?: 0.0 }
        }

        val largestLoss = if (groupRolledTrades) {
            losses.minOfOrNull { it.getCumulativeReturnPercentage() ?: 0.0 }
        } else {
            losses.minOfOrNull { it.profitPercentage ?: 0.0 }
        }

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
    private fun calculateYTDReturn(
        portfolio: Portfolio,
        closedTrades: List<PortfolioTrade>,
        groupRolledTrades: Boolean = false
    ): Double {
        val startOfYear = LocalDate.now().withDayOfYear(1)
        val ytdTrades = closedTrades.filter {
            it.exitDate?.isAfter(startOfYear.minusDays(1)) == true
        }

        val ytdProfit = if (groupRolledTrades) {
            ytdTrades.mapNotNull { it.getCumulativeProfit() }.sum()
        } else {
            ytdTrades.mapNotNull { it.profit }.sum()
        }
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
    fun getEquityCurve(portfolioId: Long): EquityCurveData {
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
