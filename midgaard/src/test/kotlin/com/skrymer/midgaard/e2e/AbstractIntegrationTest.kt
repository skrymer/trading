package com.skrymer.midgaard.e2e

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * Base class for Midgaard integration tests that need a real database. Boots the full
 * Spring context against a shared PostgreSQL container; Flyway migrates the schema on
 * startup. The container starts once per JVM and is reused across all test classes.
 */
@SpringBootTest
@Testcontainers
abstract class AbstractIntegrationTest protected constructor() {
    companion object {
        @JvmStatic
        val postgres: PostgreSQLContainer =
            PostgreSQLContainer("postgres:17")
                .withDatabaseName("datastore")
                .withUsername("trading")
                .withPassword("trading")
                .apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.getJdbcUrl() }
            registry.add("spring.datasource.username") { postgres.getUsername() }
            registry.add("spring.datasource.password") { postgres.getPassword() }
        }
    }
}
