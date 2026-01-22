<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import { api, type Policy, type PolicyConfig } from '@/api/client'
import TabView from 'primevue/tabview'
import TabPanel from 'primevue/tabpanel'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Badge from 'primevue/badge'
import Button from 'primevue/button'
import Card from 'primevue/card'
import Accordion from 'primevue/accordion'
import AccordionTab from 'primevue/accordiontab'
import Chip from 'primevue/chip'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import Dropdown from 'primevue/dropdown'
import InputSwitch from 'primevue/inputswitch'
import { useToast } from 'primevue/usetoast'

const route = useRoute()
const toast = useToast()

const policy = ref<Policy | null>(null)
const policyConfig = ref<PolicyConfig | null>(null)
const loading = ref(true)
const saving = ref(false)
const activeTab = ref(0)

// Edit form refs
const editName = ref('')
const editDescription = ref('')
const editConfig = ref<PolicyConfig | null>(null)

const severityOptions = [
  { label: 'Error', value: 'ERROR' },
  { label: 'Warning', value: 'WARNING' },
  { label: 'Info', value: 'INFO' }
]

const actionOptions = [
  { label: 'Deny', value: 'DENY' },
  { label: 'Warn', value: 'WARN' },
  { label: 'Allow', value: 'ALLOW' }
]

const confidenceOptions = [
  { label: 'High', value: 'HIGH' },
  { label: 'Medium', value: 'MEDIUM' },
  { label: 'Low', value: 'LOW' }
]

async function fetchPolicy() {
  loading.value = true
  try {
    const response = await api.getPolicy(route.params.id as string)
    policy.value = response.data

    try {
      policyConfig.value = JSON.parse(response.data.config) as PolicyConfig
      // Initialize edit form
      editName.value = policyConfig.value.name || response.data.name
      editDescription.value = policyConfig.value.description || ''
      editConfig.value = JSON.parse(JSON.stringify(policyConfig.value)) // Deep clone
    } catch (e) {
      console.error('Failed to parse policy config', e)
      policyConfig.value = null
    }
  } catch (e) {
    console.error('Failed to fetch policy', e)
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to fetch policy', life: 3000 })
  } finally {
    loading.value = false
  }
}

function getSeverityColor(severity: string) {
  switch (severity) {
    case 'ERROR': return 'danger'
    case 'WARNING': return 'warning'
    case 'INFO': return 'info'
    default: return 'secondary'
  }
}

function getActionColor(action: string) {
  switch (action) {
    case 'DENY': return 'danger'
    case 'WARN': return 'warning'
    case 'ALLOW': return 'success'
    default: return 'secondary'
  }
}

function addRule() {
  if (!editConfig.value) return
  editConfig.value.rules.push({
    name: 'new-rule',
    description: 'New rule description',
    severity: 'WARNING',
    action: 'WARN',
    enabled: true
  })
}

function removeRule(index: number) {
  if (!editConfig.value) return
  editConfig.value.rules.splice(index, 1)
}

function addExemption() {
  if (!editConfig.value) return
  editConfig.value.exemptions.push('')
}

function removeExemption(index: number) {
  if (!editConfig.value) return
  editConfig.value.exemptions.splice(index, 1)
}

async function savePolicy() {
  if (!policy.value || !editConfig.value) return

  saving.value = true
  try {
    editConfig.value.name = editName.value
    editConfig.value.description = editDescription.value

    await api.updatePolicy(policy.value.id, {
      name: editName.value,
      config: JSON.stringify(editConfig.value)
    })

    toast.add({ severity: 'success', summary: 'Success', detail: 'Policy saved successfully', life: 3000 })
    await fetchPolicy()
    activeTab.value = 0 // Switch back to overview
  } catch (e) {
    console.error('Failed to save policy', e)
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to save policy', life: 3000 })
  } finally {
    saving.value = false
  }
}

function resetEdit() {
  if (policyConfig.value) {
    editName.value = policyConfig.value.name || policy.value?.name || ''
    editDescription.value = policyConfig.value.description || ''
    editConfig.value = JSON.parse(JSON.stringify(policyConfig.value))
  }
}

onMounted(fetchPolicy)
</script>

<template>
  <div class="policy-detail">
    <div v-if="loading" class="loading">
      <i class="pi pi-spin pi-spinner"></i> Loading policy...
    </div>

    <template v-else-if="policy && policyConfig">
      <header class="page-header">
        <div>
          <div class="breadcrumb">
            <RouterLink to="/policies">Policies</RouterLink>
            <i class="pi pi-angle-right"></i>
            <span>{{ policy.name }}</span>
          </div>
          <div class="title-row">
            <h1>{{ policy.name }}</h1>
            <Badge v-if="policy.isDefault" value="Default" severity="info" />
          </div>
          <p class="description" v-if="policyConfig.description">{{ policyConfig.description }}</p>
        </div>
      </header>

      <TabView v-model:activeIndex="activeTab">
        <!-- Overview Tab -->
        <TabPanel header="Overview">
          <div class="overview-grid">
            <Card class="stat-card">
              <template #content>
                <div class="stat">
                  <i class="pi pi-list"></i>
                  <div>
                    <div class="stat-value">{{ policyConfig.rules?.length || 0 }}</div>
                    <div class="stat-label">Rules</div>
                  </div>
                </div>
              </template>
            </Card>

            <Card class="stat-card">
              <template #content>
                <div class="stat">
                  <i class="pi pi-check-circle" style="color: #22c55e;"></i>
                  <div>
                    <div class="stat-value">{{ policyConfig.allowedLicenses?.length || 0 }}</div>
                    <div class="stat-label">Allowed Licenses</div>
                  </div>
                </div>
              </template>
            </Card>

            <Card class="stat-card">
              <template #content>
                <div class="stat">
                  <i class="pi pi-ban" style="color: #ef4444;"></i>
                  <div>
                    <div class="stat-value">{{ policyConfig.deniedLicenses?.length || 0 }}</div>
                    <div class="stat-label">Denied Licenses</div>
                  </div>
                </div>
              </template>
            </Card>

            <Card class="stat-card">
              <template #content>
                <div class="stat">
                  <i class="pi pi-shield" style="color: #8b5cf6;"></i>
                  <div>
                    <div class="stat-value">{{ policyConfig.exemptions?.length || 0 }}</div>
                    <div class="stat-label">Exemptions</div>
                  </div>
                </div>
              </template>
            </Card>
          </div>

          <div class="section">
            <h3>Settings</h3>
            <div class="settings-grid">
              <div class="setting-item">
                <span class="setting-label">Fail on Error</span>
                <Badge :value="policyConfig.settings?.failOnError ? 'Yes' : 'No'"
                       :severity="policyConfig.settings?.failOnError ? 'danger' : 'secondary'" />
              </div>
              <div class="setting-item">
                <span class="setting-label">Fail on Warning</span>
                <Badge :value="policyConfig.settings?.failOnWarning ? 'Yes' : 'No'"
                       :severity="policyConfig.settings?.failOnWarning ? 'warning' : 'secondary'" />
              </div>
              <div class="setting-item">
                <span class="setting-label">Accept AI Suggestions</span>
                <Badge :value="policyConfig.settings?.acceptAiSuggestions ? 'Yes' : 'No'"
                       :severity="policyConfig.settings?.acceptAiSuggestions ? 'success' : 'secondary'" />
              </div>
              <div class="setting-item">
                <span class="setting-label">Minimum Confidence</span>
                <Badge :value="policyConfig.settings?.minimumConfidence || 'N/A'" severity="info" />
              </div>
            </div>
          </div>

          <div class="section">
            <h3>License Lists</h3>
            <div class="license-lists">
              <div class="license-list">
                <h4><i class="pi pi-check-circle"></i> Allowed Licenses</h4>
                <div class="chips">
                  <Chip v-for="license in policyConfig.allowedLicenses" :key="license" :label="license" class="allowed-chip" />
                  <span v-if="!policyConfig.allowedLicenses?.length" class="no-items">No allowed licenses defined</span>
                </div>
              </div>
              <div class="license-list">
                <h4><i class="pi pi-ban"></i> Denied Licenses</h4>
                <div class="chips">
                  <Chip v-for="license in policyConfig.deniedLicenses" :key="license" :label="license" class="denied-chip" />
                  <span v-if="!policyConfig.deniedLicenses?.length" class="no-items">No denied licenses defined</span>
                </div>
              </div>
            </div>
          </div>
        </TabPanel>

        <!-- Rules Tab -->
        <TabPanel header="Rules">
          <DataTable :value="policyConfig.rules" stripedRows showGridlines class="p-datatable-sm">
            <template #empty>
              <div class="empty-table">No rules configured</div>
            </template>

            <Column field="name" header="Name" style="min-width: 150px">
              <template #body="{ data }">
                <strong>{{ data.name }}</strong>
              </template>
            </Column>

            <Column field="description" header="Description" style="min-width: 250px" />

            <Column field="severity" header="Severity" style="width: 120px">
              <template #body="{ data }">
                <Badge :value="data.severity" :severity="getSeverityColor(data.severity)" />
              </template>
            </Column>

            <Column field="action" header="Action" style="width: 100px">
              <template #body="{ data }">
                <Badge :value="data.action" :severity="getActionColor(data.action)" />
              </template>
            </Column>

            <Column field="enabled" header="Enabled" style="width: 100px">
              <template #body="{ data }">
                <i :class="data.enabled ? 'pi pi-check-circle text-green' : 'pi pi-times-circle text-red'"></i>
              </template>
            </Column>
          </DataTable>
        </TabPanel>

        <!-- Categories Tab -->
        <TabPanel header="Categories">
          <Accordion v-if="policyConfig.licenseCategories?.length">
            <AccordionTab v-for="category in policyConfig.licenseCategories" :key="category.name" :header="category.name">
              <div class="chips">
                <Chip v-for="license in category.licenses" :key="license" :label="license" />
              </div>
            </AccordionTab>
          </Accordion>
          <div v-else class="empty-section">
            <i class="pi pi-folder-open"></i>
            <p>No license categories defined</p>
          </div>
        </TabPanel>

        <!-- Exemptions Tab -->
        <TabPanel header="Exemptions">
          <div v-if="policyConfig.exemptions?.length" class="exemptions-list">
            <DataTable :value="policyConfig.exemptions.map((e, i) => ({ id: i, dependency: e }))" stripedRows showGridlines class="p-datatable-sm">
              <Column field="dependency" header="Exempted Dependency" />
            </DataTable>
          </div>
          <div v-else class="empty-section">
            <i class="pi pi-shield"></i>
            <p>No exemptions defined</p>
            <small>Exemptions allow specific dependencies to bypass policy checks</small>
          </div>
        </TabPanel>

        <!-- Edit Tab -->
        <TabPanel header="Edit">
          <div v-if="editConfig" class="edit-form">
            <div class="section">
              <h3>Basic Information</h3>
              <div class="form-row">
                <div class="form-group">
                  <label>Policy Name</label>
                  <InputText v-model="editName" class="w-full" />
                </div>
              </div>
              <div class="form-row">
                <div class="form-group">
                  <label>Description</label>
                  <Textarea v-model="editDescription" rows="3" class="w-full" />
                </div>
              </div>
            </div>

            <div class="section">
              <div class="section-header">
                <h3>Rules</h3>
                <Button label="Add Rule" icon="pi pi-plus" size="small" @click="addRule" />
              </div>

              <div v-for="(rule, index) in editConfig.rules" :key="index" class="rule-editor">
                <div class="rule-header">
                  <InputText v-model="rule.name" placeholder="Rule name" class="rule-name-input" />
                  <Button icon="pi pi-trash" severity="danger" text size="small" @click="removeRule(index)" />
                </div>
                <div class="rule-fields">
                  <div class="field">
                    <label>Description</label>
                    <InputText v-model="rule.description" class="w-full" />
                  </div>
                  <div class="field-row">
                    <div class="field">
                      <label>Severity</label>
                      <Dropdown v-model="rule.severity" :options="severityOptions" optionLabel="label" optionValue="value" class="w-full" />
                    </div>
                    <div class="field">
                      <label>Action</label>
                      <Dropdown v-model="rule.action" :options="actionOptions" optionLabel="label" optionValue="value" class="w-full" />
                    </div>
                    <div class="field switch-field">
                      <label>Enabled</label>
                      <InputSwitch v-model="rule.enabled" />
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div class="section">
              <h3>Settings</h3>
              <div class="settings-form">
                <div class="setting-row">
                  <label>Fail on Error</label>
                  <InputSwitch v-model="editConfig.settings.failOnError" />
                </div>
                <div class="setting-row">
                  <label>Fail on Warning</label>
                  <InputSwitch v-model="editConfig.settings.failOnWarning" />
                </div>
                <div class="setting-row">
                  <label>Accept AI Suggestions</label>
                  <InputSwitch v-model="editConfig.settings.acceptAiSuggestions" />
                </div>
                <div class="setting-row">
                  <label>Minimum Confidence</label>
                  <Dropdown v-model="editConfig.settings.minimumConfidence" :options="confidenceOptions" optionLabel="label" optionValue="value" style="width: 150px" />
                </div>
              </div>
            </div>

            <div class="section">
              <div class="section-header">
                <h3>Exemptions</h3>
                <Button label="Add Exemption" icon="pi pi-plus" size="small" @click="addExemption" />
              </div>
              <div v-for="(_exemption, index) in editConfig.exemptions" :key="index" class="exemption-row">
                <InputText v-model="editConfig.exemptions[index]" placeholder="Dependency name (e.g., com.example:library)" class="w-full" />
                <Button icon="pi pi-trash" severity="danger" text size="small" @click="removeExemption(index)" />
              </div>
              <div v-if="!editConfig.exemptions?.length" class="no-items">
                No exemptions. Click "Add Exemption" to add one.
              </div>
            </div>

            <div class="form-actions">
              <Button label="Reset" severity="secondary" @click="resetEdit" />
              <Button label="Save Changes" icon="pi pi-check" @click="savePolicy" :loading="saving" />
            </div>
          </div>
        </TabPanel>
      </TabView>
    </template>

    <div v-else class="error-state">
      <i class="pi pi-exclamation-triangle"></i>
      <h3>Policy Not Found</h3>
      <p>The requested policy could not be found or has an invalid configuration.</p>
      <RouterLink to="/policies">
        <Button label="Back to Policies" />
      </RouterLink>
    </div>
  </div>
</template>

<style scoped>
.policy-detail {
  max-width: 1400px;
  margin: 0 auto;
}

.page-header {
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

.title-row {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.page-header h1 {
  margin: 0;
  font-size: 2rem;
  color: #1e293b;
}

.description {
  color: #64748b;
  margin-top: 0.5rem;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 1rem;
  margin-bottom: 1.5rem;
}

.stat-card :deep(.p-card-content) {
  padding: 0;
}

.stat {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.stat i {
  font-size: 2rem;
  color: #8b5cf6;
}

.stat-value {
  font-size: 1.75rem;
  font-weight: 600;
  color: #1e293b;
}

.stat-label {
  color: #64748b;
  font-size: 0.875rem;
}

.section {
  background: white;
  border-radius: 0.75rem;
  padding: 1.5rem;
  margin-bottom: 1.5rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.section h3 {
  margin: 0 0 1rem;
  font-size: 1.125rem;
  color: #1e293b;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.section-header h3 {
  margin: 0;
}

.settings-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 1rem;
}

.setting-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.75rem;
  background: #f8fafc;
  border-radius: 0.5rem;
}

.setting-label {
  color: #475569;
  font-size: 0.875rem;
}

.license-lists {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 1.5rem;
}

.license-list h4 {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin: 0 0 0.75rem;
  font-size: 1rem;
  color: #374151;
}

.license-list h4 .pi-check-circle {
  color: #22c55e;
}

.license-list h4 .pi-ban {
  color: #ef4444;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.allowed-chip :deep(.p-chip) {
  background: #d1fae5;
  color: #065f46;
}

.denied-chip :deep(.p-chip) {
  background: #fee2e2;
  color: #991b1b;
}

.no-items {
  color: #94a3b8;
  font-style: italic;
}

.empty-table,
.empty-section {
  text-align: center;
  padding: 2rem;
  color: #64748b;
}

.empty-section i {
  font-size: 2.5rem;
  margin-bottom: 0.75rem;
  color: #cbd5e1;
}

.empty-section p {
  margin: 0.5rem 0;
}

.empty-section small {
  font-size: 0.875rem;
}

.text-green {
  color: #22c55e;
}

.text-red {
  color: #ef4444;
}

/* Edit form styles */
.edit-form {
  max-width: 800px;
}

.form-row {
  margin-bottom: 1rem;
}

.form-group label {
  display: block;
  margin-bottom: 0.5rem;
  font-weight: 500;
  color: #374151;
}

.w-full {
  width: 100%;
}

.rule-editor {
  border: 1px solid #e2e8f0;
  border-radius: 0.5rem;
  padding: 1rem;
  margin-bottom: 1rem;
  background: #f8fafc;
}

.rule-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.75rem;
}

.rule-name-input {
  font-weight: 600;
}

.rule-fields .field {
  margin-bottom: 0.75rem;
}

.rule-fields .field label {
  display: block;
  margin-bottom: 0.25rem;
  font-size: 0.875rem;
  color: #64748b;
}

.field-row {
  display: grid;
  grid-template-columns: 1fr 1fr auto;
  gap: 1rem;
  align-items: end;
}

.switch-field {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.settings-form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.setting-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.5rem 0;
}

.setting-row label {
  color: #374151;
}

.exemption-row {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  margin-top: 2rem;
  padding-top: 1.5rem;
  border-top: 1px solid #e2e8f0;
}

.loading {
  text-align: center;
  padding: 4rem;
  color: #64748b;
}

.error-state {
  text-align: center;
  padding: 4rem;
}

.error-state i {
  font-size: 3rem;
  color: #f59e0b;
  margin-bottom: 1rem;
}

.error-state h3 {
  margin: 0 0 0.5rem;
  color: #1e293b;
}

.error-state p {
  color: #64748b;
  margin-bottom: 1.5rem;
}
</style>
