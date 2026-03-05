package com.skrymer.midgaard.repository

import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class ProviderConfigRepository(
    private val dsl: DSLContext,
) {
    private val table = DSL.table("provider_config")
    private val configKey = DSL.field("config_key", String::class.java)
    private val configValue = DSL.field("config_value", String::class.java)
    private val updatedAt = DSL.field("updated_at", LocalDateTime::class.java)

    fun findByKey(key: String): String? =
        dsl
            .select(configValue)
            .from(table)
            .where(configKey.eq(key))
            .fetchOne(configValue)

    fun upsert(
        key: String,
        value: String,
    ) {
        dsl
            .insertInto(table)
            .set(configKey, key)
            .set(configValue, value)
            .set(updatedAt, LocalDateTime.now())
            .onConflict(configKey)
            .doUpdate()
            .set(configValue, DSL.`val`(value))
            .set(updatedAt, LocalDateTime.now())
            .execute()
    }
}
