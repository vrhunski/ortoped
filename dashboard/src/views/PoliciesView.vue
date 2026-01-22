<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { api, type Policy, type PolicyConfig } from '@/api/client'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import Checkbox from 'primevue/checkbox'
import Badge from 'primevue/badge'
import { useToast } from 'primevue/usetoast'

const router = useRouter()
const toast = useToast()

const policies = ref<Policy[]>([])
const loading = ref(true)
const createDialogVisible = ref(false)
const deleteDialogVisible = ref(false)
const policyToDelete = ref<Policy | null>(null)

// Create form
const newPolicyName = ref('')
const newPolicyConfig = ref('')
const newPolicyIsDefault = ref(false)
const creating = ref(false)

const defaultPolicyConfig: PolicyConfig = {
  version: '1.0',
  name: '',
  description: '',
  rules: [
    {
      name: 'deny-copyleft',
      description: 'Deny strong copyleft licenses',
      severity: 'ERROR',
      action: 'DENY',
      enabled: true
    },
    {
      name: 'warn-unknown',
      description: 'Warn on unknown licenses',
      severity: 'WARNING',
      action: 'WARN',
      enabled: true
    }
  ],
  allowedLicenses: ['MIT', 'Apache-2.0', 'BSD-2-Clause', 'BSD-3-Clause', 'ISC'],
  deniedLicenses: ['GPL-2.0-only', 'GPL-3.0-only', 'AGPL-3.0-only'],
  licenseCategories: [
    { name: 'permissive', licenses: ['MIT', 'Apache-2.0', 'BSD-2-Clause', 'BSD-3-Clause'] },
    { name: 'copyleft', licenses: ['GPL-2.0-only', 'GPL-3.0-only', 'LGPL-2.1-only'] }
  ],
  exemptions: [],
  settings: {
    failOnError: true,
    failOnWarning: false,
    acceptAiSuggestions: true,
    minimumConfidence: 'MEDIUM'
  }
}

function getRulesCount(policy: Policy): number {
  try {
    const config = JSON.parse(policy.config) as PolicyConfig
    return config.rules?.length || 0
  } catch {
    return 0
  }
}

async function fetchPolicies() {
  loading.value = true
  try {
    const response = await api.listPolicies(1, 100)
    policies.value = response.data.policies
  } catch (e) {
    console.error('Failed to fetch policies', e)
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to fetch policies', life: 3000 })
  } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  newPolicyName.value = ''
  const config = { ...defaultPolicyConfig, name: '' }
  newPolicyConfig.value = JSON.stringify(config, null, 2)
  newPolicyIsDefault.value = false
  createDialogVisible.value = true
}

async function createPolicy() {
  if (!newPolicyName.value.trim()) {
    toast.add({ severity: 'warn', summary: 'Warning', detail: 'Please enter a policy name', life: 3000 })
    return
  }

  try {
    JSON.parse(newPolicyConfig.value)
  } catch {
    toast.add({ severity: 'error', summary: 'Error', detail: 'Invalid JSON configuration', life: 3000 })
    return
  }

  creating.value = true
  try {
    // Update the config name to match the policy name
    const config = JSON.parse(newPolicyConfig.value)
    config.name = newPolicyName.value

    await api.createPolicy({
      name: newPolicyName.value,
      config: JSON.stringify(config),
      isDefault: newPolicyIsDefault.value
    })
    toast.add({ severity: 'success', summary: 'Success', detail: 'Policy created successfully', life: 3000 })
    createDialogVisible.value = false
    await fetchPolicies()
  } catch (e) {
    console.error('Failed to create policy', e)
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to create policy', life: 3000 })
  } finally {
    creating.value = false
  }
}

function viewPolicy(policy: Policy) {
  router.push(`/policies/${policy.id}`)
}

function confirmDelete(policy: Policy) {
  policyToDelete.value = policy
  deleteDialogVisible.value = true
}

async function deletePolicy() {
  if (!policyToDelete.value) return

  try {
    await api.deletePolicy(policyToDelete.value.id)
    toast.add({ severity: 'success', summary: 'Success', detail: 'Policy deleted successfully', life: 3000 })
    deleteDialogVisible.value = false
    policyToDelete.value = null
    await fetchPolicies()
  } catch (e) {
    console.error('Failed to delete policy', e)
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to delete policy', life: 3000 })
  }
}

onMounted(fetchPolicies)
</script>

<template>
  <div class="policies-page">
    <header class="page-header">
      <div>
        <h1>Policies</h1>
        <p class="subtitle">Manage license compliance policies for your projects</p>
      </div>
      <Button label="Create Policy" icon="pi pi-plus" @click="openCreateDialog" />
    </header>

    <div class="section">
      <DataTable
        :value="policies"
        :loading="loading"
        stripedRows
        showGridlines
        tableStyle="min-width: 50rem"
        class="p-datatable-sm"
      >
        <template #empty>
          <div class="empty-state">
            <i class="pi pi-shield"></i>
            <h3>No policies found</h3>
            <p>Create your first policy to start managing license compliance.</p>
            <Button label="Create Policy" icon="pi pi-plus" @click="openCreateDialog" />
          </div>
        </template>

        <Column field="name" header="Name" sortable style="min-width: 200px">
          <template #body="{ data }">
            <div class="policy-name">
              <i class="pi pi-shield"></i>
              <span>{{ data.name }}</span>
              <Badge v-if="data.isDefault" value="Default" severity="info" />
            </div>
          </template>
        </Column>

        <Column header="Rules" style="width: 100px">
          <template #body="{ data }">
            <Badge :value="getRulesCount(data)" severity="secondary" />
          </template>
        </Column>

        <Column field="createdAt" header="Created" sortable style="width: 180px">
          <template #body="{ data }">
            {{ new Date(data.createdAt).toLocaleDateString() }}
          </template>
        </Column>

        <Column header="Actions" style="width: 150px">
          <template #body="{ data }">
            <div class="action-buttons">
              <Button
                icon="pi pi-eye"
                severity="secondary"
                text
                rounded
                @click="viewPolicy(data)"
                v-tooltip.top="'View'"
              />
              <Button
                icon="pi pi-trash"
                severity="danger"
                text
                rounded
                @click="confirmDelete(data)"
                v-tooltip.top="'Delete'"
                :disabled="data.isDefault"
              />
            </div>
          </template>
        </Column>
      </DataTable>
    </div>

    <!-- Create Policy Dialog -->
    <Dialog
      v-model:visible="createDialogVisible"
      header="Create Policy"
      modal
      :style="{ width: '600px' }"
      :closable="!creating"
    >
      <div class="form-group">
        <label for="policyName">Policy Name</label>
        <InputText
          id="policyName"
          v-model="newPolicyName"
          placeholder="Enter policy name"
          class="w-full"
        />
      </div>

      <div class="form-group">
        <label for="policyConfig">Configuration (JSON)</label>
        <Textarea
          id="policyConfig"
          v-model="newPolicyConfig"
          rows="15"
          class="w-full config-textarea"
          placeholder="Enter policy configuration..."
        />
      </div>

      <div class="form-group checkbox-group">
        <Checkbox v-model="newPolicyIsDefault" inputId="isDefault" :binary="true" />
        <label for="isDefault">Set as default policy</label>
      </div>

      <template #footer>
        <Button label="Cancel" severity="secondary" @click="createDialogVisible = false" :disabled="creating" />
        <Button label="Create" icon="pi pi-check" @click="createPolicy" :loading="creating" />
      </template>
    </Dialog>

    <!-- Delete Confirmation Dialog -->
    <Dialog
      v-model:visible="deleteDialogVisible"
      header="Confirm Delete"
      modal
      :style="{ width: '400px' }"
    >
      <div class="delete-confirm">
        <i class="pi pi-exclamation-triangle"></i>
        <p>Are you sure you want to delete the policy <strong>{{ policyToDelete?.name }}</strong>?</p>
        <p class="warning-text">This action cannot be undone.</p>
      </div>

      <template #footer>
        <Button label="Cancel" severity="secondary" @click="deleteDialogVisible = false" />
        <Button label="Delete" severity="danger" icon="pi pi-trash" @click="deletePolicy" />
      </template>
    </Dialog>
  </div>
</template>

<style scoped>
.policies-page {
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
  margin-top: 0.25rem;
}

.section {
  background: white;
  border-radius: 1rem;
  padding: 1.5rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.policy-name {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.policy-name i {
  color: #8b5cf6;
}

.action-buttons {
  display: flex;
  gap: 0.25rem;
}

.empty-state {
  text-align: center;
  padding: 3rem;
  color: #64748b;
}

.empty-state i {
  font-size: 3rem;
  margin-bottom: 1rem;
  color: #cbd5e1;
}

.empty-state h3 {
  margin: 0 0 0.5rem;
  color: #475569;
}

.empty-state p {
  margin-bottom: 1rem;
}

.form-group {
  margin-bottom: 1rem;
}

.form-group label {
  display: block;
  margin-bottom: 0.5rem;
  font-weight: 500;
  color: #374151;
}

.checkbox-group {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.checkbox-group label {
  display: inline;
  margin-bottom: 0;
}

.config-textarea {
  font-family: monospace;
  font-size: 0.85rem;
}

.delete-confirm {
  text-align: center;
  padding: 1rem;
}

.delete-confirm i {
  font-size: 3rem;
  color: #f59e0b;
  margin-bottom: 1rem;
}

.delete-confirm p {
  margin: 0.5rem 0;
}

.warning-text {
  color: #dc2626;
  font-size: 0.875rem;
}

.w-full {
  width: 100%;
}
</style>
