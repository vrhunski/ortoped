package com.ortoped.api.repository

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

object AppSettings : Table("app_settings") {
    val key = varchar("key", 100)
    val value = text("value")

    override val primaryKey = PrimaryKey(key)
}

class SettingsRepository {

    fun get(key: String): String? = transaction {
        AppSettings.selectAll().where { AppSettings.key eq key }
            .firstOrNull()
            ?.get(AppSettings.value)
    }

    fun set(key: String, value: String) {
        transaction {
            val existing = AppSettings.selectAll().where { AppSettings.key eq key }.firstOrNull()
            if (existing != null) {
                AppSettings.update({ AppSettings.key eq key }) {
                    it[AppSettings.value] = value
                }
            } else {
                AppSettings.insert {
                    it[AppSettings.key] = key
                    it[AppSettings.value] = value
                }
            }
        }
    }

    fun getAll(): Map<String, String> = transaction {
        AppSettings.selectAll().associate {
            it[AppSettings.key] to it[AppSettings.value]
        }
    }

    fun isApprovalRequired(): Boolean {
        return get("require_approval")?.toBooleanStrictOrNull() ?: false
    }
}
