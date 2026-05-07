// Coercion helpers used by every condition's `parseConfig` override. Each reads a value
// from the wire-format `Map<String, Any>` and falls back to the singleton's own default
// when the key is missing or the type doesn't match. No range validation, no string-to-
// number coercion — those are each condition's responsibility (e.g. via `init { require }`).
package com.skrymer.udgaard.backtesting.service

internal fun Map<String, Any>.numberOr(
  key: String,
  default: Double,
): Double = (this[key] as? Number)?.toDouble() ?: default

internal fun Map<String, Any>.intOr(
  key: String,
  default: Int,
): Int = (this[key] as? Number)?.toInt() ?: default

internal fun Map<String, Any>.stringOr(
  key: String,
  default: String,
): String = (this[key] as? String) ?: default
