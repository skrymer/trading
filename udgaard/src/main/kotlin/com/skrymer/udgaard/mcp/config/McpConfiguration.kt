package com.skrymer.udgaard.mcp.config

import com.skrymer.udgaard.mcp.service.StockMcpTools
import org.springframework.ai.tool.ToolCallbackProvider
import org.springframework.ai.tool.method.MethodToolCallbackProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configuration for MCP tools registration using MethodToolCallbackProvider.
 * This automatically discovers and registers all @Tool annotated methods in StockMcpTools.
 */
@Configuration
class McpConfiguration {

    @Bean
    fun stockToolCallbackProvider(stockMcpTools: StockMcpTools): ToolCallbackProvider {
        return MethodToolCallbackProvider.builder()
            .toolObjects(stockMcpTools)
            .build()
    }
}