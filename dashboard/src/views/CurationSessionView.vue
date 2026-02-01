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
import TabView from 'primevue/tabview'
import TabPanel from 'primevue/tabpanel'
import Message from 'primevue/message'
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

// EU Compliance Data
const approvalStatus = ref<any>(null)
const orLicenses = ref<any[]>([])
const currentExplanations = ref<any>(null)

// Filters
const statusFilter = ref<string | null>(null)
const priorityFilter = ref<string | null>(null)
const confidenceFilter = ref<string | null>(null)
const searchFilter = ref('')

// Dialog states
const showItemDetail = ref(false)
const showBulkAction = ref(false)
const showApproveDialog = ref(false)
const showOrLicenseDialog = ref(false)
const showSubmitForApprovalDialog = ref(false)
const currentItem = ref<CurationItem | null>(null)
const currentOrLicense = ref<any>(null)

// Action states
const submitting = ref(false)
const approverName = ref('')
const approvalComment = ref('')
const approverRole = ref('')

// Decision form
const decisionAction = ref<'ACCEPT' | 'REJECT' | 'MODIFY'>('ACCEPT')
const decisionLicense = ref('')
const decisionComment = ref('')

// EU Compliance: Justification form
const justification = ref({
  spdxId: '',
  licenseCategory: 'PERMISSIVE',
  concludedLicense: '',
  justificationType: 'AI_ACCEPTED',
  justificationText: '',
  evidenceType: '',
  evidenceReference: '',
  distributionScope: 'BINARY'
})

// OR License resolution form
const orLicenseChoice = ref('')
const orLicenseReason = ref('')

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

// EU Compliance: License category options
const licenseCategoryOptions = [
  { label: 'Permissive', value: 'PERMISSIVE' },
  { label: 'Weak Copyleft', value: 'WEAK_COPYLEFT' },
  { label: 'Strong Copyleft', value: 'STRONG_COPYLEFT' },
  { label: 'Network Copyleft (AGPL)', value: 'NETWORK_COPYLEFT' },
  { label: 'Proprietary', value: 'PROPRIETARY' },
  { label: 'Public Domain', value: 'PUBLIC_DOMAIN' },
  { label: 'Unknown', value: 'UNKNOWN' }
]

const justificationTypeOptions = [
  { label: 'AI Accepted', value: 'AI_ACCEPTED' },
  { label: 'Manual Override', value: 'MANUAL_OVERRIDE' },
  { label: 'Evidence Based', value: 'EVIDENCE_BASED' },
  { label: 'Policy Exemption', value: 'POLICY_EXEMPTION' },
  { label: 'Legal Opinion', value: 'LEGAL_OPINION' }
]

const evidenceTypeOptions = [
  { label: 'None', value: '' },
  { label: 'License File', value: 'LICENSE_FILE' },
  { label: 'Repository Inspection', value: 'REPO_INSPECTION' },
  { label: 'Vendor Confirmation', value: 'VENDOR_CONFIRMATION' },
  { label: 'Legal Opinion', value: 'LEGAL_OPINION' },
  { label: 'Prior Audit', value: 'PRIOR_AUDIT' },
  { label: 'Package Metadata', value: 'PACKAGE_METADATA' }
]

const distributionScopeOptions = [
  { label: 'Internal Use Only', value: 'INTERNAL' },
  { label: 'Binary Distribution', value: 'BINARY' },
  { label: 'Source Distribution', value: 'SOURCE' },
  { label: 'SaaS / Cloud Service', value: 'SAAS' },
  { label: 'Embedded / Device', value: 'EMBEDDED' }
]

const approverRoleOptions = [
  { label: 'Legal', value: 'LEGAL' },
  { label: 'Compliance', value: 'COMPLIANCE' },
  { label: 'Manager', value: 'MANAGER' },
  { label: 'Security', value: 'SECURITY' }
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

// EU Compliance: Check if session can be submitted for approval
const canSubmitForApproval = computed(() => {
  if (!approvalStatus.value?.readiness) return false
  return approvalStatus.value.readiness.isReady && !approvalStatus.value.isSubmittedForApproval
})

// EU Compliance: Check if current user can approve (must be different from submitter)
const isSubmittedForApproval = computed(() => {
  return approvalStatus.value?.isSubmittedForApproval || false
})

// EU Compliance: Check if item requires justification
const itemRequiresJustification = computed(() => {
  if (!currentItem.value) return false
  const license = decisionLicense.value || currentItem.value.aiSuggestion?.suggestedLicense || ''
  const nonPermissive = ['GPL', 'AGPL', 'LGPL', 'MPL', 'EPL', 'CDDL', 'Unknown', 'NOASSERTION', 'Proprietary']
  return nonPermissive.some(prefix => license.toUpperCase().includes(prefix.toUpperCase()))
})

// EU Compliance: Count unresolved OR licenses
const unresolvedOrLicenseCount = computed(() => {
  return orLicenses.value.filter(ol => !ol.isResolved).length
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

// ====================================================================
// EU Compliance Methods
// ====================================================================

// Fetch EU compliance data (approval status, OR licenses)
async function fetchEUComplianceData() {
  if (!session.value) return

  try {
    // Fetch approval status
    const approvalRes = await fetch(`/api/v1/scans/${session.value.scanId}/curation/approval/readiness`)
    if (approvalRes.ok) {
      approvalStatus.value = await approvalRes.json()
    }

    // Fetch OR licenses
    const orRes = await fetch(`/api/v1/scans/${session.value.scanId}/curation/or-licenses`)
    if (orRes.ok) {
      const orData = await orRes.json()
      orLicenses.value = orData.items || []
    }
  } catch (e) {
    console.error('Failed to fetch EU compliance data', e)
  }
}

// Fetch explanations for a specific curation item
async function fetchExplanations(dependencyId: string) {
  if (!session.value) return

  try {
    const res = await fetch(`/api/v1/scans/${session.value.scanId}/curation/items/${dependencyId}/explanations`)
    if (res.ok) {
      currentExplanations.value = await res.json()
    }
  } catch (e) {
    console.error('Failed to fetch explanations', e)
    currentExplanations.value = null
  }
}

// Submit decision with EU compliance justification
async function submitDecisionWithJustification() {
  if (!currentItem.value || !session.value) return

  submitting.value = true
  try {
    const payload = {
      action: decisionAction.value,
      curatedLicense: decisionAction.value === 'MODIFY' ? decisionLicense.value : undefined,
      comment: decisionComment.value || undefined,
      justification: itemRequiresJustification.value ? {
        spdxId: justification.value.spdxId || decisionLicense.value || currentItem.value.aiSuggestion?.suggestedLicense,
        licenseCategory: justification.value.licenseCategory,
        concludedLicense: justification.value.concludedLicense || decisionLicense.value || currentItem.value.aiSuggestion?.suggestedLicense,
        justificationType: justification.value.justificationType,
        justificationText: justification.value.justificationText,
        evidenceType: justification.value.evidenceType || undefined,
        evidenceReference: justification.value.evidenceReference || undefined,
        distributionScope: justification.value.distributionScope
      } : undefined
    }

    const res = await fetch(`/api/v1/scans/${session.value.scanId}/curation/items/${currentItem.value.dependencyId}/decide`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Curator-Id': 'curator' },
      body: JSON.stringify(payload)
    })

    if (!res.ok) {
      const error = await res.json()
      throw new Error(error.error || 'Failed to submit decision')
    }

    const response = await res.json()

    // Update local state
    const index = items.value.findIndex(i => i.id === currentItem.value!.id)
    if (index !== -1) {
      items.value[index] = response.item
    }

    // Refresh session stats and EU compliance data
    await fetchData()
    await fetchEUComplianceData()

    showItemDetail.value = false
    resetJustificationForm()
    toast.add({ severity: 'success', summary: 'Success', detail: 'Decision saved with justification', life: 2000 })
  } catch (e: any) {
    console.error('Failed to submit decision', e)
    toast.add({ severity: 'error', summary: 'Error', detail: e.message || 'Failed to save decision', life: 3000 })
  } finally {
    submitting.value = false
  }
}

// Submit for approval (EU two-role workflow)
async function submitForApproval() {
  if (!session.value) return

  submitting.value = true
  try {
    const res = await fetch(`/api/v1/scans/${session.value.scanId}/curation/submit-for-approval`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Curator-Id': 'curator' },
      body: JSON.stringify({ comment: approvalComment.value || undefined })
    })

    if (!res.ok) {
      const error = await res.json()
      throw new Error(error.error || 'Failed to submit for approval')
    }

    await fetchData()
    await fetchEUComplianceData()

    showSubmitForApprovalDialog.value = false
    toast.add({ severity: 'success', summary: 'Success', detail: 'Session submitted for approval', life: 3000 })
  } catch (e: any) {
    console.error('Failed to submit for approval', e)
    toast.add({ severity: 'error', summary: 'Error', detail: e.message || 'Failed to submit for approval', life: 3000 })
  } finally {
    submitting.value = false
  }
}

// Approve session (EU two-role workflow - different person than curator)
async function decideApproval(decision: 'APPROVED' | 'REJECTED') {
  if (!session.value || !approverName.value || !approverRole.value) return

  submitting.value = true
  try {
    const res = await fetch(`/api/v1/scans/${session.value.scanId}/curation/approval/decide`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Approver-Id': 'approver',
        'X-Approver-Name': approverName.value,
        'X-Approver-Role': approverRole.value
      },
      body: JSON.stringify({ decision, comment: approvalComment.value || undefined })
    })

    if (!res.ok) {
      const error = await res.json()
      throw new Error(error.error || 'Failed to process approval')
    }

    await fetchData()
    await fetchEUComplianceData()

    showApproveDialog.value = false
    const msg = decision === 'APPROVED' ? 'Session approved' : 'Session rejected'
    toast.add({ severity: decision === 'APPROVED' ? 'success' : 'warn', summary: msg, detail: msg, life: 3000 })
  } catch (e: any) {
    console.error('Failed to decide approval', e)
    toast.add({ severity: 'error', summary: 'Error', detail: e.message || 'Failed to process approval', life: 3000 })
  } finally {
    submitting.value = false
  }
}

// Resolve OR license
async function resolveOrLicense() {
  if (!session.value || !currentOrLicense.value || !orLicenseChoice.value) return

  submitting.value = true
  try {
    const res = await fetch(`/api/v1/scans/${session.value.scanId}/curation/items/${currentOrLicense.value.dependencyId}/resolve-or`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Curator-Id': 'curator' },
      body: JSON.stringify({
        chosenLicense: orLicenseChoice.value,
        reason: orLicenseReason.value
      })
    })

    if (!res.ok) {
      const error = await res.json()
      throw new Error(error.error || 'Failed to resolve OR license')
    }

    await fetchData()
    await fetchEUComplianceData()

    showOrLicenseDialog.value = false
    currentOrLicense.value = null
    orLicenseChoice.value = ''
    orLicenseReason.value = ''
    toast.add({ severity: 'success', summary: 'Success', detail: 'OR license resolved', life: 2000 })
  } catch (e: any) {
    console.error('Failed to resolve OR license', e)
    toast.add({ severity: 'error', summary: 'Error', detail: e.message || 'Failed to resolve OR license', life: 3000 })
  } finally {
    submitting.value = false
  }
}

// Open OR license resolution dialog
function openOrLicenseDialog(orLicense: any) {
  currentOrLicense.value = orLicense
  orLicenseChoice.value = ''
  orLicenseReason.value = ''
  showOrLicenseDialog.value = true
}

// Reset justification form
function resetJustificationForm() {
  justification.value = {
    spdxId: '',
    licenseCategory: 'PERMISSIVE',
    concludedLicense: '',
    justificationType: 'AI_ACCEPTED',
    justificationText: '',
    evidenceType: '',
    evidenceReference: '',
    distributionScope: 'BINARY'
  }
}

// Enhanced open item detail with explanations
async function openItemDetailWithExplanations(item: CurationItem) {
  openItemDetail(item)
  await fetchExplanations(item.dependencyId)
}

// Export curations as YAML
async function exportCurationsYaml() {
  if (!session.value) return

  try {
    const res = await fetch(`/api/v1/scans/${session.value.scanId}/curation/export/curations-yaml`)
    if (res.ok) {
      const data = await res.json()
      // Download the YAML content
      const blob = new Blob([data.content], { type: 'text/yaml' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = data.filename || 'curations.yml'
      a.click()
      URL.revokeObjectURL(url)
      toast.add({ severity: 'success', summary: 'Exported', detail: 'Curations YAML downloaded', life: 2000 })
    }
  } catch (e) {
    console.error('Failed to export curations', e)
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to export curations', life: 3000 })
  }
}

// Export NOTICE file
async function exportNoticeFile() {
  if (!session.value) return

  try {
    const res = await fetch(`/api/v1/scans/${session.value.scanId}/curation/export/notice`)
    if (res.ok) {
      const data = await res.json()
      const blob = new Blob([data.content], { type: 'text/plain' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = data.filename || 'NOTICE'
      a.click()
      URL.revokeObjectURL(url)
      toast.add({ severity: 'success', summary: 'Exported', detail: 'NOTICE file downloaded', life: 2000 })
    }
  } catch (e) {
    console.error('Failed to export NOTICE', e)
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to export NOTICE', life: 3000 })
  }
}

// Initial data load with EU compliance
async function initializeData() {
  await fetchData()
  await fetchEUComplianceData()
}

onMounted(initializeData)
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
            v-if="canSubmitForApproval"
            label="Submit for Approval"
            icon="pi pi-send"
            severity="success"
            @click="showSubmitForApprovalDialog = true"
          />
          <Button
            v-if="isSubmittedForApproval && session.status !== 'APPROVED'"
            label="Review & Approve"
            icon="pi pi-check"
            severity="success"
            @click="showApproveDialog = true"
          />
          <Button
            v-if="session.status === 'APPROVED'"
            label="Export YAML"
            icon="pi pi-file-export"
            severity="secondary"
            @click="exportCurationsYaml"
          />
          <Button
            v-if="session.status === 'APPROVED'"
            label="Export NOTICE"
            icon="pi pi-file"
            severity="secondary"
            @click="exportNoticeFile"
          />
          <Button
            v-if="session.status === 'APPROVED'"
            label="View Scan"
            icon="pi pi-external-link"
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

      <!-- EU Compliance: OR Licenses Warning -->
      <Message v-if="unresolvedOrLicenseCount > 0" severity="warn" :closable="false" class="eu-message">
        <template #default>
          <div class="eu-warning-content">
            <i class="pi pi-exclamation-triangle"></i>
            <div>
              <strong>{{ unresolvedOrLicenseCount }} Unresolved OR License(s)</strong>
              <p>Some dependencies have dual/multi-licensed options (e.g., "GPL-2.0 OR MIT"). You must explicitly choose which license applies before approval.</p>
            </div>
            <Button
              label="Resolve OR Licenses"
              icon="pi pi-list"
              severity="warning"
              size="small"
              @click="showOrLicenseDialog = true; currentOrLicense = orLicenses.find(o => !o.isResolved)"
            />
          </div>
        </template>
      </Message>

      <!-- EU Compliance: Approval Workflow Status -->
      <div v-if="isSubmittedForApproval && session.status !== 'APPROVED'" class="pending-approval-banner">
        <i class="pi pi-clock"></i>
        <div>
          <strong>Awaiting Approval</strong>
          <p>This session has been submitted for approval and is awaiting review by a different person (EU four-eyes principle).</p>
          <p v-if="approvalStatus?.submittedBy">Submitted by: {{ approvalStatus.submittedBy }}</p>
        </div>
        <div class="approval-actions">
          <Button
            label="Approve"
            icon="pi pi-check"
            severity="success"
            size="small"
            @click="showApproveDialog = true"
          />
          <Button
            label="Reject"
            icon="pi pi-times"
            severity="danger"
            size="small"
            outlined
            @click="showApproveDialog = true"
          />
        </div>
      </div>

      <!-- EU Compliance: Readiness Info -->
      <div v-if="approvalStatus?.readiness && !isSubmittedForApproval && session.status !== 'APPROVED'" class="readiness-banner">
        <div class="readiness-header">
          <h4>Approval Readiness</h4>
          <Badge
            :value="approvalStatus.readiness.isReady ? 'Ready' : 'Not Ready'"
            :severity="approvalStatus.readiness.isReady ? 'success' : 'warning'"
          />
        </div>
        <div class="readiness-checks">
          <div class="check-item" :class="{ passed: approvalStatus.readiness.allItemsCurated }">
            <i :class="approvalStatus.readiness.allItemsCurated ? 'pi pi-check-circle' : 'pi pi-circle'"></i>
            <span>All items curated ({{ session.statistics.total - session.statistics.pending }}/{{ session.statistics.total }})</span>
          </div>
          <div class="check-item" :class="{ passed: approvalStatus.readiness.allJustificationsComplete }">
            <i :class="approvalStatus.readiness.allJustificationsComplete ? 'pi pi-check-circle' : 'pi pi-circle'"></i>
            <span>All justifications complete ({{ approvalStatus.readiness.justificationsComplete || 0 }}/{{ approvalStatus.readiness.justificationsRequired || 0 }})</span>
          </div>
          <div class="check-item" :class="{ passed: approvalStatus.readiness.allOrLicensesResolved }">
            <i :class="approvalStatus.readiness.allOrLicensesResolved ? 'pi pi-check-circle' : 'pi pi-circle'"></i>
            <span>All OR licenses resolved ({{ (orLicenses.length || 0) - unresolvedOrLicenseCount }}/{{ orLicenses.length || 0 }})</span>
          </div>
        </div>
        <div v-if="canSubmitForApproval" class="readiness-action">
          <Button
            label="Submit for Approval"
            icon="pi pi-send"
            severity="success"
            @click="showSubmitForApprovalDialog = true"
          />
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
                  @click="openItemDetailWithExplanations(data)"
                  title="Edit with Explanations"
                />
              </div>
              <Button
                v-else
                icon="pi pi-eye"
                severity="secondary"
                text
                rounded
                size="small"
                @click="openItemDetailWithExplanations(data)"
                title="View Details"
              />
            </template>
          </Column>
        </DataTable>
      </div>
    </template>

    <!-- Item Detail Dialog (Enhanced for EU Compliance) -->
    <Dialog
      v-model:visible="showItemDetail"
      :header="currentItem?.dependencyName || 'Item Details'"
      :modal="true"
      :style="{ width: '900px' }"
    >
      <div v-if="currentItem" class="item-detail">
        <TabView>
          <!-- Tab 1: Basic Info & Decision -->
          <TabPanel header="Decision">
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
          </TabPanel>

          <!-- Tab 2: Explanations ("Why Not?" + Obligations + Compatibility) -->
          <TabPanel header="Explanations">
            <div v-if="currentExplanations" class="explanations-content">
              <!-- Why Not? Explanations -->
              <div v-if="currentExplanations.whyNotExplanations?.length" class="explanation-section">
                <h4><i class="pi pi-question-circle"></i> Why Not? Policy Violations</h4>
                <div v-for="(exp, idx) in currentExplanations.whyNotExplanations" :key="idx" class="explanation-card violation">
                  <div class="exp-header">
                    <Badge :value="exp.severity" :severity="exp.severity === 'ERROR' ? 'danger' : 'warning'" />
                    <span class="rule-name">{{ exp.ruleName }}</span>
                  </div>
                  <p class="exp-reason">{{ exp.reason }}</p>
                  <div v-if="exp.legalBasis" class="exp-detail">
                    <strong>Legal Basis:</strong> {{ exp.legalBasis }}
                  </div>
                  <div v-if="exp.resolutions?.length" class="exp-resolutions">
                    <strong>Possible Resolutions:</strong>
                    <ul>
                      <li v-for="(res, ri) in exp.resolutions" :key="ri">{{ res }}</li>
                    </ul>
                  </div>
                </div>
              </div>

              <!-- Triggered Obligations -->
              <div v-if="currentExplanations.obligations?.length" class="explanation-section">
                <h4><i class="pi pi-list-check"></i> License Obligations</h4>
                <div v-for="(obl, idx) in currentExplanations.obligations" :key="idx" class="explanation-card obligation">
                  <div class="exp-header">
                    <Badge :value="obl.effort" :severity="obl.effort === 'HIGH' ? 'danger' : obl.effort === 'MEDIUM' ? 'warning' : 'info'" />
                    <span class="obl-name">{{ obl.name }}</span>
                  </div>
                  <p class="exp-description">{{ obl.description }}</p>
                  <div v-if="obl.scope" class="exp-detail">
                    <strong>Scope:</strong> {{ obl.scope }}
                  </div>
                  <div v-if="obl.trigger" class="exp-detail">
                    <strong>Trigger:</strong> {{ obl.trigger }}
                  </div>
                </div>
              </div>

              <!-- Compatibility Issues -->
              <div v-if="currentExplanations.compatibilityIssues?.length" class="explanation-section">
                <h4><i class="pi pi-exclamation-triangle"></i> Compatibility Issues</h4>
                <div v-for="(iss, idx) in currentExplanations.compatibilityIssues" :key="idx" class="explanation-card compatibility">
                  <div class="exp-header">
                    <Badge :value="iss.severity" :severity="iss.severity === 'BLOCKING' ? 'danger' : 'warning'" />
                    <span>{{ iss.license1 }} ↔ {{ iss.license2 }}</span>
                  </div>
                  <p class="exp-reason">{{ iss.reason }}</p>
                  <div v-if="iss.affectedDependency" class="exp-detail">
                    <strong>Affected:</strong> {{ iss.affectedDependency }}
                  </div>
                </div>
              </div>

              <!-- No issues found -->
              <div v-if="!currentExplanations.whyNotExplanations?.length && !currentExplanations.obligations?.length && !currentExplanations.compatibilityIssues?.length" class="no-explanations">
                <i class="pi pi-check-circle"></i>
                <p>No policy violations, obligations, or compatibility issues found for this dependency.</p>
              </div>
            </div>
            <div v-else class="explanations-loading">
              <i class="pi pi-spin pi-spinner"></i> Loading explanations...
            </div>
          </TabPanel>

          <!-- Tab 3: Justification (EU Compliance) -->
          <TabPanel v-if="session?.status !== 'APPROVED'" header="Justification">
            <div class="justification-content">
              <Message v-if="itemRequiresJustification" severity="info" :closable="false">
                <strong>EU Compliance:</strong> This license requires a structured justification for audit purposes.
              </Message>

              <div class="justification-form">
                <div class="form-row">
                  <div class="field">
                    <label>SPDX License ID *</label>
                    <InputText
                      v-model="justification.spdxId"
                      :placeholder="currentItem.aiSuggestion?.suggestedLicense || 'e.g., MIT, GPL-3.0-only'"
                      class="w-full"
                    />
                  </div>
                  <div class="field">
                    <label>License Category *</label>
                    <Dropdown
                      v-model="justification.licenseCategory"
                      :options="licenseCategoryOptions"
                      optionLabel="label"
                      optionValue="value"
                      class="w-full"
                    />
                  </div>
                </div>

                <div class="field">
                  <label>Concluded License *</label>
                  <InputText
                    v-model="justification.concludedLicense"
                    :placeholder="currentItem.aiSuggestion?.suggestedLicense || 'Final license determination'"
                    class="w-full"
                  />
                </div>

                <div class="form-row">
                  <div class="field">
                    <label>Justification Type *</label>
                    <Dropdown
                      v-model="justification.justificationType"
                      :options="justificationTypeOptions"
                      optionLabel="label"
                      optionValue="value"
                      class="w-full"
                    />
                  </div>
                  <div class="field">
                    <label>Distribution Scope *</label>
                    <Dropdown
                      v-model="justification.distributionScope"
                      :options="distributionScopeOptions"
                      optionLabel="label"
                      optionValue="value"
                      class="w-full"
                    />
                  </div>
                </div>

                <div class="field">
                  <label>Justification Text *</label>
                  <Textarea
                    v-model="justification.justificationText"
                    rows="3"
                    class="w-full"
                    placeholder="Explain why this license determination is correct and acceptable for your use case..."
                  />
                </div>

                <div class="form-row">
                  <div class="field">
                    <label>Evidence Type</label>
                    <Dropdown
                      v-model="justification.evidenceType"
                      :options="evidenceTypeOptions"
                      optionLabel="label"
                      optionValue="value"
                      class="w-full"
                    />
                  </div>
                  <div class="field">
                    <label>Evidence Reference</label>
                    <InputText
                      v-model="justification.evidenceReference"
                      placeholder="URL or document reference..."
                      class="w-full"
                    />
                  </div>
                </div>
              </div>
            </div>
          </TabPanel>
        </TabView>
      </div>

      <template #footer>
        <Button label="Cancel" severity="secondary" @click="showItemDetail = false" />
        <Button
          v-if="session?.status !== 'APPROVED'"
          :label="itemRequiresJustification ? 'Save with Justification' : 'Save Decision'"
          icon="pi pi-check"
          @click="itemRequiresJustification ? submitDecisionWithJustification() : submitDecision()"
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

    <!-- Approve Session Dialog (EU Two-Role Approval) -->
    <Dialog
      v-model:visible="showApproveDialog"
      header="Review & Approve Curation Session"
      :modal="true"
      :style="{ width: '550px' }"
    >
      <div class="approve-content">
        <Message severity="info" :closable="false" class="eu-notice">
          <strong>EU Four-Eyes Principle:</strong> The approver must be a different person than the curator who made the decisions.
        </Message>

        <p>You are reviewing this curation session for final approval. Please verify that all license decisions are correct and properly documented.</p>

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

        <div class="form-row">
          <div class="field">
            <label>Your Name *</label>
            <InputText v-model="approverName" placeholder="Enter your name..." class="w-full" />
          </div>
          <div class="field">
            <label>Your Role *</label>
            <Dropdown
              v-model="approverRole"
              :options="approverRoleOptions"
              optionLabel="label"
              optionValue="value"
              placeholder="Select your role"
              class="w-full"
            />
          </div>
        </div>

        <div class="field">
          <label>Comment (optional)</label>
          <Textarea v-model="approvalComment" rows="2" class="w-full" placeholder="Add approval notes..." />
        </div>
      </div>

      <template #footer>
        <Button label="Cancel" severity="secondary" @click="showApproveDialog = false" />
        <Button
          label="Reject"
          icon="pi pi-times"
          severity="danger"
          outlined
          @click="decideApproval('REJECTED')"
          :loading="submitting"
          :disabled="!approverName || !approverRole"
        />
        <Button
          label="Approve"
          icon="pi pi-check"
          severity="success"
          @click="decideApproval('APPROVED')"
          :loading="submitting"
          :disabled="!approverName || !approverRole"
        />
      </template>
    </Dialog>

    <!-- Submit for Approval Dialog -->
    <Dialog
      v-model:visible="showSubmitForApprovalDialog"
      header="Submit for Approval"
      :modal="true"
      :style="{ width: '500px' }"
    >
      <div class="submit-approval-content">
        <Message severity="info" :closable="false">
          <strong>EU Compliance:</strong> After submission, a different person (approver) must review and sign off on all curation decisions.
        </Message>

        <div class="readiness-summary">
          <h4>Pre-Submission Checklist</h4>
          <div class="checklist">
            <div class="check-item passed">
              <i class="pi pi-check-circle"></i>
              <span>All {{ session?.statistics.total }} items have been reviewed</span>
            </div>
            <div class="check-item" :class="{ passed: approvalStatus?.readiness?.allJustificationsComplete }">
              <i :class="approvalStatus?.readiness?.allJustificationsComplete ? 'pi pi-check-circle' : 'pi pi-exclamation-circle'"></i>
              <span>All required justifications are complete</span>
            </div>
            <div class="check-item" :class="{ passed: unresolvedOrLicenseCount === 0 }">
              <i :class="unresolvedOrLicenseCount === 0 ? 'pi pi-check-circle' : 'pi pi-exclamation-circle'"></i>
              <span>All OR licenses have been resolved</span>
            </div>
          </div>
        </div>

        <div class="field">
          <label>Submission Notes (optional)</label>
          <Textarea v-model="approvalComment" rows="3" class="w-full" placeholder="Add any notes for the approver..." />
        </div>
      </div>

      <template #footer>
        <Button label="Cancel" severity="secondary" @click="showSubmitForApprovalDialog = false" />
        <Button
          label="Submit for Approval"
          icon="pi pi-send"
          severity="success"
          @click="submitForApproval"
          :loading="submitting"
        />
      </template>
    </Dialog>

    <!-- OR License Resolver Dialog -->
    <Dialog
      v-model:visible="showOrLicenseDialog"
      header="Resolve OR License"
      :modal="true"
      :style="{ width: '600px' }"
    >
      <div class="or-license-content">
        <Message severity="warn" :closable="false">
          <strong>Dual/Multi-License Resolution Required:</strong> This dependency is available under multiple licenses. You must explicitly choose which license to use.
        </Message>

        <!-- List of unresolved OR licenses -->
        <div v-if="!currentOrLicense" class="or-license-list">
          <h4>Unresolved OR Licenses ({{ unresolvedOrLicenseCount }})</h4>
          <div v-for="ol in orLicenses.filter(o => !o.isResolved)" :key="ol.dependencyId" class="or-license-item">
            <div class="or-license-info">
              <strong>{{ ol.dependencyName }}</strong>
              <span class="license-expression">{{ ol.originalLicense }}</span>
            </div>
            <Button
              label="Resolve"
              icon="pi pi-arrow-right"
              severity="primary"
              size="small"
              @click="openOrLicenseDialog(ol)"
            />
          </div>
        </div>

        <!-- Resolution form for selected OR license -->
        <div v-else class="or-license-resolver">
          <div class="or-license-header">
            <strong>{{ currentOrLicense.dependencyName }}</strong>
            <span class="version">@{{ currentOrLicense.dependencyVersion }}</span>
          </div>

          <div class="license-expression-display">
            <label>License Expression</label>
            <code>{{ currentOrLicense.originalLicense }}</code>
          </div>

          <div class="field">
            <label>Choose License *</label>
            <div class="license-options">
              <div
                v-for="opt in currentOrLicense.licenseOptions"
                :key="opt"
                class="license-option"
                :class="{ selected: orLicenseChoice === opt }"
                @click="orLicenseChoice = opt"
              >
                <i :class="orLicenseChoice === opt ? 'pi pi-check-circle' : 'pi pi-circle'"></i>
                <span class="license-id">{{ opt }}</span>
              </div>
            </div>
          </div>

          <div class="field">
            <label>Reason for Choice *</label>
            <Textarea
              v-model="orLicenseReason"
              rows="3"
              class="w-full"
              placeholder="Explain why you chose this license (e.g., 'MIT is more permissive and aligns with our distribution model')"
            />
          </div>
        </div>
      </div>

      <template #footer>
        <Button
          v-if="currentOrLicense"
          label="Back to List"
          severity="secondary"
          @click="currentOrLicense = null"
        />
        <Button label="Cancel" severity="secondary" @click="showOrLicenseDialog = false; currentOrLicense = null" />
        <Button
          v-if="currentOrLicense"
          label="Confirm Choice"
          icon="pi pi-check"
          severity="success"
          @click="resolveOrLicense"
          :loading="submitting"
          :disabled="!orLicenseChoice || !orLicenseReason"
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

/* SPDX styling */
.spdx-suggestion {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.spdx-badge {
  display: inline-block;
  padding: 0.125rem 0.375rem;
  border-radius: 0.25rem;
  font-size: 0.6875rem;
  width: fit-content;
}

.spdx-valid {
  background: #d1fae5;
  color: #059669;
}

.spdx-invalid {
  background: #fef3c7;
  color: #d97706;
}

.license-box.spdx {
  background: #eff6ff;
}

.spdx-info-box {
  margin-top: 1rem;
  padding: 1rem;
  background: #eff6ff;
  border-radius: 0.5rem;
  border-left: 3px solid #3b82f6;
}

.spdx-info-box label {
  display: block;
  color: #1d4ed8;
  font-size: 0.75rem;
  text-transform: uppercase;
  margin-bottom: 0.5rem;
}

.spdx-details p {
  margin: 0.25rem 0;
  font-size: 0.875rem;
  color: #374151;
}

/* ====================================================================
   EU Compliance Styles
   ==================================================================== */

.eu-message {
  margin-bottom: 1.5rem;
}

.eu-warning-content {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.eu-warning-content i {
  font-size: 1.5rem;
}

.eu-warning-content div {
  flex: 1;
}

.eu-warning-content strong {
  display: block;
  margin-bottom: 0.25rem;
}

.eu-warning-content p {
  margin: 0;
  font-size: 0.875rem;
  opacity: 0.9;
}

/* Pending Approval Banner */
.pending-approval-banner {
  display: flex;
  align-items: flex-start;
  gap: 1rem;
  padding: 1.25rem 1.5rem;
  background: linear-gradient(135deg, #fef3c7 0%, #fde68a 100%);
  border: 1px solid #f59e0b;
  border-radius: 1rem;
  margin-bottom: 1.5rem;
}

.pending-approval-banner i {
  font-size: 1.5rem;
  color: #d97706;
}

.pending-approval-banner strong {
  color: #92400e;
}

.pending-approval-banner p {
  margin: 0.25rem 0 0;
  color: #b45309;
  font-size: 0.875rem;
}

.pending-approval-banner .approval-actions {
  display: flex;
  gap: 0.5rem;
  margin-left: auto;
}

/* Readiness Banner */
.readiness-banner {
  background: white;
  border-radius: 1rem;
  padding: 1.5rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  margin-bottom: 1.5rem;
  border: 1px solid #e2e8f0;
}

.readiness-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.readiness-header h4 {
  margin: 0;
  color: #1e293b;
}

.readiness-checks {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.check-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  color: #64748b;
}

.check-item i {
  font-size: 1.125rem;
}

.check-item.passed {
  color: #059669;
}

.check-item.passed i {
  color: #10b981;
}

.readiness-action {
  margin-top: 1.5rem;
  padding-top: 1rem;
  border-top: 1px solid #e2e8f0;
}

/* Explanations Panel */
.explanations-content {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.explanation-section h4 {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin: 0 0 1rem;
  color: #1e293b;
  font-size: 1rem;
}

.explanation-card {
  padding: 1rem;
  border-radius: 0.5rem;
  margin-bottom: 0.75rem;
}

.explanation-card.violation {
  background: #fef2f2;
  border-left: 3px solid #ef4444;
}

.explanation-card.obligation {
  background: #eff6ff;
  border-left: 3px solid #3b82f6;
}

.explanation-card.compatibility {
  background: #fefce8;
  border-left: 3px solid #eab308;
}

.exp-header {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 0.5rem;
}

.exp-header .rule-name,
.exp-header .obl-name {
  font-weight: 600;
  color: #1e293b;
}

.exp-reason,
.exp-description {
  margin: 0.5rem 0;
  color: #475569;
  font-size: 0.875rem;
  line-height: 1.5;
}

.exp-detail {
  font-size: 0.8125rem;
  color: #64748b;
  margin-top: 0.5rem;
}

.exp-resolutions {
  margin-top: 0.75rem;
  padding-top: 0.75rem;
  border-top: 1px dashed #e2e8f0;
}

.exp-resolutions strong {
  display: block;
  font-size: 0.8125rem;
  color: #374151;
  margin-bottom: 0.375rem;
}

.exp-resolutions ul {
  margin: 0;
  padding-left: 1.25rem;
}

.exp-resolutions li {
  font-size: 0.8125rem;
  color: #059669;
  margin: 0.25rem 0;
}

.no-explanations {
  text-align: center;
  padding: 2rem;
  color: #10b981;
}

.no-explanations i {
  font-size: 2rem;
  margin-bottom: 0.75rem;
}

.no-explanations p {
  margin: 0;
  color: #64748b;
}

.explanations-loading {
  text-align: center;
  padding: 2rem;
  color: #64748b;
}

/* Justification Form */
.justification-content {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.justification-form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
}

/* Submit for Approval Dialog */
.submit-approval-content {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.readiness-summary h4 {
  margin: 0 0 0.75rem;
  color: #1e293b;
  font-size: 0.9375rem;
}

.checklist {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding: 1rem;
  background: #f8fafc;
  border-radius: 0.5rem;
}

.checklist .check-item {
  font-size: 0.875rem;
}

/* OR License Dialog */
.or-license-content {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.or-license-list h4 {
  margin: 0 0 0.75rem;
  color: #1e293b;
}

.or-license-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1rem;
  background: #f8fafc;
  border-radius: 0.5rem;
  margin-bottom: 0.5rem;
}

.or-license-info {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.license-expression {
  font-family: monospace;
  font-size: 0.875rem;
  color: #64748b;
}

.or-license-resolver {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.or-license-header {
  padding-bottom: 0.75rem;
  border-bottom: 1px solid #e2e8f0;
}

.or-license-header strong {
  font-size: 1.125rem;
  color: #1e293b;
}

.or-license-header .version {
  color: #64748b;
  font-size: 0.875rem;
  margin-left: 0.5rem;
}

.license-expression-display {
  padding: 1rem;
  background: #f8fafc;
  border-radius: 0.5rem;
}

.license-expression-display label {
  display: block;
  font-size: 0.75rem;
  color: #64748b;
  text-transform: uppercase;
  margin-bottom: 0.5rem;
}

.license-expression-display code {
  font-size: 1rem;
  color: #1e293b;
  font-weight: 500;
}

.license-options {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.license-option {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.875rem 1rem;
  border: 2px solid #e2e8f0;
  border-radius: 0.5rem;
  cursor: pointer;
  transition: all 0.15s ease;
}

.license-option:hover {
  border-color: #3b82f6;
  background: #eff6ff;
}

.license-option.selected {
  border-color: #3b82f6;
  background: #dbeafe;
}

.license-option i {
  font-size: 1.125rem;
  color: #94a3b8;
}

.license-option.selected i {
  color: #3b82f6;
}

.license-option .license-id {
  font-family: monospace;
  font-weight: 500;
  color: #1e293b;
}

/* EU Notice in dialogs */
.eu-notice {
  margin-bottom: 1rem;
}
</style>
