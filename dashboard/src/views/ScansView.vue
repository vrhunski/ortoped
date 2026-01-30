<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { api, type Project, type ScanSummary } from '@/api/client'
import { RouterLink } from 'vue-router'

const scans = ref<ScanSummary[]>([])
const loading = ref(true)
const page = ref(1)
const total = ref(0)

// Project name cache (resolved via projectId)
const projectById = ref<Record<string, Project | null>>({})
const projectLoadFailedById = ref<Record<string, true>>({})

// Live duration timer
const nowMs = ref(Date.now())
let durationInterval: number | null = null

async function fetchScans() {
  loading.value = true
  try {
    const response = await api.listScans({ page: page.value, pageSize: 20 })
    scans.value = response.data.scans
    total.value = response.data.total

    await hydrateProjects(scans.value)
  } catch (e) {
    console.error('Failed to fetch scans', e)
  } finally {
    loading.value = false
  }
}

async function hydrateProjects(currentScans: ScanSummary[]) {
  const uniqueProjectIds = Array.from(
    new Set(
      currentScans
        .map(s => s.projectId)
        .filter((id): id is string => typeof id === 'string' && id.length > 0)
    )
  )

  const missing = uniqueProjectIds.filter(
    id => !projectById.value[id] && !projectLoadFailedById.value[id]
  )

  if (missing.length === 0) return

  const results = await Promise.allSettled(missing.map(id => api.getProject(id)))
  results.forEach((result, idx) => {
    const projectId = missing[idx]
    if (result.status === 'fulfilled') {
      projectById.value = { ...projectById.value, [projectId]: result.value.data }
    } else {
      projectLoadFailedById.value = { ...projectLoadFailedById.value, [projectId]: true }
    }
  })
}

function getProjectLabel(projectId: string | null): string {
  if (!projectId) return '-'
  const project = projectById.value[projectId]
  if (project) return project.name
  if (projectLoadFailedById.value[projectId]) return 'Unknown project'
  return 'Loading…'
}

function formatDurationMs(durationMs: number): string {
  if (!Number.isFinite(durationMs) || durationMs < 0) return '-'
  const seconds = Math.floor(durationMs / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)

  if (hours > 0) {
    return `${hours}h ${minutes % 60}m ${seconds % 60}s`
  } else if (minutes > 0) {
    return `${minutes}m ${seconds % 60}s`
  }
  return `${seconds}s`
}

function getScanDuration(scan: ScanSummary): string {
  if (!scan.startedAt) return '-'

  const startMs = new Date(scan.startedAt).getTime()
  const endMs = scan.completedAt
    ? new Date(scan.completedAt).getTime()
    : (scan.status === 'scanning' ? nowMs.value : null)

  if (!endMs) return '-'
  return formatDurationMs(endMs - startMs)
}

function startDurationTimer() {
  if (durationInterval != null) return
  durationInterval = window.setInterval(() => {
    nowMs.value = Date.now()
  }, 1000)
}

function stopDurationTimer() {
  if (durationInterval == null) return
  window.clearInterval(durationInterval)
  durationInterval = null
}

function getStatusClass(status: string) {
  switch (status) {
    case 'complete': return 'status-success'
    case 'scanning': return 'status-warning'
    case 'failed': return 'status-error'
    default: return 'status-pending'
  }
}

onMounted(fetchScans)

watch(
  () => scans.value.some(s => s.status === 'scanning' && !!s.startedAt && !s.completedAt),
  (hasLiveDurations) => {
    if (hasLiveDurations) startDurationTimer()
    else stopDurationTimer()
  },
  { immediate: true }
)

onUnmounted(() => {
  stopDurationTimer()
})
</script>

<template>
  <div class="scans-page">
    <header class="page-header">
      <h1>Scans</h1>
      <p class="subtitle">View all license scans across projects</p>
    </header>

    <div v-if="loading" class="loading">
      <i class="pi pi-spin pi-spinner"></i> Loading scans...
    </div>

    <div v-else-if="scans.length === 0" class="empty-state">
      <i class="pi pi-search"></i>
      <h3>No scans yet</h3>
      <p>Go to a project and run your first scan.</p>
    </div>

    <div v-else class="section">
      <table class="data-table">
        <thead>
          <tr>
            <th>Project</th>
            <th>Status</th>
            <th>Duration</th>
            <th>Dependencies</th>
            <th>Resolved</th>
            <th>Unresolved</th>
            <th>AI Resolved</th>
            <th>SPDX Resolved</th>
            <th>Completed</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="scan in scans" :key="scan.id">
            <td class="project-cell">
              <RouterLink
                v-if="scan.projectId"
                :to="`/projects/${scan.projectId}`"
                class="project-link"
                :title="getProjectLabel(scan.projectId)"
              >
                {{ getProjectLabel(scan.projectId) }}
              </RouterLink>
              <span v-else class="muted">-</span>
            </td>
            <td>
              <span :class="['status-badge', getStatusClass(scan.status)]">
                {{ scan.status }}
              </span>
            </td>
            <td class="mono">{{ getScanDuration(scan) }}</td>
            <td>{{ scan.totalDependencies }}</td>
            <td>{{ scan.resolvedLicenses }}</td>
            <td>{{ scan.unresolvedLicenses }}</td>
            <td>{{ scan.aiResolvedLicenses }}</td>
            <td>{{ scan.spdxResolvedLicenses || 0 }}</td>
            <td>{{ scan.completedAt ? new Date(scan.completedAt).toLocaleString() : '-' }}</td>
            <td>
              <RouterLink :to="`/scans/${scan.id}`" class="btn btn-small">
                View Details
              </RouterLink>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.scans-page { max-width: 1400px; margin: 0 auto; }
.page-header { margin-bottom: 2rem; }
.page-header h1 { margin: 0; font-size: 2rem; color: #1e293b; }
.subtitle { color: #64748b; margin-top: 0.25rem; }

.section {
  background: white;
  border-radius: 1rem;
  padding: 1.5rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.data-table { width: 100%; border-collapse: collapse; }
.data-table th, .data-table td { padding: 0.75rem 1rem; text-align: left; border-bottom: 1px solid #e2e8f0; }
.data-table th { font-weight: 600; color: #64748b; font-size: 0.875rem; }

.project-cell { max-width: 260px; }
.project-link {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #0f172a;
  font-weight: 600;
  text-decoration: none;
}
.project-link:hover { text-decoration: underline; }

.mono { font-variant-numeric: tabular-nums; white-space: nowrap; }
.muted { color: #94a3b8; }

.status-badge { display: inline-block; padding: 0.25rem 0.75rem; border-radius: 9999px; font-size: 0.75rem; font-weight: 600; text-transform: capitalize; }
.status-success { background: #d1fae5; color: #059669; }
.status-warning { background: #fef3c7; color: #d97706; }
.status-error { background: #fee2e2; color: #dc2626; }
.status-pending { background: #e2e8f0; color: #64748b; }

.btn-small { display: inline-block; padding: 0.5rem 1rem; font-size: 0.875rem; background: #f1f5f9; color: #475569; text-decoration: none; border-radius: 0.5rem; }

.loading, .empty-state { text-align: center; padding: 4rem; color: #64748b; }
.empty-state i { font-size: 3rem; opacity: 0.3; margin-bottom: 1rem; }
.empty-state h3 { color: #1e293b; margin-bottom: 0.5rem; }
</style>
