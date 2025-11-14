package com.skrymer.udgaard.repository

import com.skrymer.udgaard.model.Portfolio
import org.springframework.data.mongodb.repository.MongoRepository

interface PortfolioRepository : MongoRepository<Portfolio, String> {
    fun findByUserId(userId: String): List<Portfolio>
}
