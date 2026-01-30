<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import type { PolicyConfig, PolicyRule } from '@/api/client'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import Button from 'primevue/button'
import Dropdown from 'primevue/dropdown'
import InputSwitch from 'primevue/inputswitch'
import TabView from 'primevue/tabview'
import TabPanel from 'primevue/tabpanel'
import Chip from 'primevue/chip'

const props = defineProps<{
  modelValue: PolicyConfig
  showJsonPreview?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: PolicyConfig): void
}>()

const config = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const activeTab = ref(0)
const jsonPreview = ref('')
const jsonError = ref('')

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

// Update JSON preview when config changes
watch(config, (newConfig) => {
  jsonPreview.value = JSON.stringify(newConfig, null, 2)
  jsonError.value = ''
}, { deep: true, immediate: true })

function addRule() {
  const newConfig = { ...config.value }
  newConfig.rules = [...(newConfig.rules || []), {
    name: 'new-rule',
    description: 'New rule description',
    severity: 'WARNING' as const,
    action: 'WARN' as const,
    enabled: true
  }]
  emit('update:modelValue', newConfig)
}

function updateRule(index: number, field: keyof PolicyRule, value: unknown) {
  const newConfig = { ...config.value }
  newConfig.rules = [...newConfig.rules]
  newConfig.rules[index] = { ...newConfig.rules[index], [field]: value }
  emit('update:modelValue', newConfig)
}

function removeRule(index: number) {
  const newConfig = { ...config.value }
  newConfig.rules = newConfig.rules.filter((_, i) => i !== index)
  emit('update:modelValue', newConfig)
}

function addLicense(list: 'allowedLicenses' | 'deniedLicenses', license: string) {
  if (!license.trim()) return
  const newConfig = { ...config.value }
  const currentList = newConfig[list] || []
  if (!currentList.includes(license)) {
    newConfig[list] = [...currentList, license]
    emit('update:modelValue', newConfig)
  }
}

function removeLicense(list: 'allowedLicenses' | 'deniedLicenses', index: number) {
  const newConfig = { ...config.value }
  const currentList = newConfig[list] || []
  newConfig[list] = currentList.filter((_: string, i: number) => i !== index)
  emit('update:modelValue', newConfig)
}

function addExemption(exemption: string) {
  if (!exemption.trim()) return
  const newConfig = { ...config.value }
  const currentExemptions = newConfig.exemptions || []
  if (!currentExemptions.includes(exemption)) {
    newConfig.exemptions = [...currentExemptions, exemption]
    emit('update:modelValue', newConfig)
  }
}

function removeExemption(index: number) {
  const newConfig = { ...config.value }
  const currentExemptions = newConfig.exemptions || []
  newConfig.exemptions = currentExemptions.filter((_: string, i: number) => i !== index)
  emit('update:modelValue', newConfig)
}

function updateSettings(field: string, value: unknown) {
  const newConfig = { ...config.value }
  newConfig.settings = { ...newConfig.settings, [field]: value }
  emit('update:modelValue', newConfig)
}

function updateBasicInfo(field: 'name' | 'description' | 'version', value: string | undefined) {
  const newConfig = { ...config.value }
  newConfig[field] = value ?? ''
  emit('update:modelValue', newConfig)
}

function applyJsonPreview() {
  try {
    const parsed = JSON.parse(jsonPreview.value)
    emit('update:modelValue', parsed)
    jsonError.value = ''
  } catch (e) {
    jsonError.value = 'Invalid JSON: ' + (e as Error).message
  }
}

// Temp inputs for adding items
const newAllowedLicense = ref('')
const newDeniedLicense = ref('')
const newExemption = ref('')
</script>

<template>
  <div class="policy-editor">
    <TabView v-model:activeIndex="activeTab">
      <!-- Basic Info Tab -->
      <TabPanel header="Basic Info">
        <div class="form-section">
          <div class="form-group">
            <label>Policy Name</label>
            <InputText
              :modelValue="config.name"
              @update:modelValue="updateBasicInfo('name', $event)"
              placeholder="Enter policy name"
              class="w-full"
            />
          </div>

          <div class="form-group">
            <label>Version</label>
            <InputText
              :modelValue="config.version"
              @update:modelValue="updateBasicInfo('version', $event)"
              placeholder="1.0"
              class="w-full"
            />
          </div>

          <div class="form-group">
            <label>Description</label>
            <Textarea
              :modelValue="config.description"
              @update:modelValue="updateBasicInfo('description', $event)"
              rows="3"
              placeholder="Describe the purpose of this policy..."
              class="w-full"
            />
          </div>
        </div>
      </TabPanel>

      <!-- Rules Tab -->
      <TabPanel header="Rules">
        <div class="section-header">
          <span class="rule-count">{{ config.rules?.length || 0 }} rules configured</span>
          <Button label="Add Rule" icon="pi pi-plus" size="small" @click="addRule" />
        </div>

        <div v-for="(rule, index) in config.rules" :key="index" class="rule-card">
          <div class="rule-header">
            <InputText
              :modelValue="rule.name"
              @update:modelValue="updateRule(index, 'name', $event)"
              placeholder="Rule name"
              class="rule-name-input"
            />
            <Button icon="pi pi-trash" severity="danger" text rounded size="small" @click="removeRule(index)" />
          </div>

          <div class="form-group">
            <label>Description</label>
            <InputText
              :modelValue="rule.description"
              @update:modelValue="updateRule(index, 'description', $event)"
              placeholder="Describe what this rule checks..."
              class="w-full"
            />
          </div>

          <div class="rule-options">
            <div class="option-group">
              <label>Severity</label>
              <Dropdown
                :modelValue="rule.severity"
                @update:modelValue="updateRule(index, 'severity', $event)"
                :options="severityOptions"
                optionLabel="label"
                optionValue="value"
                class="w-full"
              />
            </div>

            <div class="option-group">
              <label>Action</label>
              <Dropdown
                :modelValue="rule.action"
                @update:modelValue="updateRule(index, 'action', $event)"
                :options="actionOptions"
                optionLabel="label"
                optionValue="value"
                class="w-full"
              />
            </div>

            <div class="option-group switch-group">
              <label>Enabled</label>
              <InputSwitch
                :modelValue="rule.enabled"
                @update:modelValue="updateRule(index, 'enabled', $event)"
              />
            </div>
          </div>
        </div>

        <div v-if="!config.rules?.length" class="empty-state">
          <i class="pi pi-list"></i>
          <p>No rules configured. Click "Add Rule" to create your first rule.</p>
        </div>
      </TabPanel>

      <!-- Licenses Tab -->
      <TabPanel header="Licenses">
        <div class="license-section">
          <div class="license-list-section">
            <h4><i class="pi pi-check-circle text-green"></i> Allowed Licenses</h4>
            <div class="add-item-row">
              <InputText v-model="newAllowedLicense" placeholder="Add license (e.g., MIT)" class="flex-1" @keyup.enter="addLicense('allowedLicenses', newAllowedLicense); newAllowedLicense = ''" />
              <Button icon="pi pi-plus" @click="addLicense('allowedLicenses', newAllowedLicense); newAllowedLicense = ''" />
            </div>
            <div class="chips">
              <Chip
                v-for="(license, index) in config.allowedLicenses"
                :key="license"
                :label="license"
                removable
                @remove="removeLicense('allowedLicenses', index)"
                class="allowed-chip"
              />
              <span v-if="!config.allowedLicenses?.length" class="no-items">No allowed licenses</span>
            </div>
          </div>

          <div class="license-list-section">
            <h4><i class="pi pi-ban text-red"></i> Denied Licenses</h4>
            <div class="add-item-row">
              <InputText v-model="newDeniedLicense" placeholder="Add license (e.g., GPL-3.0)" class="flex-1" @keyup.enter="addLicense('deniedLicenses', newDeniedLicense); newDeniedLicense = ''" />
              <Button icon="pi pi-plus" @click="addLicense('deniedLicenses', newDeniedLicense); newDeniedLicense = ''" />
            </div>
            <div class="chips">
              <Chip
                v-for="(license, index) in config.deniedLicenses"
                :key="license"
                :label="license"
                removable
                @remove="removeLicense('deniedLicenses', index)"
                class="denied-chip"
              />
              <span v-if="!config.deniedLicenses?.length" class="no-items">No denied licenses</span>
            </div>
          </div>
        </div>
      </TabPanel>

      <!-- Settings Tab -->
      <TabPanel header="Settings">
        <div class="settings-section">
          <div class="setting-row">
            <div class="setting-info">
              <label>Fail on Error</label>
              <small>Fail the policy check if any ERROR violations are found</small>
            </div>
            <InputSwitch
              :modelValue="config.settings?.failOnError"
              @update:modelValue="updateSettings('failOnError', $event)"
            />
          </div>

          <div class="setting-row">
            <div class="setting-info">
              <label>Fail on Warning</label>
              <small>Fail the policy check if any WARNING violations are found</small>
            </div>
            <InputSwitch
              :modelValue="config.settings?.failOnWarning"
              @update:modelValue="updateSettings('failOnWarning', $event)"
            />
          </div>

          <div class="setting-row">
            <div class="setting-info">
              <label>Accept AI Suggestions</label>
              <small>Automatically accept AI license suggestions that meet confidence threshold</small>
            </div>
            <InputSwitch
              :modelValue="config.settings?.acceptAiSuggestions"
              @update:modelValue="updateSettings('acceptAiSuggestions', $event)"
            />
          </div>

          <div class="setting-row">
            <div class="setting-info">
              <label>Minimum Confidence</label>
              <small>Minimum confidence level for accepting AI suggestions</small>
            </div>
            <Dropdown
              :modelValue="config.settings?.minimumConfidence"
              @update:modelValue="updateSettings('minimumConfidence', $event)"
              :options="confidenceOptions"
              optionLabel="label"
              optionValue="value"
              style="width: 150px"
            />
          </div>
        </div>
      </TabPanel>

      <!-- Exemptions Tab -->
      <TabPanel header="Exemptions">
        <div class="exemptions-section">
          <p class="section-description">
            Exemptions allow specific dependencies to bypass policy checks. Use this for known false positives or approved exceptions.
          </p>

          <div class="add-item-row">
            <InputText v-model="newExemption" placeholder="Add dependency (e.g., com.example:library)" class="flex-1" @keyup.enter="addExemption(newExemption); newExemption = ''" />
            <Button label="Add Exemption" icon="pi pi-plus" @click="addExemption(newExemption); newExemption = ''" />
          </div>

          <div class="exemptions-list">
            <div v-for="(exemption, index) in config.exemptions" :key="index" class="exemption-item">
              <span class="exemption-name">{{ exemption }}</span>
              <Button icon="pi pi-times" severity="danger" text rounded size="small" @click="removeExemption(index)" />
            </div>
            <div v-if="!config.exemptions?.length" class="empty-state small">
              <i class="pi pi-shield"></i>
              <p>No exemptions configured</p>
            </div>
          </div>
        </div>
      </TabPanel>

      <!-- JSON Preview Tab -->
      <TabPanel v-if="showJsonPreview" header="JSON">
        <div class="json-section">
          <p class="section-description">
            Advanced users can edit the policy configuration directly as JSON.
          </p>
          <Textarea
            v-model="jsonPreview"
            rows="20"
            class="w-full json-editor"
            spellcheck="false"
          />
          <div v-if="jsonError" class="json-error">
            <i class="pi pi-exclamation-triangle"></i> {{ jsonError }}
          </div>
          <div class="json-actions">
            <Button label="Apply JSON" icon="pi pi-check" @click="applyJsonPreview" :disabled="!!jsonError" />
          </div>
        </div>
      </TabPanel>
    </TabView>
  </div>
</template>

<style scoped>
.policy-editor {
  width: 100%;
}

.form-section {
  max-width: 600px;
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

.w-full {
  width: 100%;
}

.flex-1 {
  flex: 1;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.rule-count {
  color: #64748b;
  font-size: 0.875rem;
}

.rule-card {
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
  margin-bottom: 1rem;
}

.rule-name-input {
  font-weight: 600;
  max-width: 300px;
}

.rule-options {
  display: grid;
  grid-template-columns: 1fr 1fr auto;
  gap: 1rem;
  align-items: end;
}

.option-group label {
  display: block;
  margin-bottom: 0.25rem;
  font-size: 0.875rem;
  color: #64748b;
}

.switch-group {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-bottom: 0.25rem;
}

.license-section {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 2rem;
}

.license-list-section h4 {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin: 0 0 1rem;
  font-size: 1rem;
  color: #374151;
}

.add-item-row {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 1rem;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  min-height: 2rem;
}

.allowed-chip :deep(.p-chip) {
  background: #d1fae5;
  color: #065f46;
}

.denied-chip :deep(.p-chip) {
  background: #fee2e2;
  color: #991b1b;
}

.text-green {
  color: #22c55e;
}

.text-red {
  color: #ef4444;
}

.no-items {
  color: #94a3b8;
  font-style: italic;
}

.settings-section {
  max-width: 600px;
}

.setting-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem;
  border-bottom: 1px solid #e2e8f0;
}

.setting-row:last-child {
  border-bottom: none;
}

.setting-info {
  flex: 1;
}

.setting-info label {
  display: block;
  font-weight: 500;
  color: #374151;
}

.setting-info small {
  color: #64748b;
  font-size: 0.875rem;
}

.exemptions-section {
  max-width: 600px;
}

.section-description {
  color: #64748b;
  margin-bottom: 1rem;
}

.exemptions-list {
  margin-top: 1rem;
}

.exemption-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.75rem;
  background: #f8fafc;
  border-radius: 0.5rem;
  margin-bottom: 0.5rem;
}

.exemption-name {
  font-family: monospace;
  color: #374151;
}

.empty-state {
  text-align: center;
  padding: 2rem;
  color: #64748b;
}

.empty-state.small {
  padding: 1rem;
}

.empty-state i {
  font-size: 2rem;
  margin-bottom: 0.5rem;
  color: #cbd5e1;
}

.empty-state p {
  margin: 0;
}

.json-section {
  max-width: 800px;
}

.json-editor {
  font-family: monospace;
  font-size: 0.85rem;
}

.json-error {
  color: #dc2626;
  margin-top: 0.5rem;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.json-actions {
  margin-top: 1rem;
}
</style>
