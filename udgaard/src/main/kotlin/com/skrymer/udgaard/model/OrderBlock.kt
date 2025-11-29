package com.skrymer.udgaard.model

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import java.time.LocalDate

/**
 * Represents an order block - a price zone where institutional traders placed large orders
 *
 * @param low The lowest price of the order block
 * @param high The highest price of the order block
 * @param startDate When the order block was formed
 * @param endDate When the order block was mitigated/invalidated (null if still active)
 * @param orderBlockType Type of order block (BULLISH or BEARISH)
 * @param source Source of the order block data (OVTLYR or CALCULATED)
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
data class OrderBlock(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long? = null,

  @JsonIgnore
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "stock_symbol", referencedColumnName = "symbol")
  val stock: Stock? = null,

  @Column(name = "low_price")
  val low: Double = 0.0,

  @Column(name = "high_price")
  val high: Double = 0.0,

  @Column(name = "start_date", nullable = false)
  val startDate: LocalDate = LocalDate.now(),

  @Column(name = "end_date")
  val endDate: LocalDate? = null,

  @Enumerated(EnumType.STRING)
  @Column(name = "type", length = 20)
  val orderBlockType: OrderBlockType = OrderBlockType.BEARISH,

  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  val source: OrderBlockSource = OrderBlockSource.OVTLYR,

  val volume: Long = 0L,

  @Column(name = "volume_strength")
  val volumeStrength: Double = 0.0,

  @Enumerated(EnumType.STRING)
  @Column(length = 10)
  val sensitivity: OrderBlockSensitivity? = null,

  @Column(name = "rate_of_change")
  val rateOfChange: Double = 0.0
)

enum class OrderBlockType {
  BEARISH, BULLISH
}

enum class OrderBlockSource {
  OVTLYR,      // Order block from Ovtlyr data provider
  CALCULATED   // Order block calculated using ROC algorithm
}

enum class OrderBlockSensitivity {
  HIGH,   // More order blocks detected (lower threshold, e.g., 28%)
  LOW     // Fewer, stronger order blocks (higher threshold, e.g., 50%)
}