package com.skrymer.udgaard.service

import com.skrymer.udgaard.jooq.tables.references.USER_SETTINGS
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class UserSettingsJooqRepository(
  private val dsl: DSLContext,
) {
  fun findByKey(settingKey: String, userId: Long? = null): String? =
    dsl
      .select(USER_SETTINGS.SETTING_VALUE)
      .from(USER_SETTINGS)
      .where(USER_SETTINGS.SETTING_KEY.eq(settingKey))
      .and(
        if (userId != null) {
          USER_SETTINGS.USER_ID.eq(userId)
        } else {
          USER_SETTINGS.USER_ID.isNull
        },
      ).fetchOne(USER_SETTINGS.SETTING_VALUE)

  fun upsert(settingKey: String, settingValue: String, userId: Long? = null) {
    dsl
      .insertInto(USER_SETTINGS)
      .set(USER_SETTINGS.SETTING_KEY, settingKey)
      .set(USER_SETTINGS.SETTING_VALUE, settingValue)
      .set(USER_SETTINGS.USER_ID, userId)
      .set(USER_SETTINGS.UPDATED_AT, LocalDateTime.now())
      .onConflict(
        if (userId != null) {
          listOf(USER_SETTINGS.USER_ID, USER_SETTINGS.SETTING_KEY)
        } else {
          listOf(USER_SETTINGS.SETTING_KEY)
        },
      ).where(
        if (userId != null) {
          USER_SETTINGS.USER_ID.isNotNull
        } else {
          USER_SETTINGS.USER_ID.isNull
        },
      ).doUpdate()
      .set(USER_SETTINGS.SETTING_VALUE, DSL.`val`(settingValue))
      .set(USER_SETTINGS.UPDATED_AT, LocalDateTime.now())
      .execute()
  }
}
