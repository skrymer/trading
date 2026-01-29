package com.skrymer.udgaard.repository.jooq

import com.skrymer.udgaard.domain.ExecutionDomain
import com.skrymer.udgaard.jooq.tables.pojos.Executions
import com.skrymer.udgaard.jooq.tables.references.EXECUTIONS
import com.skrymer.udgaard.mapper.ExecutionMapper
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

/**
 * jOOQ-based repository for Execution operations
 */
@Repository
class ExecutionJooqRepository(
  private val dsl: DSLContext,
  private val mapper: ExecutionMapper,
) {
  /**
   * Find execution by ID
   */
  fun findById(id: Long): ExecutionDomain? {
    val execution =
      dsl
        .selectFrom(EXECUTIONS)
        .where(EXECUTIONS.ID.eq(id))
        .fetchOneInto(Executions::class.java) ?: return null

    return mapper.toDomain(execution)
  }

  /**
   * Find all executions for a position
   */
  fun findByPositionId(positionId: Long): List<ExecutionDomain> {
    val executions =
      dsl
        .selectFrom(EXECUTIONS)
        .where(EXECUTIONS.POSITION_ID.eq(positionId))
        .orderBy(EXECUTIONS.EXECUTION_DATE.asc(), EXECUTIONS.EXECUTION_TIME.asc())
        .fetchInto(Executions::class.java)

    return executions.map { mapper.toDomain(it) }
  }

  /**
   * Find execution by broker trade ID
   * Used to check if an execution has already been imported
   */
  fun findByBrokerTradeId(brokerTradeId: String): ExecutionDomain? {
    val execution =
      dsl
        .selectFrom(EXECUTIONS)
        .where(EXECUTIONS.BROKER_TRADE_ID.eq(brokerTradeId))
        .fetchOneInto(Executions::class.java) ?: return null

    return mapper.toDomain(execution)
  }

  /**
   * Save execution (insert only - executions are immutable)
   */
  fun save(execution: ExecutionDomain): ExecutionDomain {
    if (execution.id != null) {
      throw IllegalArgumentException("Executions are immutable - cannot update existing execution")
    }

    val pojo = mapper.toPojo(execution)

    val record =
      dsl
        .insertInto(EXECUTIONS)
        .set(EXECUTIONS.POSITION_ID, pojo.positionId)
        .set(EXECUTIONS.BROKER_TRADE_ID, pojo.brokerTradeId)
        .set(EXECUTIONS.LINKED_BROKER_TRADE_ID, pojo.linkedBrokerTradeId)
        .set(EXECUTIONS.QUANTITY, pojo.quantity)
        .set(EXECUTIONS.PRICE, pojo.price)
        .set(EXECUTIONS.EXECUTION_DATE, pojo.executionDate)
        .set(EXECUTIONS.EXECUTION_TIME, pojo.executionTime)
        .set(EXECUTIONS.COMMISSION, pojo.commission)
        .set(EXECUTIONS.NOTES, pojo.notes)
        .returningResult(EXECUTIONS.ID)
        .fetchOne()

    val newId = record?.getValue(EXECUTIONS.ID) ?: throw IllegalStateException("Failed to insert execution")
    return execution.copy(id = newId)
  }

  /**
   * Delete execution by ID
   * Only used for cascading deletes when position is deleted
   */
  fun delete(id: Long) {
    dsl
      .deleteFrom(EXECUTIONS)
      .where(EXECUTIONS.ID.eq(id))
      .execute()
  }
}
