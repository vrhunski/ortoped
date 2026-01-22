<script setup lang="ts">
import { onMounted } from 'vue'
import { useDashboardStore } from '@/stores/dashboard'
import { RouterLink } from 'vue-router'

const store = useDashboardStore()

onMounted(() => {
  store.fetchDashboardData()
})

function getStatusClass(status: string) {
  switch (status) {
    case 'complete': return 'status-success'
    case 'scanning': return 'status-warning'
    case 'failed': return 'status-error'
    default: return 'status-pending'
  }
}
</script>

<template>
  <div class="dashboard">
    <header class="page-header">
      <h1>Dashboard</h1>
      <p class="subtitle">License compliance overview</p>
    </header>

    <!-- Stats Cards -->
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-icon projects">
          <i class="pi pi-folder"></i>
        </div>
        <div class="stat-content">
          <span class="stat-value">{{ store.stats.totalProjects }}</span>
          <span class="stat-label">Projects</span>
        </div>
      </div>

      <div class="stat-card">
        <div class="stat-icon scans">
          <i class="pi pi-search"></i>
        </div>
        <div class="stat-content">
          <span class="stat-value">{{ store.stats.totalScans }}</span>
          <span class="stat-label">Total Scans</span>
        </div>
      </div>

      <div class="stat-card">
        <div class="stat-icon resolved">
          <i class="pi pi-check-circle"></i>
        </div>
        <div class="stat-content">
          <span class="stat-value">{{ store.stats.resolutionRate }}%</span>
          <span class="stat-label">License Resolution</span>
        </div>
      </div>

      <div class="stat-card">
        <div class="stat-icon ai">
          <i class="pi pi-bolt"></i>
        </div>
        <div class="stat-content">
          <span class="stat-value">{{ store.stats.aiResolvedLicenses }}</span>
          <span class="stat-label">AI Resolved</span>
        </div>
      </div>
    </div>

    <!-- Recent Scans -->
    <div class="section">
      <div class="section-header">
        <h2>Recent Scans</h2>
        <RouterLink to="/scans" class="view-all">View All</RouterLink>
      </div>

      <div v-if="store.loading" class="loading">
        <i class="pi pi-spin pi-spinner"></i> Loading...
      </div>

      <div v-else-if="store.recentScans.length === 0" class="empty-state">
        <i class="pi pi-inbox"></i>
        <p>No scans yet. Create a project and run your first scan.</p>
        <RouterLink to="/projects" class="btn btn-primary">Create Project</RouterLink>
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
          <tr v-for="scan in store.recentScans" :key="scan.id">
            <td>
              <span :class="['status-badge', getStatusClass(scan.status)]">
                {{ scan.status }}
              </span>
            </td>
            <td>{{ scan.totalDependencies }}</td>
            <td>{{ scan.resolvedLicenses }}</td>
            <td>{{ scan.unresolvedLicenses }}</td>
            <td>{{ scan.aiResolvedLicenses }}</td>
            <td>{{ scan.completedAt ? new Date(scan.completedAt).toLocaleDateString() : '-' }}</td>
            <td>
              <RouterLink :to="`/scans/${scan.id}`" class="btn btn-small">
                View Details
              </RouterLink>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Quick Stats -->
    <div class="section">
      <h2>License Overview</h2>
      <div class="license-stats">
        <div class="license-bar">
          <div
            class="bar-segment resolved"
            :style="{ width: `${store.stats.resolutionRate}%` }"
          ></div>
          <div
            class="bar-segment unresolved"
            :style="{ width: `${100 - store.stats.resolutionRate}%` }"
          ></div>
        </div>
        <div class="license-legend">
          <span class="legend-item resolved">
            <span class="dot"></span>
            Resolved ({{ store.stats.resolvedLicenses }})
          </span>
          <span class="legend-item unresolved">
            <span class="dot"></span>
            Unresolved ({{ store.stats.unresolvedLicenses }})
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dashboard {
  max-width: 1400px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 2rem;
}

.page-header h1 {
  margin: 0;
  font-size: 2rem;
  color: #1e293b;
}

.subtitle {
  color: #64748b;
  margin-top: 0.25rem;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 1.5rem;
  margin-bottom: 2rem;
}

.stat-card {
  background: white;
  border-radius: 1rem;
  padding: 1.5rem;
  display: flex;
  align-items: center;
  gap: 1rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.5rem;
}

.stat-icon.projects { background: #dbeafe; color: #3b82f6; }
.stat-icon.scans { background: #fce7f3; color: #ec4899; }
.stat-icon.resolved { background: #d1fae5; color: #10b981; }
.stat-icon.ai { background: #fef3c7; color: #f59e0b; }

.stat-content {
  display: flex;
  flex-direction: column;
}

.stat-value {
  font-size: 1.75rem;
  font-weight: 700;
  color: #1e293b;
}

.stat-label {
  color: #64748b;
  font-size: 0.875rem;
}

.section {
  background: white;
  border-radius: 1rem;
  padding: 1.5rem;
  margin-bottom: 1.5rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
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

.view-all {
  color: #3b82f6;
  text-decoration: none;
  font-size: 0.875rem;
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

.btn {
  display: inline-block;
  padding: 0.5rem 1rem;
  border-radius: 0.5rem;
  text-decoration: none;
  font-weight: 500;
  transition: all 0.2s;
}

.btn-primary {
  background: #3b82f6;
  color: white;
}

.btn-small {
  padding: 0.25rem 0.75rem;
  font-size: 0.875rem;
  background: #f1f5f9;
  color: #475569;
}

.btn-small:hover {
  background: #e2e8f0;
}

.loading, .empty-state {
  text-align: center;
  padding: 3rem;
  color: #64748b;
}

.empty-state i {
  font-size: 3rem;
  margin-bottom: 1rem;
  opacity: 0.5;
}

.license-stats {
  padding: 1rem 0;
}

.license-bar {
  display: flex;
  height: 24px;
  border-radius: 12px;
  overflow: hidden;
  margin-bottom: 1rem;
}

.bar-segment {
  height: 100%;
  transition: width 0.3s;
}

.bar-segment.resolved { background: #10b981; }
.bar-segment.unresolved { background: #f87171; }

.license-legend {
  display: flex;
  gap: 2rem;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.875rem;
  color: #64748b;
}

.legend-item .dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
}

.legend-item.resolved .dot { background: #10b981; }
.legend-item.unresolved .dot { background: #f87171; }
</style>
