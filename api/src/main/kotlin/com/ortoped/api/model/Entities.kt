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

    // EU Compliance fields (V6 migration)
    val curationRequired = bool("curation_required").default(true)
    val curationComplete = bool("curation_complete").default(false)
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

    // Curator info
    val curatorId = varchar("curator_id", 100).default("system")
    val curatorName = varchar("curator_name", 255).nullable()

    // Approval
    val approvedBy = varchar("approved_by", 100).nullable()
    val approverName = varchar("approver_name", 255).nullable()
    val approverRole = varchar("approver_role", 50).nullable()
    val approvedAt = timestamp("approved_at").nullable()
    val approvalComment = text("approval_comment").nullable()

    // EU Compliance: Two-role approval workflow (V6 migration)
    val submittedForApproval = bool("submitted_for_approval").default(false)
    val submittedAt = timestamp("submitted_at").nullable()
    val submittedBy = varchar("submitted_by", 100).nullable()

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

    // EU Compliance fields (V6 migration)
    val requiresJustification = bool("requires_justification").default(true)
    val justificationComplete = bool("justification_complete").default(false)
    val blockingPolicyRule = varchar("blocking_policy_rule", 100).nullable()
    val isOrLicense = bool("is_or_license").default(false)
    val orLicenseChoice = varchar("or_license_choice", 255).nullable()
    val distributionScope = varchar("distribution_scope", 50).default("BINARY")

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

// ============================================================================
// ORT CACHE TABLES (Phase 5 - PostgreSQL Storage)
// ============================================================================

/**
 * ORT package cache table - caches analyzer results per-package
 */
object OrtPackageCache : UUIDTable("ort_package_cache") {
    val packageId = varchar("package_id", 500)
    val packageType = varchar("package_type", 50)
    val ortVersion = varchar("ort_version", 50)
    val analyzerVersion = varchar("analyzer_version", 50).nullable()
    val declaredLicenses = text("declared_licenses").nullable() // JSONB
    val concludedLicense = varchar("concluded_license", 255).nullable()
    val homepageUrl = varchar("homepage_url", 1000).nullable()
    val vcsUrl = varchar("vcs_url", 1000).nullable()
    val description = text("description").nullable()
    val sourceArtifactHash = varchar("source_artifact_hash", 128).nullable()
    val vcsRevision = varchar("vcs_revision", 128).nullable()
    val createdAt = timestamp("created_at")
    val lastAccessedAt = timestamp("last_accessed_at")
    val accessCount = integer("access_count").default(1)
}

/**
 * ORT scan result cache table - caches full scan results per-project
 */
object OrtScanResultCache : UUIDTable("ort_scan_result_cache") {
    val projectUrl = varchar("project_url", 1000).nullable()
    val projectRevision = varchar("project_revision", 128).nullable()
    val projectPath = varchar("project_path", 500).nullable()
    val ortVersion = varchar("ort_version", 50)
    val configHash = varchar("config_hash", 64)
    val ortResultJson = binary("ort_result_json").nullable() // GZIP compressed
    val resultSizeBytes = integer("result_size_bytes").nullable()
    val packageCount = integer("package_count").nullable()
    val issueCount = integer("issue_count").nullable()
    val createdAt = timestamp("created_at")
    val expiresAt = timestamp("expires_at").nullable()
}

/**
 * License resolution cache table - caches AI and SPDX resolution results
 */
object LicenseResolutionCache : UUIDTable("license_resolution_cache") {
    val packageId = varchar("package_id", 500)
    val declaredLicenseRaw = text("declared_license_raw").nullable()
    val resolvedSpdxId = varchar("resolved_spdx_id", 255).nullable()
    val resolutionSource = varchar("resolution_source", 50).nullable() // AI, SPDX_MATCH, SCANNER, MANUAL
    val confidence = varchar("confidence", 20).nullable() // HIGH, MEDIUM, LOW
    val reasoning = text("reasoning").nullable()
    val createdAt = timestamp("created_at")
}

/**
 * Resolution source enum
 */
enum class ResolutionSource(val value: String) {
    AI("AI"),
    SPDX_MATCH("SPDX_MATCH"),
    SCANNER("SCANNER"),
    MANUAL("MANUAL");

    companion object {
        fun fromString(value: String): ResolutionSource? {
            return entries.find { it.value == value }
        }
    }
}

// ============================================================================
// EU COMPLIANCE TABLES (V6 migration)
// ============================================================================

/**
 * Audit logs table - immutable log of all curation actions (EU requirement)
 */
object AuditLogs : UUIDTable("audit_logs") {
    val entityType = varchar("entity_type", 50) // CURATION, SESSION, APPROVAL, JUSTIFICATION
    val entityId = uuid("entity_id")
    val action = varchar("action", 50) // CREATE, DECIDE, APPROVE, REJECT, MODIFY, SUBMIT
    val actorId = varchar("actor_id", 100)
    val actorRole = varchar("actor_role", 50) // CURATOR, APPROVER, SYSTEM
    val previousState = text("previous_state").nullable() // JSONB
    val newState = text("new_state") // JSONB
    val changeSummary = text("change_summary")
    val ipAddress = varchar("ip_address", 45).nullable()
    val userAgent = text("user_agent").nullable()
    val createdAt = timestamp("created_at")
}

/**
 * Curation justifications table - structured justification for curation decisions
 */
object CurationJustifications : UUIDTable("curation_justifications") {
    val curationId = uuid("curation_id").references(Curations.id)

    // License identification
    val spdxId = varchar("spdx_id", 100)
    val licenseCategory = varchar("license_category", 50) // PERMISSIVE, WEAK_COPYLEFT, etc.
    val concludedLicense = varchar("concluded_license", 255)

    // Justification
    val justificationType = varchar("justification_type", 50) // AI_ACCEPTED, MANUAL_OVERRIDE, etc.
    val justificationText = text("justification_text")

    // Policy context
    val policyRuleId = varchar("policy_rule_id", 100).nullable()
    val policyRuleName = varchar("policy_rule_name", 255).nullable()

    // Evidence
    val evidenceType = varchar("evidence_type", 50).nullable()
    val evidenceReference = text("evidence_reference").nullable()
    val evidenceCollectedAt = timestamp("evidence_collected_at").nullable()

    // Distribution scope
    val distributionScope = varchar("distribution_scope", 50).default("BINARY")

    // Curator info
    val curatorId = varchar("curator_id", 100)
    val curatorName = varchar("curator_name", 255).nullable()
    val curatorEmail = varchar("curator_email", 255).nullable()
    val curatedAt = timestamp("curated_at")

    // Integrity
    val justificationHash = varchar("justification_hash", 64).nullable()
}

/**
 * Curation approvals table - two-role approval workflow
 */
object CurationApprovals : UUIDTable("curation_approvals") {
    val sessionId = uuid("session_id").references(CurationSessions.id)

    // Submitter (curator)
    val submitterId = varchar("submitter_id", 100)
    val submitterName = varchar("submitter_name", 255).nullable()
    val submittedAt = timestamp("submitted_at")

    // Approver (must be different)
    val approverId = varchar("approver_id", 100).nullable()
    val approverName = varchar("approver_name", 255).nullable()
    val approverRole = varchar("approver_role", 50).nullable()

    // Decision
    val decision = varchar("decision", 20).nullable() // APPROVED, REJECTED, RETURNED
    val decisionComment = text("decision_comment").nullable()
    val decidedAt = timestamp("decided_at").nullable()

    // If returned
    val returnReason = text("return_reason").nullable()
    val revisionItems = text("revision_items").nullable() // JSON array

    val createdAt = timestamp("created_at")
}

/**
 * OR license resolutions table - track OR license choices
 */
object OrLicenseResolutions : UUIDTable("or_license_resolutions") {
    val curationId = uuid("curation_id").references(Curations.id)
    val originalExpression = varchar("original_expression", 500)
    val licenseOptions = text("license_options") // JSON array
    val chosenLicense = varchar("chosen_license", 255).nullable()
    val choiceReason = text("choice_reason").nullable()
    val resolvedBy = varchar("resolved_by", 100).nullable()
    val resolvedAt = timestamp("resolved_at").nullable()
    val isResolved = bool("is_resolved").default(false)
}

// ============================================================================
// EU COMPLIANCE ENUMS
// ============================================================================

/**
 * Distribution scope enum
 */
enum class DistributionScopeEnum(val value: String) {
    INTERNAL("INTERNAL"),
    BINARY("BINARY"),
    SOURCE("SOURCE"),
    SAAS("SAAS"),
    EMBEDDED("EMBEDDED");

    companion object {
        fun fromString(value: String): DistributionScopeEnum {
            return entries.find { it.value == value } ?: BINARY
        }
    }
}

/**
 * Justification type enum
 */
enum class JustificationTypeEnum(val value: String) {
    AI_ACCEPTED("AI_ACCEPTED"),
    MANUAL_OVERRIDE("MANUAL_OVERRIDE"),
    EVIDENCE_BASED("EVIDENCE_BASED"),
    POLICY_EXEMPTION("POLICY_EXEMPTION"),
    LEGAL_OPINION("LEGAL_OPINION");

    companion object {
        fun fromString(value: String): JustificationTypeEnum? {
            return entries.find { it.value == value }
        }
    }
}

/**
 * Evidence type enum
 */
enum class EvidenceTypeEnum(val value: String) {
    LICENSE_FILE("LICENSE_FILE"),
    REPO_INSPECTION("REPO_INSPECTION"),
    VENDOR_CONFIRMATION("VENDOR_CONFIRMATION"),
    LEGAL_OPINION("LEGAL_OPINION"),
    PRIOR_AUDIT("PRIOR_AUDIT"),
    PACKAGE_METADATA("PACKAGE_METADATA");

    companion object {
        fun fromString(value: String): EvidenceTypeEnum? {
            return entries.find { it.value == value }
        }
    }
}

/**
 * Approval decision enum
 */
enum class ApprovalDecisionEnum(val value: String) {
    APPROVED("APPROVED"),
    REJECTED("REJECTED"),
    RETURNED("RETURNED");

    companion object {
        fun fromString(value: String): ApprovalDecisionEnum? {
            return entries.find { it.value == value }
        }
    }
}

/**
 * Actor role enum for audit logs
 */
enum class ActorRoleEnum(val value: String) {
    CURATOR("CURATOR"),
    APPROVER("APPROVER"),
    SYSTEM("SYSTEM");

    companion object {
        fun fromString(value: String): ActorRoleEnum {
            return entries.find { it.value == value } ?: SYSTEM
        }
    }
}

/**
 * License category enum
 */
enum class LicenseCategoryEnum(val value: String, val requiresJustification: Boolean) {
    PERMISSIVE("PERMISSIVE", false),
    WEAK_COPYLEFT("WEAK_COPYLEFT", true),
    STRONG_COPYLEFT("STRONG_COPYLEFT", true),
    NETWORK_COPYLEFT("NETWORK_COPYLEFT", true),
    PROPRIETARY("PROPRIETARY", true),
    PUBLIC_DOMAIN("PUBLIC_DOMAIN", false),
    UNKNOWN("UNKNOWN", true);

    companion object {
        fun fromString(value: String): LicenseCategoryEnum {
            return entries.find { it.value == value } ?: UNKNOWN
        }
    }
}

/**
 * Audit action enum
 */
enum class AuditActionEnum(val value: String) {
    CREATE("CREATE"),
    DECIDE("DECIDE"),
    JUSTIFY("JUSTIFY"),
    SUBMIT("SUBMIT"),
    APPROVE("APPROVE"),
    REJECT("REJECT"),
    RETURN("RETURN"),
    MODIFY("MODIFY"),
    RESOLVE_OR("RESOLVE_OR");

    companion object {
        fun fromString(value: String): AuditActionEnum? {
            return entries.find { it.value == value }
        }
    }
}

/**
 * Audit entity type enum
 */
enum class AuditEntityTypeEnum(val value: String) {
    CURATION("CURATION"),
    SESSION("SESSION"),
    APPROVAL("APPROVAL"),
    JUSTIFICATION("JUSTIFICATION"),
    OR_LICENSE("OR_LICENSE");

    companion object {
        fun fromString(value: String): AuditEntityTypeEnum? {
            return entries.find { it.value == value }
        }
    }
}
