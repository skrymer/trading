package com.skrymer.udgaard.backtesting.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConditionConfigParsingTest {
  @Test
  fun `numberOr returns default when key is missing`() {
    // Given
    val params = emptyMap<String, Any>()

    // When
    val result = params.numberOr("k", 1.5)

    // Then
    assertEquals(1.5, result)
  }

  @Test
  fun `numberOr returns default when value is not a Number`() {
    // Given
    val params = mapOf<String, Any>("k" to "not-a-number")

    // When
    val result = params.numberOr("k", 1.5)

    // Then
    assertEquals(1.5, result)
  }

  @Test
  fun `numberOr widens Int to Double`() {
    // Given: Jackson commonly hands us Int for whole-number JSON
    val params = mapOf<String, Any>("k" to 7)

    // When
    val result = params.numberOr("k", 1.5)

    // Then
    assertEquals(7.0, result)
  }

  @Test
  fun `numberOr widens Long to Double`() {
    // Given
    val params = mapOf<String, Any>("k" to 100L)

    // When
    val result = params.numberOr("k", 0.0)

    // Then
    assertEquals(100.0, result)
  }

  @Test
  fun `intOr returns default when key is missing`() {
    // Given
    val params = emptyMap<String, Any>()

    // When
    val result = params.intOr("k", 42)

    // Then
    assertEquals(42, result)
  }

  @Test
  fun `intOr returns default when value is not a Number`() {
    // Given
    val params = mapOf<String, Any>("k" to "not-a-number")

    // When
    val result = params.intOr("k", 42)

    // Then
    assertEquals(42, result)
  }

  @Test
  fun `intOr narrows Long to Int`() {
    // Given
    val params = mapOf<String, Any>("k" to 100L)

    // When
    val result = params.intOr("k", 0)

    // Then
    assertEquals(100, result)
  }

  @Test
  fun `intOr truncates Double to Int`() {
    // Given: legacy parity — same truncation behaviour as the pre-refactor when-table
    val params = mapOf<String, Any>("k" to 3.7)

    // When
    val result = params.intOr("k", 0)

    // Then
    assertEquals(3, result)
  }

  @Test
  fun `stringOr returns default when key is missing`() {
    // Given
    val params = emptyMap<String, Any>()

    // When
    val result = params.stringOr("k", "default")

    // Then
    assertEquals("default", result)
  }

  @Test
  fun `stringOr returns default when value is not a String`() {
    // Given
    val params = mapOf<String, Any>("k" to 42)

    // When
    val result = params.stringOr("k", "default")

    // Then
    assertEquals("default", result)
  }

  @Test
  fun `stringOr returns the supplied value when present and a String`() {
    // Given
    val params = mapOf<String, Any>("k" to "hello")

    // When
    val result = params.stringOr("k", "default")

    // Then
    assertEquals("hello", result)
  }
}
