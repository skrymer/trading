package com.skrymer.udgaard.model

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import java.time.LocalDate

/**
 * Represents an order block - a price zone where institutional traders placed large orders.
 * Order blocks are calculated using rate of change (ROC) analysis on volume data.
 *
 * @param low The lowest price of the order block
 * @param high The highest price of the order block
 * @param startDate When the order block was formed
 * @param endDate When the order block was mitigated/invalidated (null if still active)
 * @param orderBlockType Type of order block (BULLISH or BEARISH)
 * @param volume Trading volume when the order block was formed
 * @param volumeStrength Relative volume strength (volume / average volume)
 * @param sensitivity Sensitivity level used for detection (HIGH or LOW)
 * @param rateOfChange The rate of change percentage that triggered this order block
 */
@Entity
@Table(
  name = "order_blocks",
  indexes = [
    Index(name = "idx_order_block_stock", columnList = "stock_symbol"),
    Index(name = "idx_order_block_type", columnList = "type"),
    Index(name = "idx_order_block_start_date", columnList = "start_date")
  ]
)
class OrderBlock(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  var id: Long? = null,

  @JsonIgnore
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "stock_symbol", referencedColumnName = "symbol")
  var stock: Stock? = null,

  @Column(name = "low_price")
  var low: Double = 0.0,

  @Column(name = "high_price")
  var high: Double = 0.0,

  @Column(name = "start_date", nullable = false)
  var startDate: LocalDate = LocalDate.now(),

  @Column(name = "end_date")
  var endDate: LocalDate? = null,

  @Enumerated(EnumType.STRING)
  @Column(name = "type", length = 20)
  var orderBlockType: OrderBlockType = OrderBlockType.BEARISH,

  var volume: Long = 0L,

  @Column(name = "volume_strength")
  var volumeStrength: Double = 0.0,

  @Enumerated(EnumType.STRING)
  @Column(length = 10)
  var sensitivity: OrderBlockSensitivity? = null,

  @Column(name = "rate_of_change")
  var rateOfChange: Double = 0.0
) {
  // No-arg constructor for JPA
  constructor() : this(
    id = null,
    stock = null,
    low = 0.0,
    high = 0.0,
    startDate = LocalDate.now(),
    endDate = null,
    orderBlockType = OrderBlockType.BEARISH,
    volume = 0L,
    volumeStrength = 0.0,
    sensitivity = null,
    rateOfChange = 0.0
  )
}

enum class OrderBlockType {
  BEARISH, BULLISH
}

enum class OrderBlockSensitivity {
  HIGH,   // More order blocks detected (lower threshold, e.g., 28%)
  LOW     // Fewer, stronger order blocks (higher threshold, e.g., 50%)
}