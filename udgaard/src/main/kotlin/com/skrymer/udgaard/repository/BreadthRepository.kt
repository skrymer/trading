package com.skrymer.udgaard.repository

import com.skrymer.udgaard.model.Breadth
import org.springframework.data.mongodb.repository.MongoRepository

/**
 * MongoDB repository for breadth data (market and sector breadth).
 * Uses string IDs generated from BreadthSymbol identifiers.
 */
interface BreadthRepository : MongoRepository<Breadth, String>
