<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter, RouterLink } from 'vue-router'
import { api, type CurationSession, type ScanSummary } from '@/api/client'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Badge from 'primevue/badge'
import Dialog from 'primevue/dialog'
import Dropdown from 'primevue/dropdown'
import ProgressBar from 'primevue/progressbar'
import { useToast } from 'primevue/usetoast'

const router = useRouter()
const toast = useToast()

const sessions = ref<CurationSession[]>([])
const scans = ref<ScanSummary[]>([])
const loading = ref(true)
const showStartDialog = ref(false)
const selectedScanId = ref<string | null>(null)
const starting = ref(false)

// Computed: scans eligible for curation (complete, not already curated)
const eligibleScans = computed(() => {
  const curatedScanIds = new Set(sessions.value.map(s => s.scanId))
  return scans.value
    .filter(s => s.status === 'complete' && !curatedScanIds.has(s.id))
    .map(s => ({
      label: `${s.id.slice(0, 8)}... - ${s.totalDependencies} deps (${new Date(s.completedAt || s.startedAt || '').toLocaleDateString()})`,
      value: s.id
    }))
})

async function fetchData() {
  loading.value = true
  try {
    const [scansRes] = await Promise.all([
      api.listScans({ status: 'complete', pageSize: 100 })
    ])
    scans.value = scansRes.data.scans

    // Fetch curation sessions for each completed scan
    const sessionPromises = scans.value.map(async (scan) => {
      try {
        const res = await api.getCurationSessionByScan(scan.id)
        return res.data
      } catch {
        return null
      }
    })

    const sessionResults = await Promise.all(sessionPromises)
    sessions.value = sessionResults.filter((s): s is CurationSession => s !== null)
  } catch (e) {
    console.error('Failed to fetch curation data', e)
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to load curation sessions', life: 3000 })
  } finally {
    loading.value = false
  }
}

async function startCuration() {
  if (!selectedScanId.value) return

  starting.value = true
  try {
    const response = await api.startCurationSession(selectedScanId.value)
    sessions.value.unshift(response.data)
    showStartDialog.value = false
    selectedScanId.value = null
    toast.add({ severity: 'success', summary: 'Success', detail: 'Curation session started', life: 3000 })
    router.push(`/curations/${response.data.scanId}`)
  } catch (e) {
    console.error('Failed to start curation', e)
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to start curation session', life: 3000 })
  } finally {
    starting.value = false
  }
}

function getStatusSeverity(status: string): 'success' | 'warning' | 'info' | 'danger' | 'secondary' {
  switch (status) {
    case 'APPROVED': return 'success'
    case 'COMPLETED': return 'info'
    case 'IN_PROGRESS': return 'warning'
    default: return 'secondary'
  }
}

function getCompletionPercentage(session: CurationSession): number {
  if (session.statistics.total === 0) return 100
  return Math.round(((session.statistics.total - session.statistics.pending) / session.statistics.total) * 100)
}

function getScanInfo(scanId: string): ScanSummary | undefined {
  return scans.value.find(s => s.id === scanId)
}

onMounted(fetchData)
</script>

<template>
  <div class="curations-view">
    <header class="page-header">
      <div>
        <h1>License Curation</h1>
        <p class="subtitle">Review and approve AI-suggested licenses</p>
      </div>
      <Button
        label="Start New Curation"
        icon="pi pi-plus"
        @click="showStartDialog = true"
        :disabled="eligibleScans.length === 0"
      />
    </header>

    <div v-if="loading" class="loading">
      <i class="pi pi-spin pi-spinner"></i> Loading curation sessions...
    </div>

    <template v-else>
      <!-- Summary Cards -->
      <div class="summary-cards">
        <div class="card">
          <div class="card-value">{{ sessions.length }}</div>
          <div class="card-label">Total Sessions</div>
        </div>
        <div class="card success">
           <div class="card-value">{{ sessions.filter(s => s.status === 'APPROVED').length }}</div>
           <div class="card-label">Approved</div>
         </div>
         <div class="card warning">
           <div class="card-value">{{ sessions.filter(s => s.status === 'IN_PROGRESS').length }}</div>
           <div class="card-label">In Progress</div>
         </div>
         <div class="card info">
           <div class="card-value">{{ sessions.reduce((sum, s) => sum + s.statistics.total, 0) }}</div>
           <div class="card-label">Total Items</div>
         </div>
      </div>

      <!-- Sessions Table -->
      <div class="section" v-if="sessions.length > 0">
        <DataTable
          :value="sessions"
          stripedRows
          showGridlines
          class="p-datatable-sm"
          :paginator="sessions.length > 10"
          :rows="10"
          emptyMessage="No curation sessions yet. Start one by selecting a completed scan."
        >
          <Column header="Scan" style="min-width: 200px">
            <template #body="{ data }">
              <div class="scan-info">
                <RouterLink :to="`/scans/${data.scanId}`" class="scan-link">
                  {{ data.scanId.slice(0, 8) }}...
                </RouterLink>
                <div v-if="getScanInfo(data.scanId)" class="scan-meta">
                  {{ getScanInfo(data.scanId)?.totalDependencies }} dependencies
                </div>
              </div>
            </template>
          </Column>

          <Column field="status" header="Status" style="width: 130px" sortable>
            <template #body="{ data }">
              <Badge :value="data.status" :severity="getStatusSeverity(data.status)" />
            </template>
          </Column>

          <Column header="Progress" style="width: 200px">
            <template #body="{ data }">
              <div class="progress-cell">
                <ProgressBar :value="getCompletionPercentage(data)" :showValue="false" style="height: 8px" />
                <span class="progress-text">
                  {{ data.statistics.total - data.statistics.pending }} / {{ data.statistics.total }}
                </span>
              </div>
            </template>
          </Column>

          <Column header="Decisions" style="width: 200px">
            <template #body="{ data }">
              <div class="decision-stats">
                <span class="stat accepted" title="Accepted">
                  <i class="pi pi-check"></i> {{ data.statistics.accepted }}
                </span>
                <span class="stat rejected" title="Rejected">
                  <i class="pi pi-times"></i> {{ data.statistics.rejected }}
                </span>
                <span class="stat modified" title="Modified">
                  <i class="pi pi-pencil"></i> {{ data.statistics.modified }}
                </span>
              </div>
            </template>
          </Column>

          <Column header="Approval" style="width: 180px">
            <template #body="{ data }">
              <div v-if="data.approval" class="approval-info">
                <i class="pi pi-check-circle"></i>
                <span>{{ data.approval.approvedBy }}</span>
                <div class="approval-date">{{ new Date(data.approval.approvedAt).toLocaleDateString() }}</div>
              </div>
              <span v-else class="not-approved">Not approved</span>
            </template>
          </Column>

          <Column field="createdAt" header="Created" style="width: 140px" sortable>
            <template #body="{ data }">
              {{ new Date(data.createdAt).toLocaleDateString() }}
            </template>
          </Column>

          <Column header="Actions" style="width: 100px">
            <template #body="{ data }">
              <Button
                icon="pi pi-arrow-right"
                severity="secondary"
                text
                rounded
                @click="router.push(`/curations/${data.scanId}`)"
                title="Open session"
              />
            </template>
          </Column>
        </DataTable>
      </div>
    </template>

    <!-- Start Curation Dialog -->
    <Dialog
      v-model:visible="showStartDialog"
      header="Start New Curation Session"
      :modal="true"
      :style="{ width: '500px' }"
    >
      <div class="dialog-content">
        <p>Select a completed scan to start a curation session. You'll be able to review and approve AI-suggested licenses for each dependency.</p>

        <div class="field">
          <label>Select Scan</label>
          <Dropdown
            v-model="selectedScanId"
            :options="eligibleScans"
            optionLabel="label"
            optionValue="value"
            placeholder="Choose a scan..."
            class="w-full"
            :disabled="eligibleScans.length === 0"
          />
        </div>

        <div v-if="eligibleScans.length === 0" class="no-scans-message">
          <i class="pi pi-info-circle"></i>
          <span>No eligible scans available. All completed scans already have curation sessions.</span>
        </div>
      </div>

      <template #footer>
        <Button label="Cancel" severity="secondary" @click="showStartDialog = false" />
        <Button
          label="Start Curation"
          icon="pi pi-play"
          @click="startCuration"
          :loading="starting"
          :disabled="!selectedScanId"
        />
      </template>
    </Dialog>
  </div>
</template>

<style scoped>
.curations-view {
  max-width: 1400px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 2rem;
}

.page-header h1 {
  margin: 0;
  font-size: 2rem;
  color: #1e293b;
}

.subtitle {
  color: #64748b;
  margin: 0.5rem 0 0;
}

/* Summary Cards */
.summary-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 1rem;
  margin-bottom: 1.5rem;
}

.card {
  background: white;
  padding: 1.5rem;
  border-radius: 1rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  text-align: center;
}

.card.success {
  border-left: 4px solid #10b981;
}

.card.warning {
  border-left: 4px solid #f59e0b;
}

.card.info {
  border-left: 4px solid #3b82f6;
}

.card-value {
  font-size: 2rem;
  font-weight: 700;
  color: #1e293b;
}

.card-label {
  color: #64748b;
  font-size: 0.875rem;
  margin-top: 0.25rem;
}

/* Section */
.section {
  background: white;
  border-radius: 1rem;
  padding: 1.5rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

/* Table cells */
.scan-info {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.scan-link {
  font-family: monospace;
  color: #3b82f6;
  text-decoration: none;
  font-weight: 500;
}

.scan-link:hover {
  text-decoration: underline;
}

.scan-meta {
  font-size: 0.75rem;
  color: #64748b;
}

.progress-cell {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.progress-text {
  font-size: 0.75rem;
  color: #64748b;
}

.decision-stats {
  display: flex;
  gap: 0.75rem;
}

.decision-stats .stat {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.875rem;
}

.decision-stats .accepted {
  color: #10b981;
}

.decision-stats .rejected {
  color: #ef4444;
}

.decision-stats .modified {
  color: #8b5cf6;
}

.approval-info {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.125rem;
  font-size: 0.875rem;
  color: #10b981;
}

.approval-info i {
  margin-right: 0.25rem;
}

.approval-date {
  font-size: 0.75rem;
  color: #64748b;
}

.not-approved {
  color: #94a3b8;
  font-size: 0.875rem;
}

/* Dialog */
.dialog-content {
  padding: 1rem 0;
}

.dialog-content p {
  color: #64748b;
  margin-bottom: 1.5rem;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.field label {
  font-weight: 500;
  color: #374151;
}

.w-full {
  width: 100%;
}

.no-scans-message {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  padding: 1rem;
  background: #fef3c7;
  border-radius: 0.5rem;
  margin-top: 1rem;
  color: #92400e;
  font-size: 0.875rem;
}

.no-scans-message i {
  margin-top: 0.125rem;
}

.loading {
  text-align: center;
  padding: 4rem;
  color: #64748b;
}

.empty-state {
  text-align: center;
  padding: 4rem;
  color: #64748b;
}

.empty-state i {
  font-size: 3rem;
  margin-bottom: 1rem;
  display: block;
}

.empty-state p {
  font-size: 1.1rem;
  margin: 0;
}
</style>
