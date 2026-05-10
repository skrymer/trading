package com.skrymer.udgaard.portfolio.model

import com.skrymer.udgaard.portfolio.integration.broker.BrokerType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class PortfolioTest {
  @Test
  fun `create stamps now and seeds currentBalance from initialBalance`() {
    // Given
    val before = LocalDateTime.now()

    // When
    val portfolio = Portfolio.create(
      name = "Test",
      initialBalance = 10_000.0,
      currency = "USD",
      userId = "alice",
    )

    // Then
    val after = LocalDateTime.now()
    assertNull(portfolio.id)
    assertEquals("alice", portfolio.userId)
    assertEquals("Test", portfolio.name)
    assertEquals(10_000.0, portfolio.initialBalance)
    assertEquals(10_000.0, portfolio.currentBalance)
    assertEquals("USD", portfolio.currency)
    assertEquals(BrokerType.MANUAL, portfolio.broker)
    assertNull(portfolio.lastSyncDate)
    assertTrue(!portfolio.createdDate.isBefore(before) && !portfolio.createdDate.isAfter(after))
    assertEquals(portfolio.createdDate, portfolio.lastUpdated)
  }

  @Test
  fun `withBalanceUpdated changes currentBalance and bumps lastUpdated only`() {
    // Given
    val original = Portfolio.create(name = "Test", initialBalance = 10_000.0, currency = "USD")
    val originalCreated = original.createdDate
    val originalLastUpdated = original.lastUpdated.minus(1, ChronoUnit.HOURS)
    val portfolio = original.copy(lastUpdated = originalLastUpdated)

    // When
    val updated = portfolio.withBalanceUpdated(12_500.0)

    // Then
    assertEquals(12_500.0, updated.currentBalance)
    assertEquals(originalCreated, updated.createdDate)
    assertTrue(updated.lastUpdated.isAfter(originalLastUpdated))
    assertEquals(portfolio.initialBalance, updated.initialBalance)
  }

  @Test
  fun `create seeds baseCurrency from currency`() {
    // Given a non-USD portfolio
    // When
    val portfolio = Portfolio.create(name = "AUD account", initialBalance = 5_000.0, currency = "AUD")

    // Then: baseCurrency follows currency rather than defaulting to USD
    assertEquals("AUD", portfolio.currency)
    assertEquals("AUD", portfolio.baseCurrency)
  }

  @Test
  fun `create rejects blank name`() {
    // When / Then
    assertThrows(IllegalArgumentException::class.java) {
      Portfolio.create(name = "", initialBalance = 1.0, currency = "USD")
    }
  }

  @Test
  fun `create rejects blank currency`() {
    // When / Then
    assertThrows(IllegalArgumentException::class.java) {
      Portfolio.create(name = "x", initialBalance = 1.0, currency = "")
    }
  }

  @Test
  fun `create rejects negative initialBalance`() {
    // When / Then
    assertThrows(IllegalArgumentException::class.java) {
      Portfolio.create(name = "x", initialBalance = -1.0, currency = "USD")
    }
  }

  @Test
  fun `create allows zero initialBalance for empty broker accounts`() {
    // When
    val portfolio = Portfolio.create(name = "Empty", initialBalance = 0.0, currency = "USD")

    // Then
    assertEquals(0.0, portfolio.initialBalance)
    assertEquals(0.0, portfolio.currentBalance)
  }

  @Test
  fun `withRealizedPnlApplied adds pnl plus commissions to currentBalance and bumps lastUpdated`() {
    // Given: portfolio at $10,000, lastUpdated stale
    val original = Portfolio.create(name = "Test", initialBalance = 10_000.0, currency = "USD")
    val staleLastUpdated = original.lastUpdated.minus(1, ChronoUnit.HOURS)
    val portfolio = original.copy(lastUpdated = staleLastUpdated)

    // When: a closed position contributes +$200 of P&L and -$5 of commissions (negative sign per broker convention)
    val applied = portfolio.withRealizedPnlApplied(realizedPnl = 200.0, commissions = -5.0)

    // Then: balance moves by (200 + -5) = 195; createdDate untouched; initialBalance untouched; lastUpdated bumped
    assertEquals(10_195.0, applied.currentBalance)
    assertEquals(original.createdDate, applied.createdDate)
    assertEquals(10_000.0, applied.initialBalance, "Initial balance is the historical seed and should never change")
    assertTrue(applied.lastUpdated.isAfter(staleLastUpdated))
  }

  @Test
  fun `withSyncCompleted sets lastSyncDate and lastUpdated to the supplied timestamp`() {
    // Given
    val portfolio = Portfolio.create(name = "Test", initialBalance = 10_000.0, currency = "USD")
    val originalCreated = portfolio.createdDate
    val syncedAt = LocalDateTime.of(2026, 5, 8, 9, 30)

    // When
    val synced = portfolio.withSyncCompleted(syncedAt)

    // Then
    assertEquals(syncedAt, synced.lastSyncDate)
    assertEquals(syncedAt, synced.lastUpdated)
    assertEquals(originalCreated, synced.createdDate)
    assertNotNull(synced.lastSyncDate)
  }
}
