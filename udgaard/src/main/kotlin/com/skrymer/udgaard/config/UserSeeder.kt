package com.skrymer.udgaard.config

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.LocalDateTime

@Component
@ConditionalOnProperty("app.security.enabled", havingValue = "true", matchIfMissing = true)
class UserSeeder(
  private val userRepository: UserRepository,
  private val passwordEncoder: PasswordEncoder,
  private val securityProperties: SecurityProperties
) : ApplicationRunner {
  private val logger = LoggerFactory.getLogger(UserSeeder::class.java)

  override fun run(args: ApplicationArguments) {
    if (userRepository.hasAnyUsers()) {
      logger.info("Users table not empty, skipping seed")
      return
    }

    val seed = securityProperties.seed
    val apiKeyHash = sha256(seed.apiKey)
    val expiresAt = seed.apiKeyExpiresInDays?.let {
      LocalDateTime.now().plusDays(it)
    }

    userRepository.save(
      AppUser(
        username = seed.username,
        passwordHash = passwordEncoder.encode(seed.password),
        apiKeyHash = apiKeyHash,
        apiKeyExpiresAt = expiresAt,
        role = "USER"
      )
    )

    logger.info("Seeded default admin user '{}' — API key: {}", seed.username, seed.apiKey)
  }

  companion object {
    fun sha256(input: String): String {
      val digest = MessageDigest.getInstance("SHA-256")
      return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
  }
}
