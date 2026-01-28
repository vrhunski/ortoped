<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { api, type CurationTemplate, type TemplateCondition, type TemplateAction } from '@/api/client'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Badge from 'primevue/badge'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import Dropdown from 'primevue/dropdown'
import InputSwitch from 'primevue/inputswitch'
import { useToast } from 'primevue/usetoast'

const toast = useToast()

// Data
const templates = ref<CurationTemplate[]>([])
const loading = ref(true)

// Dialog states
const showEditor = ref(false)
const showDeleteConfirm = ref(false)
const editingTemplate = ref<CurationTemplate | null>(null)
const templateToDelete = ref<CurationTemplate | null>(null)

// Form state
const formName = ref('')
const formDescription = ref('')
const formIsActive = ref(true)
const formConditions = ref<TemplateCondition[]>([])
const formActions = ref<TemplateAction[]>([])
const saving = ref(false)

// Options for dropdowns
const conditionFields = [
  { label: 'AI Confidence', value: 'aiConfidence' },
  { label: 'AI Suggested License', value: 'aiSuggestedLicense' },
  { label: 'Original License', value: 'originalLicense' },
  { label: 'Dependency Name', value: 'dependencyName' },
  { label: 'Scope', value: 'scope' },
  { label: 'Priority', value: 'priority' }
]

const conditionOperators = [
  { label: 'Equals', value: 'EQUALS' },
  { label: 'Not Equals', value: 'NOT_EQUALS' },
  { label: 'Contains', value: 'CONTAINS' },
  { label: 'Starts With', value: 'STARTS_WITH' },
  { label: 'Ends With', value: 'ENDS_WITH' },
  { label: 'Matches Pattern', value: 'MATCHES' },
  { label: 'Is Empty', value: 'IS_EMPTY' },
  { label: 'Is Not Empty', value: 'IS_NOT_EMPTY' }
]

const actionTypes = [
  { label: 'Set Status', value: 'SET_STATUS' },
  { label: 'Set License', value: 'SET_LICENSE' },
  { label: 'Add Comment', value: 'ADD_COMMENT' },
  { label: 'Set Priority', value: 'SET_PRIORITY' }
]

const statusValues = [
  { label: 'Accept', value: 'ACCEPTED' },
  { label: 'Reject', value: 'REJECTED' },
  { label: 'Modify', value: 'MODIFIED' }
]

const priorityValues = [
  { label: 'High', value: 'HIGH' },
  { label: 'Medium', value: 'MEDIUM' },
  { label: 'Low', value: 'LOW' }
]

// Computed
const isEditing = computed(() => editingTemplate.value !== null)

// Fetch templates
async function fetchTemplates() {
  loading.value = true
  try {
    const response = await api.listTemplates()
    templates.value = response.data.templates
  } catch (e) {
    console.error('Failed to fetch templates', e)
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to load templates', life: 3000 })
  } finally {
    loading.value = false
  }
}

// Open editor for new template
function openNewTemplate() {
  editingTemplate.value = null
  formName.value = ''
  formDescription.value = ''
  formIsActive.value = true
  formConditions.value = [{ field: 'aiConfidence', operator: 'EQUALS', value: '' }]
  formActions.value = [{ type: 'SET_STATUS', value: 'ACCEPTED' }]
  showEditor.value = true
}

// Open editor for existing template
function openEditTemplate(template: CurationTemplate) {
  editingTemplate.value = template
  formName.value = template.name
  formDescription.value = template.description || ''
  formIsActive.value = template.isActive
  formConditions.value = [...template.conditions]
  formActions.value = [...template.actions]
  showEditor.value = true
}

// Add condition
function addCondition() {
  formConditions.value.push({ field: 'aiConfidence', operator: 'EQUALS', value: '' })
}

// Remove condition
function removeCondition(index: number) {
  formConditions.value.splice(index, 1)
}

// Add action
function addAction() {
  formActions.value.push({ type: 'SET_STATUS', value: 'ACCEPTED' })
}

// Remove action
function removeAction(index: number) {
  formActions.value.splice(index, 1)
}

// Get value options for action type
function getActionValueOptions(actionType: string) {
  switch (actionType) {
    case 'SET_STATUS':
      return statusValues
    case 'SET_PRIORITY':
      return priorityValues
    default:
      return null
  }
}

// Save template
async function saveTemplate() {
  if (!formName.value || formConditions.value.length === 0 || formActions.value.length === 0) {
    toast.add({ severity: 'warn', summary: 'Validation', detail: 'Please fill in all required fields', life: 3000 })
    return
  }

  saving.value = true
  try {
    const templateData = {
      name: formName.value,
      description: formDescription.value || null,
      conditions: formConditions.value,
      actions: formActions.value,
      isActive: formIsActive.value
    }

    if (isEditing.value && editingTemplate.value) {
      await api.updateTemplate(editingTemplate.value.id, templateData)
      toast.add({ severity: 'success', summary: 'Success', detail: 'Template updated', life: 2000 })
    } else {
      await api.createTemplate(templateData as any)
      toast.add({ severity: 'success', summary: 'Success', detail: 'Template created', life: 2000 })
    }

    await fetchTemplates()
    showEditor.value = false
  } catch (e) {
    console.error('Failed to save template', e)
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to save template', life: 3000 })
  } finally {
    saving.value = false
  }
}

// Confirm delete
function confirmDelete(template: CurationTemplate) {
  templateToDelete.value = template
  showDeleteConfirm.value = true
}

// Delete template
async function deleteTemplate() {
  if (!templateToDelete.value) return

  try {
    await api.deleteTemplate(templateToDelete.value.id)
    templates.value = templates.value.filter(t => t.id !== templateToDelete.value!.id)
    showDeleteConfirm.value = false
    templateToDelete.value = null
    toast.add({ severity: 'success', summary: 'Success', detail: 'Template deleted', life: 2000 })
  } catch (e) {
    console.error('Failed to delete template', e)
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to delete template', life: 3000 })
  }
}

// Toggle template active state
async function toggleActive(template: CurationTemplate) {
  try {
    await api.updateTemplate(template.id, { isActive: !template.isActive })
    template.isActive = !template.isActive
  } catch (e) {
    console.error('Failed to toggle template', e)
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to update template', life: 3000 })
  }
}

// Format condition for display
function formatCondition(condition: TemplateCondition): string {
  const field = conditionFields.find(f => f.value === condition.field)?.label || condition.field
  const operator = conditionOperators.find(o => o.value === condition.operator)?.label || condition.operator
  return `${field} ${operator.toLowerCase()} "${condition.value}"`
}

// Format action for display
function formatAction(action: TemplateAction): string {
  const type = actionTypes.find(t => t.value === action.type)?.label || action.type
  return `${type}: ${action.value}`
}

onMounted(fetchTemplates)
</script>

<template>
  <div class="templates-view">
    <header class="page-header">
      <div>
        <h1>Curation Templates</h1>
        <p class="subtitle">Automate curation decisions with reusable templates</p>
      </div>
      <Button
        label="Create Template"
        icon="pi pi-plus"
        @click="openNewTemplate"
      />
    </header>

    <div v-if="loading" class="loading">
      <i class="pi pi-spin pi-spinner"></i> Loading templates...
    </div>

    <div v-else class="section">
      <DataTable
        :value="templates"
        stripedRows
        showGridlines
        class="p-datatable-sm"
        emptyMessage="No templates yet. Create one to automate curation decisions."
      >
        <Column field="name" header="Name" style="min-width: 200px" sortable>
          <template #body="{ data }">
            <div class="template-name">
              <strong>{{ data.name }}</strong>
              <span v-if="data.description" class="description">{{ data.description }}</span>
            </div>
          </template>
        </Column>

        <Column header="Conditions" style="min-width: 250px">
          <template #body="{ data }">
            <div class="conditions-list">
              <span v-for="(c, i) in data.conditions" :key="i" class="condition-badge">
                {{ formatCondition(c) }}
              </span>
            </div>
          </template>
        </Column>

        <Column header="Actions" style="min-width: 200px">
          <template #body="{ data }">
            <div class="actions-list">
              <span v-for="(a, i) in data.actions" :key="i" class="action-badge">
                {{ formatAction(a) }}
              </span>
            </div>
          </template>
        </Column>

        <Column field="isActive" header="Status" style="width: 100px">
          <template #body="{ data }">
            <Badge
              :value="data.isActive ? 'Active' : 'Inactive'"
              :severity="data.isActive ? 'success' : 'secondary'"
            />
          </template>
        </Column>

        <Column field="usageCount" header="Usage" style="width: 80px" sortable>
          <template #body="{ data }">
            <span class="usage-count">{{ data.usageCount }}</span>
          </template>
        </Column>

        <Column header="Actions" style="width: 150px">
          <template #body="{ data }">
            <div class="table-actions">
              <Button
                icon="pi pi-pencil"
                severity="secondary"
                text
                rounded
                size="small"
                @click="openEditTemplate(data)"
                title="Edit"
              />
              <Button
                :icon="data.isActive ? 'pi pi-pause' : 'pi pi-play'"
                severity="secondary"
                text
                rounded
                size="small"
                @click="toggleActive(data)"
                :title="data.isActive ? 'Deactivate' : 'Activate'"
              />
              <Button
                icon="pi pi-trash"
                severity="danger"
                text
                rounded
                size="small"
                @click="confirmDelete(data)"
                title="Delete"
              />
            </div>
          </template>
        </Column>
      </DataTable>
    </div>

    <!-- Template Editor Dialog -->
    <Dialog
      v-model:visible="showEditor"
      :header="isEditing ? 'Edit Template' : 'Create Template'"
      :modal="true"
      :style="{ width: '800px' }"
    >
      <div class="editor-content">
        <!-- Basic Info -->
        <div class="form-section">
          <h4>Basic Information</h4>
          <div class="form-row">
            <div class="field flex-1">
              <label>Name *</label>
              <InputText v-model="formName" placeholder="Template name..." class="w-full" />
            </div>
            <div class="field">
              <label>Active</label>
              <InputSwitch v-model="formIsActive" />
            </div>
          </div>
          <div class="field">
            <label>Description</label>
            <Textarea v-model="formDescription" rows="2" class="w-full" placeholder="Describe what this template does..." />
          </div>
        </div>

        <!-- Conditions -->
        <div class="form-section">
          <div class="section-header">
            <h4>Conditions</h4>
            <Button
              label="Add Condition"
              icon="pi pi-plus"
              severity="secondary"
              size="small"
              @click="addCondition"
            />
          </div>
          <p class="help-text">Items must match ALL conditions to trigger this template.</p>

          <div v-for="(condition, index) in formConditions" :key="index" class="condition-row">
            <Dropdown
              v-model="condition.field"
              :options="conditionFields"
              optionLabel="label"
              optionValue="value"
              placeholder="Field"
              class="field-dropdown"
            />
            <Dropdown
              v-model="condition.operator"
              :options="conditionOperators"
              optionLabel="label"
              optionValue="value"
              placeholder="Operator"
              class="operator-dropdown"
            />
            <InputText
              v-model="condition.value"
              placeholder="Value..."
              class="value-input"
              v-if="!['IS_EMPTY', 'IS_NOT_EMPTY'].includes(condition.operator)"
            />
            <Button
              icon="pi pi-times"
              severity="danger"
              text
              rounded
              size="small"
              @click="removeCondition(index)"
              :disabled="formConditions.length === 1"
            />
          </div>
        </div>

        <!-- Actions -->
        <div class="form-section">
          <div class="section-header">
            <h4>Actions</h4>
            <Button
              label="Add Action"
              icon="pi pi-plus"
              severity="secondary"
              size="small"
              @click="addAction"
            />
          </div>
          <p class="help-text">Actions to apply when conditions match.</p>

          <div v-for="(action, index) in formActions" :key="index" class="action-row">
            <Dropdown
              v-model="action.type"
              :options="actionTypes"
              optionLabel="label"
              optionValue="value"
              placeholder="Action Type"
              class="type-dropdown"
            />
            <Dropdown
              v-if="getActionValueOptions(action.type)"
              v-model="action.value"
              :options="getActionValueOptions(action.type)!"
              optionLabel="label"
              optionValue="value"
              placeholder="Value"
              class="value-dropdown"
            />
            <InputText
              v-else
              v-model="action.value"
              placeholder="Value..."
              class="value-input"
            />
            <Button
              icon="pi pi-times"
              severity="danger"
              text
              rounded
              size="small"
              @click="removeAction(index)"
              :disabled="formActions.length === 1"
            />
          </div>
        </div>
      </div>

      <template #footer>
        <Button label="Cancel" severity="secondary" @click="showEditor = false" />
        <Button
          :label="isEditing ? 'Update Template' : 'Create Template'"
          icon="pi pi-check"
          @click="saveTemplate"
          :loading="saving"
        />
      </template>
    </Dialog>

    <!-- Delete Confirmation Dialog -->
    <Dialog
      v-model:visible="showDeleteConfirm"
      header="Delete Template"
      :modal="true"
      :style="{ width: '400px' }"
    >
      <div class="delete-content">
        <i class="pi pi-exclamation-triangle warning-icon"></i>
        <p>Are you sure you want to delete the template "{{ templateToDelete?.name }}"?</p>
        <p class="warning-text">This action cannot be undone.</p>
      </div>

      <template #footer>
        <Button label="Cancel" severity="secondary" @click="showDeleteConfirm = false" />
        <Button label="Delete" severity="danger" icon="pi pi-trash" @click="deleteTemplate" />
      </template>
    </Dialog>
  </div>
</template>

<style scoped>
.templates-view {
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

.section {
  background: white;
  border-radius: 1rem;
  padding: 1.5rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

/* Table styles */
.template-name {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.template-name .description {
  color: #64748b;
  font-size: 0.8125rem;
}

.conditions-list,
.actions-list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.375rem;
}

.condition-badge,
.action-badge {
  display: inline-block;
  padding: 0.25rem 0.5rem;
  border-radius: 0.25rem;
  font-size: 0.75rem;
  font-family: monospace;
}

.condition-badge {
  background: #f0f9ff;
  color: #0369a1;
}

.action-badge {
  background: #f0fdf4;
  color: #166534;
}

.usage-count {
  font-weight: 500;
  color: #64748b;
}

.table-actions {
  display: flex;
  gap: 0.25rem;
}

/* Editor Dialog */
.editor-content {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.form-section {
  padding-bottom: 1rem;
  border-bottom: 1px solid #e2e8f0;
}

.form-section:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.form-section h4 {
  margin: 0 0 1rem;
  color: #1e293b;
  font-size: 1rem;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.5rem;
}

.section-header h4 {
  margin: 0;
}

.help-text {
  color: #64748b;
  font-size: 0.8125rem;
  margin: 0 0 1rem;
}

.form-row {
  display: flex;
  gap: 1rem;
  align-items: flex-end;
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

.flex-1 {
  flex: 1;
}

.w-full {
  width: 100%;
}

.condition-row,
.action-row {
  display: flex;
  gap: 0.75rem;
  align-items: center;
  margin-bottom: 0.75rem;
}

.field-dropdown {
  width: 180px;
}

.operator-dropdown {
  width: 150px;
}

.type-dropdown {
  width: 180px;
}

.value-dropdown {
  width: 150px;
}

.value-input {
  flex: 1;
}

/* Delete Dialog */
.delete-content {
  text-align: center;
  padding: 1rem 0;
}

.warning-icon {
  font-size: 3rem;
  color: #f59e0b;
  margin-bottom: 1rem;
}

.delete-content p {
  margin: 0;
  color: #374151;
}

.warning-text {
  color: #dc2626;
  font-size: 0.875rem;
  margin-top: 0.5rem !important;
}

.loading {
  text-align: center;
  padding: 4rem;
  color: #64748b;
}
</style>
