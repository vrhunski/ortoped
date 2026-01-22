package com.ortoped.api.model

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Projects table
 */
object Projects : UUIDTable("projects") {
    val name = varchar("name", 255)
    val repositoryUrl = varchar("repository_url", 500).nullable()
    val defaultBranch = varchar("default_branch", 100).default("main")
    val policyId = uuid("policy_id").references(Policies.id).nullable()
    val createdAt = timestamp("created_at")
}

/**
 * Scans table
 */
object Scans : UUIDTable("scans") {
    val projectId = uuid("project_id").references(Projects.id).nullable()
    val status = varchar("status", 50) // pending, scanning, complete, failed
    val enableAi = bool("enable_ai").default(true)
    val result = text("result").nullable() // Full ScanResult as JSON
    val summary = text("summary").nullable() // Quick access summary
    val startedAt = timestamp("started_at").nullable()
    val completedAt = timestamp("completed_at").nullable()
    val errorMessage = text("error_message").nullable()
    val createdAt = timestamp("created_at")
}

/**
 * Policies table
 */
object Policies : UUIDTable("policies") {
    val name = varchar("name", 255)
    val config = text("config") // PolicyConfig as JSON string
    val isDefault = bool("is_default").default(false)
    val createdAt = timestamp("created_at")
}

/**
 * Policy evaluations table
 */
object PolicyEvaluations : UUIDTable("policy_evaluations") {
    val scanId = uuid("scan_id").references(Scans.id)
    val policyId = uuid("policy_id").references(Policies.id)
    val passed = bool("passed")
    val report = text("report") // PolicyReport as JSON
    val errorCount = integer("error_count").default(0)
    val createdAt = timestamp("created_at")
}

/**
 * API Keys table
 */
object ApiKeys : UUIDTable("api_keys") {
    val name = varchar("name", 255)
    val keyHash = varchar("key_hash", 255)
    val keyPrefix = varchar("key_prefix", 10)
    val createdAt = timestamp("created_at")
}

/**
 * Scan status enum
 */
enum class ScanStatus(val value: String) {
    PENDING("pending"),
    SCANNING("scanning"),
    COMPLETE("complete"),
    FAILED("failed");

    companion object {
        fun fromString(value: String): ScanStatus {
            return entries.find { it.value == value } ?: PENDING
        }
    }
}
