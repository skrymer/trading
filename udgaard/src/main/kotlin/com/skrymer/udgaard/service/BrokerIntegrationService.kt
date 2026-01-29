package com.skrymer.udgaard.service

import com.skrymer.udgaard.domain.ImportResult
import com.skrymer.udgaard.domain.InstrumentTypeDomain
import com.skrymer.udgaard.integration.broker.*
import com.skrymer.udgaard.repository.jooq.ExecutionJooqRepository
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
  private val executionRepository: ExecutionJooqRepository,
) {
  companion object {
    private val logger: Logger = LoggerFactory.getLogger(BrokerIntegrationService::class.java)
  }

  /**
   * Create portfolio from broker data
   */
  @Transactional
  fun createPortfolioFromBroker(
    name: String,
    broker: BrokerType,
    credentials: BrokerCredentials,
    startDate: LocalDate? = null,
    currency: String = "USD",
    initialBalance: Double? = null,
  ): CreateFromBrokerResult {
    if (startDate != null) {
      logger.info("Creating portfolio from broker: name=$name, broker=$broker, startDate=$startDate")
    } else {
      logger.info("Creating portfolio from broker: name=$name, broker=$broker (using template defaults)")
    }

    // Calculate end date and validate if startDate is provided
    val endDate = if (startDate != null) LocalDate.now().minusDays(2) else null

    if (startDate != null && endDate != null) {
      // Validate date range (IBKR limit: 366 days)
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
    }

    // 1. Get broker adapter
    val adapter = adapterFactory.getAdapter(broker)

    // 2. Fetch trades AND account info (single API call to minimize rate limiting)
    val brokerData = adapter.fetchTrades(credentials, "", startDate, endDate)
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
    val updatedPortfolio =
      portfolio.copy(
        broker = broker,
        brokerAccountId = accountInfo.accountId,
        brokerConfig = extractBrokerConfig(credentials),
        lastSyncDate = LocalDateTime.now(),
      )

    val savedPortfolio = portfolioService.updatePortfolioWithBrokerInfo(updatedPortfolio)
    logger.info("Created portfolio: id=${savedPortfolio.id}, broker=$broker, accountId=${accountInfo.accountId}")

    // 5. Import trades
    val imported = importTrades(portfolio.id!!, lots, rolls)

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

    // Fetch trades since last sync
    val lastSync = portfolio.lastSyncDate?.toLocalDate() ?: portfolio.createdDate.toLocalDate()
    val endDate = LocalDate.now().minusDays(1)

    // Validate date range (IBKR limit: 366 days)
    val daysBetween = java.time.temporal.ChronoUnit.DAYS
      .between(lastSync, endDate)
    if (daysBetween > 366) {
      throw IllegalArgumentException(
        "Sync date range exceeds IBKR's 366 day limit. " +
          "Last sync was $daysBetween days ago (on $lastSync). " +
          "Portfolio must be synced at least once every 366 days.",
      )
    }
    if (lastSync.isAfter(endDate)) {
      logger.info("Portfolio already up to date. Last sync: $lastSync, latest data available: $endDate")
      return PortfolioSyncResult(
        tradesAdded = 0,
        tradesUpdated = 0,
        rollsDetected = 0,
        lastSyncDate = LocalDateTime.now(),
        errors = listOf("Portfolio is already up to date. No new data available."),
      )
    }

    // Fetch trades AND account info
    val brokerData =
      adapter.fetchTrades(
        credentials,
        portfolio.brokerAccountId ?: "",
        lastSync,
        endDate,
      )
    val standardizedTrades = brokerData.trades

    // Process trades
    val lots = tradeProcessor.splitPartialCloses(standardizedTrades)
    val rolls = tradeProcessor.detectOptionRolls(lots)

    // Import/update trades
    val imported = importTrades(portfolioId, lots, rolls)

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

    var executionsCreated = 0
    val processedBrokerTradeIds = mutableSetOf<String>()

    // Group lots by broker_trade_id to aggregate quantities from FIFO splits
    val openingTrades = lots.groupBy { it.openTrade.brokerTradeId }
    val closingTrades = lots.mapNotNull { it.closeTrade }.groupBy { it.brokerTradeId }

    // Process opening executions (aggregate quantities for same broker_trade_id)
    openingTrades.forEach { (brokerTradeId, lotsWithSameTrade) ->
      if (brokerTradeId != null && brokerTradeId !in processedBrokerTradeIds) {
        val existingOpen = executionRepository.findByBrokerTradeId(brokerTradeId)
        if (existingOpen == null) {
          // Sum quantities from all lots that share this broker_trade_id
          val totalQuantity = lotsWithSameTrade.sumOf { it.openTrade.quantity }
          val firstTrade = lotsWithSameTrade.first().openTrade

          positionService.addExecution(
            positionId = position.id!!,
            quantity = totalQuantity,
            price = firstTrade.price,
            executionDate = firstTrade.tradeDate,
            brokerTradeId = brokerTradeId,
            commission = firstTrade.commission
          )
          executionsCreated++
          processedBrokerTradeIds.add(brokerTradeId)
        }
      }
    }

    // Process closing executions (aggregate quantities for same broker_trade_id)
    closingTrades.forEach { (brokerTradeId, tradesWithSameBrokerId) ->
      if (brokerTradeId != null && brokerTradeId !in processedBrokerTradeIds) {
        val existingClose = executionRepository.findByBrokerTradeId(brokerTradeId)
        if (existingClose == null) {
          // Sum quantities from all closing trades that share this broker_trade_id
          val totalQuantity = tradesWithSameBrokerId.sumOf { it.quantity }
          val firstTrade = tradesWithSameBrokerId.first()

          positionService.addExecution(
            positionId = position.id!!,
            quantity = -totalQuantity,
            price = firstTrade.price,
            executionDate = firstTrade.tradeDate,
            brokerTradeId = brokerTradeId,
            commission = firstTrade.commission
          )
          executionsCreated++
          processedBrokerTradeIds.add(brokerTradeId)
        }
      }
    }

    // Check if position should be closed
    val updatedPosition = positionService.getPositionById(position.id!!)
    if (updatedPosition != null && updatedPosition.currentQuantity == 0) {
      // Find the latest closing date
      val latestCloseDate = lots.mapNotNull { it.closeTrade?.tradeDate }.maxOrNull()
      if (latestCloseDate != null) {
        positionService.closePosition(position.id!!, latestCloseDate)
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
   * DEPRECATED: Replaced by importLotGroup
   * Import regular (non-rolled) lot into portfolio
   * Creates a simple position with opening and optionally closing execution
   */
  private fun importRegularLot(
    portfolioId: Long,
    lot: TradeLot,
  ): RegularLotImportResult {
    // Skip if opening execution already exists (sync scenario)
    if (lot.openTrade.brokerTradeId != null) {
      val existingOpen = executionRepository.findByBrokerTradeId(lot.openTrade.brokerTradeId)
      if (existingOpen != null) {
        logger.debug("Opening execution ${lot.openTrade.brokerTradeId} already exists, skipping lot")
        return RegularLotImportResult(
          positionsCreated = 0,
          newPositions = 0,
          executionsCreated = 0
        )
      }
    }

    // Create position
    val position = positionService.findOrCreatePosition(
      portfolioId = portfolioId,
      symbol = lot.symbol,
      instrumentType = mapAssetType(lot.openTrade.assetType),
      underlyingSymbol = lot.openTrade.optionDetails?.underlyingSymbol,
      optionType = lot.openTrade.optionDetails?.optionType,
      strikePrice = lot.openTrade.optionDetails?.strike,
      expirationDate = lot.openTrade.optionDetails?.expiry,
      openedDate = lot.openTrade.tradeDate,
      currency = lot.openTrade.currency,
      entryStrategy = "Broker Import",
      exitStrategy = "Broker Import"
    )

    var executionsCreated = 0

    // Add opening execution
    positionService.addExecution(
      positionId = position.id!!,
      quantity = lot.openTrade.quantity,
      price = lot.openTrade.price,
      executionDate = lot.openTrade.tradeDate,
      brokerTradeId = lot.openTrade.brokerTradeId,
      commission = lot.openTrade.commission
    )
    executionsCreated++

    // Add closing execution if closed (skip if already exists)
    if (lot.closeTrade != null) {
      if (lot.closeTrade.brokerTradeId != null) {
        val existingClose = executionRepository.findByBrokerTradeId(lot.closeTrade.brokerTradeId)
        if (existingClose == null) {
          positionService.addExecution(
            positionId = position.id!!,
            quantity = -lot.closeTrade.quantity,
            price = lot.closeTrade.price,
            executionDate = lot.closeTrade.tradeDate,
            brokerTradeId = lot.closeTrade.brokerTradeId,
            commission = lot.closeTrade.commission
          )
          executionsCreated++
        } else {
          logger.debug("Closing execution ${lot.closeTrade.brokerTradeId} already exists, skipping")
        }
      } else {
        // No broker trade ID, always insert
        positionService.addExecution(
          positionId = position.id!!,
          quantity = -lot.closeTrade.quantity,
          price = lot.closeTrade.price,
          executionDate = lot.closeTrade.tradeDate,
          brokerTradeId = null,
          commission = lot.closeTrade.commission
        )
        executionsCreated++
      }

      // Only close position if it has no remaining quantity
      val updatedPosition = positionService.getPositionById(position.id!!)
      if (updatedPosition?.currentQuantity == 0) {
        positionService.closePosition(position.id!!, lot.closeTrade.tradeDate)
      }
    }

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

    val isNewPosition = existingPosition == null && position.currentQuantity == 0

    // Process each lot in the chain
    chain.lots.forEachIndexed { index, lot ->
      val isLast = index == chain.lots.lastIndex

      // Add opening execution (skip if already exists)
      if (lot.openTrade.brokerTradeId != null) {
        val existingOpen = executionRepository.findByBrokerTradeId(lot.openTrade.brokerTradeId)
        if (existingOpen == null) {
          positionService.addExecution(
            positionId = position.id!!,
            quantity = lot.openTrade.quantity,
            price = lot.openTrade.price,
            executionDate = lot.openTrade.tradeDate,
            brokerTradeId = lot.openTrade.brokerTradeId,
            commission = lot.openTrade.commission
          )
          executionsCreated++
        } else {
          logger.debug("Execution ${lot.openTrade.brokerTradeId} already exists, skipping")
        }
      }

      // Add closing execution (skip if already exists)
      if (lot.closeTrade != null && lot.closeTrade.brokerTradeId != null) {
        val existingClose = executionRepository.findByBrokerTradeId(lot.closeTrade.brokerTradeId)
        if (existingClose == null) {
          positionService.addExecution(
            positionId = position.id!!,
            quantity = -lot.closeTrade.quantity,
            price = lot.closeTrade.price,
            executionDate = lot.closeTrade.tradeDate,
            brokerTradeId = lot.closeTrade.brokerTradeId,
            commission = lot.closeTrade.commission
          )
          executionsCreated++
        } else {
          logger.debug("Execution ${lot.closeTrade.brokerTradeId} already exists, skipping")
        }
      }

      // Update position metadata if rolling to next lot
      if (!isLast) {
        val nextLot = chain.lots[index + 1]
        positionService.updatePositionAfterRoll(
          positionId = position.id!!,
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
        position.id!!,
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
   * Map AssetType to InstrumentTypeDomain
   */
  private fun mapAssetType(assetType: AssetType): InstrumentTypeDomain =
    when (assetType) {
      AssetType.STOCK -> InstrumentTypeDomain.STOCK
      AssetType.OPTION -> InstrumentTypeDomain.OPTION
      AssetType.ETF -> InstrumentTypeDomain.LEVERAGED_ETF
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
}

/**
 * Result from creating portfolio from broker
 */
data class CreateFromBrokerResult(
  val portfolio: com.skrymer.udgaard.domain.PortfolioDomain,
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
