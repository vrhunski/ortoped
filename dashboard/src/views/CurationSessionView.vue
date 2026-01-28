<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { useRoute, useRouter, RouterLink } from 'vue-router'
import { api, type CurationSession, type CurationItem, type CurationDecision } from '@/api/client'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Badge from 'primevue/badge'
import Dropdown from 'primevue/dropdown'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import Dialog from 'primevue/dialog'
import ProgressBar from 'primevue/progressbar'
import { useToast } from 'primevue/usetoast'

const route = useRoute()
const router = useRouter()
const toast = useToast()

// Data
const session = ref<CurationSession | null>(null)
const items = ref<CurationItem[]>([])
const selectedItems = ref<CurationItem[]>([])
const loading = ref(true)
const total = ref(0)

// Filters
const statusFilter = ref<string | null>(null)
const priorityFilter = ref<string | null>(null)
const confidenceFilter = ref<string | null>(null)
const searchFilter = ref('')

// Dialog states
const showItemDetail = ref(false)
const showBulkAction = ref(false)
const showApproveDialog = ref(false)
const currentItem = ref<CurationItem | null>(null)

// Action states
const submitting = ref(false)
const approverName = ref('')
const approvalComment = ref('')

// Decision form
const decisionAction = ref<'ACCEPT' | 'REJECT' | 'MODIFY'>('ACCEPT')
const decisionLicense = ref('')
const decisionComment = ref('')

// Filter options
const statusOptions = [
  { label: 'All Statuses', value: null },
  { label: 'Pending', value: 'PENDING' },
  { label: 'Accepted', value: 'ACCEPTED' },
  { label: 'Rejected', value: 'REJECTED' },
  { label: 'Modified', value: 'MODIFIED' }
]

const priorityOptions = [
  { label: 'All Priorities', value: null },
  { label: 'High', value: 'HIGH' },
  { label: 'Medium', value: 'MEDIUM' },
  { label: 'Low', value: 'LOW' }
]

const confidenceOptions = [
  { label: 'All Confidence', value: null },
  { label: 'High', value: 'HIGH' },
  { label: 'Medium', value: 'MEDIUM' },
  { label: 'Low', value: 'LOW' }
]

// Computed
const completionPercentage = computed(() => {
  if (!session.value || session.value.statistics.total === 0) return 100
  return Math.round(((session.value.statistics.total - session.value.statistics.pending) / session.value.statistics.total) * 100)
})

const filteredItems = computed(() => {
  let result = [...items.value]

  if (statusFilter.value) {
    result = result.filter(i => i.status === statusFilter.value)
  }
  if (priorityFilter.value) {
    result = result.filter(i => i.priority?.level === priorityFilter.value)
  }
  if (confidenceFilter.value) {
    result = result.filter(i => i.aiSuggestion?.confidence === confidenceFilter.value)
  }
  if (searchFilter.value) {
    const search = searchFilter.value.toLowerCase()
    result = result.filter(i =>
      i.dependencyName.toLowerCase().includes(search) ||
      i.aiSuggestion?.suggestedLicense?.toLowerCase().includes(search) ||
      i.originalConcludedLicense?.toLowerCase().includes(search)
    )
  }

  // Sort: pending first, then by priority
  const priorityOrder: Record<string, number> = { HIGH: 0, MEDIUM: 1, LOW: 2 }
  result.sort((a, b) => {
    if (a.status === 'PENDING' && b.status !== 'PENDING') return -1
    if (b.status === 'PENDING' && a.status !== 'PENDING') return 1
    const aPriority = a.priority?.level || 'LOW'
    const bPriority = b.priority?.level || 'LOW'
    return (priorityOrder[aPriority] || 2) - (priorityOrder[bPriority] || 2)
  })

  return result
})

const canApprove = computed(() => {
  return session.value?.status !== 'APPROVED' && session.value?.statistics.pending === 0
})

// Fetch data
async function fetchData() {
  loading.value = true
  try {
    const sessionId = route.params.id as string
    const sessionRes = await api.getCurationSessionByScan(sessionId)
    session.value = sessionRes.data

    // Use scanId from session for items
    const itemsRes = await api.listCurationItems(session.value.scanId, { pageSize: 500 })
    items.value = itemsRes.data.items
    total.value = itemsRes.data.total
  } catch (e) {
    console.error('Failed to fetch curation data', e)
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to load curation session', life: 3000 })
  } finally {
    loading.value = false
  }
}

// Open item detail
function openItemDetail(item: CurationItem) {
  currentItem.value = item
  decisionAction.value = item.status === 'PENDING' ? 'ACCEPT' : (item.status as 'ACCEPT' | 'REJECT' | 'MODIFY')
  decisionLicense.value = item.curatedLicense || item.aiSuggestion?.suggestedLicense || ''
  decisionComment.value = item.curatorComment || ''
  showItemDetail.value = true
}

// Submit decision for single item
async function submitDecision() {
  if (!currentItem.value || !session.value) return

  submitting.value = true
  try {
    const decision: CurationDecision = {
      action: decisionAction.value,
      curatedLicense: decisionAction.value === 'MODIFY' ? decisionLicense.value : undefined,
      comment: decisionComment.value || undefined,
      curatorId: 'curator' // TODO: Get from auth
    }

    const response = await api.submitCurationDecision(session.value.scanId, currentItem.value.dependencyId, decision)

    // Update local state
    const index = items.value.findIndex(i => i.id === currentItem.value!.id)
    if (index !== -1) {
      items.value[index] = response.data
    }

    // Refresh session stats
    const sessionRes = await api.getCurationSession(session.value.scanId)
    session.value = sessionRes.data

    showItemDetail.value = false
    toast.add({ severity: 'success', summary: 'Success', detail: 'Decision saved', life: 2000 })
  } catch (e) {
    console.error('Failed to submit decision', e)
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to save decision', life: 3000 })
  } finally {
    submitting.value = false
  }
}

// Open bulk action dialog
function openBulkAction() {
  if (selectedItems.value.length === 0) {
    toast.add({ severity: 'warn', summary: 'Warning', detail: 'Select items first', life: 2000 })
    return
  }
  decisionAction.value = 'ACCEPT'
  decisionComment.value = ''
  showBulkAction.value = true
}

// Submit bulk decision
async function submitBulkDecision() {
  if (selectedItems.value.length === 0 || !session.value) return

  submitting.value = true
  try {
    const dependencyIds = selectedItems.value.map(i => i.dependencyId)
    const decision: CurationDecision = {
      action: decisionAction.value,
      comment: decisionComment.value || undefined,
      curatorId: 'curator'
    }

    await api.bulkCurationDecision(session.value.scanId, dependencyIds, decision)

    // Refresh data
    await fetchData()
    selectedItems.value = []

    showBulkAction.value = false
    toast.add({ severity: 'success', summary: 'Success', detail: `Updated ${dependencyIds.length} items`, life: 2000 })
  } catch (e) {
    console.error('Failed to submit bulk decision', e)
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to update items', life: 3000 })
  } finally {
    submitting.value = false
  }
}

// Approve session
async function approveSession() {
  if (!session.value || !approverName.value) return

  submitting.value = true
  try {
    const response = await api.approveCurationSession(
      session.value.scanId,
      approverName.value,
      approvalComment.value || undefined
    )

    session.value = response.data
    showApproveDialog.value = false
    toast.add({ severity: 'success', summary: 'Approved', detail: 'Curation session approved', life: 3000 })
  } catch (e) {
    console.error('Failed to approve session', e)
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to approve session', life: 3000 })
  } finally {
    submitting.value = false
  }
}

// Quick actions
async function quickAccept(item: CurationItem) {
  if (!session.value) return

  try {
    const response = await api.submitCurationDecision(session.value.scanId, item.dependencyId, {
      action: 'ACCEPT',
      curatorId: 'curator'
    })

    const index = items.value.findIndex(i => i.id === item.id)
    if (index !== -1) {
      items.value[index] = response.data
    }

    const sessionRes = await api.getCurationSession(session.value.scanId)
    session.value = sessionRes.data
  } catch (e) {
    console.error('Failed to accept', e)
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to accept', life: 2000 })
  }
}

async function quickReject(item: CurationItem) {
  if (!session.value) return

  try {
    const response = await api.submitCurationDecision(session.value.scanId, item.dependencyId, {
      action: 'REJECT',
      curatorId: 'curator'
    })

    const index = items.value.findIndex(i => i.id === item.id)
    if (index !== -1) {
      items.value[index] = response.data
    }

    const sessionRes = await api.getCurationSession(session.value.scanId)
    session.value = sessionRes.data
  } catch (e) {
    console.error('Failed to reject', e)
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to reject', life: 2000 })
  }
}

// Helpers
function getStatusSeverity(status: string): 'success' | 'warning' | 'danger' | 'info' | 'secondary' {
  switch (status) {
    case 'ACCEPTED': return 'success'
    case 'REJECTED': return 'danger'
    case 'MODIFIED': return 'info'
    case 'PENDING': return 'warning'
    default: return 'secondary'
  }
}

function getPrioritySeverity(priority?: string): 'danger' | 'warning' | 'info' {
  switch (priority) {
    case 'CRITICAL': return 'danger'
    case 'HIGH': return 'warning'
    case 'MEDIUM': return 'info'
    default: return 'info'
  }
}

function getConfidenceClass(confidence: string | null): string {
  switch (confidence) {
    case 'HIGH': return 'confidence-high'
    case 'MEDIUM': return 'confidence-medium'
    default: return 'confidence-low'
  }
}

function getSpdxValidationClass(validated: boolean | null): string {
  return validated ? 'spdx-valid' : 'spdx-invalid'
}

// Watch for filter changes to reset selection
watch([statusFilter, priorityFilter, confidenceFilter, searchFilter], () => {
  selectedItems.value = []
})

onMounted(fetchData)
</script>

<template>
  <div class="curation-session">
    <div v-if="loading" class="loading">
      <i class="pi pi-spin pi-spinner"></i> Loading curation session...
    </div>

    <template v-else-if="session">
      <!-- Header -->
      <header class="page-header">
        <div>
          <div class="breadcrumb">
            <RouterLink to="/curations">Curations</RouterLink>
            <i class="pi pi-angle-right"></i>
            <span>Session Details</span>
          </div>
          <h1>Curation Session</h1>
          <div class="session-meta">
            <Badge :value="session.status" :severity="getStatusSeverity(session.status)" />
            <span>Created: {{ new Date(session.createdAt).toLocaleString() }}</span>
            <RouterLink :to="`/scans/${session.scanId}`" class="scan-link">
              <i class="pi pi-external-link"></i> View Scan
            </RouterLink>
          </div>
        </div>
        <div class="header-actions">
          <Button
            v-if="canApprove"
            label="Approve Session"
            icon="pi pi-check"
            severity="success"
            @click="showApproveDialog = true"
          />
          <Button
            v-if="session.status === 'APPROVED'"
            label="Download Report"
            icon="pi pi-download"
            severity="secondary"
            @click="router.push(`/scans/${session.scanId}`)"
          />
        </div>
      </header>

      <!-- Progress Banner -->
      <div class="progress-banner">
        <div class="progress-info">
          <h3>Progress</h3>
          <span class="progress-text">
            {{ session.statistics.total - session.statistics.pending }} of {{ session.statistics.total }} items reviewed
          </span>
        </div>
        <div class="progress-bar-container">
          <ProgressBar :value="completionPercentage" style="height: 12px" />
        </div>
        <div class="stats-row">
          <div class="stat accepted">
            <i class="pi pi-check"></i>
            <span class="value">{{ session.statistics.accepted }}</span>
            <span class="label">Accepted</span>
          </div>
          <div class="stat rejected">
            <i class="pi pi-times"></i>
            <span class="value">{{ session.statistics.rejected }}</span>
            <span class="label">Rejected</span>
          </div>
          <div class="stat modified">
            <i class="pi pi-pencil"></i>
            <span class="value">{{ session.statistics.modified }}</span>
            <span class="label">Modified</span>
          </div>
          <div class="stat pending">
            <i class="pi pi-clock"></i>
            <span class="value">{{ session.statistics.pending }}</span>
            <span class="label">Pending</span>
          </div>
        </div>
      </div>

      <!-- Approval Notice -->
      <div v-if="session.status === 'APPROVED' && session.approval" class="approval-banner">
        <i class="pi pi-check-circle"></i>
        <div>
          <strong>Session Approved</strong>
          <p>Approved by {{ session.approval.approvedBy }} on {{ new Date(session.approval.approvedAt).toLocaleString() }}</p>
          <p v-if="session.approval.comment" class="comment">{{ session.approval.comment }}</p>
        </div>
      </div>

      <!-- Filters & Bulk Actions -->
      <div class="toolbar">
        <div class="filters">
          <Dropdown
            v-model="statusFilter"
            :options="statusOptions"
            optionLabel="label"
            optionValue="value"
            placeholder="Status"
            class="filter-dropdown"
          />
          <Dropdown
            v-model="priorityFilter"
            :options="priorityOptions"
            optionLabel="label"
            optionValue="value"
            placeholder="Priority"
            class="filter-dropdown"
          />
          <Dropdown
            v-model="confidenceFilter"
            :options="confidenceOptions"
            optionLabel="label"
            optionValue="value"
            placeholder="Confidence"
            class="filter-dropdown"
          />
          <InputText
            v-model="searchFilter"
            placeholder="Search dependencies..."
            class="search-input"
          />
        </div>
        <div class="bulk-actions" v-if="session.status !== 'APPROVED'">
          <span v-if="selectedItems.length > 0" class="selected-count">
            {{ selectedItems.length }} selected
          </span>
          <Button
            label="Bulk Action"
            icon="pi pi-list"
            severity="secondary"
            :disabled="selectedItems.length === 0"
            @click="openBulkAction"
          />
        </div>
      </div>

      <!-- Items Table -->
      <div class="section">
        <DataTable
          v-model:selection="selectedItems"
          :value="filteredItems"
          stripedRows
          showGridlines
          class="p-datatable-sm"
          :paginator="filteredItems.length > 20"
          :rows="20"
          dataKey="id"
          :selectionMode="session.status !== 'APPROVED' ? undefined : undefined"
        >
          <Column v-if="session.status !== 'APPROVED'" selectionMode="multiple" style="width: 50px" />

          <Column field="dependencyName" header="Dependency" style="min-width: 250px" sortable>
            <template #body="{ data }">
              <div class="dependency-cell">
                <strong>{{ data.dependencyName }}</strong>
                <span class="version">@{{ data.dependencyVersion }}</span>
                <div class="scope">{{ data.scope }}</div>
              </div>
            </template>
          </Column>

          <Column header="Original License" style="width: 150px">
            <template #body="{ data }">
              <span class="license-badge original">
                {{ data.originalConcludedLicense || 'Unknown' }}
              </span>
            </template>
          </Column>

          <Column header="AI Suggestion" style="min-width: 200px">
            <template #body="{ data }">
              <div v-if="data.aiSuggestion?.suggestedLicense" class="ai-suggestion">
                <span class="suggested-license">{{ data.aiSuggestion.suggestedLicense }}</span>
                <span :class="['confidence', getConfidenceClass(data.aiSuggestion.confidence)]">
                  {{ data.aiSuggestion.confidence }}
                </span>
              </div>
              <span v-else class="no-suggestion">No AI suggestion</span>
            </template>
          </Column>

          <Column header="SPDX Suggestion" style="min-width: 200px">
            <template #body="{ data }">
              <div v-if="data.spdxSuggestion?.licenseId" class="spdx-suggestion">
                <span class="suggested-license">{{ data.spdxSuggestion.licenseId }}</span>
                <span class="spdx-badge" :class="getSpdxValidationClass(data.spdxValidated)">
                  {{ data.spdxValidated ? '✓ Valid' : '⚠ Invalid' }}
                </span>
              </div>
              <span v-else class="no-suggestion">No SPDX suggestion</span>
            </template>
          </Column>

          <Column field="priority" header="Priority" style="width: 100px" sortable>
            <template #body="{ data }">
              <Badge :value="data.priority?.level" :severity="getPrioritySeverity(data.priority?.level)" />
            </template>
          </Column>

          <Column field="status" header="Status" style="width: 110px" sortable>
            <template #body="{ data }">
              <Badge :value="data.status" :severity="getStatusSeverity(data.status)" />
            </template>
          </Column>

          <Column header="Final License" style="width: 150px">
            <template #body="{ data }">
              <span v-if="data.curatedLicense" class="license-badge curated">
                {{ data.curatedLicense }}
              </span>
              <span v-else-if="data.status === 'ACCEPTED'" class="license-badge accepted">
                {{ data.aiSuggestion?.suggestedLicense }}
              </span>
              <span v-else class="no-license">-</span>
            </template>
          </Column>

          <Column header="Actions" style="width: 150px">
            <template #body="{ data }">
              <div class="action-buttons" v-if="session?.status !== 'APPROVED'">
                <Button
                  v-if="data.status === 'PENDING'"
                  icon="pi pi-check"
                  severity="success"
                  text
                  rounded
                  size="small"
                  @click="quickAccept(data)"
                  title="Accept"
                />
                <Button
                  v-if="data.status === 'PENDING'"
                  icon="pi pi-times"
                  severity="danger"
                  text
                  rounded
                  size="small"
                  @click="quickReject(data)"
                  title="Reject"
                />
                <Button
                  icon="pi pi-pencil"
                  severity="secondary"
                  text
                  rounded
                  size="small"
                  @click="openItemDetail(data)"
                  title="Edit"
                />
              </div>
              <Button
                v-else
                icon="pi pi-eye"
                severity="secondary"
                text
                rounded
                size="small"
                @click="openItemDetail(data)"
                title="View"
              />
            </template>
          </Column>
        </DataTable>
      </div>
    </template>

    <!-- Item Detail Dialog -->
    <Dialog
      v-model:visible="showItemDetail"
      :header="currentItem?.dependencyName || 'Item Details'"
      :modal="true"
      :style="{ width: '700px' }"
    >
      <div v-if="currentItem" class="item-detail">
        <div class="detail-section">
          <h4>Dependency Information</h4>
          <div class="info-grid">
            <div class="info-item">
              <label>Name</label>
              <span>{{ currentItem.dependencyName }}</span>
            </div>
            <div class="info-item">
              <label>Version</label>
              <span>{{ currentItem.dependencyVersion }}</span>
            </div>
            <div class="info-item">
              <label>Scope</label>
              <span>{{ currentItem.scope }}</span>
            </div>
            <div class="info-item">
              <label>Priority</label>
              <Badge :value="currentItem.priority?.level" :severity="getPrioritySeverity(currentItem.priority?.level)" />
            </div>
          </div>
        </div>

        <div class="detail-section">
          <h4>License Information</h4>
          <div class="license-comparison">
            <div class="license-box original">
              <label>Original License</label>
              <span>{{ currentItem.originalConcludedLicense || 'Unknown' }}</span>
            </div>
            <i class="pi pi-arrow-right"></i>
            <div class="license-box suggested">
              <label>AI Suggested</label>
              <span>{{ currentItem.aiSuggestion?.suggestedLicense || 'None' }}</span>
              <span v-if="currentItem.aiSuggestion?.confidence" :class="['confidence-badge', getConfidenceClass(currentItem.aiSuggestion.confidence)]">
                {{ currentItem.aiSuggestion.confidence }}
              </span>
            </div>
            <i class="pi pi-arrow-right"></i>
            <div class="license-box spdx">
              <label>SPDX Suggested</label>
              <span>{{ currentItem.spdxLicense?.licenseId || 'None' }}</span>
              <span v-if="currentItem.spdxLicense" :class="['spdx-badge', getSpdxValidationClass(currentItem.spdxValidated)]">
                {{ currentItem.spdxValidated ? '✓ Valid' : '⚠ Invalid' }}
              </span>
            </div>
          </div>
          <div v-if="currentItem.aiSuggestion?.reasoning" class="reasoning-box">
            <label>AI Reasoning</label>
            <p>{{ currentItem.aiSuggestion.reasoning }}</p>
          </div>
          <div v-if="currentItem.spdxLicense" class="spdx-info-box">
            <label>SPDX Information</label>
            <div class="spdx-details">
              <p><strong>License:</strong> {{ currentItem.spdxLicense.name }}</p>
              <p v-if="currentItem.spdxLicense.isOsiApproved"><strong>OSI Approved:</strong> Yes</p>
              <p v-if="currentItem.spdxLicense.isFsfLibre"><strong>FSF Libre:</strong> Yes</p>
            </div>
          </div>
        </div>

        <div v-if="session?.status !== 'APPROVED'" class="detail-section">
          <h4>Your Decision</h4>
          <div class="decision-form">
            <div class="action-selector">
              <Button
                label="Accept"
                :severity="decisionAction === 'ACCEPT' ? 'success' : 'secondary'"
                :outlined="decisionAction !== 'ACCEPT'"
                @click="decisionAction = 'ACCEPT'"
              />
              <Button
                label="Reject"
                :severity="decisionAction === 'REJECT' ? 'danger' : 'secondary'"
                :outlined="decisionAction !== 'REJECT'"
                @click="decisionAction = 'REJECT'"
              />
              <Button
                label="Modify"
                :severity="decisionAction === 'MODIFY' ? 'info' : 'secondary'"
                :outlined="decisionAction !== 'MODIFY'"
                @click="decisionAction = 'MODIFY'"
              />
            </div>

            <div v-if="decisionAction === 'MODIFY'" class="field">
              <label>Custom License</label>
              <InputText v-model="decisionLicense" placeholder="Enter SPDX license ID..." class="w-full" />
            </div>

            <div class="field">
              <label>Comment (optional)</label>
              <Textarea v-model="decisionComment" rows="2" class="w-full" placeholder="Add a note about this decision..." />
            </div>
          </div>
        </div>

        <div v-else class="detail-section">
          <h4>Decision</h4>
          <div class="decision-summary">
            <Badge :value="currentItem.status" :severity="getStatusSeverity(currentItem.status)" />
            <span v-if="currentItem.curatedLicense">License: {{ currentItem.curatedLicense }}</span>
            <span v-if="currentItem.curatorId">By: {{ currentItem.curatorId }}</span>
            <p v-if="currentItem.curatorComment" class="comment">{{ currentItem.curatorComment }}</p>
          </div>
        </div>
      </div>

      <template #footer>
        <Button label="Cancel" severity="secondary" @click="showItemDetail = false" />
        <Button
          v-if="session?.status !== 'APPROVED'"
          label="Save Decision"
          icon="pi pi-check"
          @click="submitDecision"
          :loading="submitting"
        />
      </template>
    </Dialog>

    <!-- Bulk Action Dialog -->
    <Dialog
      v-model:visible="showBulkAction"
      header="Bulk Action"
      :modal="true"
      :style="{ width: '500px' }"
    >
      <div class="bulk-action-content">
        <p>Apply action to {{ selectedItems.length }} selected items:</p>

        <div class="action-selector">
          <Button
            label="Accept All"
            :severity="decisionAction === 'ACCEPT' ? 'success' : 'secondary'"
            :outlined="decisionAction !== 'ACCEPT'"
            @click="decisionAction = 'ACCEPT'"
          />
          <Button
            label="Reject All"
            :severity="decisionAction === 'REJECT' ? 'danger' : 'secondary'"
            :outlined="decisionAction !== 'REJECT'"
            @click="decisionAction = 'REJECT'"
          />
        </div>

        <div class="field">
          <label>Comment (optional)</label>
          <Textarea v-model="decisionComment" rows="2" class="w-full" placeholder="Add a note for all items..." />
        </div>
      </div>

      <template #footer>
        <Button label="Cancel" severity="secondary" @click="showBulkAction = false" />
        <Button
          label="Apply"
          icon="pi pi-check"
          :severity="decisionAction === 'ACCEPT' ? 'success' : 'danger'"
          @click="submitBulkDecision"
          :loading="submitting"
        />
      </template>
    </Dialog>

    <!-- Approve Session Dialog -->
    <Dialog
      v-model:visible="showApproveDialog"
      header="Approve Curation Session"
      :modal="true"
      :style="{ width: '500px' }"
    >
      <div class="approve-content">
        <p>You are about to approve this curation session. This action confirms that all license decisions have been reviewed and are correct.</p>

        <div class="approval-summary">
          <div class="summary-item">
            <span class="label">Total Items</span>
            <span class="value">{{ session?.statistics.total }}</span>
          </div>
          <div class="summary-item accepted">
            <span class="label">Accepted</span>
            <span class="value">{{ session?.statistics.accepted }}</span>
          </div>
          <div class="summary-item rejected">
            <span class="label">Rejected</span>
            <span class="value">{{ session?.statistics.rejected }}</span>
          </div>
          <div class="summary-item modified">
            <span class="label">Modified</span>
            <span class="value">{{ session?.statistics.modified }}</span>
          </div>
        </div>

        <div class="field">
          <label>Your Name *</label>
          <InputText v-model="approverName" placeholder="Enter your name..." class="w-full" />
        </div>

        <div class="field">
          <label>Comment (optional)</label>
          <Textarea v-model="approvalComment" rows="2" class="w-full" placeholder="Add approval notes..." />
        </div>
      </div>

      <template #footer>
        <Button label="Cancel" severity="secondary" @click="showApproveDialog = false" />
        <Button
          label="Approve Session"
          icon="pi pi-check"
          severity="success"
          @click="approveSession"
          :loading="submitting"
          :disabled="!approverName"
        />
      </template>
    </Dialog>
  </div>
</template>

<style scoped>
.curation-session {
  max-width: 1600px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 1.5rem;
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

.session-meta {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-top: 0.5rem;
  font-size: 0.875rem;
  color: #64748b;
}

.scan-link {
  color: #3b82f6;
  text-decoration: none;
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.header-actions {
  display: flex;
  gap: 0.75rem;
}

/* Progress Banner */
.progress-banner {
  background: white;
  border-radius: 1rem;
  padding: 1.5rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  margin-bottom: 1.5rem;
}

.progress-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.75rem;
}

.progress-info h3 {
  margin: 0;
  font-size: 1rem;
  color: #1e293b;
}

.progress-text {
  color: #64748b;
  font-size: 0.875rem;
}

.progress-bar-container {
  margin-bottom: 1rem;
}

.stats-row {
  display: flex;
  gap: 2rem;
}

.stats-row .stat {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.stats-row .stat .value {
  font-weight: 600;
  font-size: 1.125rem;
}

.stats-row .stat .label {
  color: #64748b;
  font-size: 0.875rem;
}

.stats-row .accepted { color: #10b981; }
.stats-row .rejected { color: #ef4444; }
.stats-row .modified { color: #8b5cf6; }
.stats-row .pending { color: #f59e0b; }

/* Approval Banner */
.approval-banner {
  display: flex;
  align-items: flex-start;
  gap: 1rem;
  padding: 1.25rem 1.5rem;
  background: linear-gradient(135deg, #d1fae5 0%, #a7f3d0 100%);
  border: 1px solid #10b981;
  border-radius: 1rem;
  margin-bottom: 1.5rem;
}

.approval-banner i {
  font-size: 1.5rem;
  color: #059669;
}

.approval-banner strong {
  color: #065f46;
}

.approval-banner p {
  margin: 0.25rem 0 0;
  color: #047857;
  font-size: 0.875rem;
}

.approval-banner .comment {
  margin-top: 0.5rem;
  font-style: italic;
}

/* Toolbar */
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
  gap: 1rem;
  flex-wrap: wrap;
}

.filters {
  display: flex;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.filter-dropdown {
  min-width: 140px;
}

.search-input {
  min-width: 200px;
}

.bulk-actions {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.selected-count {
  color: #3b82f6;
  font-weight: 500;
}

/* Section */
.section {
  background: white;
  border-radius: 1rem;
  padding: 1.5rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

/* Table cells */
.dependency-cell {
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
}

.dependency-cell .version {
  color: #64748b;
  font-size: 0.875rem;
}

.dependency-cell .scope {
  color: #94a3b8;
  font-size: 0.75rem;
}

.license-badge {
  display: inline-block;
  padding: 0.25rem 0.5rem;
  border-radius: 0.25rem;
  font-size: 0.8125rem;
  font-family: monospace;
}

.license-badge.original {
  background: #f1f5f9;
  color: #475569;
}

.license-badge.curated {
  background: #dbeafe;
  color: #1d4ed8;
}

.license-badge.accepted {
  background: #d1fae5;
  color: #059669;
}

.ai-suggestion {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.suggested-license {
  font-weight: 500;
  color: #1e293b;
}

.confidence {
  display: inline-block;
  padding: 0.125rem 0.375rem;
  border-radius: 0.25rem;
  font-size: 0.6875rem;
  width: fit-content;
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

.no-suggestion, .no-license {
  color: #94a3b8;
}

.action-buttons {
  display: flex;
  gap: 0.25rem;
}

/* Item Detail Dialog */
.item-detail {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.detail-section h4 {
  margin: 0 0 1rem;
  color: #1e293b;
  font-size: 1rem;
  border-bottom: 1px solid #e2e8f0;
  padding-bottom: 0.5rem;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1rem;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.info-item label {
  color: #64748b;
  font-size: 0.75rem;
  text-transform: uppercase;
}

.info-item span {
  color: #1e293b;
  font-weight: 500;
}

.license-comparison {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.license-box {
  flex: 1;
  padding: 1rem;
  border-radius: 0.5rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.license-box label {
  color: #64748b;
  font-size: 0.75rem;
  text-transform: uppercase;
}

.license-box.original {
  background: #f8fafc;
}

.license-box.suggested {
  background: #f0fdf4;
}

.license-box span:not(.confidence-badge) {
  font-weight: 600;
  font-size: 1.125rem;
  color: #1e293b;
}

.confidence-badge {
  font-size: 0.6875rem;
  padding: 0.125rem 0.5rem;
  border-radius: 0.25rem;
  width: fit-content;
}

.reasoning-box {
  margin-top: 1rem;
  padding: 1rem;
  background: #f8fafc;
  border-radius: 0.5rem;
}

.reasoning-box label {
  display: block;
  color: #64748b;
  font-size: 0.75rem;
  text-transform: uppercase;
  margin-bottom: 0.5rem;
}

.reasoning-box p {
  margin: 0;
  color: #475569;
  font-size: 0.875rem;
  line-height: 1.5;
}

.decision-form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.action-selector {
  display: flex;
  gap: 0.5rem;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.field label {
  font-weight: 500;
  color: #374151;
  font-size: 0.875rem;
}

.w-full {
  width: 100%;
}

.decision-summary {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 1rem;
}

.decision-summary .comment {
  width: 100%;
  margin: 0.5rem 0 0;
  padding: 0.5rem;
  background: #f8fafc;
  border-radius: 0.25rem;
  font-style: italic;
  color: #64748b;
}

/* Bulk Action Dialog */
.bulk-action-content {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.bulk-action-content p {
  color: #64748b;
}

/* Approve Dialog */
.approve-content {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.approve-content > p {
  color: #64748b;
}

.approval-summary {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0.75rem;
  padding: 1rem;
  background: #f8fafc;
  border-radius: 0.5rem;
}

.summary-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.25rem;
}

.summary-item .label {
  font-size: 0.75rem;
  color: #64748b;
}

.summary-item .value {
  font-size: 1.25rem;
  font-weight: 600;
  color: #1e293b;
}

.summary-item.accepted .value { color: #10b981; }
.summary-item.rejected .value { color: #ef4444; }
.summary-item.modified .value { color: #8b5cf6; }

.loading {
  text-align: center;
  padding: 4rem;
  color: #64748b;
}
</style>
