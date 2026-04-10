package com.skrymer.udgaard.portfolio.service

import com.skrymer.udgaard.data.integration.midgaard.MidgaardClient
import com.skrymer.udgaard.portfolio.integration.broker.AssetType
import com.skrymer.udgaard.portfolio.integration.broker.BrokerAdapter
import com.skrymer.udgaard.portfolio.integration.broker.BrokerAdapterFactory
import com.skrymer.udgaard.portfolio.integration.broker.BrokerCredentials
import com.skrymer.udgaard.portfolio.integration.broker.BrokerDataResult
import com.skrymer.udgaard.portfolio.integration.broker.BrokerType
import com.skrymer.udgaard.portfolio.integration.broker.RollChain
import com.skrymer.udgaard.portfolio.integration.broker.RollPair
import com.skrymer.udgaard.portfolio.integration.broker.StandardizedCashTransaction
import com.skrymer.udgaard.portfolio.integration.broker.TradeLot
import com.skrymer.udgaard.portfolio.integration.broker.TradeProcessor
import com.skrymer.udgaard.portfolio.integration.ibkr.IBKRApiException
import com.skrymer.udgaard.portfolio.model.CashTransactionSource
import com.skrymer.udgaard.portfolio.model.ImportResult
import com.skrymer.udgaard.portfolio.model.InstrumentType
import com.skrymer.udgaard.portfolio.repository.ExecutionJooqRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Service for broker integration (import and sync portfolios from external brokers)
 * Refactored to use Position + Execution architecture
 */
@Service
class BrokerIntegrationService(
  private val adapterFactory: BrokerAdapterFactory,
  private val tradeProcessor: TradeProcessor,
  private val portfolioService: PortfolioService,
  private val positionService: PositionService,
  private val portfolioStatsService: PortfolioStatsService,
  private val executionRepository: ExecutionJooqRepository,
  private val forexTrackingService: ForexTrackingService,
  private val cashTransactionService: CashTransactionService,
  private val midgaardClient: MidgaardClient,
) {
  /**
   * Create portfolio from broker data
   */
  @Transactional
  fun createPortfolioFromBroker(
    name: String,
    broker: BrokerType,
    credentials: BrokerCredentials,
    startDate: LocalDate,
    currency: String = "USD",
    initialBalance: Double? = null,
  ): CreateFromBrokerResult {
    logger.info("Creating portfolio from broker: name=$name, broker=$broker, startDate=$startDate")

    val endDate = validateAndGetEndDate(startDate)

    // 1. Get broker adapter
    val adapter = adapterFactory.getAdapter(broker)

    // 2. Fetch trades AND account info (single API call to minimize rate limiting)
    // If yesterday's data isn't available yet (e.g. timezone gap with IBKR US), fall back to 2 days ago
    val brokerData = fetchWithDateFallback(adapter, credentials, startDate, endDate)
    val standardizedTrades = brokerData.trades
    val accountInfo = brokerData.accountInfo

    // 3. Process trades (broker-agnostic logic)
    val lots = tradeProcessor.splitPartialCloses(standardizedTrades)
    val rolls = tradeProcessor.detectOptionRolls(lots)

    // 4. Create portfolio
    val portfolio =
      portfolioService.createPortfolio(
        name = name,
        initialBalance = initialBalance ?: accountInfo.balance ?: 10000.0,
        currency = currency,
        userId = null,
      )

    // Update portfolio with broker info
    // Detect base currency from account info (e.g. AUD for IBKR Australia)
    val baseCurrency = accountInfo.currency.takeIf { it != currency } ?: currency

    // Fetch initial FX rate if base currency differs from trade currency
    val initialFxRate = if (baseCurrency != currency) {
      midgaardClient
        .getHistoricalExchangeRate(currency, baseCurrency, startDate)
        .also { rate ->
          if (rate != null) {
            logger.info("Fetched initial FX rate $currency/$baseCurrency for $startDate: $rate")
          } else {
            logger.warn("Could not fetch initial FX rate $currency/$baseCurrency for $startDate")
          }
        }
    } else {
      null
    }

    val updatedPortfolio =
      portfolio.copy(
        broker = broker,
        brokerAccountId = accountInfo.accountId,
        brokerConfig = extractBrokerConfig(credentials),
        lastSyncDate = LocalDateTime.now(),
        baseCurrency = baseCurrency,
        initialFxRate = initialFxRate,
      )

    val savedPortfolio = portfolioService.updatePortfolioWithBrokerInfo(updatedPortfolio)
    val portfolioId = savedPortfolio.id
      ?: throw IllegalStateException("Portfolio ID is null after save")
    logger.info("Created portfolio: id=$portfolioId, broker=$broker, accountId=${accountInfo.accountId}")

    // 5. Import trades
    val imported = importTrades(portfolioId, lots, rolls)

    // 6. Import cash transactions and recalculate balance
    importCashTransactions(portfolioId, brokerData.cashTransactions, currency)
    if (brokerData.cashTransactions.isNotEmpty()) {
      portfolioStatsService.recalculatePortfolioBalance(portfolioId)
    }

    logger.info(
      "Portfolio import complete: ${imported.positionsCreated} positions, " +
        "${imported.executionsCreated} executions, ${imported.rollsDetected} rolls",
    )

    return CreateFromBrokerResult(
      portfolio = savedPortfolio,
      tradesImported = imported.positionsCreated,
      rollsDetected = imported.rollsDetected,
      warnings = emptyList(),
    )
  }

  /**
   * Sync portfolio with broker data
   */
  @Transactional
  fun syncPortfolio(
    portfolioId: Long,
    credentials: BrokerCredentials,
  ): PortfolioSyncResult {
    logger.info("Syncing portfolio: portfolioId=$portfolioId")

    val portfolio =
      portfolioService.getPortfolio(portfolioId)
        ?: throw IllegalArgumentException("Portfolio not found: $portfolioId")

    if (portfolio.broker == BrokerType.MANUAL) {
      throw IllegalArgumentException("Cannot sync MANUAL portfolio")
    }

    // Get adapter for this portfolio's broker
    val adapter = adapterFactory.getAdapter(portfolio.broker)

    // Fetch trades from 3 months ago to ensure long-running positions have their open side
    val startDate = LocalDate.now().minusMonths(3)
    val endDate = LocalDate.now().minusDays(1)

    // Fetch trades AND account info
    // If yesterday's data isn't available yet, fall back to 2 days ago
    val brokerData = fetchWithDateFallback(adapter, credentials, startDate, endDate, portfolio.brokerAccountId ?: "")
    val standardizedTrades = brokerData.trades

    // Process trades
    val lots = tradeProcessor.splitPartialCloses(standardizedTrades)
    val rolls = tradeProcessor.detectOptionRolls(lots)

    // Import/update trades
    val imported = importTrades(portfolioId, lots, rolls)

    // Import cash transactions and recalculate balance
    importCashTransactions(portfolioId, brokerData.cashTransactions, portfolio.currency)
    if (brokerData.cashTransactions.isNotEmpty()) {
      portfolioStatsService.recalculatePortfolioBalance(portfolioId)
    }

    // Update last sync date
    portfolioService.updateLastSyncDate(portfolioId, LocalDateTime.now())

    logger.info(
      "Portfolio sync complete: ${imported.newPositions} new positions, " +
        "${imported.executionsCreated} executions, ${imported.rollsDetected} rolls",
    )

    return PortfolioSyncResult(
      tradesAdded = imported.newPositions,
      tradesUpdated = 0,
      rollsDetected = imported.rollsDetected,
      lastSyncDate = LocalDateTime.now(),
      errors = emptyList(),
    )
  }

  /**
   * Test broker connection
   */
  fun testConnection(
    broker: BrokerType,
    credentials: BrokerCredentials,
  ): Boolean {
    logger.info("Testing broker connection: broker=$broker")
    val adapter = adapterFactory.getAdapter(broker)
    return adapter.testConnection(credentials, "")
  }

  /**
   * Import trades into portfolio
   * Creates Positions and Executions from TradeLots and RollChains
   */
  private fun importTrades(
    portfolioId: Long,
    lots: List<TradeLot>,
    rolls: List<RollPair>,
  ): ImportResult {
    var positionsCreated = 0
    var newPositions = 0
    var executionsCreated = 0

    // 1. Build roll chains from roll pairs
    val rollChains = tradeProcessor.buildRollChains(rolls)

    // 2. Get all lots that are part of roll chains
    val lotsInChains = rollChains.flatMap { it.lots }.toSet()

    // 3. Import roll chains as single positions
    rollChains.forEach { chain ->
      val result = importRollChain(portfolioId, chain)
      positionsCreated += result.positionsCreated
      newPositions += result.newPositions
      executionsCreated += result.executionsCreated
    }

    // 4. Group regular (non-rolled) lots by position key
    val regularLots = lots.filter { it !in lotsInChains }
    val groupedLots = regularLots.groupBy { lot ->
      PositionKey(
        symbol = lot.symbol,
        strike = lot.openTrade.optionDetails?.strike,
        expiry = lot.openTrade.optionDetails?.expiry
      )
    }

    // 5. Import each group as a single position
    groupedLots.forEach { (key, lotsForPosition) ->
      val result = importLotGroup(portfolioId, lotsForPosition)
      positionsCreated += result.positionsCreated
      newPositions += result.newPositions
      executionsCreated += result.executionsCreated
    }

    return ImportResult(
      positionsCreated = positionsCreated,
      newPositions = newPositions,
      executionsCreated = executionsCreated,
      rollsDetected = rollChains.size,
    )
  }

  /**
   * Import a group of lots (for the same symbol/strike/expiry) as a single position
   * Handles partial closes where multiple lots share the same closing broker_trade_id
   */
  private fun importLotGroup(
    portfolioId: Long,
    lots: List<TradeLot>,
  ): RegularLotImportResult {
    if (lots.isEmpty()) {
      return RegularLotImportResult(0, 0, 0)
    }

    if (allBrokerTradeIdsExist(lots)) {
      logger.debug("Skipping lot group for ${lots.first().symbol}: all executions already imported")
      return RegularLotImportResult(0, 0, 0)
    }

    // Use first lot to determine position characteristics
    val firstLot = lots.first()

    // Find or create position for this symbol/strike/expiry
    val position = positionService.findOrCreatePosition(
      portfolioId = portfolioId,
      symbol = firstLot.symbol,
      instrumentType = mapAssetType(firstLot.openTrade.assetType),
      underlyingSymbol = firstLot.openTrade.optionDetails?.underlyingSymbol,
      optionType = firstLot.openTrade.optionDetails?.optionType,
      strikePrice = firstLot.openTrade.optionDetails?.strike,
      expirationDate = firstLot.openTrade.optionDetails?.expiry,
      openedDate = lots.minOf { it.openTrade.tradeDate },
      currency = firstLot.openTrade.currency,
      entryStrategy = "Broker Import",
      exitStrategy = "Broker Import"
    )

    val positionId = position.id!!
    var executionsCreated = 0
    val processedBrokerTradeIds = mutableSetOf<String>()

    // Group lots by broker_trade_id to aggregate quantities from FIFO splits
    val openingTrades = lots.groupBy { it.openTrade.brokerTradeId }
    val closingTrades = lots.mapNotNull { it.closeTrade }.groupBy { it.brokerTradeId }

    // Process opening executions (aggregate quantities for same broker_trade_id)
    openingTrades.forEach { (brokerTradeId, lotsWithSameTrade) ->
      if (brokerTradeId !in processedBrokerTradeIds) {
        val existingOpen = executionRepository.findByBrokerTradeId(brokerTradeId)
        if (existingOpen == null) {
          // Sum quantities from all lots that share this broker_trade_id
          val totalQuantity = lotsWithSameTrade.sumOf { it.openTrade.quantity }
          val firstTrade = lotsWithSameTrade.first().openTrade

          val execution = positionService.addExecution(
            positionId = positionId,
            quantity = totalQuantity,
            price = firstTrade.price,
            executionDate = firstTrade.tradeDate,
            brokerTradeId = brokerTradeId,
            commission = firstTrade.commission,
            fxRateToBase = firstTrade.fxRateToBase,
          )
          // Track forex if FX rate is available
          if (firstTrade.fxRateToBase != null) {
            forexTrackingService.processExecution(
              portfolioId = portfolioId,
              executionId = execution.id,
              date = firstTrade.tradeDate,
              netCashUsd = firstTrade.netAmount,
              fxRate = firstTrade.fxRateToBase,
              description = "${firstTrade.symbol} ${firstTrade.direction}",
            )
          }
          executionsCreated++
          processedBrokerTradeIds.add(brokerTradeId)
        }
      }
    }

    // Process closing executions (aggregate quantities for same broker_trade_id)
    closingTrades.forEach { (brokerTradeId, tradesWithSameBrokerId) ->
      if (brokerTradeId !in processedBrokerTradeIds) {
        val existingClose = executionRepository.findByBrokerTradeId(brokerTradeId)
        if (existingClose == null) {
          // Sum quantities from all closing trades that share this broker_trade_id
          val totalQuantity = tradesWithSameBrokerId.sumOf { it.quantity }
          val firstTrade = tradesWithSameBrokerId.first()

          val execution = positionService.addExecution(
            positionId = positionId,
            quantity = -totalQuantity,
            price = firstTrade.price,
            executionDate = firstTrade.tradeDate,
            brokerTradeId = brokerTradeId,
            commission = firstTrade.commission,
            fxRateToBase = firstTrade.fxRateToBase,
          )
          // Track forex if FX rate is available
          if (firstTrade.fxRateToBase != null) {
            forexTrackingService.processExecution(
              portfolioId = portfolioId,
              executionId = execution.id,
              date = firstTrade.tradeDate,
              netCashUsd = firstTrade.netAmount,
              fxRate = firstTrade.fxRateToBase,
              description = "${firstTrade.symbol} ${firstTrade.direction}",
            )
          }
          executionsCreated++
          processedBrokerTradeIds.add(brokerTradeId)
        }
      }
    }

    // Check if position should be closed
    val updatedPosition = positionService.getPositionById(positionId)
    if (updatedPosition != null && updatedPosition.currentQuantity == 0) {
      // Find the latest closing date
      val latestCloseDate = lots.mapNotNull { it.closeTrade?.tradeDate }.maxOrNull()
      if (latestCloseDate != null) {
        positionService.closePosition(positionId, latestCloseDate)
      }
    }

    logger.info(
      "Imported lot group for ${firstLot.symbol}: ${lots.size} lots, " +
        "$executionsCreated executions, qty: ${updatedPosition?.currentQuantity ?: 0}"
    )

    return RegularLotImportResult(
      positionsCreated = 1,
      newPositions = 1,
      executionsCreated = executionsCreated
    )
  }

  /**
   * Import roll chain into portfolio
   * Creates ONE position with all executions from the entire chain
   * Example: MP $55 → $60 → $63 becomes one position with 6 executions
   */
  private fun importRollChain(
    portfolioId: Long,
    chain: RollChain,
  ): ChainImportResult {
    if (allBrokerTradeIdsExist(chain.lots)) {
      logger.debug("Skipping roll chain for ${chain.underlying}: all executions already imported")
      return ChainImportResult(0, 0, 0)
    }

    var executionsCreated = 0

    // Find existing position for this underlying, or create new one
    val existingPosition = positionService.findOpenPositionByUnderlying(
      portfolioId = portfolioId,
      underlyingSymbol = chain.underlying
    )

    val position = if (existingPosition != null) {
      existingPosition
    } else {
      // Create new position using first lot's details
      val firstLot = chain.lots.first()
      positionService.findOrCreatePosition(
        portfolioId = portfolioId,
        symbol = firstLot.symbol,
        instrumentType = mapAssetType(firstLot.openTrade.assetType),
        underlyingSymbol = chain.underlying,
        optionType = firstLot.openTrade.optionDetails?.optionType,
        strikePrice = firstLot.openTrade.optionDetails?.strike,
        expirationDate = firstLot.openTrade.optionDetails?.expiry,
        openedDate = firstLot.openTrade.tradeDate,
        currency = firstLot.openTrade.currency,
        entryStrategy = "Broker Import",
        exitStrategy = "Broker Import"
      )
    }

    val positionId = position.id!!
    val isNewPosition = existingPosition == null && position.currentQuantity == 0

    // Process each lot in the chain
    chain.lots.forEachIndexed { index, lot ->
      val isLast = index == chain.lots.lastIndex

      // Add opening execution (skip if already exists)
      val existingOpen = executionRepository.findByBrokerTradeId(lot.openTrade.brokerTradeId)
      if (existingOpen == null) {
        val openExecution = positionService.addExecution(
          positionId = positionId,
          quantity = lot.openTrade.quantity,
          price = lot.openTrade.price,
          executionDate = lot.openTrade.tradeDate,
          brokerTradeId = lot.openTrade.brokerTradeId,
          commission = lot.openTrade.commission,
          fxRateToBase = lot.openTrade.fxRateToBase,
        )
        if (lot.openTrade.fxRateToBase != null) {
          forexTrackingService.processExecution(
            portfolioId = portfolioId,
            executionId = openExecution.id,
            date = lot.openTrade.tradeDate,
            netCashUsd = lot.openTrade.netAmount,
            fxRate = lot.openTrade.fxRateToBase,
            description = "${lot.openTrade.symbol} ${lot.openTrade.direction}",
          )
        }
        executionsCreated++
      } else {
        logger.debug("Execution ${lot.openTrade.brokerTradeId} already exists, skipping")
      }

      // Add closing execution (skip if already exists)
      if (lot.closeTrade != null) {
        val existingClose = executionRepository.findByBrokerTradeId(lot.closeTrade.brokerTradeId)
        if (existingClose == null) {
          val closeExecution = positionService.addExecution(
            positionId = positionId,
            quantity = -lot.closeTrade.quantity,
            price = lot.closeTrade.price,
            executionDate = lot.closeTrade.tradeDate,
            brokerTradeId = lot.closeTrade.brokerTradeId,
            commission = lot.closeTrade.commission,
            fxRateToBase = lot.closeTrade.fxRateToBase,
          )
          if (lot.closeTrade.fxRateToBase != null) {
            forexTrackingService.processExecution(
              portfolioId = portfolioId,
              executionId = closeExecution.id,
              date = lot.closeTrade.tradeDate,
              netCashUsd = lot.closeTrade.netAmount,
              fxRate = lot.closeTrade.fxRateToBase,
              description = "${lot.closeTrade.symbol} ${lot.closeTrade.direction}",
            )
          }
          executionsCreated++
        } else {
          logger.debug("Execution ${lot.closeTrade.brokerTradeId} already exists, skipping")
        }
      }

      // Update position metadata if rolling to next lot
      if (!isLast) {
        val nextLot = chain.lots[index + 1]
        positionService.updatePositionAfterRoll(
          positionId = positionId,
          newSymbol = nextLot.symbol,
          newStrike = nextLot.openTrade.optionDetails?.strike,
          newExpiry = nextLot.openTrade.optionDetails?.expiry,
          notes = "Rolled from ${lot.symbol}"
        )
      }
    }

    // Close position if chain is closed
    if (chain.isClosed) {
      positionService.closePosition(
        positionId,
        chain.lots
          .last()
          .closeTrade!!
          .tradeDate
      )
    }

    logger.info(
      "Imported roll chain for ${chain.underlying}: ${chain.lots.size} strikes, " +
        "$executionsCreated executions, status: ${if (chain.isClosed) "CLOSED" else "OPEN"}"
    )

    return ChainImportResult(
      positionsCreated = if (isNewPosition) 1 else 0,
      newPositions = if (isNewPosition) 1 else 0,
      executionsCreated = executionsCreated
    )
  }

  private fun importCashTransactions(
    portfolioId: Long,
    cashTransactions: List<StandardizedCashTransaction>,
    portfolioCurrency: String,
  ) {
    if (cashTransactions.isEmpty()) return

    var imported = 0
    cashTransactions.forEach { cashTx ->
      val convertedAmount = convertToPortfolioCurrency(cashTx, portfolioCurrency)

      val result = cashTransactionService.addCashTransaction(
        portfolioId = portfolioId,
        type = cashTx.type,
        amount = cashTx.amount,
        transactionDate = cashTx.transactionDate,
        description = cashTx.description,
        currency = cashTx.currency,
        convertedAmount = convertedAmount,
        fxRateToBase = cashTx.fxRateToBase,
        brokerTransactionId = cashTx.brokerTransactionId,
        source = CashTransactionSource.BROKER,
      )
      if (result.id != null) imported++
    }

    logger.info("Imported $imported cash transactions for portfolio $portfolioId")
  }

  private fun convertToPortfolioCurrency(
    cashTx: StandardizedCashTransaction,
    portfolioCurrency: String,
  ): Double {
    if (cashTx.currency == portfolioCurrency) return cashTx.amount

    val rate = midgaardClient.getHistoricalExchangeRate(
      portfolioCurrency,
      cashTx.currency,
      cashTx.transactionDate,
    )
    if (rate == null) {
      logger.warn(
        "Could not get FX rate {}/{} on {} for cash tx {}, using original amount",
        portfolioCurrency,
        cashTx.currency,
        cashTx.transactionDate,
        cashTx.brokerTransactionId,
      )
      return cashTx.amount
    }

    val converted = cashTx.amount / rate
    logger.debug(
      "Converted {} {} → {:.2f} {} (rate: {})",
      cashTx.amount,
      cashTx.currency,
      converted,
      portfolioCurrency,
      rate,
    )
    return converted
  }

  /**
   * Fetch trades with date fallback: if endDate data isn't available yet (IBKR error 1003),
   * retry with endDate minus 1 day to handle timezone gaps with IBKR US
   */
  private fun fetchWithDateFallback(
    adapter: BrokerAdapter,
    credentials: BrokerCredentials,
    startDate: LocalDate,
    endDate: LocalDate,
    accountId: String = "",
  ): BrokerDataResult =
    try {
      adapter.fetchTrades(credentials, accountId, startDate, endDate)
    } catch (e: IBKRApiException) {
      if (e.message?.contains("1003") == true) {
        val fallbackEndDate = endDate.minusDays(1)
        logger.info("IBKR data not available for $endDate, retrying with $fallbackEndDate after cooldown")
        Thread.sleep(IBKR_RETRY_DELAY_MS)
        adapter.fetchTrades(credentials, accountId, startDate, fallbackEndDate)
      } else {
        throw e
      }
    }

  /**
   * Validate date range and return end date
   */
  private fun validateAndGetEndDate(startDate: LocalDate): LocalDate {
    val endDate = LocalDate.now().minusDays(1)
    val daysBetween = java.time.temporal.ChronoUnit.DAYS
      .between(startDate, endDate)
    if (daysBetween > 366) {
      throw IllegalArgumentException(
        "Date range exceeds IBKR's 366 day limit. " +
          "Selected range: $daysBetween days (from $startDate to $endDate). " +
          "Please select a start date within the last 366 days.",
      )
    }
    if (startDate.isAfter(endDate)) {
      throw IllegalArgumentException("Start date cannot be in the future. Latest data available is for $endDate")
    }
    return endDate
  }

  /**
   * Extract broker config from credentials
   */
  private fun extractBrokerConfig(credentials: BrokerCredentials): Map<String, String> =
    when (credentials) {
      is BrokerCredentials.IBKRCredentials ->
        mapOf(
          "queryId" to credentials.queryId,
        )
      is BrokerCredentials.SchwabCredentials -> mapOf() // Future implementation
      is BrokerCredentials.OAuthCredentials -> mapOf() // Future implementation
    }

  /**
   * Map AssetType to InstrumentType
   */
  private fun mapAssetType(assetType: AssetType): InstrumentType =
    when (assetType) {
      AssetType.STOCK -> InstrumentType.STOCK
      AssetType.OPTION -> InstrumentType.OPTION
      AssetType.ETF -> InstrumentType.ETF
    }

  /**
   * Result from importing a regular (non-rolled) lot
   */
  private data class RegularLotImportResult(
    val positionsCreated: Int,
    val newPositions: Int,
    val executionsCreated: Int,
  )

  /**
   * Result from importing a roll chain
   */
  private data class ChainImportResult(
    val positionsCreated: Int,
    val newPositions: Int,
    val executionsCreated: Int,
  )

  /**
   * Key for grouping lots by position (symbol + strike + expiry)
   */
  private data class PositionKey(
    val symbol: String,
    val strike: Double?,
    val expiry: java.time.LocalDate?,
  )

  private fun allBrokerTradeIdsExist(lots: List<TradeLot>): Boolean {
    val brokerTradeIds = lots
      .flatMap { lot ->
        listOfNotNull(lot.openTrade.brokerTradeId, lot.closeTrade?.brokerTradeId)
      }.distinct()
    if (brokerTradeIds.isEmpty()) return false
    val existing = executionRepository.findExistingBrokerTradeIds(brokerTradeIds)
    return existing.containsAll(brokerTradeIds)
  }

  companion object {
    private val logger: Logger = LoggerFactory.getLogger(BrokerIntegrationService::class.java)
    private const val IBKR_RETRY_DELAY_MS = 1000L
  }
}

/**
 * Result from creating portfolio from broker
 */
data class CreateFromBrokerResult(
  val portfolio: com.skrymer.udgaard.portfolio.model.Portfolio,
  val tradesImported: Int,
  val rollsDetected: Int,
  val warnings: List<String>,
)

/**
 * Result from syncing portfolio
 */
data class PortfolioSyncResult(
  val tradesAdded: Int,
  val tradesUpdated: Int,
  val rollsDetected: Int,
  val lastSyncDate: LocalDateTime,
  val errors: List<String>,
)
