package com.skrymer.udgaard.backtesting.model

/**
 * The five canonical market-regime labels (CONTEXT.md "Market regimes"). A trading day with no
 * defensible read carries no label at all (fail-closed null), never a default member.
 */
enum class RegimeLabel {
  THRUST,
  GRIND,
  NARROW,
  CHOP,
  CRISIS,
}
