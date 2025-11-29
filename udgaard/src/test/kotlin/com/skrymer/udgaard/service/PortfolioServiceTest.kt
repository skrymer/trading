package com.skrymer.udgaard.service

import com.skrymer.udgaard.model.*
import com.skrymer.udgaard.repository.PortfolioRepository
import com.skrymer.udgaard.repository.PortfolioTradeRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class PortfolioServiceTest {

    private lateinit var portfolioService: PortfolioService
    private lateinit var portfolioRepository: PortfolioRepository
    private lateinit var portfolioTradeRepository: PortfolioTradeRepository
    private lateinit var stockService: StockService
    private lateinit var strategyRegistry: StrategyRegistry

    @BeforeEach
    fun setup() {
        portfolioRepository = mock<PortfolioRepository>()
        portfolioTradeRepository = mock<PortfolioTradeRepository>()
        stockService = mock<StockService>()
        strategyRegistry = mock<StrategyRegistry>()
        portfolioService = PortfolioService(
            portfolioRepository,
            portfolioTradeRepository,
            stockService,
            strategyRegistry
        )
    }

    // ================== openTrade Tests ==================

    @Test
    fun `openTrade should deduct stock trade cost from portfolio balance`() {
        // Given: Portfolio with $100,000 balance
        val portfolioId = 1L
        val initialBalance = 100000.0
        val portfolio = Portfolio(
            id = 1L,
            name = "Test Portfolio",
            initialBalance = initialBalance,
            currentBalance = initialBalance,
            currency = "USD",
            createdDate = LocalDateTime.now().minusDays(30)
        )

        whenever(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio))
        whenever(portfolioRepository.save(any())).thenAnswer { it.arguments[0] }
        whenever(portfolioTradeRepository.save(any<PortfolioTrade>())).thenAnswer { it.arguments[0] }

        // When: Opening a $25,000 stock position (100 shares at $250)
        val trade = portfolioService.openTrade(
            portfolioId = portfolioId,
            symbol = "AAPL",
            entryPrice = 250.0,
            entryDate = LocalDate.now(),
            quantity = 100,
            entryStrategy = "TestEntry",
            exitStrategy = "TestExit",
            currency = "USD",
            instrumentType = InstrumentType.STOCK
        )

        // Then: Portfolio balance should be reduced by $25,000
        val expectedBalance = 75000.0
        verify(portfolioRepository).save(argThat { currentBalance == expectedBalance })
        assertEquals(InstrumentType.STOCK, trade.instrumentType)
        assertEquals(250.0, trade.entryPrice)
        assertEquals(100, trade.quantity)
    }

    @Test
    fun `openTrade should deduct option trade cost from portfolio balance`() {
        // Given: Portfolio with $100,000 balance
        val portfolioId = 1L
        val initialBalance = 100000.0
        val portfolio = Portfolio(
            id = 1L,
            name = "Test Portfolio",
            initialBalance = initialBalance,
            currentBalance = initialBalance,
            currency = "USD",
            createdDate = LocalDateTime.now().minusDays(30)
        )

        whenever(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio))
        whenever(portfolioRepository.save(any())).thenAnswer { it.arguments[0] }
        whenever(portfolioTradeRepository.save(any<PortfolioTrade>())).thenAnswer { it.arguments[0] }

        // When: Opening an option position (5 contracts at $10 premium, 100 multiplier = $5,000)
        val trade = portfolioService.openTrade(
            portfolioId = portfolioId,
            symbol = "AAPL250C",
            entryPrice = 10.0,
            entryDate = LocalDate.now(),
            quantity = 5,
            entryStrategy = "TestEntry",
            exitStrategy = "TestExit",
            currency = "USD",
            instrumentType = InstrumentType.OPTION,
            optionType = OptionType.CALL,
            strikePrice = 250.0,
            expirationDate = LocalDate.now().plusDays(30),
            contracts = 5,
            multiplier = 100
        )

        // Then: Portfolio balance should be reduced by $5,000 (5 * 10 * 100)
        val expectedBalance = 95000.0
        verify(portfolioRepository).save(argThat { currentBalance == expectedBalance })
        assertEquals(InstrumentType.OPTION, trade.instrumentType)
        assertEquals(10.0, trade.entryPrice)
        assertEquals(5, trade.contracts)
    }

    // ================== closeTrade Tests ==================

    @Test
    fun `closeTrade should add exit value to portfolio balance for profitable stock trade`() {
        // Given: Portfolio with $75,000 balance and an open $25,000 stock position
        val portfolioId = 1L
        val tradeId = 1L
        val portfolio = Portfolio(
            id = 1L,
            name = "Test Portfolio",
            initialBalance = 100000.0,
            currentBalance = 75000.0,
            currency = "USD"
        )

        val openTrade = PortfolioTrade(
            id = 1L,
            portfolioId = portfolioId,
            symbol = "AAPL",
            entryPrice = 250.0,
            entryDate = LocalDate.now().minusDays(10),
            quantity = 100,
            entryStrategy = "TestEntry",
            exitStrategy = "TestExit",
            currency = "USD",
            status = TradeStatus.OPEN,
            instrumentType = InstrumentType.STOCK
        )

        whenever(portfolioTradeRepository.findById(tradeId)).thenReturn(Optional.of(openTrade))
        whenever(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio))
        whenever(portfolioRepository.save(any())).thenAnswer { it.arguments[0] }
        whenever(portfolioTradeRepository.save(any<PortfolioTrade>())).thenAnswer { it.arguments[0] }

        // When: Closing the trade at $300 (exit value = 100 * 300 = $30,000)
        val closedTrade = portfolioService.closeTrade(
            tradeId = tradeId,
            exitPrice = 300.0,
            exitDate = LocalDate.now()
        )

        // Then: Balance should be $75,000 + $30,000 = $105,000
        val expectedBalance = 105000.0
        verify(portfolioRepository).save(argThat { currentBalance == expectedBalance })
        assertEquals(TradeStatus.CLOSED, closedTrade?.status)
        assertEquals(300.0, closedTrade?.exitPrice)
        assertEquals(5000.0, closedTrade?.profit) // Profit: (300-250) * 100 = $5,000
    }

    @Test
    fun `closeTrade should add exit value to portfolio balance for losing option trade`() {
        // Given: Portfolio with $95,000 balance and an open $5,000 option position
        val portfolioId = 1L
        val tradeId = 1L
        val portfolio = Portfolio(
            id = 1L,
            name = "Test Portfolio",
            initialBalance = 100000.0,
            currentBalance = 95000.0,
            currency = "USD"
        )

        val openTrade = PortfolioTrade(
            id = 1L,
            portfolioId = portfolioId,
            symbol = "AAPL250C",
            entryPrice = 10.0,
            entryDate = LocalDate.now().minusDays(5),
            quantity = 5,
            entryStrategy = "TestEntry",
            exitStrategy = "TestExit",
            currency = "USD",
            status = TradeStatus.OPEN,
            instrumentType = InstrumentType.OPTION,
            optionType = OptionType.CALL,
            strikePrice = 250.0,
            expirationDate = LocalDate.now().plusDays(25),
            contracts = 5,
            multiplier = 100
        )

        whenever(portfolioTradeRepository.findById(tradeId)).thenReturn(Optional.of(openTrade))
        whenever(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio))
        whenever(portfolioRepository.save(any())).thenAnswer { it.arguments[0] }
        whenever(portfolioTradeRepository.save(any<PortfolioTrade>())).thenAnswer { it.arguments[0] }

        // When: Closing at $6 (exit value = 5 * 6 * 100 = $3,000, loss of $2,000)
        val closedTrade = portfolioService.closeTrade(
            tradeId = tradeId,
            exitPrice = 6.0,
            exitDate = LocalDate.now()
        )

        // Then: Balance should be $95,000 + $3,000 = $98,000
        val expectedBalance = 98000.0
        verify(portfolioRepository).save(argThat { currentBalance == expectedBalance })
        assertEquals(TradeStatus.CLOSED, closedTrade?.status)
        assertEquals(6.0, closedTrade?.exitPrice)
        assertEquals(-2000.0, closedTrade?.profit) // Loss: (6-10) * 5 * 100 = -$2,000
    }

    // ================== deleteTrade Tests ==================

    @Test
    fun `deleteTrade should refund stock trade cost to portfolio balance`() {
        // Given: Portfolio with $75,000 balance and an open $25,000 stock position
        val portfolioId = 1L
        val tradeId = 1L
        val portfolio = Portfolio(
            id = 1L,
            name = "Test Portfolio",
            initialBalance = 100000.0,
            currentBalance = 75000.0,
            currency = "USD"
        )

        val openTrade = PortfolioTrade(
            id = 1L,
            portfolioId = portfolioId,
            symbol = "AAPL",
            entryPrice = 250.0,
            entryDate = LocalDate.now(),
            quantity = 100,
            entryStrategy = "TestEntry",
            exitStrategy = "TestExit",
            currency = "USD",
            status = TradeStatus.OPEN,
            instrumentType = InstrumentType.STOCK
        )

        whenever(portfolioTradeRepository.findById(tradeId)).thenReturn(Optional.of(openTrade))
        whenever(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio))
        whenever(portfolioRepository.save(any())).thenAnswer { it.arguments[0] }

        // When: Deleting the trade
        val result = portfolioService.deleteTrade(tradeId)

        // Then: Balance should be refunded to $100,000
        val expectedBalance = 100000.0
        verify(portfolioRepository).save(argThat { currentBalance == expectedBalance })
        verify(portfolioTradeRepository).deleteById(tradeId)
        assertTrue(result)
    }

    @Test
    fun `deleteTrade should refund option trade cost to portfolio balance`() {
        // Given: Portfolio with $95,000 balance and an open $5,000 option position
        val portfolioId = 1L
        val tradeId = 1L
        val portfolio = Portfolio(
            id = 1L,
            name = "Test Portfolio",
            initialBalance = 100000.0,
            currentBalance = 95000.0,
            currency = "USD"
        )

        val openTrade = PortfolioTrade(
            id = 1L,
            portfolioId = portfolioId,
            symbol = "AAPL250C",
            entryPrice = 10.0,
            entryDate = LocalDate.now(),
            quantity = 5,
            entryStrategy = "TestEntry",
            exitStrategy = "TestExit",
            currency = "USD",
            status = TradeStatus.OPEN,
            instrumentType = InstrumentType.OPTION,
            optionType = OptionType.CALL,
            strikePrice = 250.0,
            expirationDate = LocalDate.now().plusDays(30),
            contracts = 5,
            multiplier = 100
        )

        whenever(portfolioTradeRepository.findById(tradeId)).thenReturn(Optional.of(openTrade))
        whenever(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio))
        whenever(portfolioRepository.save(any())).thenAnswer { it.arguments[0] }

        // When: Deleting the trade
        val result = portfolioService.deleteTrade(tradeId)

        // Then: Balance should be refunded to $100,000
        val expectedBalance = 100000.0
        verify(portfolioRepository).save(argThat { currentBalance == expectedBalance })
        verify(portfolioTradeRepository).deleteById(tradeId)
        assertTrue(result)
    }

    @Test
    fun `deleteTrade should throw exception for closed trades`() {
        // Given: A closed trade
        val tradeId = 1L
        val closedTrade = PortfolioTrade(
            id = 1L,
            portfolioId = 1L,
            symbol = "AAPL",
            entryPrice = 250.0,
            entryDate = LocalDate.now().minusDays(10),
            exitPrice = 300.0,
            exitDate = LocalDate.now(),
            quantity = 100,
            entryStrategy = "TestEntry",
            exitStrategy = "TestExit",
            currency = "USD",
            status = TradeStatus.CLOSED,
            instrumentType = InstrumentType.STOCK
        )

        whenever(portfolioTradeRepository.findById(tradeId)).thenReturn(Optional.of(closedTrade))

        // When/Then: Should throw exception
        assertThrows(IllegalArgumentException::class.java) {
            portfolioService.deleteTrade(tradeId)
        }
    }

    // ================== updateTrade Tests ==================

    @Test
    fun `updateTrade should increase balance deduction when stock quantity increases`() {
        // Given: Portfolio with $75,000 balance and an open stock position (100 shares at $250 = $25,000)
        val portfolioId = 1L
        val tradeId = 1L
        val portfolio = Portfolio(
            id = 1L,
            name = "Test Portfolio",
            initialBalance = 100000.0,
            currentBalance = 75000.0,
            currency = "USD"
        )

        val openTrade = PortfolioTrade(
            id = 1L,
            portfolioId = portfolioId,
            symbol = "AAPL",
            entryPrice = 250.0,
            entryDate = LocalDate.now(),
            quantity = 100,
            entryStrategy = "TestEntry",
            exitStrategy = "TestExit",
            currency = "USD",
            status = TradeStatus.OPEN,
            instrumentType = InstrumentType.STOCK
        )

        whenever(portfolioTradeRepository.findById(tradeId)).thenReturn(Optional.of(openTrade))
        whenever(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio))
        whenever(portfolioRepository.save(any())).thenAnswer { it.arguments[0] }
        whenever(portfolioTradeRepository.save(any<PortfolioTrade>())).thenAnswer { it.arguments[0] }

        // When: Updating quantity to 120 shares (new cost = $30,000, difference = $5,000)
        val updatedTrade = portfolioService.updateTrade(
            tradeId = tradeId,
            quantity = 120
        )

        // Then: Balance should decrease by $5,000 to $70,000
        val expectedBalance = 70000.0
        verify(portfolioRepository).save(argThat { currentBalance == expectedBalance })
        assertEquals(120, updatedTrade?.quantity)
    }

    @Test
    fun `updateTrade should decrease balance deduction when option contracts decrease`() {
        // Given: Portfolio with $95,000 balance and an open option position (5 contracts at $10 = $5,000)
        val portfolioId = 1L
        val tradeId = 1L
        val portfolio = Portfolio(
            id = 1L,
            name = "Test Portfolio",
            initialBalance = 100000.0,
            currentBalance = 95000.0,
            currency = "USD"
        )

        val openTrade = PortfolioTrade(
            id = 1L,
            portfolioId = portfolioId,
            symbol = "AAPL250C",
            entryPrice = 10.0,
            entryDate = LocalDate.now(),
            quantity = 5,
            entryStrategy = "TestEntry",
            exitStrategy = "TestExit",
            currency = "USD",
            status = TradeStatus.OPEN,
            instrumentType = InstrumentType.OPTION,
            optionType = OptionType.CALL,
            strikePrice = 250.0,
            expirationDate = LocalDate.now().plusDays(30),
            contracts = 5,
            multiplier = 100
        )

        whenever(portfolioTradeRepository.findById(tradeId)).thenReturn(Optional.of(openTrade))
        whenever(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio))
        whenever(portfolioRepository.save(any())).thenAnswer { it.arguments[0] }
        whenever(portfolioTradeRepository.save(any<PortfolioTrade>())).thenAnswer { it.arguments[0] }

        // When: Updating contracts to 3 (new cost = $3,000, refund = $2,000)
        val updatedTrade = portfolioService.updateTrade(
            tradeId = tradeId,
            contracts = 3
        )

        // Then: Balance should increase by $2,000 to $97,000
        val expectedBalance = 97000.0
        verify(portfolioRepository).save(argThat { currentBalance == expectedBalance })
        assertEquals(3, updatedTrade?.contracts)
    }

    @Test
    fun `updateTrade should adjust balance when entry price changes`() {
        // Given: Portfolio with $75,000 balance and an open stock position (100 shares at $250 = $25,000)
        val portfolioId = 1L
        val tradeId = 1L
        val portfolio = Portfolio(
            id = 1L,
            name = "Test Portfolio",
            initialBalance = 100000.0,
            currentBalance = 75000.0,
            currency = "USD"
        )

        val openTrade = PortfolioTrade(
            id = 1L,
            portfolioId = portfolioId,
            symbol = "AAPL",
            entryPrice = 250.0,
            entryDate = LocalDate.now(),
            quantity = 100,
            entryStrategy = "TestEntry",
            exitStrategy = "TestExit",
            currency = "USD",
            status = TradeStatus.OPEN,
            instrumentType = InstrumentType.STOCK
        )

        whenever(portfolioTradeRepository.findById(tradeId)).thenReturn(Optional.of(openTrade))
        whenever(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio))
        whenever(portfolioRepository.save(any())).thenAnswer { it.arguments[0] }
        whenever(portfolioTradeRepository.save(any<PortfolioTrade>())).thenAnswer { it.arguments[0] }

        // When: Updating entry price to $200 (new cost = $20,000, refund = $5,000)
        val updatedTrade = portfolioService.updateTrade(
            tradeId = tradeId,
            entryPrice = 200.0
        )

        // Then: Balance should increase by $5,000 to $80,000
        val expectedBalance = 80000.0
        verify(portfolioRepository).save(argThat { currentBalance == expectedBalance })
        assertEquals(200.0, updatedTrade?.entryPrice)
    }

    @Test
    fun `updateTrade should not change balance when non-cost fields are updated`() {
        // Given: Portfolio with $75,000 balance and an open stock position
        val portfolioId = 1L
        val tradeId = 1L
        val portfolio = Portfolio(
            id = 1L,
            name = "Test Portfolio",
            initialBalance = 100000.0,
            currentBalance = 75000.0,
            currency = "USD"
        )

        val openTrade = PortfolioTrade(
            id = 1L,
            portfolioId = portfolioId,
            symbol = "AAPL",
            entryPrice = 250.0,
            entryDate = LocalDate.now(),
            quantity = 100,
            entryStrategy = "TestEntry",
            exitStrategy = "TestExit",
            currency = "USD",
            status = TradeStatus.OPEN,
            instrumentType = InstrumentType.STOCK
        )

        whenever(portfolioTradeRepository.findById(tradeId)).thenReturn(Optional.of(openTrade))
        whenever(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio))
        whenever(portfolioRepository.save(any())).thenAnswer { it.arguments[0] }
        whenever(portfolioTradeRepository.save(any<PortfolioTrade>())).thenAnswer { it.arguments[0] }

        // When: Updating only the exit strategy
        val updatedTrade = portfolioService.updateTrade(
            tradeId = tradeId,
            exitStrategy = "NewExitStrategy"
        )

        // Then: Balance should remain unchanged at $75,000
        verify(portfolioRepository, never()).save(any())
        assertEquals("NewExitStrategy", updatedTrade?.exitStrategy)
    }
}
