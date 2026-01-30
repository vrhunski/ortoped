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
  { label: 'Allow', value: 'ALLOW' },
  { label: 'Deny', value: 'DENY' },
  { label: 'Review', value: 'REVIEW' }
]

// Ensure config has all required nested properties with defaults
function normalizeConfig(config: PolicyConfig): PolicyConfig {
  return {
    ...config,
    settings: {
      ...config.settings,
      failOn: {
        errors: true,
        warnings: false,
        ...config.settings?.failOn
      },
      aiSuggestions: {
        acceptHighConfidence: true,
        treatMediumAsWarning: true,
        rejectLowConfidence: false,
        ...config.settings?.aiSuggestions
      },
      exemptions: config.settings?.exemptions || []
    }
  }
}

async function fetchPolicy() {
  loading.value = true
  try {
    const response = await api.getPolicy(route.params.id as string)
    policy.value = response.data

    try {
      const parsedConfig = JSON.parse(response.data.config) as PolicyConfig
      policyConfig.value = normalizeConfig(parsedConfig)
      // Initialize edit form
      editName.value = policyConfig.value.name || response.data.name
      editDescription.value = policyConfig.value.description || ''
      editConfig.value = normalizeConfig(JSON.parse(JSON.stringify(parsedConfig))) // Deep clone with defaults
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
  const ruleId = `rule-${Date.now()}`
  editConfig.value.rules.push({
    id: ruleId,
    name: 'new-rule',
    description: 'New rule description',
    severity: 'WARNING',
    enabled: true,
    scopes: [],
    action: 'REVIEW'
  })
}

function removeRule(index: number) {
  if (!editConfig.value) return
  editConfig.value.rules.splice(index, 1)
}

function addExemption() {
  if (!editConfig.value) return
  if (!editConfig.value.settings.exemptions) {
    editConfig.value.settings.exemptions = []
  }
  editConfig.value.settings.exemptions.push({
    dependency: '',
    reason: '',
    approvedBy: '',
    approvedDate: ''
  })
}

function removeExemption(index: number) {
  if (!editConfig.value || !editConfig.value.settings.exemptions) return
  editConfig.value.settings.exemptions.splice(index, 1)
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
    editConfig.value = normalizeConfig(JSON.parse(JSON.stringify(policyConfig.value)))
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
                  <i class="pi pi-tags" style="color: #22c55e;"></i>
                  <div>
                    <div class="stat-value">{{ Object.keys(policyConfig.categories || {}).length }}</div>
                    <div class="stat-label">Categories</div>
                  </div>
                </div>
              </template>
            </Card>

            <Card class="stat-card">
              <template #content>
                <div class="stat">
                  <i class="pi pi-cog" style="color: #3b82f6;"></i>
                  <div>
                    <div class="stat-value">{{ policyConfig.settings?.exemptions?.length || 0 }}</div>
                    <div class="stat-label">Exemptions</div>
                  </div>
                </div>
              </template>
            </Card>

            <Card class="stat-card">
              <template #content>
                <div class="stat">
                  <i class="pi pi-info-circle" style="color: #f59e0b;"></i>
                  <div>
                    <div class="stat-value">{{ policyConfig.version }}</div>
                    <div class="stat-label">Version</div>
                  </div>
                </div>
              </template>
            </Card>
          </div>

          <div class="section">
            <h3>Settings</h3>
            <div class="settings-grid">
              <div class="setting-item">
                <span class="setting-label">Fail on Errors</span>
                <Badge :value="policyConfig.settings?.failOn?.errors ? 'Yes' : 'No'"
                       :severity="policyConfig.settings?.failOn?.errors ? 'danger' : 'secondary'" />
              </div>
              <div class="setting-item">
                <span class="setting-label">Fail on Warnings</span>
                <Badge :value="policyConfig.settings?.failOn?.warnings ? 'Yes' : 'No'"
                       :severity="policyConfig.settings?.failOn?.warnings ? 'warning' : 'secondary'" />
              </div>
              <div class="setting-item">
                <span class="setting-label">AI High Confidence</span>
                <Badge :value="policyConfig.settings?.aiSuggestions?.acceptHighConfidence ? 'Accept' : 'Review'"
                       :severity="policyConfig.settings?.aiSuggestions?.acceptHighConfidence ? 'success' : 'secondary'" />
              </div>
              <div class="setting-item">
                <span class="setting-label">AI Medium Confidence</span>
                <Badge :value="policyConfig.settings?.aiSuggestions?.treatMediumAsWarning ? 'Warning' : 'Review'"
                       severity="info" />
              </div>
            </div>
          </div>
          <div class="section">
            <h3>License Categories</h3>
            <div class="categories-summary">
              <div v-for="(category, categoryName) in policyConfig.categories" :key="categoryName" class="category-summary">
                <div class="category-header">
                  <h4>{{ categoryName }}</h4>
                  <Badge :value="category.licenses?.length || 0" severity="info" />
                </div>
                <p v-if="category.description" class="category-description">{{ category.description }}</p>
                <div class="category-licenses">
                  <Chip v-for="license in (category.licenses || []).slice(0, 5)" :key="license" :label="license" size="small" />
                  <Badge v-if="(category.licenses || []).length > 5" :value="`+${(category.licenses || []).length - 5} more`" severity="secondary" />
                </div>
              </div>
              <div v-if="!policyConfig.categories || !Object.keys(policyConfig.categories).length" class="no-categories">
                No license categories defined
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
          <Accordion v-if="policyConfig.categories && Object.keys(policyConfig.categories).length">
            <AccordionTab v-for="(category, categoryName) in policyConfig.categories" :key="categoryName" :header="categoryName">
              <div class="category-info" v-if="category.description">
                <p class="category-description">{{ category.description }}</p>
              </div>
              <div class="chips">
                <Chip v-for="license in category.licenses" :key="license" :label="license" />
              </div>
              <div v-if="!category.licenses?.length" class="no-licenses">
                No licenses in this category
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
          <div v-if="policyConfig.settings?.exemptions?.length" class="exemptions-list">
            <DataTable :value="policyConfig.settings.exemptions" stripedRows showGridlines class="p-datatable-sm">
              <Column field="dependency" header="Dependency Pattern" style="min-width: 200px" />
              <Column field="reason" header="Reason" style="min-width: 250px" />
              <Column field="approvedBy" header="Approved By" style="min-width: 150px" />
              <Column field="approvedDate" header="Approval Date" style="min-width: 120px" />
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

            <div class="section" v-if="editConfig.settings.failOn && editConfig.settings.aiSuggestions">
              <h3>Settings</h3>
              <div class="settings-form">
                <div class="setting-row">
                  <label>Fail on Errors</label>
                  <InputSwitch v-model="editConfig.settings.failOn.errors" />
                </div>
                <div class="setting-row">
                  <label>Fail on Warnings</label>
                  <InputSwitch v-model="editConfig.settings.failOn.warnings" />
                </div>
                <div class="setting-row">
                  <label>Accept High Confidence AI</label>
                  <InputSwitch v-model="editConfig.settings.aiSuggestions.acceptHighConfidence" />
                </div>
                <div class="setting-row">
                  <label>Treat Medium AI as Warning</label>
                  <InputSwitch v-model="editConfig.settings.aiSuggestions.treatMediumAsWarning" />
                </div>
              </div>
            </div>

            <div class="section" v-if="editConfig.settings.exemptions">
              <div class="section-header">
                <h3>Exemptions</h3>
                <Button label="Add Exemption" icon="pi pi-plus" size="small" @click="addExemption" />
              </div>
              <div v-for="(exemption, index) in editConfig.settings.exemptions" :key="index" class="exemption-item">
                <div class="exemption-fields">
                  <InputText v-model="exemption.dependency" placeholder="Dependency pattern (e.g., npm::package:* )" class="w-full mb-2" />
                  <InputText v-model="exemption.reason" placeholder="Reason for exemption" class="w-full mb-2" />
                  <InputText v-model="exemption.approvedBy" placeholder="Approved by" class="w-full mb-2" />
                  <InputText v-model="exemption.approvedDate" placeholder="Approval date" class="w-full" />
                </div>
                <Button icon="pi pi-trash" severity="danger" text size="small" @click="removeExemption(index)" />
              </div>
              <div v-if="!editConfig.settings.exemptions.length" class="no-items">
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

/* Category summary styles */
.categories-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 1rem;
}

.category-summary {
  border: 1px solid #e2e8f0;
  border-radius: 0.5rem;
  padding: 1rem;
  background: #f8fafc;
}

.category-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.5rem;
}

.category-header h4 {
  margin: 0;
  font-size: 1rem;
  color: #1e293b;
}

.category-description {
  color: #64748b;
  font-size: 0.875rem;
  margin: 0.5rem 0;
}

.category-licenses {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
}

.no-categories {
  text-align: center;
  padding: 2rem;
  color: #94a3b8;
  font-style: italic;
  grid-column: 1 / -1;
}

.category-info {
  margin-bottom: 0.75rem;
}

.no-licenses {
  color: #94a3b8;
  font-style: italic;
  font-size: 0.875rem;
}

.exemption-item {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
  padding: 0.75rem;
  border: 1px solid #e2e8f0;
  border-radius: 0.5rem;
  background: #f8fafc;
}

.exemption-fields {
  flex: 1;
}
</style>
