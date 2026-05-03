package com.skrymer.udgaard.backtesting.dto

import com.skrymer.udgaard.backtesting.model.MonteCarloTechniqueType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MonteCarloRequestDtoTest {
  @Test
  fun `null thresholds construct fine`() {
    // Given a request with drawdownThresholds omitted (default null)
    // When constructing the DTO
    val dto = MonteCarloRequestDto(backtestId = "bt-1")

    // Then no exception, field is null
    assertNull(dto.drawdownThresholds)
  }

  @Test
  fun `valid threshold list constructs`() {
    // Given a valid list of percent thresholds
    // When constructing the DTO
    val dto = MonteCarloRequestDto(
      backtestId = "bt-1",
      drawdownThresholds = listOf(20.0, 25.0, 30.0),
    )

    // Then the list is preserved verbatim (sorting/dedupe happens later in the service)
    assertEquals(listOf(20.0, 25.0, 30.0), dto.drawdownThresholds)
  }

  @Test
  fun `empty threshold list rejected`() {
    // Given an empty list
    // When constructing the DTO
    // Then init validation throws
    assertThrows<IllegalArgumentException> {
      MonteCarloRequestDto(backtestId = "bt-1", drawdownThresholds = emptyList())
    }
  }

  @Test
  fun `threshold of zero rejected`() {
    // Given a list containing 0.0 (out of (0,100) range)
    // When constructing the DTO
    // Then init validation throws
    assertThrows<IllegalArgumentException> {
      MonteCarloRequestDto(backtestId = "bt-1", drawdownThresholds = listOf(0.0, 25.0))
    }
  }

  @Test
  fun `threshold of one hundred rejected`() {
    // Given a list containing 100.0 (out of (0,100) range)
    // When constructing the DTO
    // Then init validation throws
    assertThrows<IllegalArgumentException> {
      MonteCarloRequestDto(backtestId = "bt-1", drawdownThresholds = listOf(25.0, 100.0))
    }
  }

  @Test
  fun `negative threshold rejected`() {
    // Given a list containing a negative value
    // When constructing the DTO
    // Then init validation throws
    assertThrows<IllegalArgumentException> {
      MonteCarloRequestDto(backtestId = "bt-1", drawdownThresholds = listOf(-5.0, 25.0))
    }
  }

  @Test
  fun `non-default request fields preserved alongside thresholds`() {
    // Given a fully-populated request including drawdownThresholds
    // When constructing
    val dto = MonteCarloRequestDto(
      backtestId = "bt-1",
      technique = MonteCarloTechniqueType.BOOTSTRAP_RESAMPLING,
      iterations = 5000,
      seed = 42L,
      drawdownThresholds = listOf(20.0, 30.0),
    )

    // Then all fields are accessible (smoke test against accidental field ordering breaks)
    assertEquals("bt-1", dto.backtestId)
    assertEquals(5000, dto.iterations)
    assertEquals(42L, dto.seed)
    assertEquals(listOf(20.0, 30.0), dto.drawdownThresholds)
  }

  @Test
  fun `null blockSize constructs fine`() {
    // Given a request with blockSize omitted (default null = IID)
    // When constructing the DTO
    val dto = MonteCarloRequestDto(backtestId = "bt-1")

    // Then no exception, field is null
    assertNull(dto.blockSize)
  }

  @Test
  fun `valid blockSize values construct`() {
    // Given assorted valid block sizes within the [1, MAX_BLOCK_SIZE] range
    // When constructing the DTO with each
    // Then no exception
    listOf(1, 2, 5, 10, 50, 5_000, MonteCarloRequestDto.MAX_BLOCK_SIZE).forEach { size ->
      val dto = MonteCarloRequestDto(backtestId = "bt-1", blockSize = size)
      assertEquals(size, dto.blockSize, "blockSize=$size should be accepted")
    }
  }

  @Test
  fun `blockSize out of range rejected`() {
    // Given out-of-range blockSize values
    // When constructing the DTO
    // Then init validation throws — covers the lower bound (0, negative) and upper bound
    // (MAX_BLOCK_SIZE + 1, an obviously-wrong value like 1_000_000 typed instead of 1_000)
    assertThrows<IllegalArgumentException> {
      MonteCarloRequestDto(backtestId = "bt-1", blockSize = 0)
    }
    assertThrows<IllegalArgumentException> {
      MonteCarloRequestDto(backtestId = "bt-1", blockSize = -3)
    }
    assertThrows<IllegalArgumentException> {
      MonteCarloRequestDto(backtestId = "bt-1", blockSize = MonteCarloRequestDto.MAX_BLOCK_SIZE + 1)
    }
    assertThrows<IllegalArgumentException> {
      MonteCarloRequestDto(backtestId = "bt-1", blockSize = 1_000_000)
    }
  }

  @Test
  fun `blockSize accepted on any technique — silently ignored on non-bootstrap`() {
    // Given a TRADE_SHUFFLING request with blockSize set (the field is silently ignored
    // there per the design — mirrors how drawdownThresholds is silently ignored on
    // BOOTSTRAP_RESAMPLING). When constructing the DTO. Then no exception.
    val dto = MonteCarloRequestDto(
      backtestId = "bt-1",
      technique = MonteCarloTechniqueType.TRADE_SHUFFLING,
      blockSize = 10,
    )
    assertEquals(10, dto.blockSize)
    assertEquals(MonteCarloTechniqueType.TRADE_SHUFFLING, dto.technique)
  }
}
