<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import { api, type Scan, type Dependency } from '@/api/client'

const route = useRoute()
const scan = ref<Scan | null>(null)
const dependencies = ref<Dependency[]>([])
const loading = ref(true)
const page = ref(1)
const total = ref(0)
const filter = ref('')

const filteredDependencies = computed(() => {
  if (!filter.value) return dependencies.value
  const search = filter.value.toLowerCase()
  return dependencies.value.filter(d =>
    d.name.toLowerCase().includes(search) ||
    d.concludedLicense?.toLowerCase().includes(search)
  )
})

async function fetchData() {
  loading.value = true
  try {
    const scanRes = await api.getScan(route.params.id as string)
    scan.value = scanRes.data

    if (scan.value.status === 'complete') {
      const depsRes = await api.getDependencies(route.params.id as string, page.value, 100)
      dependencies.value = depsRes.data.dependencies
      total.value = depsRes.data.total
    }
  } catch (e) {
    console.error('Failed to fetch scan data', e)
  } finally {
    loading.value = false
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
  }
}

function getConfidenceClass(confidence: string) {
  switch (confidence) {
    case 'HIGH': return 'confidence-high'
    case 'MEDIUM': return 'confidence-medium'
    default: return 'confidence-low'
  }
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
          <button class="btn btn-secondary" @click="downloadSbom('cyclonedx-json')">
            <i class="pi pi-download"></i> Export SBOM
          </button>
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

      <!-- Scan complete - show dependencies -->
      <template v-else>
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

          <table class="data-table">
            <thead>
              <tr>
                <th>Package</th>
                <th>Version</th>
                <th>License</th>
                <th>Status</th>
                <th>AI Suggestion</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="dep in filteredDependencies" :key="dep.id">
                <td>
                  <strong>{{ dep.name }}</strong>
                  <div class="dep-id">{{ dep.id }}</div>
                </td>
                <td>{{ dep.version }}</td>
                <td>
                  <span class="license-badge">
                    {{ dep.concludedLicense || dep.declaredLicenses[0] || 'Unknown' }}
                  </span>
                </td>
                <td>
                  <span v-if="dep.isResolved" class="status-badge status-success">Resolved</span>
                  <span v-else class="status-badge status-warning">Unresolved</span>
                </td>
                <td>
                  <div v-if="dep.aiSuggestion" class="ai-suggestion">
                    <span class="suggested-license">{{ dep.aiSuggestion.suggestedLicense }}</span>
                    <span :class="['confidence', getConfidenceClass(dep.aiSuggestion.confidence)]">
                      {{ dep.aiSuggestion.confidence }}
                    </span>
                    <div class="reasoning">{{ dep.aiSuggestion.reasoning }}</div>
                  </div>
                  <span v-else class="no-suggestion">-</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>
    </template>
  </div>
</template>

<style scoped>
.scan-detail { max-width: 1400px; margin: 0 auto; }

.page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 2rem; }
.breadcrumb { display: flex; align-items: center; gap: 0.5rem; font-size: 0.875rem; color: #64748b; margin-bottom: 0.5rem; }
.breadcrumb a { color: #3b82f6; text-decoration: none; }
.page-header h1 { margin: 0; font-size: 2rem; color: #1e293b; }
.scan-meta { display: flex; align-items: center; gap: 1rem; margin-top: 0.5rem; color: #64748b; font-size: 0.875rem; }

.header-actions { display: flex; gap: 0.75rem; }
.btn { display: inline-flex; align-items: center; gap: 0.5rem; padding: 0.75rem 1.25rem; border-radius: 0.5rem; font-weight: 500; cursor: pointer; border: none; }
.btn-secondary { background: #e2e8f0; color: #475569; }

.section { background: white; border-radius: 1rem; padding: 1.5rem; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1); margin-bottom: 1.5rem; }
.section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; }
.section h2 { margin: 0; font-size: 1.25rem; color: #1e293b; }

.filter-input { padding: 0.5rem 1rem; border: 1px solid #e2e8f0; border-radius: 0.5rem; width: 250px; }

.data-table { width: 100%; border-collapse: collapse; }
.data-table th, .data-table td { padding: 0.75rem 1rem; text-align: left; border-bottom: 1px solid #e2e8f0; }
.data-table th { font-weight: 600; color: #64748b; font-size: 0.875rem; }

.dep-id { font-size: 0.75rem; color: #94a3b8; margin-top: 0.25rem; }

.license-badge { display: inline-block; padding: 0.25rem 0.5rem; background: #f1f5f9; border-radius: 0.25rem; font-size: 0.875rem; font-family: monospace; }

.status-badge { display: inline-block; padding: 0.25rem 0.75rem; border-radius: 9999px; font-size: 0.75rem; font-weight: 600; }
.status-success { background: #d1fae5; color: #059669; }
.status-warning { background: #fef3c7; color: #d97706; }

.ai-suggestion { font-size: 0.875rem; }
.suggested-license { font-weight: 600; color: #1e293b; }
.confidence { margin-left: 0.5rem; padding: 0.125rem 0.5rem; border-radius: 0.25rem; font-size: 0.75rem; }
.confidence-high { background: #d1fae5; color: #059669; }
.confidence-medium { background: #fef3c7; color: #d97706; }
.confidence-low { background: #fee2e2; color: #dc2626; }
.reasoning { color: #64748b; margin-top: 0.25rem; font-size: 0.75rem; }
.no-suggestion { color: #94a3b8; }

.scanning-state, .error-state { text-align: center; padding: 4rem; }
.scanning-state i, .error-state i { font-size: 3rem; margin-bottom: 1rem; }
.scanning-state i { color: #3b82f6; }
.error-state i { color: #dc2626; }
.scanning-state h3, .error-state h3 { margin: 0 0 0.5rem; color: #1e293b; }
.scanning-state p, .error-state p { color: #64748b; }

.loading { text-align: center; padding: 4rem; color: #64748b; }
</style>
