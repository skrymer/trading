package com.skrymer.udgaard.config

import com.skrymer.udgaard.jooq.tables.references.USERS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

data class AppUser(
  val id: Long? = null,
  val username: String,
  val passwordHash: String,
  val apiKeyHash: String?,
  val apiKeyExpiresAt: LocalDateTime?,
  val role: String = "USER",
  val enabled: Boolean = true
)

@Repository
class UserRepository(
  private val dsl: DSLContext
) {
  fun findByUsername(name: String): AppUser? =
    dsl
      .selectFrom(USERS)
      .where(USERS.USERNAME.eq(name))
      .fetchOne()
      ?.let { record ->
        AppUser(
          id = record.get(USERS.ID),
          username = record.get(USERS.USERNAME)!!,
          passwordHash = record.get(USERS.PASSWORD_HASH)!!,
          apiKeyHash = record.get(USERS.API_KEY_HASH),
          apiKeyExpiresAt = record.get(USERS.API_KEY_EXPIRES_AT),
          role = record.get(USERS.ROLE)!!,
          enabled = record.get(USERS.ENABLED)!!
        )
      }

  fun findByApiKeyHash(hash: String): AppUser? =
    dsl
      .selectFrom(USERS)
      .where(USERS.API_KEY_HASH.eq(hash))
      .fetchOne()
      ?.let { record ->
        AppUser(
          id = record.get(USERS.ID),
          username = record.get(USERS.USERNAME)!!,
          passwordHash = record.get(USERS.PASSWORD_HASH)!!,
          apiKeyHash = record.get(USERS.API_KEY_HASH),
          apiKeyExpiresAt = record.get(USERS.API_KEY_EXPIRES_AT),
          role = record.get(USERS.ROLE)!!,
          enabled = record.get(USERS.ENABLED)!!
        )
      }

  fun save(user: AppUser): AppUser {
    dsl
      .insertInto(USERS)
      .set(USERS.USERNAME, user.username)
      .set(USERS.PASSWORD_HASH, user.passwordHash)
      .set(USERS.API_KEY_HASH, user.apiKeyHash)
      .set(USERS.API_KEY_EXPIRES_AT, user.apiKeyExpiresAt)
      .set(USERS.ROLE, user.role)
      .set(USERS.ENABLED, user.enabled)
      .execute()
    return findByUsername(user.username)!!
  }

  fun existsByUsername(name: String): Boolean =
    dsl.fetchCount(dsl.selectFrom(USERS).where(USERS.USERNAME.eq(name))) > 0

  fun hasAnyUsers(): Boolean =
    dsl.fetchCount(dsl.selectFrom(USERS)) > 0
}
