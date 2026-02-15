package com.ortoped.core.policy.explanation

/**
 * Interface for looking up past curation decisions.
 *
 * Implementations query either the local file cache (CLI standalone mode)
 * or PostgreSQL (API mode) for past decisions on the same license or family.
 */
interface PastDecisionLookup {

    /**
     * Find past curation decisions for the given license.
     * Returns decisions on the same license SPDX ID or the same license family.
     *
     * @param license SPDX license identifier (e.g., "Apache-2.0")
     * @param limit Maximum number of past decisions to return
     * @return List of past decisions, most recent first
     */
    fun findPastDecisions(license: String, limit: Int = 5): List<PastDecision>
}
