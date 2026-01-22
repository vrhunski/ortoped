<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import { api, type Project, type ScanSummary } from '@/api/client'

const route = useRoute()
const project = ref<Project | null>(null)
const scans = ref<ScanSummary[]>([])
const loading = ref(true)
const scanning = ref(false)

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

async function triggerScan(demoMode = false) {
  if (!project.value) return
  scanning.value = true
  try {
    await api.triggerScan({
      projectId: project.value.id,
      enableAi: true,
      demoMode
    })
    // Refresh scans list
    const scansRes = await api.listScans({ projectId: project.value.id, pageSize: 20 })
    scans.value = scansRes.data.scans
  } catch (e) {
    console.error('Failed to trigger scan', e)
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

onMounted(fetchData)
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
        <div class="header-actions">
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
            <i class="pi pi-refresh"></i>
            {{ scanning ? 'Starting...' : 'Run Scan' }}
          </button>
        </div>
      </header>

      <div class="section">
        <h2>Scan History</h2>

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
              <th>Completed</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="scan in scans" :key="scan.id">
              <td>
                <span :class="['status-badge', getStatusClass(scan.status)]">
                  {{ scan.status }}
                </span>
              </td>
              <td>{{ scan.totalDependencies }}</td>
              <td>{{ scan.resolvedLicenses }}</td>
              <td>{{ scan.unresolvedLicenses }}</td>
              <td>{{ scan.aiResolvedLicenses }}</td>
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

.header-actions {
  display: flex;
  gap: 0.75rem;
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
}

.section h2 {
  margin: 0 0 1rem;
  font-size: 1.25rem;
  color: #1e293b;
}

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
  display: inline-block;
  padding: 0.25rem 0.75rem;
  border-radius: 9999px;
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: capitalize;
}

.status-success { background: #d1fae5; color: #059669; }
.status-warning { background: #fef3c7; color: #d97706; }
.status-error { background: #fee2e2; color: #dc2626; }
.status-pending { background: #e2e8f0; color: #64748b; }

.loading, .empty-state {
  text-align: center;
  padding: 3rem;
  color: #64748b;
}
</style>
