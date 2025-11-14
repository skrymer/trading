package com.skrymer.udgaard.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

/**
 * Represents a user's trading portfolio
 */
@Document(collection = "portfolios")
data class Portfolio(
    @Id
    val id: String? = null,
    val userId: String? = null, // For future multi-user support
    val name: String,
    val initialBalance: Double,
    var currentBalance: Double,
    val currency: String, // e.g., "USD", "EUR", "GBP"
    val createdDate: LocalDateTime = LocalDateTime.now(),
    var lastUpdated: LocalDateTime = LocalDateTime.now()
)
