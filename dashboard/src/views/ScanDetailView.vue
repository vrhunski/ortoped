<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import { api, type Scan, type Dependency, type Policy, type PolicyReport, type SpdxLicenseDetailResponse, type ReportSummary, type CurationSession } from '@/api/client'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Dropdown from 'primevue/dropdown'
import Badge from 'primevue/badge'
import { useToast } from 'primevue/usetoast'
import LicenseDetailPopup from '@/components/LicenseDetailPopup.vue'

const route = useRoute()
const toast = useToast()

const scan = ref<Scan | null>(null)
const dependencies = ref<Dependency[]>([])
const loading = ref(true)
const total = ref(0)
const filter = ref('')

// Policy evaluation
const policies = ref<Policy[]>([])
const selectedPolicyId = ref<string | null>(null)
const evaluating = ref(false)
const policyReport = ref<PolicyReport | null>(null)

// License popup
const showLicensePopup = ref(false)
const selectedLicense = ref<SpdxLicenseDetailResponse | null>(null)
const loadingLicense = ref(false)
const licenseError = ref<string | null>(null)

// Report & Curation
const reportSummary = ref<ReportSummary | null>(null)
const curationSession = ref<CurationSession | null>(null)
const downloadingReport = ref(false)

const filteredDependencies = computed(() => {
  let deps = [...dependencies.value]

  // Apply text filter if present
  if (filter.value) {
    const search = filter.value.toLowerCase()
    deps = deps.filter(d =>
      d.name.toLowerCase().includes(search) ||
      d.concludedLicense?.toLowerCase().includes(search)
    )
  }

  // Sort: unresolved first (with AI suggestions at top), then resolved
  deps.sort((a, b) => {
    // Unresolved with AI suggestions first
    if (!a.isResolved && a.aiSuggestion && (b.isResolved || !b.aiSuggestion)) return -1
    if (!b.isResolved && b.aiSuggestion && (a.isResolved || !a.aiSuggestion)) return 1
    // Then unresolved without AI suggestions
    if (!a.isResolved && b.isResolved) return -1
    if (!b.isResolved && a.isResolved) return 1
    // Then alphabetically by name
    return a.name.localeCompare(b.name)
  })

  return deps
})

const policyOptions = computed(() =>
  policies.value.map(p => ({ label: p.name + (p.isDefault ? ' (Default)' : ''), value: p.id }))
)

async function fetchData() {
  loading.value = true
  try {
    const [scanRes, policiesRes] = await Promise.all([
      api.getScan(route.params.id as string),
      api.listPolicies(1, 100)
    ])

    scan.value = scanRes.data
    policies.value = policiesRes.data.policies

    // Pre-select default policy
    const defaultPolicy = policies.value.find(p => p.isDefault)
    if (defaultPolicy) {
      selectedPolicyId.value = defaultPolicy.id
    } else if (policies.value.length > 0) {
      selectedPolicyId.value = policies.value[0].id
    }

    if (scan.value.status === 'complete') {
      // Fetch all dependencies (handle pagination to get all)
      const allDependencies: Dependency[] = []
      let currentPage = 1
      const pageSize = 100
      let hasMore = true

      while (hasMore) {
        const depsRes = await api.getDependencies(route.params.id as string, currentPage, pageSize)
        allDependencies.push(...depsRes.data.dependencies)
        total.value = depsRes.data.total

        if (allDependencies.length >= depsRes.data.total) {
          hasMore = false
        } else {
          currentPage++
        }
      }

      dependencies.value = allDependencies

      // Fetch report summary and curation session in parallel
      await Promise.all([
        fetchReportSummary(),
        fetchCurationSession()
      ])
    }
  } catch (e) {
    console.error('Failed to fetch scan data', e)
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to fetch scan data', life: 3000 })
  } finally {
    loading.value = false
  }
}

async function runPolicyCheck() {
  if (!selectedPolicyId.value || !scan.value) return

  evaluating.value = true
  policyReport.value = null

  try {
    const response = await api.evaluatePolicy(scan.value.id, selectedPolicyId.value)
    const rawData = response.data as unknown as { report: string; policyId: string; scanId: string; passed: boolean; errorCount: number; warningCount: number; createdAt: string }

    // Parse the report JSON string from the API response
    const reportData = JSON.parse(rawData.report)

    // Map the parsed report to our expected structure
    policyReport.value = {
      policyId: rawData.policyId,
      policyName: reportData.policyName,
      scanId: rawData.scanId,
      passed: rawData.passed,
      errorCount: rawData.errorCount,
      warningCount: rawData.warningCount,
      infoCount: reportData.summary?.infoCount || 0,
      violations: reportData.violations?.map((v: Record<string, unknown>) => ({
        severity: v.severity as 'ERROR' | 'WARNING' | 'INFO',
        rule: v.ruleName as string,
        message: v.message as string,
        dependency: v.dependencyName as string,
        license: v.license as string,
        suggestion: v.aiSuggestion as string | undefined
      })) || [],
      evaluatedAt: rawData.createdAt
    }

    if (policyReport.value.passed) {
      toast.add({ severity: 'success', summary: 'Policy Check Passed', detail: 'No violations found', life: 3000 })
    } else {
      toast.add({
        severity: 'warn',
        summary: 'Policy Check Failed',
        detail: `Found ${policyReport.value.errorCount} errors, ${policyReport.value.warningCount} warnings`,
        life: 5000
      })
    }
  } catch (e) {
    console.error('Failed to evaluate policy', e)
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to run policy check', life: 3000 })
  } finally {
    evaluating.value = false
  }
}

async function downloadSbom(format: string) {
  try {
    const response = await api.generateSbom(route.params.id as string, format)
    const blob = new Blob([response.data.content], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = response.data.filename
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    console.error('Failed to download SBOM', e)
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to download SBOM', life: 3000 })
  }
}

// Report & Curation functions
async function fetchReportSummary() {
  try {
    const response = await api.getReportSummary(route.params.id as string)
    reportSummary.value = response.data
  } catch (e) {
    console.error('Failed to fetch report summary', e)
  }
}

async function fetchCurationSession() {
  try {
    const response = await api.getCurationSessionByScan(route.params.id as string)
    curationSession.value = response.data
  } catch (e) {
    // No curation session exists yet - that's OK
    curationSession.value = null
  }
}

async function downloadReport(format: 'json' | 'html') {
  downloadingReport.value = true
  try {
    const response = await api.generateReport(route.params.id as string, {
      format,
      includePolicy: true,
      includeCuration: true,
      includeAuditTrail: true,
      includeDependencyDetails: true
    })

    const content = response.data.content
    const filename = response.data.filename
    const mimeType = format === 'html' ? 'text/html' : 'application/json'

    const blob = new Blob([content], { type: mimeType })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    a.click()
    URL.revokeObjectURL(url)

    toast.add({ severity: 'success', summary: 'Success', detail: `Report downloaded as ${format.toUpperCase()}`, life: 2000 })
  } catch (e) {
    console.error('Failed to download report', e)
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to generate report', life: 3000 })
  } finally {
    downloadingReport.value = false
  }
}

async function downloadOrtExport() {
  downloadingReport.value = true
  try {
    const response = await api.downloadOrtExport(route.params.id as string)

    const content = response.data.content
    const filename = response.data.filename

    const blob = new Blob([content], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    a.click()
    URL.revokeObjectURL(url)

    toast.add({ severity: 'success', summary: 'Success', detail: 'ORT export downloaded', life: 2000 })
  } catch (e) {
    console.error('Failed to download ORT export', e)
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to generate ORT export', life: 3000 })
  } finally {
    downloadingReport.value = false
  }
}

function getConfidenceClass(confidence: string) {
  switch (confidence) {
    case 'HIGH': return 'confidence-high'
    case 'MEDIUM': return 'confidence-medium'
    default: return 'confidence-low'
  }
}

function getSeverityColor(severity: string): 'danger' | 'warning' | 'info' | 'secondary' {
  switch (severity) {
    case 'ERROR': return 'danger'
    case 'WARNING': return 'warning'
    case 'INFO': return 'info'
    default: return 'secondary'
  }
}

// License popup functions
async function fetchLicenseDetails(licenseId: string) {
  loadingLicense.value = true
  licenseError.value = null
  selectedLicense.value = null
  try {
    const response = await api.getSpdxLicense(licenseId)
    selectedLicense.value = response.data
  } catch (e) {
    console.error('Failed to fetch license details', e)
    licenseError.value = 'Failed to load license details'
  } finally {
    loadingLicense.value = false
  }
}

function onLicenseHover(event: MouseEvent, licenseId: string | undefined) {
  if (!licenseId) return

  // Clear any pending hide timeout
  if (hidePopupTimeout) {
    clearTimeout(hidePopupTimeout)
    hidePopupTimeout = null
  }

  showLicensePopup.value = true
  fetchLicenseDetails(licenseId)

  // Position popup with improved logic
  setTimeout(() => {
    const popup = document.querySelector('.license-popup') as HTMLElement
    if (popup) {
      const rect = popup.getBoundingClientRect()
      const viewportWidth = window.innerWidth
      const viewportHeight = window.innerHeight
      const mouseX = event.clientX
      const mouseY = event.clientY

      let left = mouseX + 15
      let top = mouseY + 15

      // Adjust horizontal position
      if (left + rect.width > viewportWidth) {
        left = mouseX - rect.width - 15
      }

      // Adjust vertical position
      if (top + rect.height > viewportHeight) {
        top = mouseY - rect.height - 15
      }

      // Ensure popup stays within viewport bounds
      left = Math.max(10, Math.min(left, viewportWidth - rect.width - 10))
      top = Math.max(10, Math.min(top, viewportHeight - rect.height - 10))

      popup.style.left = left + 'px'
      popup.style.top = top + 'px'
      popup.style.opacity = '1'
      popup.style.transform = 'scale(1)'
    }
  }, 50)
}

let hidePopupTimeout: ReturnType<typeof setTimeout> | null = null

function onLicenseLeave() {
  // Delay hiding to allow moving mouse to popup with longer delay for better UX
  hidePopupTimeout = setTimeout(() => {
    hideLicensePopup()
  }, 300)
}

function onPopupEnter() {
  // Cancel hide if mouse enters popup
  if (hidePopupTimeout) {
    clearTimeout(hidePopupTimeout)
    hidePopupTimeout = null
  }
}

function onPopupLeave() {
  // Hide popup when mouse leaves it with animation
  hideLicensePopup()
}

function hideLicensePopup() {
  const popup = document.querySelector('.license-popup') as HTMLElement
  if (popup) {
    popup.style.opacity = '0'
    popup.style.transform = 'scale(0.95)'
  }

  setTimeout(() => {
    showLicensePopup.value = false
    setTimeout(() => {
      selectedLicense.value = null
      licenseError.value = null
      loadingLicense.value = false
    }, 200)
  }, 150)
}

onMounted(fetchData)
</script>

<template>
  <div class="scan-detail">
    <div v-if="loading" class="loading">
      <i class="pi pi-spin pi-spinner"></i> Loading...
    </div>

    <template v-else-if="scan">
      <header class="page-header">
        <div>
          <div class="breadcrumb">
            <RouterLink to="/scans">Scans</RouterLink>
            <i class="pi pi-angle-right"></i>
            <span>Scan Details</span>
          </div>
          <h1>Scan Details</h1>
          <div class="scan-meta">
            <span :class="['status-badge', scan.status === 'complete' ? 'status-success' : 'status-warning']">
              {{ scan.status }}
            </span>
            <span>Created: {{ new Date(scan.createdAt).toLocaleString() }}</span>
          </div>
        </div>
        <div class="header-actions" v-if="scan.status === 'complete'">
          <Button label="Export SBOM" icon="pi pi-download" severity="secondary" @click="downloadSbom('cyclonedx-json')" />
          <Button label="Report (JSON)" icon="pi pi-file" severity="secondary" @click="downloadReport('json')" :loading="downloadingReport" />
          <Button label="Report (HTML)" icon="pi pi-file" severity="secondary" @click="downloadReport('html')" :loading="downloadingReport" />
          <Button label="ORT Export" icon="pi pi-share-alt" severity="secondary" @click="downloadOrtExport" :loading="downloadingReport" title="Export in ORT Evaluator format" />
        </div>
      </header>

      <!-- Scan in progress -->
      <div v-if="scan.status === 'scanning'" class="section scanning-state">
        <i class="pi pi-spin pi-spinner"></i>
        <h3>Scan in Progress</h3>
        <p>Analyzing dependencies and resolving licenses...</p>
      </div>

      <!-- Scan failed -->
      <div v-else-if="scan.status === 'failed'" class="section error-state">
        <i class="pi pi-times-circle"></i>
        <h3>Scan Failed</h3>
        <p>{{ scan.errorMessage || 'An unknown error occurred' }}</p>
      </div>

      <!-- Scan complete -->
      <template v-else>
        <!-- Compliance Banner -->
        <div v-if="policyReport" :class="['compliance-banner', policyReport.passed ? 'compliance-passed' : 'compliance-failed']">
          <div class="compliance-icon">
            <i :class="policyReport.passed ? 'pi pi-check-circle' : 'pi pi-times-circle'"></i>
          </div>
          <div class="compliance-info">
            <h3>{{ policyReport.passed ? 'Policy Check Passed' : 'Policy Check Failed' }}</h3>
            <p>Policy: {{ policyReport.policyName }}</p>
          </div>
          <div class="compliance-stats">
            <div class="stat" v-if="policyReport.errorCount > 0">
              <Badge :value="policyReport.errorCount" severity="danger" />
              <span>Errors</span>
            </div>
            <div class="stat" v-if="policyReport.warningCount > 0">
              <Badge :value="policyReport.warningCount" severity="warning" />
              <span>Warnings</span>
            </div>
            <div class="stat" v-if="policyReport.infoCount > 0">
              <Badge :value="policyReport.infoCount" severity="info" />
              <span>Info</span>
            </div>
            <div class="stat" v-if="policyReport.passed">
              <Badge value="0" severity="success" />
              <span>Violations</span>
            </div>
          </div>
        </div>

        <!-- Policy Evaluation Section -->
        <div class="section policy-section">
          <div class="section-header">
            <h2><i class="pi pi-shield"></i> Policy Compliance</h2>
          </div>
          <div class="policy-controls">
            <div class="policy-selector">
              <label>Select Policy:</label>
              <Dropdown
                v-model="selectedPolicyId"
                :options="policyOptions"
                optionLabel="label"
                optionValue="value"
                placeholder="Select a policy"
                class="policy-dropdown"
                :disabled="evaluating || policies.length === 0"
              />
            </div>
            <Button
              label="Run Policy Check"
              icon="pi pi-play"
              @click="runPolicyCheck"
              :loading="evaluating"
              :disabled="!selectedPolicyId || evaluating"
            />
          </div>
          <p v-if="policies.length === 0" class="no-policies">
            No policies available. <RouterLink to="/policies">Create a policy</RouterLink> to run compliance checks.
          </p>
        </div>

        <!-- Curation Section -->
        <div class="section curation-section">
          <div class="section-header">
            <h2><i class="pi pi-check-square"></i> License Curation</h2>
          </div>
          <div v-if="curationSession" class="curation-status">
            <div class="curation-info">
              <Badge
                :value="curationSession.status"
                :severity="curationSession.status === 'APPROVED' ? 'success' : curationSession.status === 'IN_PROGRESS' ? 'warning' : 'info'"
              />
              <div class="curation-stats">
                <span><strong>{{ curationSession.statistics.total - curationSession.statistics.pending }}</strong> / {{ curationSession.statistics.total }} reviewed</span>
                <span class="stat-item accepted"><i class="pi pi-check"></i> {{ curationSession.statistics.accepted }} accepted</span>
                <span class="stat-item rejected"><i class="pi pi-times"></i> {{ curationSession.statistics.rejected }} rejected</span>
                <span class="stat-item modified"><i class="pi pi-pencil"></i> {{ curationSession.statistics.modified }} modified</span>
              </div>
            </div>
            <RouterLink :to="`/curations/${curationSession.scanId}`">
              <Button
                :label="curationSession.status === 'APPROVED' ? 'View Curation' : 'Continue Curation'"
                :icon="curationSession.status === 'APPROVED' ? 'pi pi-eye' : 'pi pi-arrow-right'"
                :severity="curationSession.status === 'APPROVED' ? 'secondary' : 'primary'"
              />
            </RouterLink>
          </div>
          <div v-else class="no-curation">
            <p>No curation session started for this scan. Start one to review and approve AI-suggested licenses.</p>
            <RouterLink to="/curations">
              <Button label="Start Curation" icon="pi pi-plus" />
            </RouterLink>
          </div>
        </div>

        <!-- Violations Section -->
        <div v-if="policyReport && policyReport.violations.length > 0" class="section violations-section">
          <div class="section-header">
            <h2><i class="pi pi-exclamation-triangle"></i> Violations ({{ policyReport.violations.length }})</h2>
          </div>

          <DataTable
            :value="policyReport.violations"
            stripedRows
            showGridlines
            class="p-datatable-sm"
            :paginator="policyReport.violations.length > 10"
            :rows="10"
          >
            <Column field="severity" header="Severity" style="width: 100px" sortable>
              <template #body="{ data }">
                <Badge :value="data.severity" :severity="getSeverityColor(data.severity)" />
              </template>
            </Column>

            <Column field="dependency" header="Dependency" style="min-width: 200px" sortable>
              <template #body="{ data }">
                <code class="dependency-name">{{ data.dependency }}</code>
              </template>
            </Column>

            <Column field="license" header="License" style="width: 150px" sortable>
              <template #body="{ data }">
                <span class="license-badge">{{ data.license || 'Unknown' }}</span>
              </template>
            </Column>

            <Column field="rule" header="Rule" style="width: 150px" sortable>
              <template #body="{ data }">
                <span class="rule-name">{{ data.rule }}</span>
              </template>
            </Column>

            <Column field="message" header="Message" style="min-width: 250px">
              <template #body="{ data }">
                <div class="violation-message">
                  {{ data.message }}
                  <div v-if="data.suggestion" class="suggestion">
                    <i class="pi pi-lightbulb"></i> {{ data.suggestion }}
                  </div>
                </div>
              </template>
            </Column>
          </DataTable>
        </div>

        <!-- Dependencies Section -->
        <div class="section">
          <div class="section-header">
            <h2>Dependencies ({{ total }})</h2>
            <input
              v-model="filter"
              type="text"
              placeholder="Filter dependencies..."
              class="filter-input"
            />
          </div>

          <DataTable
            :value="filteredDependencies"
            stripedRows
            showGridlines
            class="p-datatable-sm"
            :paginator="filteredDependencies.length > 20"
            :rows="20"
          >
            <Column field="name" header="Package" style="min-width: 200px" sortable>
              <template #body="{ data }">
                <div>
                  <strong>{{ data.name }}</strong>
                  <div class="dep-id">{{ data.id }}</div>
                </div>
              </template>
            </Column>

            <Column field="version" header="Version" style="width: 120px" sortable />

            <Column header="License" style="width: 150px">
              <template #body="{ data }">
                <span class="license-badge">
                  {{ data.concludedLicense || data.declaredLicenses[0] || 'Unknown' }}
                </span>
              </template>
            </Column>

            <Column field="isResolved" header="Status" style="width: 120px">
              <template #body="{ data }">
                <Badge v-if="data.isResolved" value="Resolved" severity="success" />
                <Badge v-else value="Unresolved" severity="warning" />
              </template>
            </Column>

            <Column header="AI Suggestion" style="min-width: 200px">
              <template #body="{ data }">
                <div v-if="data.aiSuggestion" class="ai-suggestion">
                  <span class="suggested-license">{{ data.aiSuggestion.suggestedLicense }}</span>
                  <span :class="['confidence', getConfidenceClass(data.aiSuggestion.confidence)]">
                    {{ data.aiSuggestion.confidence }}
                  </span>
                  <div class="reasoning">{{ data.aiSuggestion.reasoning }}</div>
                </div>
                <span v-else class="no-suggestion">-</span>
              </template>
            </Column>

            <Column header="SPDX Validation" style="min-width: 220px">
              <template #body="{ data }">
                <div
                  v-if="data.spdxLicense?.licenseId || data.spdxSuggestion?.licenseId"
                  class="spdx-container"
                  :class="{ 'has-hover': data.spdxLicense?.licenseId || data.spdxSuggestion?.licenseId }"
                >
                  <div
                    class="spdx-suggestion"
                    @mouseenter="onLicenseHover($event, data.spdxLicense?.licenseId || data.spdxSuggestion?.licenseId)"
                    @mouseleave="onLicenseLeave"
                    @focus="onLicenseHover($event, data.spdxLicense?.licenseId || data.spdxSuggestion?.licenseId)"
                    @blur="onLicenseLeave"
                    @keydown.enter="onLicenseHover($event, data.spdxLicense?.licenseId || data.spdxSuggestion?.licenseId)"
                    @keydown.escape="onLicenseLeave"
                    :tabindex="data.spdxLicense?.licenseId || data.spdxSuggestion?.licenseId ? 0 : -1"
                    :title="data.spdxValidated ? 'Click to view SPDX license details' : 'SPDX validation failed - click to view suggested license'"
                    role="button"
                    :aria-label="`View SPDX license details for ${data.spdxLicense?.licenseId || data.spdxSuggestion?.licenseId}`"
                  >
                    <div class="spdx-license-info">
                      <span class="suggested-license">
                        {{ data.spdxLicense?.licenseId || data.spdxSuggestion?.licenseId }}
                      </span>
                      <i class="pi pi-info-circle spdx-info-icon" v-if="data.spdxLicense?.licenseId || data.spdxSuggestion?.licenseId"></i>
                    </div>
                    <div class="spdx-status">
                      <Badge
                        :value="data.spdxValidated ? 'Valid' : 'Invalid'"
                        :severity="data.spdxValidated ? 'success' : 'warning'"
                        class="spdx-status-badge"
                      />
                      <span v-if="data.spdxSuggestion && !data.spdxValidated" class="spdx-suggestion-label">
                        Suggested
                      </span>
                    </div>
                  </div>
                </div>
                <div v-else class="no-spdx">
                  <span class="no-suggestion">Not validated</span>
                  <small class="spdx-hint">Enable SPDX validation in scan config</small>
                </div>
              </template>
            </Column>
          </DataTable>
        </div>
      </template>
    </template>

    <LicenseDetailPopup
      :visible="showLicensePopup"
      :license="selectedLicense"
      :loading="loadingLicense"
      :error="licenseError"
      @mouseenter="onPopupEnter"
      @mouseleave="onPopupLeave"
      @close="onPopupLeave"
    />
  </div>
</template>

<style scoped>
.scan-detail {
  max-width: 1400px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 2rem;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.875rem;
  color: #64748b;
  margin-bottom: 0.5rem;
}

.breadcrumb a {
  color: #3b82f6;
  text-decoration: none;
}

.page-header h1 {
  margin: 0;
  font-size: 2rem;
  color: #1e293b;
}

.scan-meta {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-top: 0.5rem;
  color: #64748b;
  font-size: 0.875rem;
}

.header-actions {
  display: flex;
  gap: 0.75rem;
}

/* Compliance Banner */
.compliance-banner {
  display: flex;
  align-items: center;
  gap: 1.5rem;
  padding: 1.25rem 1.5rem;
  border-radius: 1rem;
  margin-bottom: 1.5rem;
}

.compliance-passed {
  background: linear-gradient(135deg, #d1fae5 0%, #a7f3d0 100%);
  border: 1px solid #10b981;
}

.compliance-failed {
  background: linear-gradient(135deg, #fee2e2 0%, #fecaca 100%);
  border: 1px solid #ef4444;
}

.compliance-icon {
  font-size: 2.5rem;
}

.compliance-passed .compliance-icon {
  color: #059669;
}

.compliance-failed .compliance-icon {
  color: #dc2626;
}

.compliance-info {
  flex: 1;
}

.compliance-info h3 {
  margin: 0;
  font-size: 1.25rem;
  color: #1e293b;
}

.compliance-info p {
  margin: 0.25rem 0 0;
  color: #475569;
  font-size: 0.875rem;
}

.compliance-stats {
  display: flex;
  gap: 1.5rem;
}

.compliance-stats .stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.25rem;
}

.compliance-stats .stat span {
  font-size: 0.75rem;
  color: #64748b;
}

/* Policy Section */
.policy-section {
  background: white;
  border-radius: 1rem;
  padding: 1.5rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  margin-bottom: 1.5rem;
}

.policy-section .section-header h2 {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.policy-section .section-header h2 i {
  color: #8b5cf6;
}

.policy-controls {
  display: flex;
  align-items: flex-end;
  gap: 1rem;
  margin-top: 1rem;
}

.policy-selector {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.policy-selector label {
  font-size: 0.875rem;
  color: #64748b;
}

.policy-dropdown {
  min-width: 300px;
}

.no-policies {
  color: #64748b;
  margin-top: 1rem;
}

.no-policies a {
  color: #3b82f6;
}

/* Violations Section */
.violations-section {
  background: white;
  border-radius: 1rem;
  padding: 1.5rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  margin-bottom: 1.5rem;
}

.violations-section .section-header h2 {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: #dc2626;
}

.violations-section .section-header h2 i {
  color: #f59e0b;
}

.dependency-name {
  font-family: monospace;
  font-size: 0.875rem;
  background: #f1f5f9;
  padding: 0.125rem 0.375rem;
  border-radius: 0.25rem;
}

.rule-name {
  font-family: monospace;
  font-size: 0.875rem;
  color: #6366f1;
}

.violation-message {
  font-size: 0.875rem;
  color: #374151;
}

.suggestion {
  margin-top: 0.5rem;
  padding: 0.5rem;
  background: #fef3c7;
  border-radius: 0.25rem;
  font-size: 0.8rem;
  color: #92400e;
}

.suggestion i {
  color: #f59e0b;
  margin-right: 0.25rem;
}

/* Dependencies Section */
.section {
  background: white;
  border-radius: 1rem;
  padding: 1.5rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  margin-bottom: 1.5rem;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.section h2 {
  margin: 0;
  font-size: 1.25rem;
  color: #1e293b;
}

.filter-input {
  padding: 0.5rem 1rem;
  border: 1px solid #e2e8f0;
  border-radius: 0.5rem;
  width: 250px;
}

.dep-id {
  font-size: 0.75rem;
  color: #94a3b8;
  margin-top: 0.25rem;
}

.license-badge {
  display: inline-block;
  padding: 0.25rem 0.5rem;
  background: #f1f5f9;
  border-radius: 0.25rem;
  font-size: 0.875rem;
  font-family: monospace;
}

.status-badge {
  display: inline-block;
  padding: 0.25rem 0.75rem;
  border-radius: 9999px;
  font-size: 0.75rem;
  font-weight: 600;
}

.status-success {
  background: #d1fae5;
  color: #059669;
}

.status-warning {
  background: #fef3c7;
  color: #d97706;
}

.ai-suggestion {
  font-size: 0.875rem;
}

.suggested-license {
  font-weight: 600;
  color: #1e293b;
}

.confidence {
  margin-left: 0.5rem;
  padding: 0.125rem 0.5rem;
  border-radius: 0.25rem;
  font-size: 0.75rem;
}

.confidence-high {
  background: #d1fae5;
  color: #059669;
}

.confidence-medium {
  background: #fef3c7;
  color: #d97706;
}

.confidence-low {
  background: #fee2e2;
  color: #dc2626;
}

.reasoning {
  color: #64748b;
  margin-top: 0.25rem;
  font-size: 0.75rem;
}

.no-suggestion {
  color: #94a3b8;
}

/* SPDX Validation Styles */
.spdx-container {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.spdx-container.has-hover {
  cursor: pointer;
}

.spdx-suggestion {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
  padding: 0.5rem;
  border-radius: 0.375rem;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  transition: all 0.2s ease;
}

.spdx-suggestion:hover,
.spdx-suggestion:focus {
  background: #f1f5f9;
  border-color: #cbd5e1;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  transform: translateY(-1px);
  outline: 2px solid #3b82f6;
  outline-offset: 2px;
}

.spdx-license-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.spdx-info-icon {
  color: #64748b;
  font-size: 0.875rem;
  opacity: 0.7;
  transition: opacity 0.2s ease;
}

.spdx-suggestion:hover .spdx-info-icon {
  opacity: 1;
  color: #3b82f6;
}

.spdx-status {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.spdx-status-badge {
  font-size: 0.75rem !important;
  padding: 0.125rem 0.375rem !important;
}

.spdx-suggestion-label {
  font-size: 0.75rem;
  color: #64748b;
  font-style: italic;
}

.no-spdx {
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
}

.spdx-hint {
  color: #94a3b8;
  font-size: 0.75rem;
  font-style: italic;
}

.scanning-state,
.error-state {
  text-align: center;
  padding: 4rem;
}

.scanning-state i,
.error-state i {
  font-size: 3rem;
  margin-bottom: 1rem;
}

.scanning-state i {
  color: #3b82f6;
}

.error-state i {
  color: #dc2626;
}

.scanning-state h3,
.error-state h3 {
  margin: 0 0 0.5rem;
  color: #1e293b;
}

.scanning-state p,
.error-state p {
  color: #64748b;
}

.loading {
  text-align: center;
  padding: 4rem;
  color: #64748b;
}

/* Curation Section */
.curation-section {
  background: white;
  border-radius: 1rem;
  padding: 1.5rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  margin-bottom: 1.5rem;
}

.curation-section .section-header h2 {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.curation-section .section-header h2 i {
  color: #10b981;
}

.curation-status {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 1rem;
}

.curation-info {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.curation-stats {
  display: flex;
  gap: 1.5rem;
  font-size: 0.875rem;
  color: #64748b;
}

.curation-stats .stat-item {
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.curation-stats .stat-item.accepted {
  color: #10b981;
}

.curation-stats .stat-item.rejected {
  color: #ef4444;
}

.curation-stats .stat-item.modified {
  color: #8b5cf6;
}

.no-curation {
  margin-top: 1rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.no-curation p {
  color: #64748b;
  margin: 0;
}
</style>
