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
    val enableSpdx = bool("enable_spdx").default(false)
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

// ============================================================================
// CURATION TABLES (Phase 8)
// ============================================================================

/**
 * Curation sessions table - tracks overall curation progress for a scan
 */
object CurationSessions : UUIDTable("curation_sessions") {
    val scanId = uuid("scan_id").references(Scans.id)
    val status = varchar("status", 20) // IN_PROGRESS, COMPLETED, APPROVED

    // Statistics
    val totalItems = integer("total_items").default(0)
    val pendingItems = integer("pending_items").default(0)
    val acceptedItems = integer("accepted_items").default(0)
    val rejectedItems = integer("rejected_items").default(0)
    val modifiedItems = integer("modified_items").default(0)

    // Approval
    val approvedBy = varchar("approved_by", 100).nullable()
    val approvedAt = timestamp("approved_at").nullable()
    val approvalComment = text("approval_comment").nullable()

    // Timestamps
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}

/**
 * Curations table - individual curation decisions for each dependency
 */
object Curations : UUIDTable("curations") {
    val sessionId = uuid("session_id").references(CurationSessions.id)
    val scanId = uuid("scan_id").references(Scans.id)
    val dependencyId = varchar("dependency_id", 255)
    val dependencyName = varchar("dependency_name", 255)
    val dependencyVersion = varchar("dependency_version", 100)
    val dependencyScope = varchar("dependency_scope", 50).nullable()

    // Original state
    val originalLicense = varchar("original_license", 255).nullable()
    val declaredLicenses = text("declared_licenses").nullable() // JSON array
    val detectedLicenses = text("detected_licenses").nullable() // JSON array

    // AI suggestion
    val aiSuggestedLicense = varchar("ai_suggested_license", 255).nullable()
    val aiConfidence = varchar("ai_confidence", 20).nullable()
    val aiReasoning = text("ai_reasoning").nullable()
    val aiAlternatives = text("ai_alternatives").nullable() // JSON array

    // Curation decision
    val status = varchar("status", 20) // PENDING, ACCEPTED, REJECTED, MODIFIED
    val curatedLicense = varchar("curated_license", 255).nullable()
    val curatorComment = text("curator_comment").nullable()
    val curatorId = varchar("curator_id", 100).nullable()

    // Priority scoring (Phase 9)
    val priorityLevel = varchar("priority_level", 20).nullable()
    val priorityScore = decimal("priority_score", 5, 4).nullable()
    val priorityFactors = text("priority_factors").nullable() // JSON array

    // SPDX validation (Phase 9)
    val spdxValidated = bool("spdx_validated").default(false)
    val spdxLicenseData = text("spdx_license_data").nullable() // JSON object

    // Timestamps
    val createdAt = timestamp("created_at")
    val curatedAt = timestamp("curated_at").nullable()
}

/**
 * Curated scans table - for incremental curation support
 */
object CuratedScans : UUIDTable("curated_scans") {
    val scanId = uuid("scan_id").references(Scans.id)
    val previousScanId = uuid("previous_scan_id").references(Scans.id).nullable()
    val sessionId = uuid("session_id").references(CurationSessions.id).nullable()
    val curatorId = varchar("curator_id", 100).nullable()
    val dependencyHash = varchar("dependency_hash", 64).nullable()
    val dependencyCount = integer("dependency_count").default(0)
    val curatedAt = timestamp("curated_at")
}

/**
 * Curation templates table - reusable curation patterns
 */
object CurationTemplates : UUIDTable("curation_templates") {
    val name = varchar("name", 255)
    val description = text("description").nullable()
    val conditions = text("conditions") // JSON array
    val actions = text("actions") // JSON array
    val createdBy = varchar("created_by", 100).nullable()
    val isGlobal = bool("is_global").default(false)
    val isActive = bool("is_active").default(true)
    val usageCount = integer("usage_count").default(0)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}

// ============================================================================
// CURATION ENUMS
// ============================================================================

/**
 * Curation status enum
 */
enum class CurationStatus(val value: String) {
    PENDING("PENDING"),
    ACCEPTED("ACCEPTED"),
    REJECTED("REJECTED"),
    MODIFIED("MODIFIED");

    companion object {
        fun fromString(value: String): CurationStatus {
            return entries.find { it.value == value } ?: PENDING
        }
    }
}

/**
 * Curation session status enum
 */
enum class CurationSessionStatus(val value: String) {
    IN_PROGRESS("IN_PROGRESS"),
    COMPLETED("COMPLETED"),
    APPROVED("APPROVED");

    companion object {
        fun fromString(value: String): CurationSessionStatus {
            return entries.find { it.value == value } ?: IN_PROGRESS
        }
    }
}

/**
 * Priority level enum for smart filtering
 */
enum class PriorityLevel(val value: String) {
    CRITICAL("CRITICAL"),
    HIGH("HIGH"),
    MEDIUM("MEDIUM"),
    LOW("LOW");

    companion object {
        fun fromString(value: String): PriorityLevel? {
            return entries.find { it.value == value }
        }
    }
}
