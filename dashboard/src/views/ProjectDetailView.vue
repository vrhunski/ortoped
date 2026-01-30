<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import { api, type Project, type ScanSummary, type PackageManagerInfo } from '@/api/client'
import InputSwitch from 'primevue/inputswitch'
import MultiSelect from 'primevue/multiselect'
import { useToast } from 'primevue/usetoast'

const toast = useToast()
const route = useRoute()
const project = ref<Project | null>(null)
const scans = ref<ScanSummary[]>([])
const loading = ref(true)
const scanning = ref(false)

// Package managers list
const packageManagers = ref<PackageManagerInfo[]>([])
const showAdvancedConfig = ref(false)

// Scan configuration with smart defaults
const scanConfig = ref({
  enableAi: true,
  enableSpdx: false,
  enableSourceScan: false,
  parallelAiCalls: true
})

// Analyzer configuration with ORT defaults
const analyzerConfig = ref({
  allowDynamicVersions: true,
  skipExcluded: true,
  disabledPackageManagers: [] as string[]
})

const SCAN_CONFIG_KEY = 'ortoped.scanConfig'
const ANALYZER_CONFIG_KEY = 'ortoped.analyzerConfig'

// Polling state
let pollInterval: number | null = null
const POLL_INTERVAL_MS = 3000

// Check if any scan is in progress
const hasActiveScans = computed(() =>
  scans.value.some(s => s.status === 'pending' || s.status === 'scanning')
)

// Load saved scan config from localStorage
function loadScanConfig() {
  const saved = localStorage.getItem(SCAN_CONFIG_KEY)
  if (saved) {
    try {
      scanConfig.value = { ...scanConfig.value, ...JSON.parse(saved) }
    } catch (e) {
      console.warn('Failed to parse saved scan config')
    }
  }
}

// Save scan config to localStorage
function saveScanConfig() {
  localStorage.setItem(SCAN_CONFIG_KEY, JSON.stringify(scanConfig.value))
}

// Load saved analyzer config from localStorage
function loadAnalyzerConfig() {
  const saved = localStorage.getItem(ANALYZER_CONFIG_KEY)
  if (saved) {
    try {
      analyzerConfig.value = { ...analyzerConfig.value, ...JSON.parse(saved) }
    } catch (e) {
      console.warn('Failed to parse saved analyzer config')
    }
  }
}

// Save analyzer config to localStorage
function saveAnalyzerConfig() {
  localStorage.setItem(ANALYZER_CONFIG_KEY, JSON.stringify(analyzerConfig.value))
}

// Fetch available package managers
async function fetchPackageManagers() {
  try {
    const res = await api.getPackageManagers()
    packageManagers.value = res.data.packageManagers
  } catch (e) {
    console.error('Failed to fetch package managers', e)
  }
}

// Group package managers by category for display
const packageManagerOptions = computed(() => {
  const groups: Record<string, PackageManagerInfo[]> = {}
  packageManagers.value.forEach(pm => {
    if (!groups[pm.category]) groups[pm.category] = []
    groups[pm.category].push(pm)
  })
  return Object.entries(groups).map(([category, items]) => ({
    label: category,
    items: items
  }))
})

// Download ORT config.yml
async function downloadOrtConfig() {
  try {
    const res = await api.generateOrtConfig({
      allowDynamicVersions: analyzerConfig.value.allowDynamicVersions,
      skipExcluded: analyzerConfig.value.skipExcluded,
      disabledPackageManagers: analyzerConfig.value.disabledPackageManagers
    })
    const blob = new Blob([res.data.configYml], { type: 'text/yaml' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = res.data.filename
    a.click()
    URL.revokeObjectURL(url)
    toast.add({
      severity: 'success',
      summary: 'Config Downloaded',
      detail: 'ORT config.yml saved successfully',
      life: 3000
    })
  } catch (e) {
    console.error('Failed to download ORT config', e)
    toast.add({
      severity: 'error',
      summary: 'Download Failed',
      detail: 'Failed to download ORT config',
      life: 5000
    })
  }
}

// Format duration from timestamps
function formatDuration(startedAt: string | null, completedAt: string | null): string {
  if (!startedAt) return '-'

  const start = new Date(startedAt).getTime()
  const end = completedAt ? new Date(completedAt).getTime() : Date.now()
  const durationMs = end - start

  if (durationMs < 0) return '-'

  const seconds = Math.floor(durationMs / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)

  if (hours > 0) {
    return `${hours}h ${minutes % 60}m ${seconds % 60}s`
  } else if (minutes > 0) {
    return `${minutes}m ${seconds % 60}s`
  } else {
    return `${seconds}s`
  }
}

async function fetchData() {
  loading.value = true
  try {
    const [projectRes, scansRes] = await Promise.all([
      api.getProject(route.params.id as string),
      api.listScans({ projectId: route.params.id as string, pageSize: 20 })
    ])
    project.value = projectRes.data
    scans.value = scansRes.data.scans
  } catch (e) {
    console.error('Failed to fetch project data', e)
  } finally {
    loading.value = false
  }
}

async function refreshScans() {
  if (!project.value) return
  try {
    const scansRes = await api.listScans({ projectId: project.value.id, pageSize: 20 })
    scans.value = scansRes.data.scans
  } catch (e) {
    console.error('Failed to refresh scans', e)
  }
}

function startPolling() {
  if (pollInterval) return // Already polling

  pollInterval = window.setInterval(async () => {
    if (!hasActiveScans.value) {
      stopPolling()
      return
    }
    await refreshScans()
  }, POLL_INTERVAL_MS)
}

function stopPolling() {
  if (pollInterval) {
    clearInterval(pollInterval)
    pollInterval = null
  }
}

async function triggerScan(demoMode = false) {
  if (!project.value) return
  scanning.value = true

  // Save user preferences
  saveScanConfig()
  saveAnalyzerConfig()

  try {
    if (demoMode) {
      // Demo mode: fixed config
      await api.triggerScan({
        projectId: project.value.id,
        enableAi: true,
        enableSourceScan: false,
        parallelAiCalls: true,
        demoMode: true
      })
      toast.add({
        severity: 'success',
        summary: 'Demo Scan Started',
        detail: 'Demo scan started with default settings',
        life: 3000
      })
    } else {
      // Normal scan: use user config including analyzer settings
      await api.triggerScan({
        projectId: project.value.id,
        enableAi: scanConfig.value.enableAi,
        enableSpdx: scanConfig.value.enableSpdx,
        enableSourceScan: scanConfig.value.enableSourceScan,
        parallelAiCalls: scanConfig.value.parallelAiCalls,
        demoMode: false,
        // Analyzer configuration
        allowDynamicVersions: analyzerConfig.value.allowDynamicVersions,
        skipExcluded: analyzerConfig.value.skipExcluded,
        disabledPackageManagers: analyzerConfig.value.disabledPackageManagers
      })
      const configSummary = [
        scanConfig.value.enableAi ? 'AI' : null,
        scanConfig.value.enableSourceScan ? 'Source Scan' : null,
        scanConfig.value.parallelAiCalls && scanConfig.value.enableAi ? 'Parallel' : null
      ].filter(Boolean).join(', ')
      toast.add({
        severity: 'success',
        summary: 'Scan Started',
        detail: configSummary ? `Scan started with: ${configSummary}` : 'Scan started',
        life: 3000
      })
    }
    await refreshScans()
    startPolling()
  } catch (e) {
    console.error('Failed to trigger scan', e)
    toast.add({
      severity: 'error',
      summary: 'Scan Failed',
      detail: 'Failed to start scan',
      life: 5000
    })
  } finally {
    scanning.value = false
  }
}

function getStatusClass(status: string) {
  switch (status) {
    case 'complete': return 'status-success'
    case 'scanning': return 'status-warning'
    case 'failed': return 'status-error'
    default: return 'status-pending'
  }
}

// Watch for active scans and manage polling
watch(hasActiveScans, (hasActive) => {
  if (hasActive) {
    startPolling()
  } else {
    stopPolling()
  }
})

onMounted(() => {
  loadScanConfig()
  loadAnalyzerConfig()
  fetchPackageManagers()
  fetchData()
})

onUnmounted(() => {
  stopPolling()
})
</script>

<template>
  <div class="project-detail">
    <div v-if="loading" class="loading">
      <i class="pi pi-spin pi-spinner"></i> Loading...
    </div>

    <template v-else-if="project">
      <header class="page-header">
        <div>
          <div class="breadcrumb">
            <RouterLink to="/projects">Projects</RouterLink>
            <i class="pi pi-angle-right"></i>
            <span>{{ project.name }}</span>
          </div>
          <h1>{{ project.name }}</h1>
          <div class="project-meta">
            <span v-if="project.repositoryUrl">
              <i class="pi pi-github"></i> {{ project.repositoryUrl }}
            </span>
            <span>
              <i class="pi pi-code-branch"></i> {{ project.defaultBranch }}
            </span>
          </div>
        </div>
      </header>

      <!-- Scan Configuration -->
      <div class="section scan-config-section">
        <h2>Scan Configuration</h2>
        <div class="scan-config-panel">
          <div class="config-row">
            <div class="config-info">
              <span class="config-label">Enable AI Analysis</span>
              <span class="config-description">Use Claude to resolve unknown licenses</span>
            </div>
            <InputSwitch v-model="scanConfig.enableAi" />
          </div>

          <div class="config-row">
            <div class="config-info">
              <span class="config-label">Enable SPDX Validation</span>
              <span class="config-description">Validate licenses against SPDX database</span>
            </div>
            <InputSwitch v-model="scanConfig.enableSpdx" />
          </div>

          <div class="config-row">
            <div class="config-info">
              <span class="config-label">Source Code Scan</span>
              <span class="config-description">Deep scan with ScanCode (slower)</span>
            </div>
            <InputSwitch v-model="scanConfig.enableSourceScan" />
          </div>

          <div class="config-row" :class="{ disabled: !scanConfig.enableAi }">
            <div class="config-info">
              <span class="config-label">Parallel AI Processing</span>
              <span class="config-description">Process packages concurrently</span>
            </div>
            <InputSwitch
              v-model="scanConfig.parallelAiCalls"
              :disabled="!scanConfig.enableAi"
            />
          </div>
        </div>

        <div class="scan-actions">
          <button
            class="btn btn-secondary"
            @click="triggerScan(true)"
            :disabled="scanning"
          >
            <i class="pi pi-play"></i> Demo Scan
          </button>
          <button
            class="btn btn-primary"
            @click="triggerScan(false)"
            :disabled="scanning || !project.repositoryUrl"
          >
            <i class="pi pi-refresh" :class="{ 'pi-spin': scanning }"></i>
            {{ scanning ? 'Starting...' : 'Run Scan' }}
          </button>
        </div>
      </div>

      <!-- Analyzer Configuration -->
      <div class="section analyzer-config-section">
        <div class="section-header clickable" @click="showAdvancedConfig = !showAdvancedConfig">
          <h2>
            <i :class="['pi', showAdvancedConfig ? 'pi-chevron-down' : 'pi-chevron-right']"></i>
            Analyzer Configuration
          </h2>
          <span class="section-subtitle">Configure ORT analyzer behavior</span>
        </div>

        <div v-show="showAdvancedConfig" class="config-content">
          <div class="scan-config-panel">
            <div class="config-row">
              <div class="config-info">
                <span class="config-label">Allow Dynamic Versions</span>
                <span class="config-description">
                  Analyze projects with version ranges (^1.0.0) even without lockfiles
                </span>
              </div>
              <InputSwitch v-model="analyzerConfig.allowDynamicVersions" />
            </div>

            <div class="config-row">
              <div class="config-info">
                <span class="config-label">Skip Excluded Dependencies</span>
                <span class="config-description">
                  Skip dependencies marked as excluded in .ort.yml configuration
                </span>
              </div>
              <InputSwitch v-model="analyzerConfig.skipExcluded" />
            </div>

            <div class="config-row config-row-vertical">
              <div class="config-info">
                <span class="config-label">Disabled Package Managers</span>
                <span class="config-description">
                  Select package managers to exclude from analysis
                </span>
              </div>
              <div class="package-manager-select">
                <MultiSelect
                  v-model="analyzerConfig.disabledPackageManagers"
                  :options="packageManagerOptions"
                  optionLabel="displayName"
                  optionValue="name"
                  optionGroupLabel="label"
                  optionGroupChildren="items"
                  placeholder="Select package managers to disable"
                  display="chip"
                  :filter="true"
                  filterPlaceholder="Search..."
                  class="w-full"
                >
                  <template #option="{ option }">
                    <div class="pm-option">
                      <span class="pm-name">{{ option.displayName }}</span>
                      <span class="pm-patterns">{{ option.filePatterns.join(', ') }}</span>
                    </div>
                  </template>
                </MultiSelect>
              </div>
            </div>
          </div>

          <div class="config-actions">
            <button class="btn btn-outline" @click="downloadOrtConfig">
              <i class="pi pi-download"></i> Download ORT Config
            </button>
          </div>
        </div>
      </div>

      <div class="section">
        <div class="section-header">
          <h2>Scan History</h2>
          <span v-if="hasActiveScans" class="scanning-indicator">
            <i class="pi pi-spin pi-spinner"></i> Scanning...
          </span>
        </div>

        <div v-if="scans.length === 0" class="empty-state">
          <p>No scans yet. Run your first scan to see results here.</p>
        </div>

        <table v-else class="data-table">
          <thead>
            <tr>
              <th>Status</th>
              <th>Dependencies</th>
              <th>Resolved</th>
              <th>Unresolved</th>
              <th>AI Resolved</th>
              <th>SPDX Resolved</th>
              <th>Duration</th>
              <th>Completed</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="scan in scans" :key="scan.id">
              <td>
                <span :class="['status-badge', getStatusClass(scan.status)]">
                  <i v-if="scan.status === 'scanning'" class="pi pi-spin pi-spinner status-spinner"></i>
                  {{ scan.status }}
                </span>
              </td>
              <td>{{ scan.totalDependencies }}</td>
              <td>{{ scan.resolvedLicenses }}</td>
              <td>{{ scan.unresolvedLicenses }}</td>
              <td>{{ scan.aiResolvedLicenses }}</td>
              <td>{{ scan.spdxResolvedLicenses || 0 }}</td>
              <td class="duration-cell">{{ formatDuration(scan.startedAt, scan.completedAt) }}</td>
              <td>{{ scan.completedAt ? new Date(scan.completedAt).toLocaleString() : '-' }}</td>
              <td>
                <RouterLink :to="`/scans/${scan.id}`" class="btn btn-small">
                  View
                </RouterLink>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </div>
</template>

<style scoped>
.project-detail {
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

.project-meta {
  display: flex;
  gap: 1.5rem;
  margin-top: 0.5rem;
  color: #64748b;
  font-size: 0.875rem;
}

.project-meta span {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.btn {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1.25rem;
  border-radius: 0.5rem;
  font-weight: 500;
  cursor: pointer;
  border: none;
  transition: all 0.2s;
}

.btn-primary {
  background: #3b82f6;
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background: #2563eb;
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-secondary {
  background: #e2e8f0;
  color: #475569;
}

.btn-secondary:hover:not(:disabled) {
  background: #cbd5e1;
}

.btn-secondary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-small {
  padding: 0.5rem 1rem;
  font-size: 0.875rem;
  background: #f1f5f9;
  color: #475569;
  text-decoration: none;
}

.section {
  background: white;
  border-radius: 1rem;
  padding: 1.5rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  margin-bottom: 1.5rem;
}

.section h2 {
  margin: 0 0 1rem;
  font-size: 1.25rem;
  color: #1e293b;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.section-header h2 {
  margin: 0;
}

.scanning-indicator {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: #d97706;
  font-size: 0.875rem;
  font-weight: 500;
}

/* Scan Configuration Styles */
.scan-config-section h2 {
  margin-bottom: 1rem;
}

.scan-config-panel {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  overflow: hidden;
  margin-bottom: 1.5rem;
}

.config-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1rem 1.25rem;
  border-bottom: 1px solid #e2e8f0;
  transition: background-color 0.2s;
}

.config-row:last-child {
  border-bottom: none;
}

.config-row:hover {
  background: #f1f5f9;
}

.config-row.disabled {
  opacity: 0.5;
}

.config-info {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.config-label {
  font-weight: 500;
  color: #334155;
}

.config-description {
  font-size: 0.85rem;
  color: #64748b;
}

.scan-actions {
  display: flex;
  gap: 0.75rem;
}

/* Data Table */
.data-table {
  width: 100%;
  border-collapse: collapse;
}

.data-table th,
.data-table td {
  padding: 0.75rem 1rem;
  text-align: left;
  border-bottom: 1px solid #e2e8f0;
}

.data-table th {
  font-weight: 600;
  color: #64748b;
  font-size: 0.875rem;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.25rem 0.75rem;
  border-radius: 9999px;
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: capitalize;
}

.status-spinner {
  font-size: 0.625rem;
}

.status-success { background: #d1fae5; color: #059669; }
.status-warning { background: #fef3c7; color: #d97706; }
.status-error { background: #fee2e2; color: #dc2626; }
.status-pending { background: #e2e8f0; color: #64748b; }

.duration-cell {
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Roboto Mono', monospace;
  font-size: 0.875rem;
  color: #475569;
}

.loading, .empty-state {
  text-align: center;
  padding: 3rem;
  color: #64748b;
}

/* Analyzer Configuration Styles */
.analyzer-config-section .section-header {
  cursor: pointer;
  user-select: none;
}

.analyzer-config-section .section-header.clickable:hover {
  opacity: 0.8;
}

.analyzer-config-section .section-header h2 {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.25rem;
}

.analyzer-config-section .section-header h2 i {
  font-size: 0.875rem;
  color: #64748b;
}

.section-subtitle {
  font-size: 0.875rem;
  color: #64748b;
  font-weight: normal;
}

.config-content {
  margin-top: 1rem;
}

.config-row-vertical {
  flex-direction: column;
  align-items: flex-start;
  gap: 0.75rem;
}

.package-manager-select {
  width: 100%;
}

.package-manager-select :deep(.p-multiselect) {
  width: 100%;
}

.pm-option {
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
}

.pm-name {
  font-weight: 500;
  color: #334155;
}

.pm-patterns {
  font-size: 0.75rem;
  color: #94a3b8;
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Roboto Mono', monospace;
}

.config-actions {
  display: flex;
  gap: 0.75rem;
  margin-top: 1rem;
}

.btn-outline {
  background: transparent;
  border: 1px solid #e2e8f0;
  color: #64748b;
}

.btn-outline:hover {
  background: #f1f5f9;
  color: #334155;
}

.w-full {
  width: 100%;
}
</style>
