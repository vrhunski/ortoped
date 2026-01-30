package com.ortoped.api.service

import com.ortoped.core.graph.LicenseKnowledgeGraph
import com.ortoped.core.graph.LicenseGraphLoader
import com.ortoped.core.graph.model.*
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val logger = KotlinLogging.logger {}

/**
 * Service layer for the License Knowledge Graph.
 *
 * Provides thread-safe access to the graph and handles initialization.
 * This is the main entry point for API routes to interact with the graph.
 */
class LicenseGraphService {

    private val graph = LicenseKnowledgeGraph()
    private val initMutex = Mutex()
    private var initialized = false

    /**
     * Ensure the graph is initialized with standard data.
     * Thread-safe - can be called multiple times.
     */
    suspend fun ensureInitialized() {
        if (initialized) return
        initMutex.withLock {
            if (!initialized) {
                logger.info { "Initializing license knowledge graph..." }
                LicenseGraphLoader(graph).loadAll()
                initialized = true
                logger.info { "License knowledge graph initialized successfully" }
            }
        }
    }

    /**
     * Check if the graph has been initialized
     */
    fun isInitialized(): Boolean = initialized

    // =========================================================================
    // License Queries
    // =========================================================================

    /**
     * Get a license by its SPDX ID
     */
    suspend fun getLicense(spdxId: String): LicenseNode? {
        ensureInitialized()
        return graph.getLicense(spdxId)
    }

    /**
     * Get all licenses
     */
    suspend fun getAllLicenses(): List<LicenseNode> {
        ensureInitialized()
        return graph.getAllLicenses()
    }

    /**
     * Get licenses by category
     */
    suspend fun getLicensesByCategory(category: LicenseCategory): List<LicenseNode> {
        ensureInitialized()
        return graph.getLicensesByCategory(category)
    }

    /**
     * Get licenses by family
     */
    suspend fun getLicensesByFamily(family: String): List<LicenseNode> {
        ensureInitialized()
        return graph.getLicensesByFamily(family)
    }

    /**
     * Search licenses by name or ID
     */
    suspend fun searchLicenses(query: String, limit: Int = 20): List<LicenseNode> {
        ensureInitialized()
        return graph.searchLicenses(query, limit)
    }

    /**
     * Get complete details for a license
     */
    suspend fun getLicenseDetails(spdxId: String): LicenseDetails? {
        ensureInitialized()
        return graph.getLicenseDetails(spdxId)
    }

    // =========================================================================
    // Compatibility Queries
    // =========================================================================

    /**
     * Check compatibility between two licenses
     */
    suspend fun checkCompatibility(
        license1: String,
        license2: String,
        useCaseId: String? = null
    ): CompatibilityResult {
        ensureInitialized()
        val useCase = useCaseId?.let { graph.getUseCase(it) }
        return graph.checkCompatibility(license1, license2, useCase)
    }

    /**
     * Check compatibility between multiple license pairs
     */
    suspend fun checkCompatibilityMatrix(
        licenses: List<String>
    ): List<CompatibilityResult> {
        ensureInitialized()
        val results = mutableListOf<CompatibilityResult>()

        for (i in licenses.indices) {
            for (j in i + 1 until licenses.size) {
                results.add(graph.checkCompatibility(licenses[i], licenses[j]))
            }
        }

        return results
    }

    /**
     * Find compatibility path between two licenses
     */
    suspend fun findCompatibilityPath(
        source: String,
        target: String,
        maxDepth: Int = 3
    ): CompatibilityPath? {
        ensureInitialized()
        return graph.findCompatibilityPath(source, target, maxDepth)
    }

    // =========================================================================
    // Obligation Queries
    // =========================================================================

    /**
     * Get obligations for a license
     */
    suspend fun getObligationsForLicense(licenseId: String): List<ObligationWithScope> {
        ensureInitialized()
        return graph.getObligationsForLicense(licenseId)
    }

    /**
     * Get aggregated obligations from a set of licenses
     */
    suspend fun getAggregatedObligations(licenseIds: List<String>): AggregatedObligations {
        ensureInitialized()
        return graph.aggregateObligations(licenseIds)
    }

    /**
     * Get all standard obligations
     */
    suspend fun getAllObligations(): List<ObligationNode> {
        ensureInitialized()
        return graph.getAllObligations()
    }

    /**
     * Get a specific obligation by ID
     */
    suspend fun getObligation(id: String): ObligationNode? {
        ensureInitialized()
        return graph.getObligation(id)
    }

    // =========================================================================
    // Rights Queries
    // =========================================================================

    /**
     * Get rights for a license
     */
    suspend fun getRightsForLicense(licenseId: String): List<RightNode> {
        ensureInitialized()
        return graph.getRightsForLicense(licenseId)
    }

    /**
     * Get all standard rights
     */
    suspend fun getAllRights(): List<RightNode> {
        ensureInitialized()
        return graph.getAllRights()
    }

    // =========================================================================
    // Dependency Tree Analysis
    // =========================================================================

    /**
     * Analyze a dependency tree for license conflicts and obligations
     */
    suspend fun analyzeDependencyTree(
        dependencies: List<DependencyLicense>,
        useCaseId: String? = null
    ): DependencyTreeAnalysis {
        ensureInitialized()
        val useCase = useCaseId?.let { graph.getUseCase(it) }
        return graph.analyzeDependencyTree(dependencies, useCase)
    }

    /**
     * Quick conflict check without full analysis
     */
    suspend fun findConflicts(
        dependencies: List<DependencyLicense>
    ): List<LicenseConflict> {
        ensureInitialized()
        val analysis = graph.analyzeDependencyTree(dependencies)
        return analysis.conflicts
    }

    // =========================================================================
    // Use Cases
    // =========================================================================

    /**
     * Get all use cases
     */
    suspend fun getAllUseCases(): List<UseCaseNode> {
        ensureInitialized()
        return graph.getAllUseCases()
    }

    /**
     * Get a specific use case by ID
     */
    suspend fun getUseCase(id: String): UseCaseNode? {
        ensureInitialized()
        return graph.getUseCase(id)
    }

    // =========================================================================
    // Statistics
    // =========================================================================

    /**
     * Get graph statistics
     */
    suspend fun getStatistics(): GraphStatistics {
        ensureInitialized()
        return graph.getStatistics()
    }

    // =========================================================================
    // Admin Operations
    // =========================================================================

    /**
     * Reload the graph with fresh data.
     * Note: This clears all existing data.
     */
    suspend fun reload() {
        initMutex.withLock {
            logger.info { "Reloading license knowledge graph..." }
            graph.clear()
            LicenseGraphLoader(graph).loadAll()
            logger.info { "License knowledge graph reloaded" }
        }
    }

    /**
     * Add a custom license to the graph
     */
    suspend fun addCustomLicense(license: LicenseNode) {
        ensureInitialized()
        graph.addLicense(license)
        logger.info { "Added custom license: ${license.id}" }
    }

    /**
     * Add a custom compatibility rule
     */
    suspend fun addCompatibilityRule(edge: CompatibilityEdge) {
        ensureInitialized()
        graph.addEdge(edge)
        logger.info { "Added compatibility rule: ${edge.sourceId} <-> ${edge.targetId}" }
    }
}
