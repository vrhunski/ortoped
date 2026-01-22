<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api, type ScanSummary } from '@/api/client'
import { RouterLink } from 'vue-router'

const scans = ref<ScanSummary[]>([])
const loading = ref(true)
const page = ref(1)
const total = ref(0)

async function fetchScans() {
  loading.value = true
  try {
    const response = await api.listScans({ page: page.value, pageSize: 20 })
    scans.value = response.data.scans
    total.value = response.data.total
  } catch (e) {
    console.error('Failed to fetch scans', e)
  } finally {
    loading.value = false
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

onMounted(fetchScans)
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
