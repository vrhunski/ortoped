package com.ortoped.core.graph

import com.ortoped.core.graph.model.*
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * In-memory license knowledge graph with fast traversal and reasoning.
 *
 * This is the core engine that stores license nodes, their relationships,
 * and provides query capabilities for compatibility checking, obligation
 * aggregation, and dependency tree analysis.
 */
class LicenseKnowledgeGraph {

    // =========================================================================
    // Node Storage
    // =========================================================================

    private val licenses = ConcurrentHashMap<String, LicenseNode>()
    private val obligations = ConcurrentHashMap<String, ObligationNode>()
    private val rights = ConcurrentHashMap<String, RightNode>()
    private val conditions = ConcurrentHashMap<String, ConditionNode>()
    private val limitations = ConcurrentHashMap<String, LimitationNode>()
    private val useCases = ConcurrentHashMap<String, UseCaseNode>()

    // =========================================================================
    // Edge Storage
    // =========================================================================

    private val outgoingEdges = ConcurrentHashMap<String, MutableList<GraphEdge>>()
    private val incomingEdges = ConcurrentHashMap<String, MutableList<GraphEdge>>()

    // =========================================================================
    // Indexes for Fast Lookup
    // =========================================================================

    // Bidirectional compatibility index: (license1, license2) -> edge
    private val compatibilityIndex = ConcurrentHashMap<Pair<String, String>, CompatibilityEdge>()

    // License family index: family -> set of license IDs
    private val familyIndex = ConcurrentHashMap<String, MutableSet<String>>()

    // Category index: category -> set of license IDs
    private val categoryIndex = ConcurrentHashMap<LicenseCategory, MutableSet<String>>()

    // Track last update time
    private var lastUpdated: Instant = Instant.now()

    // =========================================================================
    // Node Management - Licenses
    // =========================================================================

    /**
     * Add a license node to the graph
     */
    fun addLicense(license: LicenseNode) {
        val normalizedId = license.id.uppercase()
        licenses[normalizedId] = license.copy(id = normalizedId)

        // Update family index
        license.family?.let { family ->
            familyIndex.getOrPut(family) { ConcurrentHashMap.newKeySet() }.add(normalizedId)
        }

        // Update category index
        categoryIndex.getOrPut(license.category) { ConcurrentHashMap.newKeySet() }.add(normalizedId)

        lastUpdated = Instant.now()
        logger.debug { "Added license: $normalizedId (${license.name})" }
    }

    /**
     * Get a license by SPDX ID
     */
    fun getLicense(spdxId: String): LicenseNode? = licenses[spdxId.uppercase()]

    /**
     * Get all licenses
     */
    fun getAllLicenses(): List<LicenseNode> = licenses.values.toList()

    /**
     * Get licenses by category
     */
    fun getLicensesByCategory(category: LicenseCategory): List<LicenseNode> {
        return categoryIndex[category]?.mapNotNull { licenses[it] } ?: emptyList()
    }

    /**
     * Get licenses by family
     */
    fun getLicensesByFamily(family: String): List<LicenseNode> {
        return familyIndex[family]?.mapNotNull { licenses[it] } ?: emptyList()
    }

    /**
     * Search licenses by name or ID
     */
    fun searchLicenses(query: String, limit: Int = 20): List<LicenseNode> {
        val normalizedQuery = query.lowercase()
        return licenses.values
            .filter { license ->
                license.id.lowercase().contains(normalizedQuery) ||
                license.spdxId.lowercase().contains(normalizedQuery) ||
                license.name.lowercase().contains(normalizedQuery)
            }
            .take(limit)
    }

    // =========================================================================
    // Node Management - Obligations
    // =========================================================================

    fun addObligation(obligation: ObligationNode) {
        obligations[obligation.id] = obligation
        lastUpdated = Instant.now()
        logger.debug { "Added obligation: ${obligation.id} (${obligation.name})" }
    }

    fun getObligation(id: String): ObligationNode? = obligations[id]

    fun getAllObligations(): List<ObligationNode> = obligations.values.toList()

    // =========================================================================
    // Node Management - Rights
    // =========================================================================

    fun addRight(right: RightNode) {
        rights[right.id] = right
        lastUpdated = Instant.now()
        logger.debug { "Added right: ${right.id} (${right.name})" }
    }

    fun getRight(id: String): RightNode? = rights[id]

    fun getAllRights(): List<RightNode> = rights.values.toList()

    // =========================================================================
    // Node Management - Conditions
    // =========================================================================

    fun addCondition(condition: ConditionNode) {
        conditions[condition.id] = condition
        lastUpdated = Instant.now()
    }

    fun getCondition(id: String): ConditionNode? = conditions[id]

    // =========================================================================
    // Node Management - Limitations
    // =========================================================================

    fun addLimitation(limitation: LimitationNode) {
        limitations[limitation.id] = limitation
        lastUpdated = Instant.now()
    }

    fun getLimitation(id: String): LimitationNode? = limitations[id]

    // =========================================================================
    // Node Management - Use Cases
    // =========================================================================

    fun addUseCase(useCase: UseCaseNode) {
        useCases[useCase.id] = useCase
        lastUpdated = Instant.now()
    }

    fun getUseCase(id: String): UseCaseNode? = useCases[id]

    fun getAllUseCases(): List<UseCaseNode> = useCases.values.toList()

    // =========================================================================
    // Edge Management
    // =========================================================================

    /**
     * Add an edge to the graph
     */
    fun addEdge(edge: GraphEdge) {
        val sourceId = edge.sourceId.uppercase()
        val targetId = if (edge is CompatibilityEdge) edge.targetId.uppercase() else edge.targetId

        // Store in outgoing edges
        outgoingEdges.getOrPut(sourceId) { mutableListOf() }.add(edge)

        // Store in incoming edges
        incomingEdges.getOrPut(targetId) { mutableListOf() }.add(edge)

        // Index compatibility edges for fast lookup
        if (edge is CompatibilityEdge) {
            val key = Pair(sourceId, targetId)
            compatibilityIndex[key] = edge.copy(sourceId = sourceId, targetId = targetId)

            // Add reverse if bidirectional
            if (edge.direction == CompatibilityDirection.BIDIRECTIONAL) {
                val reverseKey = Pair(targetId, sourceId)
                compatibilityIndex[reverseKey] = edge.copy(
                    id = "${edge.id}-reverse",
                    sourceId = targetId,
                    targetId = sourceId
                )
            }
        }

        lastUpdated = Instant.now()
        logger.debug { "Added edge: ${edge.edgeType} from $sourceId to $targetId" }
    }

    /**
     * Get all outgoing edges from a node
     */
    fun getOutgoingEdges(nodeId: String): List<GraphEdge> =
        outgoingEdges[nodeId.uppercase()] ?: emptyList()

    /**
     * Get all incoming edges to a node
     */
    fun getIncomingEdges(nodeId: String): List<GraphEdge> =
        incomingEdges[nodeId.uppercase()] ?: emptyList()

    /**
     * Get outgoing edges of a specific type
     */
    fun getOutgoingEdges(nodeId: String, edgeType: EdgeType): List<GraphEdge> =
        getOutgoingEdges(nodeId).filter { it.edgeType == edgeType }

    // =========================================================================
    // Compatibility Queries
    // =========================================================================

    /**
     * Check if two licenses are compatible
     */
    fun checkCompatibility(
        license1: String,
        license2: String,
        useCase: UseCaseNode? = null
    ): CompatibilityResult {
        val l1 = license1.uppercase()
        val l2 = license2.uppercase()

        logger.debug { "Checking compatibility: $l1 <-> $l2" }

        // Same license is always compatible
        if (l1 == l2) {
            return CompatibilityResult(
                license1 = l1,
                license2 = l2,
                compatible = true,
                level = CompatibilityLevel.FULL,
                reason = "Same license - fully compatible",
                path = listOf(l1)
            )
        }

        // Direct edge lookup
        val directEdge = compatibilityIndex[Pair(l1, l2)]
        if (directEdge != null) {
            return CompatibilityResult(
                license1 = l1,
                license2 = l2,
                compatible = directEdge.compatibility.isCompatible,
                level = directEdge.compatibility,
                reason = directEdge.notes.firstOrNull() ?: "Direct compatibility rule",
                conditions = directEdge.conditions,
                path = listOf(l1, l2),
                sources = directEdge.sources,
                dominantLicense = if (directEdge.direction == CompatibilityDirection.FORWARD) l2 else null
            )
        }

        // Infer compatibility from categories
        return inferCompatibility(l1, l2, useCase)
    }

    /**
     * Infer compatibility based on license properties
     */
    private fun inferCompatibility(
        license1: String,
        license2: String,
        useCase: UseCaseNode?
    ): CompatibilityResult {
        val l1Node = licenses[license1]
        val l2Node = licenses[license2]

        if (l1Node == null || l2Node == null) {
            val missing = listOfNotNull(
                if (l1Node == null) license1 else null,
                if (l2Node == null) license2 else null
            )
            return CompatibilityResult(
                license1 = license1,
                license2 = license2,
                compatible = true,  // Assume compatible but flag for review
                level = CompatibilityLevel.UNKNOWN,
                reason = "License(s) not found in knowledge graph: ${missing.joinToString(", ")}",
                requiresReview = true
            )
        }

        // Permissive + Permissive = Always OK
        if (l1Node.category == LicenseCategory.PERMISSIVE &&
            l2Node.category == LicenseCategory.PERMISSIVE) {
            return CompatibilityResult(
                license1 = license1,
                license2 = license2,
                compatible = true,
                level = CompatibilityLevel.FULL,
                reason = "Both licenses are permissive - fully compatible",
                conditions = listOf("Maintain attribution notices from both licenses"),
                inferredRule = "permissive-combination"
            )
        }

        // Public Domain + Anything = OK
        if (l1Node.category == LicenseCategory.PUBLIC_DOMAIN ||
            l2Node.category == LicenseCategory.PUBLIC_DOMAIN) {
            return CompatibilityResult(
                license1 = license1,
                license2 = license2,
                compatible = true,
                level = CompatibilityLevel.FULL,
                reason = "Public domain works can be combined with any license",
                inferredRule = "public-domain-combination"
            )
        }

        // Permissive + Copyleft = OK (copyleft dominates)
        if (l1Node.category == LicenseCategory.PERMISSIVE &&
            l2Node.copyleftStrength != CopyleftStrength.NONE) {
            return CompatibilityResult(
                license1 = license1,
                license2 = license2,
                compatible = true,
                level = CompatibilityLevel.CONDITIONAL,
                reason = "Permissive license can be combined with copyleft",
                conditions = listOf(
                    "Combined work must follow ${license2} terms",
                    "Copyleft obligations apply to derivative work"
                ),
                dominantLicense = license2,
                inferredRule = "permissive-under-copyleft"
            )
        }

        // Reverse: Copyleft + Permissive
        if (l2Node.category == LicenseCategory.PERMISSIVE &&
            l1Node.copyleftStrength != CopyleftStrength.NONE) {
            return CompatibilityResult(
                license1 = license1,
                license2 = license2,
                compatible = true,
                level = CompatibilityLevel.CONDITIONAL,
                reason = "Copyleft license can incorporate permissive code",
                conditions = listOf(
                    "Combined work must follow ${license1} terms",
                    "Copyleft obligations apply to derivative work"
                ),
                dominantLicense = license1,
                inferredRule = "copyleft-over-permissive"
            )
        }

        // Strong Copyleft + Strong Copyleft (different families) = Problem
        if (l1Node.copyleftStrength == CopyleftStrength.STRONG &&
            l2Node.copyleftStrength == CopyleftStrength.STRONG &&
            l1Node.family != l2Node.family) {
            return CompatibilityResult(
                license1 = license1,
                license2 = license2,
                compatible = false,
                level = CompatibilityLevel.INCOMPATIBLE,
                reason = "Different strong copyleft licenses cannot be combined",
                notes = listOf(
                    "Both require derivative works under their own terms",
                    "This creates an irreconcilable conflict"
                ),
                suggestions = listOf(
                    "Replace one dependency with an alternative under a compatible license",
                    "Contact upstream for dual-licensing options"
                ),
                inferredRule = "copyleft-conflict"
            )
        }

        // Same family copyleft - check version compatibility
        if (l1Node.family != null && l1Node.family == l2Node.family &&
            l1Node.copyleftStrength != CopyleftStrength.NONE) {
            return checkSameFamilyCompatibility(l1Node, l2Node)
        }

        // Weak copyleft (LGPL, MPL) with strong copyleft
        if ((l1Node.copyleftStrength == CopyleftStrength.LIBRARY ||
             l1Node.copyleftStrength == CopyleftStrength.FILE) &&
            l2Node.copyleftStrength == CopyleftStrength.STRONG) {
            return CompatibilityResult(
                license1 = license1,
                license2 = license2,
                compatible = true,
                level = CompatibilityLevel.CONDITIONAL,
                reason = "Weak copyleft can generally be combined with strong copyleft",
                conditions = listOf(
                    "Combined work follows strong copyleft terms",
                    "Check specific license compatibility requirements"
                ),
                dominantLicense = license2,
                requiresReview = true,
                inferredRule = "weak-under-strong-copyleft"
            )
        }

        // Network copyleft (AGPL) is more restrictive
        if (l1Node.copyleftStrength == CopyleftStrength.NETWORK ||
            l2Node.copyleftStrength == CopyleftStrength.NETWORK) {
            val agplLicense = if (l1Node.copyleftStrength == CopyleftStrength.NETWORK) license1 else license2
            return CompatibilityResult(
                license1 = license1,
                license2 = license2,
                compatible = true,
                level = CompatibilityLevel.CONDITIONAL,
                reason = "Network copyleft (AGPL) applies network disclosure requirements",
                conditions = listOf(
                    "Network service users must be able to obtain source code",
                    "AGPL obligations extend to network distribution"
                ),
                dominantLicense = agplLicense,
                requiresReview = true,
                inferredRule = "network-copyleft"
            )
        }

        // Default: Unknown - needs review
        return CompatibilityResult(
            license1 = license1,
            license2 = license2,
            compatible = true,  // Assume compatible but flag for review
            level = CompatibilityLevel.UNKNOWN,
            reason = "Compatibility could not be automatically determined",
            notes = listOf(
                "Categories: ${l1Node.category.displayName} + ${l2Node.category.displayName}",
                "Copyleft: ${l1Node.copyleftStrength.displayName} + ${l2Node.copyleftStrength.displayName}"
            ),
            requiresReview = true
        )
    }

    /**
     * Check compatibility between licenses in the same family
     */
    private fun checkSameFamilyCompatibility(
        l1Node: LicenseNode,
        l2Node: LicenseNode
    ): CompatibilityResult {
        val family = l1Node.family ?: return CompatibilityResult(
            license1 = l1Node.id,
            license2 = l2Node.id,
            compatible = true,
            level = CompatibilityLevel.UNKNOWN,
            reason = "Cannot determine family compatibility",
            requiresReview = true
        )

        // GPL family version compatibility
        if (family == LicenseFamilies.GPL) {
            val v1 = l1Node.version?.toDoubleOrNull() ?: 0.0
            val v2 = l2Node.version?.toDoubleOrNull() ?: 0.0

            // GPL-2.0-only vs GPL-3.0 incompatibility
            if ((l1Node.spdxId.contains("2.0") && l1Node.spdxId.contains("only") &&
                 l2Node.spdxId.contains("3.0")) ||
                (l2Node.spdxId.contains("2.0") && l2Node.spdxId.contains("only") &&
                 l1Node.spdxId.contains("3.0"))) {
                return CompatibilityResult(
                    license1 = l1Node.id,
                    license2 = l2Node.id,
                    compatible = false,
                    level = CompatibilityLevel.INCOMPATIBLE,
                    reason = "GPL-2.0-only and GPL-3.0 are not compatible",
                    notes = listOf(
                        "GPL-3.0 added patent provisions that GPL-2.0-only cannot accept",
                        "Check if the GPL-2.0 code is 'or later' licensed - that IS compatible"
                    ),
                    suggestions = listOf(
                        "Check if the GPL-2.0 code allows 'or later' versions",
                        "Contact upstream for dual-licensing options",
                        "Find an alternative dependency"
                    ),
                    inferredRule = "gpl-version-conflict"
                )
            }

            // "or later" versions are generally compatible with later versions
            if (l1Node.spdxId.contains("or-later") || l2Node.spdxId.contains("or-later")) {
                return CompatibilityResult(
                    license1 = l1Node.id,
                    license2 = l2Node.id,
                    compatible = true,
                    level = CompatibilityLevel.CONDITIONAL,
                    reason = "'Or later' clause allows compatibility with newer GPL versions",
                    conditions = listOf("Combined work uses the later GPL version"),
                    dominantLicense = if (v1 > v2) l1Node.id else l2Node.id,
                    inferredRule = "gpl-or-later"
                )
            }
        }

        // Same family, same version = compatible
        if (l1Node.version == l2Node.version) {
            return CompatibilityResult(
                license1 = l1Node.id,
                license2 = l2Node.id,
                compatible = true,
                level = CompatibilityLevel.FULL,
                reason = "Same license family and version",
                inferredRule = "same-family-version"
            )
        }

        // Default for same family
        return CompatibilityResult(
            license1 = l1Node.id,
            license2 = l2Node.id,
            compatible = true,
            level = CompatibilityLevel.CONDITIONAL,
            reason = "Same license family (${family}) - check version compatibility",
            requiresReview = true,
            inferredRule = "same-family-different-version"
        )
    }

    // =========================================================================
    // Obligation Queries
    // =========================================================================

    /**
     * Get all obligations for a license
     */
    fun getObligationsForLicense(licenseId: String): List<ObligationWithScope> {
        val normalizedId = licenseId.uppercase()
        return outgoingEdges[normalizedId]
            ?.filterIsInstance<ObligationEdge>()
            ?.mapNotNull { edge ->
                obligations[edge.targetId]?.let { obligation ->
                    ObligationWithScope(
                        obligation = obligation,
                        scope = edge.scope,
                        trigger = edge.trigger
                    )
                }
            }
            ?: emptyList()
    }

    /**
     * Aggregate obligations from multiple licenses
     */
    fun aggregateObligations(licenseIds: List<String>): AggregatedObligations {
        val allObligations = mutableMapOf<String, MutableList<ObligationSource>>()
        val scopeMap = mutableMapOf<String, ObligationScope>()

        licenseIds.forEach { licenseId ->
            val normalizedId = licenseId.uppercase()
            val license = licenses[normalizedId]

            outgoingEdges[normalizedId]
                ?.filterIsInstance<ObligationEdge>()
                ?.forEach { edge ->
                    val obligation = obligations[edge.targetId]
                    if (obligation != null) {
                        allObligations.getOrPut(obligation.id) { mutableListOf() }
                            .add(ObligationSource(
                                licenseId = normalizedId,
                                licenseName = license?.name,
                                trigger = edge.trigger,
                                scope = edge.scope
                            ))

                        // Track most restrictive scope
                        val currentScope = scopeMap[obligation.id]
                        if (currentScope == null || edge.scope.restrictiveness > currentScope.restrictiveness) {
                            scopeMap[obligation.id] = edge.scope
                        }
                    }
                }
        }

        val aggregatedList = allObligations.map { (obligationId, sources) ->
            val obligation = obligations[obligationId]!!
            AggregatedObligation(
                obligationId = obligation.id,
                obligationName = obligation.name,
                description = obligation.description,
                effort = obligation.effort,
                sources = sources,
                mostRestrictiveScope = scopeMap[obligationId] ?: ObligationScope.COMPONENT,
                examples = obligation.examples
            )
        }.sortedByDescending { it.effort.level }

        val highestEffort = aggregatedList.maxByOrNull { it.effort.level }?.effort

        return AggregatedObligations(
            obligations = aggregatedList,
            totalLicenses = licenseIds.size,
            highestEffort = highestEffort,
            uniqueObligationCount = aggregatedList.size
        )
    }

    // =========================================================================
    // Distribution-Aware Obligations (EU Compliance)
    // =========================================================================

    /**
     * Distribution scope for filtering obligations
     */
    enum class DistributionScope(val displayName: String) {
        INTERNAL("Internal Use"),
        BINARY("Binary Distribution"),
        SOURCE("Source Distribution"),
        SAAS("SaaS/Cloud Service"),
        EMBEDDED("Embedded/Device")
    }

    /**
     * Get obligations filtered by distribution scope (EU compliance requirement).
     * Different distribution types trigger different obligations:
     * - INTERNAL: Minimal obligations (no distribution)
     * - BINARY: Standard distribution obligations
     * - SOURCE: Source disclosure obligations apply
     * - SAAS: Network copyleft (AGPL) obligations apply
     * - EMBEDDED: All distribution obligations plus embedded-specific
     */
    fun getObligationsForDistribution(
        licenseId: String,
        distributionScope: DistributionScope
    ): List<ObligationWithDistribution> {
        val normalizedId = licenseId.uppercase()
        val licenseNode = licenses[normalizedId] ?: return emptyList()

        val allObligations = getObligationsForLicense(normalizedId)

        return allObligations.mapNotNull { obligationWithScope ->
            val obligation = obligationWithScope.obligation
            val trigger = obligationWithScope.trigger

            // Determine if obligation applies to this distribution scope
            val applies = when (distributionScope) {
                DistributionScope.INTERNAL -> {
                    // Internal use - only attribution and no-warranty typically apply
                    trigger in listOf(
                        TriggerCondition.ALWAYS
                    )
                }
                DistributionScope.BINARY -> {
                    // Binary distribution - most obligations except source disclosure
                    trigger in listOf(
                        TriggerCondition.ALWAYS,
                        TriggerCondition.ON_DISTRIBUTION,
                        TriggerCondition.ON_STATIC_LINKING,
                        TriggerCondition.ON_DYNAMIC_LINKING
                    )
                }
                DistributionScope.SOURCE -> {
                    // Source distribution - all standard obligations apply
                    trigger in listOf(
                        TriggerCondition.ALWAYS,
                        TriggerCondition.ON_DISTRIBUTION,
                        TriggerCondition.ON_MODIFICATION,
                        TriggerCondition.ON_DERIVATIVE,
                        TriggerCondition.ON_STATIC_LINKING,
                        TriggerCondition.ON_DYNAMIC_LINKING
                    )
                }
                DistributionScope.SAAS -> {
                    // SaaS - network copyleft triggers, special handling for AGPL
                    val isNetworkCopyleft = licenseNode.copyleftStrength == CopyleftStrength.NETWORK
                    if (isNetworkCopyleft) {
                        // AGPL: Network distribution triggers source disclosure
                        true
                    } else {
                        // Non-AGPL: SaaS typically exempts from distribution obligations
                        trigger in listOf(
                            TriggerCondition.ALWAYS,
                            TriggerCondition.ON_NETWORK_USE
                        )
                    }
                }
                DistributionScope.EMBEDDED -> {
                    // Embedded - all obligations apply plus device-specific concerns
                    true
                }
            }

            if (!applies) return@mapNotNull null

            // Calculate effort adjustment based on scope
            val adjustedEffort = adjustEffortForScope(obligation.effort, distributionScope, licenseNode)

            ObligationWithDistribution(
                obligation = obligation,
                scope = obligationWithScope.scope,
                trigger = trigger,
                distributionScope = distributionScope.name,
                adjustedEffort = adjustedEffort,
                isApplicable = true,
                applicabilityReason = getApplicabilityReason(trigger, distributionScope, licenseNode)
            )
        }
    }

    private fun adjustEffortForScope(
        baseEffort: EffortLevel,
        distributionScope: DistributionScope,
        licenseNode: LicenseNode
    ): EffortLevel {
        // Adjust effort based on distribution context
        return when {
            // Internal use is generally lower effort
            distributionScope == DistributionScope.INTERNAL -> {
                when (baseEffort) {
                    EffortLevel.HIGH -> EffortLevel.MEDIUM
                    EffortLevel.VERY_HIGH -> EffortLevel.HIGH
                    else -> baseEffort
                }
            }
            // SaaS with AGPL is highest effort
            distributionScope == DistributionScope.SAAS &&
                    licenseNode.copyleftStrength == CopyleftStrength.NETWORK -> EffortLevel.VERY_HIGH
            // Embedded often requires more effort
            distributionScope == DistributionScope.EMBEDDED &&
                    licenseNode.copyleftStrength != CopyleftStrength.NONE -> {
                when (baseEffort) {
                    EffortLevel.MEDIUM -> EffortLevel.HIGH
                    EffortLevel.HIGH -> EffortLevel.VERY_HIGH
                    else -> baseEffort
                }
            }
            else -> baseEffort
        }
    }

    private fun getApplicabilityReason(
        trigger: TriggerCondition,
        distributionScope: DistributionScope,
        licenseNode: LicenseNode
    ): String {
        return when {
            distributionScope == DistributionScope.INTERNAL ->
                "Applies to internal use (no distribution)"
            distributionScope == DistributionScope.SAAS &&
                    licenseNode.copyleftStrength == CopyleftStrength.NETWORK ->
                "AGPL network disclosure: SaaS users must be able to obtain source code"
            distributionScope == DistributionScope.EMBEDDED &&
                    licenseNode.copyleftStrength == CopyleftStrength.STRONG ->
                "Copyleft applies to embedded devices - must provide source/offer"
            trigger == TriggerCondition.ON_DISTRIBUTION ->
                "Triggered by ${distributionScope.displayName.lowercase()}"
            else -> "Standard license obligation"
        }
    }

    // =========================================================================
    // Rights Queries
    // =========================================================================

    /**
     * Get all rights for a license
     */
    fun getRightsForLicense(licenseId: String): List<RightNode> {
        val normalizedId = licenseId.uppercase()
        return outgoingEdges[normalizedId]
            ?.filterIsInstance<RightEdge>()
            ?.mapNotNull { edge -> rights[edge.targetId] }
            ?: emptyList()
    }

    // =========================================================================
    // Path Finding
    // =========================================================================

    /**
     * Find compatibility path between two licenses through intermediate licenses
     */
    fun findCompatibilityPath(
        source: String,
        target: String,
        maxDepth: Int = 3
    ): CompatibilityPath? {
        val sourceNorm = source.uppercase()
        val targetNorm = target.uppercase()

        if (sourceNorm == targetNorm) {
            return CompatibilityPath(
                sourceLicense = sourceNorm,
                targetLicense = targetNorm,
                licenses = listOf(sourceNorm),
                steps = emptyList(),
                overallCompatibility = CompatibilityLevel.FULL,
                allConditions = emptyList()
            )
        }

        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<List<String>>()
        queue.add(listOf(sourceNorm))

        while (queue.isNotEmpty()) {
            val path = queue.removeFirst()
            val current = path.last()

            if (current == targetNorm) {
                return buildCompatibilityPath(sourceNorm, targetNorm, path)
            }

            if (path.size > maxDepth || current in visited) continue
            visited.add(current)

            // Explore compatible neighbors
            outgoingEdges[current]
                ?.filterIsInstance<CompatibilityEdge>()
                ?.filter { it.compatibility.isCompatible }
                ?.forEach { edge ->
                    val nextNode = edge.targetId.uppercase()
                    if (nextNode !in visited) {
                        queue.add(path + nextNode)
                    }
                }
        }

        return null  // No path found
    }

    private fun buildCompatibilityPath(
        source: String,
        target: String,
        licensePath: List<String>
    ): CompatibilityPath {
        val steps = licensePath.zipWithNext().map { (from, to) ->
            val edge = compatibilityIndex[Pair(from, to)]
            CompatibilityStep(
                from = from,
                to = to,
                compatibility = edge?.compatibility ?: CompatibilityLevel.UNKNOWN,
                conditions = edge?.conditions ?: emptyList()
            )
        }

        val overallCompatibility = steps
            .minByOrNull { it.compatibility.ordinal }
            ?.compatibility
            ?: CompatibilityLevel.UNKNOWN

        return CompatibilityPath(
            sourceLicense = source,
            targetLicense = target,
            licenses = licensePath,
            steps = steps,
            overallCompatibility = overallCompatibility,
            allConditions = steps.flatMap { it.conditions }.distinct()
        )
    }

    // =========================================================================
    // Dependency Tree Analysis
    // =========================================================================

    /**
     * Analyze a dependency tree for license conflicts
     */
    fun analyzeDependencyTree(
        dependencies: List<DependencyLicense>,
        targetUseCase: UseCaseNode? = null
    ): DependencyTreeAnalysis {
        val conflicts = mutableListOf<LicenseConflict>()
        val licenseList = dependencies.map { it.license.uppercase() }
        val uniqueLicenses = licenseList.distinct()

        // Check all pairs for compatibility
        for (i in dependencies.indices) {
            for (j in i + 1 until dependencies.size) {
                val dep1 = dependencies[i]
                val dep2 = dependencies[j]

                val result = checkCompatibility(dep1.license, dep2.license, targetUseCase)

                if (!result.compatible) {
                    conflicts.add(LicenseConflict(
                        id = "${dep1.dependencyId}-${dep2.dependencyId}",
                        dependency1 = dep1,
                        dependency2 = dep2,
                        reason = result.reason,
                        severity = if (result.level == CompatibilityLevel.INCOMPATIBLE)
                            ConflictSeverity.BLOCKING else ConflictSeverity.WARNING,
                        suggestions = result.suggestions,
                        legalReferences = result.sources
                    ))
                }
            }
        }

        // Calculate distributions
        val licenseDistribution = licenseList.groupingBy { it }.eachCount()
        val categoryDistribution = licenseList
            .mapNotNull { licenses[it]?.category }
            .groupingBy { it.displayName }
            .eachCount()

        // Find dominant license
        val dominantLicense = findDominantLicense(uniqueLicenses)

        // Aggregate obligations
        val aggregatedObligations = aggregateObligations(uniqueLicenses)

        // Determine compliance status
        val complianceStatus = when {
            conflicts.any { it.severity == ConflictSeverity.BLOCKING } -> ComplianceStatus.BLOCKED
            conflicts.isNotEmpty() -> ComplianceStatus.WARNINGS
            aggregatedObligations.obligations.any { it.effort >= EffortLevel.HIGH } ->
                ComplianceStatus.REQUIRES_REVIEW
            else -> ComplianceStatus.COMPLIANT
        }

        // Generate recommendations
        val recommendations = generateRecommendations(conflicts, aggregatedObligations)

        // Calculate risk score
        val riskScore = calculateRiskScore(conflicts, aggregatedObligations, uniqueLicenses)

        return DependencyTreeAnalysis(
            totalDependencies = dependencies.size,
            uniqueLicenses = uniqueLicenses,
            licenseDistribution = licenseDistribution,
            categoryDistribution = categoryDistribution,
            conflicts = conflicts,
            dominantLicense = dominantLicense,
            aggregatedObligations = aggregatedObligations,
            complianceStatus = complianceStatus,
            recommendations = recommendations,
            riskScore = riskScore
        )
    }

    /**
     * Find the most restrictive (dominant) license
     */
    private fun findDominantLicense(licenseIds: List<String>): DominantLicenseInfo? {
        val licensesWithNodes = licenseIds.mapNotNull { id ->
            licenses[id]?.let { id to it }
        }

        if (licensesWithNodes.isEmpty()) return null

        val dominant = licensesWithNodes.maxByOrNull { (_, node) ->
            node.copyleftStrength.propagationLevel * 10 + node.category.riskLevel
        } ?: return null

        val (id, node) = dominant
        val count = licenseIds.count { it == id }

        return DominantLicenseInfo(
            licenseId = id,
            licenseName = node.name,
            category = node.category,
            copyleftStrength = node.copyleftStrength,
            reason = "Most restrictive license based on copyleft strength and category",
            dependencyCount = count
        )
    }

    /**
     * Generate compliance recommendations
     */
    private fun generateRecommendations(
        conflicts: List<LicenseConflict>,
        aggregatedObligations: AggregatedObligations
    ): List<ComplianceRecommendation> {
        val recommendations = mutableListOf<ComplianceRecommendation>()

        // Recommend based on conflicts
        conflicts.forEach { conflict ->
            recommendations.add(ComplianceRecommendation(
                type = RecommendationType.RESOLVE_CONFLICT,
                priority = if (conflict.severity == ConflictSeverity.BLOCKING)
                    RecommendationPriority.CRITICAL else RecommendationPriority.HIGH,
                title = "License conflict: ${conflict.dependency1.license} vs ${conflict.dependency2.license}",
                description = conflict.reason,
                actions = conflict.suggestions,
                affectedDependencies = listOf(
                    conflict.dependency1.dependencyName,
                    conflict.dependency2.dependencyName
                )
            ))
        }

        // Recommend based on high-effort obligations
        aggregatedObligations.obligations
            .filter { it.effort >= EffortLevel.HIGH }
            .forEach { obligation ->
                recommendations.add(ComplianceRecommendation(
                    type = RecommendationType.FULFILL_OBLIGATION,
                    priority = if (obligation.effort == EffortLevel.VERY_HIGH)
                        RecommendationPriority.HIGH else RecommendationPriority.MEDIUM,
                    title = "High-effort obligation: ${obligation.obligationName}",
                    description = obligation.description,
                    actions = obligation.examples,
                    affectedDependencies = obligation.sources.map { it.licenseId },
                    estimatedEffort = obligation.effort
                ))
            }

        return recommendations.sortedByDescending { it.priority.level }
    }

    /**
     * Calculate overall risk score (0.0 - 1.0)
     */
    private fun calculateRiskScore(
        conflicts: List<LicenseConflict>,
        aggregatedObligations: AggregatedObligations,
        uniqueLicenses: List<String>
    ): Double {
        var score = 0.0

        // Conflicts contribute heavily to risk
        score += conflicts.count { it.severity == ConflictSeverity.BLOCKING } * 0.3
        score += conflicts.count { it.severity == ConflictSeverity.WARNING } * 0.1

        // High-effort obligations contribute to risk
        score += aggregatedObligations.obligations.count { it.effort == EffortLevel.VERY_HIGH } * 0.15
        score += aggregatedObligations.obligations.count { it.effort == EffortLevel.HIGH } * 0.08

        // Strong copyleft licenses increase risk
        val copyleftCount = uniqueLicenses.count { id ->
            val node = licenses[id]
            node?.copyleftStrength == CopyleftStrength.STRONG ||
            node?.copyleftStrength == CopyleftStrength.NETWORK
        }
        score += copyleftCount * 0.05

        return score.coerceIn(0.0, 1.0)
    }

    // =========================================================================
    // License Details
    // =========================================================================

    /**
     * Get complete details for a license including all relationships
     */
    fun getLicenseDetails(licenseId: String): LicenseDetails? {
        val normalizedId = licenseId.uppercase()
        val license = licenses[normalizedId] ?: return null

        val obligationsWithScope = getObligationsForLicense(normalizedId)
        val licenseRights = getRightsForLicense(normalizedId)

        val licenseConditions = outgoingEdges[normalizedId]
            ?.filterIsInstance<ConditionEdge>()
            ?.mapNotNull { conditions[it.targetId] }
            ?: emptyList()

        val licenseLimitations = outgoingEdges[normalizedId]
            ?.filterIsInstance<LimitationEdge>()
            ?.mapNotNull { limitations[it.targetId] }
            ?: emptyList()

        // Get compatible licenses
        val compatibleWith = compatibilityIndex.entries
            .filter { it.key.first == normalizedId && it.value.compatibility.isCompatible }
            .map { (key, edge) ->
                val targetLicense = licenses[key.second]
                CompatibleLicenseSummary(
                    licenseId = key.second,
                    licenseName = targetLicense?.name ?: key.second,
                    compatibility = edge.compatibility,
                    direction = edge.direction,
                    conditions = edge.conditions
                )
            }

        // Get incompatible licenses
        val incompatibleWith = compatibilityIndex.entries
            .filter { it.key.first == normalizedId && !it.value.compatibility.isCompatible }
            .map { (key, edge) ->
                IncompatibleLicenseSummary(
                    licenseId = key.second,
                    licenseName = licenses[key.second]?.name ?: key.second,
                    reason = edge.notes.firstOrNull() ?: "Incompatible",
                    suggestions = emptyList()
                )
            }

        return LicenseDetails(
            license = license,
            obligations = obligationsWithScope,
            rights = licenseRights,
            conditions = licenseConditions,
            limitations = licenseLimitations,
            compatibleWith = compatibleWith,
            incompatibleWith = incompatibleWith
        )
    }

    // =========================================================================
    // Statistics
    // =========================================================================

    /**
     * Get statistics about the knowledge graph
     */
    fun getStatistics(): GraphStatistics {
        val compatEdgeCount = compatibilityIndex.size
        val obligationEdgeCount = outgoingEdges.values.flatten()
            .count { it is ObligationEdge }

        return GraphStatistics(
            totalLicenses = licenses.size,
            totalObligations = obligations.size,
            totalRights = rights.size,
            totalConditions = conditions.size,
            totalLimitations = limitations.size,
            totalUseCases = useCases.size,
            totalEdges = outgoingEdges.values.sumOf { it.size },
            totalCompatibilityEdges = compatEdgeCount,
            totalObligationEdges = obligationEdgeCount,
            licensesByCategory = categoryIndex.mapValues { it.value.size }
                .mapKeys { it.key.displayName },
            licenseFamilies = familyIndex.keys.toList(),
            lastUpdated = lastUpdated.toString()
        )
    }

    /**
     * Clear all data from the graph
     */
    fun clear() {
        licenses.clear()
        obligations.clear()
        rights.clear()
        conditions.clear()
        limitations.clear()
        useCases.clear()
        outgoingEdges.clear()
        incomingEdges.clear()
        compatibilityIndex.clear()
        familyIndex.clear()
        categoryIndex.clear()
        lastUpdated = Instant.now()
        logger.info { "Graph cleared" }
    }
}
