package com.skrymer.udgaard.portfolio.mapper

import com.skrymer.udgaard.jooq.tables.pojos.Executions
import com.skrymer.udgaard.portfolio.model.Execution
import org.springframework.stereotype.Component

/**
 * Mapper between jOOQ Execution POJOs and domain models
 */
@Component
class ExecutionMapper {
  /**
   * Convert jOOQ Execution POJO to domain model
   */
  fun toDomain(execution: Executions): Execution =
    Execution(
      id = execution.id,
      positionId = execution.positionId,
      brokerTradeId = execution.brokerTradeId,
      linkedBrokerTradeId = execution.linkedBrokerTradeId,
      quantity = execution.quantity,
      price = execution.price.toDouble(),
      executionDate = execution.executionDate,
      executionTime = execution.executionTime,
      commission = execution.commission?.toDouble(),
      fxRateToBase = execution.fxRateToBase?.toDouble(),
      notes = execution.notes,
      createdAt = execution.createdAt,
    )

  /**
   * Convert domain model to jOOQ Execution POJO
   */
  fun toPojo(execution: Execution): Executions =
    Executions(
      positionId = execution.positionId,
      brokerTradeId = execution.brokerTradeId,
      linkedBrokerTradeId = execution.linkedBrokerTradeId,
      quantity = execution.quantity,
      price = execution.price.toBigDecimal(),
      executionDate = execution.executionDate,
      executionTime = execution.executionTime,
      commission = execution.commission?.toBigDecimal(),
      fxRateToBase = execution.fxRateToBase?.toBigDecimal(),
      notes = execution.notes,
      createdAt = execution.createdAt,
    ).apply {
      id = execution.id
    }
}
