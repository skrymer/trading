package com.skrymer.udgaard.mapper

import com.skrymer.udgaard.domain.ExecutionDomain
import com.skrymer.udgaard.jooq.tables.pojos.Executions
import org.springframework.stereotype.Component

/**
 * Mapper between jOOQ Execution POJOs and domain models
 */
@Component
class ExecutionMapper {
  /**
   * Convert jOOQ Execution POJO to domain model
   */
  fun toDomain(execution: Executions): ExecutionDomain =
    ExecutionDomain(
      id = execution.id,
      positionId = execution.positionId ?: 0,
      brokerTradeId = execution.brokerTradeId,
      linkedBrokerTradeId = execution.linkedBrokerTradeId,
      quantity = execution.quantity ?: 0,
      price = execution.price?.toDouble() ?: 0.0,
      executionDate = execution.executionDate ?: java.time.LocalDate.now(),
      executionTime = execution.executionTime,
      commission = execution.commission?.toDouble(),
      notes = execution.notes,
      createdAt = execution.createdAt,
    )

  /**
   * Convert domain model to jOOQ Execution POJO
   */
  fun toPojo(execution: ExecutionDomain): Executions =
    Executions(
      positionId = execution.positionId,
      brokerTradeId = execution.brokerTradeId,
      linkedBrokerTradeId = execution.linkedBrokerTradeId,
      quantity = execution.quantity,
      price = execution.price.toBigDecimal(),
      executionDate = execution.executionDate,
      executionTime = execution.executionTime,
      commission = execution.commission?.toBigDecimal(),
      notes = execution.notes,
      createdAt = execution.createdAt,
    ).apply {
      id = execution.id
    }
}
