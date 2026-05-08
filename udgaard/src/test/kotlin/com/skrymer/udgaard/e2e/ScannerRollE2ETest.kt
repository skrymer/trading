package com.skrymer.udgaard.e2e

import com.skrymer.udgaard.scanner.dto.AddScannerTradeRequest
import com.skrymer.udgaard.scanner.dto.RollScannerTradeRequest
import com.skrymer.udgaard.scanner.model.ScannerTrade
import com.skrymer.udgaard.scanner.model.TradeStatus
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

/**
 * Covers POST /api/scanner/trades/{id}/roll over real HTTP + Postgres.
 *
 * The load-bearing invariant pinned here is the **`@Transactional` rollback contract**:
 * `rollTrade` performs `delete(old)` then `save(new)` in one transaction. If the new-save
 * leg throws, the old delete must roll back so the DB never observes the half-step. The
 * rollback test triggers a real mid-transaction failure by passing an invalid
 * `newExpirationDate` — `LocalDate.parse` throws `DateTimeParseException` *after* the
 * delete leg has already run, exercising the rollback path without mocks/spies.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScannerRollE2ETest : AbstractIntegrationTest() {
  @Autowired
  private lateinit var dsl: DSLContext

  @BeforeEach
  fun cleanScannerTradesTable() {
    dsl.deleteFrom(DSL.table("scanner_trades")).execute()
  }

  @Test
  fun `POST roll accumulates rolledCredits and rollCount across multiple rolls`() {
    // Given: an OPTION trade with optionPrice set (rollTrade requires it)
    val saved = restTemplate
      .postForEntity(
        "/api/scanner/trades",
        jsonEntity(
          AddScannerTradeRequest(
            symbol = "AAPL",
            sectorSymbol = "XLK",
            instrumentType = "OPTION",
            entryPrice = 5.0,
            entryDate = "2024-01-15",
            quantity = 1,
            entryStrategyName = "TestEntry",
            exitStrategyName = "TestExit",
            optionType = "PUT",
            strikePrice = 140.0,
            expirationDate = "2024-02-16",
            optionPrice = 5.0,
            multiplier = 100,
            notes = "rolled once already, +50",
          ),
        ),
        ScannerTrade::class.java,
      ).body!!

    // rolledCredits + rollCount are engine-managed (no API setter), so seed the
    // "already-rolled-once" state via DSL — that's what proves the next roll *accumulates*
    // rather than replaces.
    dsl
      .update(DSL.table("scanner_trades"))
      .set(DSL.field("rolled_credits"), 50.0)
      .set(DSL.field("roll_count"), 1)
      .where(DSL.field("id").eq(saved.id))
      .execute()

    // When: roll at a closePrice of 3.0 → roll credit = (3.0 - 5.0) * 1 * 100 = -200
    val rollResponse = restTemplate.postForEntity(
      "/api/scanner/trades/${saved.id}/roll",
      jsonEntity(
        RollScannerTradeRequest(
          closePrice = 3.0,
          newStrikePrice = 135.0,
          newExpirationDate = "2024-03-15",
          newEntryPrice = 4.5,
          newEntryDate = "2024-02-16",
          newQuantity = 1,
        ),
      ),
      ScannerTrade::class.java,
    )

    // Then
    assertEquals(HttpStatus.OK, rollResponse.statusCode)
    val rolled = rollResponse.body!!
    assertNotNull(rolled.id)
    assertEquals(-150.0, rolled.rolledCredits, "50 (existing) + (-200) (this roll's credit)")
    assertEquals(2, rolled.rollCount, "1 (existing) + 1 = 2")
    assertEquals(135.0, rolled.strikePrice)
    assertEquals(4.5, rolled.entryPrice)
    assertEquals(TradeStatus.OPEN, rolled.status)

    // Old trade is gone, replaced by the new one
    assertEquals(1, dsl.fetchCount(DSL.table("scanner_trades")))
    assertEquals(0, idCount(saved.id!!), "old trade row deleted")
    assertNotEquals(saved.id, rolled.id, "new trade has a fresh id")
  }

  @Test
  fun `POST roll rolls back the delete when the new-save leg fails mid-transaction`() {
    // Given: a baseline OPTION trade with distinguishing fields the new-row construction
    // would have overwritten on a successful roll
    val saved = restTemplate
      .postForEntity(
        "/api/scanner/trades",
        jsonEntity(
          AddScannerTradeRequest(
            symbol = "AAPL",
            sectorSymbol = "XLK",
            instrumentType = "OPTION",
            entryPrice = 5.0,
            entryDate = "2024-01-15",
            quantity = 1,
            entryStrategyName = "TestEntry",
            exitStrategyName = "TestExit",
            optionType = "PUT",
            strikePrice = 140.0,
            expirationDate = "2024-02-16",
            optionPrice = 5.0,
            multiplier = 100,
            notes = "seed for rollback test",
          ),
        ),
        ScannerTrade::class.java,
      ).body!!

    // When: roll with a malformed newExpirationDate. The service's `delete(old)` leg runs
    // before `LocalDate.parse(request.newExpirationDate)` — the parse throws
    // DateTimeParseException, which is a RuntimeException, so @Transactional rolls back.
    val rollResponse = restTemplate.postForEntity(
      "/api/scanner/trades/${saved.id}/roll",
      jsonEntity(
        RollScannerTradeRequest(
          closePrice = 3.0,
          newStrikePrice = 135.0,
          newExpirationDate = "not-a-date",
          newEntryPrice = 4.5,
          newEntryDate = "2024-02-16",
          newQuantity = 1,
        ),
      ),
      String::class.java,
    )

    // Then: GlobalExceptionHandler maps DateTimeParseException to 400, AND the old trade
    // is still present — the delete leg rolled back when the parse before save failed.
    // Asserting on `notes` and `strike_price` (fields the new-row construction would have
    // overwritten) makes the test degrade into a stricter check rather than a silent no-op
    // if rollTrade is ever refactored to validate inputs before the delete leg.
    assertEquals(HttpStatus.BAD_REQUEST, rollResponse.statusCode)
    assertEquals(1, dsl.fetchCount(DSL.table("scanner_trades")))
    assertEquals(1, idCount(saved.id!!), "old trade row preserved by @Transactional rollback")
    assertEquals("OPEN", statusOf(saved.id))
    assertEquals("seed for rollback test", notesOf(saved.id))
    assertEquals(140.0, strikePriceOf(saved.id))
  }

  private fun idCount(id: Long): Int =
    dsl
      .selectCount()
      .from(DSL.table("scanner_trades"))
      .where(DSL.field("id").eq(id))
      .fetchOne(0, Int::class.java) ?: 0

  private fun statusOf(id: Long): String? =
    dsl
      .select(DSL.field("status", String::class.java))
      .from(DSL.table("scanner_trades"))
      .where(DSL.field("id").eq(id))
      .fetchOne()
      ?.value1()

  private fun notesOf(id: Long): String? =
    dsl
      .select(DSL.field("notes", String::class.java))
      .from(DSL.table("scanner_trades"))
      .where(DSL.field("id").eq(id))
      .fetchOne()
      ?.value1()

  private fun strikePriceOf(id: Long): Double? =
    dsl
      .select(DSL.field("strike_price", java.math.BigDecimal::class.java))
      .from(DSL.table("scanner_trades"))
      .where(DSL.field("id").eq(id))
      .fetchOne()
      ?.value1()
      ?.toDouble()
}
