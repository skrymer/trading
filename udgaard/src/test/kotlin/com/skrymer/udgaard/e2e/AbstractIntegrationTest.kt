package com.skrymer.udgaard.e2e

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
abstract class AbstractIntegrationTest {
  @Autowired
  protected lateinit var restTemplate: TestRestTemplate

  companion object {
    @JvmStatic
    val postgres: PostgreSQLContainer =
      PostgreSQLContainer("postgres:17")
        .withDatabaseName("trading")
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
