package com.skrymer.udgaard.repository

import com.skrymer.udgaard.model.Portfolio
import org.springframework.data.jpa.repository.JpaRepository

interface PortfolioRepository : JpaRepository<Portfolio, Long> {
  fun findByUserId(userId: String): List<Portfolio>
}
