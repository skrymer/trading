package com.skrymer.udgaard.config

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.util.LinkedMultiValueMap
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDateTime
import kotlin.test.assertEquals

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@TestPropertySource(
  properties = [
    "app.security.enabled=true",
    "app.security.seed.username=testadmin",
    "app.security.seed.password=testpassword",
    "app.security.seed.api-key=test-api-key-12345"
  ]
)
class SecurityConfigTest {
  @Autowired
  private lateinit var restTemplate: TestRestTemplate

  @Autowired
  private lateinit var userRepository: UserRepository

  @Test
  fun `unauthenticated request returns 401`() {
    val response = restTemplate.getForEntity("/api/stocks", String::class.java)
    assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
  }

  @Test
  fun `valid API key returns 200`() {
    val headers = HttpHeaders().apply {
      set("X-API-Key", "test-api-key-12345")
    }
    val entity = HttpEntity<String>(headers)
    val response = restTemplate.exchange("/api/auth/check", HttpMethod.GET, entity, String::class.java)
    assertEquals(HttpStatus.OK, response.statusCode)
  }

  @Test
  fun `invalid API key returns 401`() {
    val headers = HttpHeaders().apply {
      set("X-API-Key", "wrong-key")
    }
    val entity = HttpEntity<String>(headers)
    val response = restTemplate.exchange("/api/stocks", HttpMethod.GET, entity, String::class.java)
    assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
  }

  @Test
  fun `expired API key returns 401`() {
    val expiredHash = UserSeeder.sha256("expired-key-12345")
    userRepository.save(
      AppUser(
        username = "expired-user",
        passwordHash = "not-used",
        apiKeyHash = expiredHash,
        apiKeyExpiresAt = LocalDateTime.now().minusDays(1),
        role = "USER"
      )
    )

    val headers = HttpHeaders().apply {
      set("X-API-Key", "expired-key-12345")
    }
    val entity = HttpEntity<String>(headers)
    val response = restTemplate.exchange("/api/stocks", HttpMethod.GET, entity, String::class.java)
    assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
  }

  @Test
  fun `actuator health is public`() {
    val response = restTemplate.getForEntity("/actuator/health", String::class.java)
    assertEquals(HttpStatus.OK, response.statusCode)
  }

  @Test
  fun `form login with correct credentials returns 200`() {
    val headers = HttpHeaders().apply {
      contentType = MediaType.APPLICATION_FORM_URLENCODED
    }
    val body = LinkedMultiValueMap<String, String>().apply {
      add("username", "testadmin")
      add("password", "testpassword")
    }
    val entity = HttpEntity(body, headers)
    val response = restTemplate.postForEntity("/api/auth/login", entity, String::class.java)
    assertEquals(HttpStatus.OK, response.statusCode)
  }

  @Test
  fun `form login with wrong credentials returns 401`() {
    val headers = HttpHeaders().apply {
      contentType = MediaType.APPLICATION_FORM_URLENCODED
    }
    val body = LinkedMultiValueMap<String, String>().apply {
      add("username", "testadmin")
      add("password", "wrongpassword")
    }
    val entity = HttpEntity(body, headers)
    val response = restTemplate.postForEntity("/api/auth/login", entity, String::class.java)
    assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
  }

  companion object {
    @JvmStatic
    val postgres: PostgreSQLContainer<*> =
      PostgreSQLContainer("postgres:17")
        .withDatabaseName("trading")
        .withUsername("trading")
        .withPassword("trading")
        .apply { start() }

    @JvmStatic
    @DynamicPropertySource
    fun configureProperties(registry: DynamicPropertyRegistry) {
      registry.add("spring.datasource.url") { postgres.jdbcUrl }
      registry.add("spring.datasource.username") { postgres.username }
      registry.add("spring.datasource.password") { postgres.password }
    }
  }
}
