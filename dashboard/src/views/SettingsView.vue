<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api, type ApiKey } from '@/api/client'

const apiKeys = ref<ApiKey[]>([])
const loading = ref(true)
const newKeyName = ref('')
const newKey = ref<string | null>(null)

async function fetchApiKeys() {
  loading.value = true
  try {
    const response = await api.listApiKeys()
    apiKeys.value = response.data.apiKeys
  } catch (e) {
    console.error('Failed to fetch API keys', e)
  } finally {
    loading.value = false
  }
}

async function createApiKey() {
  if (!newKeyName.value) return
  try {
    const response = await api.createApiKey(newKeyName.value)
    newKey.value = response.data.apiKey
    newKeyName.value = ''
    await fetchApiKeys()
  } catch (e) {
    console.error('Failed to create API key', e)
  }
}

async function deleteApiKey(id: string) {
  if (!confirm('Are you sure? This action cannot be undone.')) return
  try {
    await api.deleteApiKey(id)
    await fetchApiKeys()
  } catch (e) {
    console.error('Failed to delete API key', e)
  }
}

function copyToClipboard(text: string) {
  navigator.clipboard.writeText(text)
}

onMounted(fetchApiKeys)
</script>

<template>
  <div class="settings-page">
    <header class="page-header">
      <h1>Settings</h1>
      <p class="subtitle">Manage API keys and configuration</p>
    </header>

    <div class="section">
      <h2>API Keys</h2>
      <p class="section-description">Generate API keys for programmatic access to the OrtoPed API.</p>

      <!-- New Key Alert -->
      <div v-if="newKey" class="new-key-alert">
        <div class="alert-header">
          <i class="pi pi-check-circle"></i>
          <strong>API Key Created</strong>
        </div>
        <p>Copy this key now. You won't be able to see it again!</p>
        <div class="key-display">
          <code>{{ newKey }}</code>
          <button class="btn-icon" @click="copyToClipboard(newKey!)">
            <i class="pi pi-copy"></i>
          </button>
        </div>
        <button class="btn btn-small" @click="newKey = null">Dismiss</button>
      </div>

      <!-- Create new key -->
      <div class="create-key-form">
        <input
          v-model="newKeyName"
          type="text"
          placeholder="Key name (e.g., CI Pipeline)"
          class="input"
        />
        <button class="btn btn-primary" @click="createApiKey" :disabled="!newKeyName">
          <i class="pi pi-plus"></i> Create Key
        </button>
      </div>

      <!-- Existing keys -->
      <div v-if="loading" class="loading">
        <i class="pi pi-spin pi-spinner"></i> Loading...
      </div>

      <table v-else-if="apiKeys.length > 0" class="data-table">
        <thead>
          <tr>
            <th>Name</th>
            <th>Key Prefix</th>
            <th>Created</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="key in apiKeys" :key="key.id">
            <td><strong>{{ key.name }}</strong></td>
            <td><code>{{ key.keyPrefix }}...</code></td>
            <td>{{ new Date(key.createdAt).toLocaleDateString() }}</td>
            <td>
              <button class="btn btn-small btn-danger" @click="deleteApiKey(key.id)">
                Delete
              </button>
            </td>
          </tr>
        </tbody>
      </table>

      <div v-else class="empty-state">
        <p>No API keys yet. Create one to get started.</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.settings-page { max-width: 800px; margin: 0 auto; }
.page-header { margin-bottom: 2rem; }
.page-header h1 { margin: 0; font-size: 2rem; color: #1e293b; }
.subtitle { color: #64748b; margin-top: 0.25rem; }

.section { background: white; border-radius: 1rem; padding: 1.5rem; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1); }
.section h2 { margin: 0 0 0.5rem; font-size: 1.25rem; color: #1e293b; }
.section-description { color: #64748b; margin-bottom: 1.5rem; }

.create-key-form { display: flex; gap: 0.75rem; margin-bottom: 1.5rem; }
.input { flex: 1; padding: 0.75rem; border: 1px solid #e2e8f0; border-radius: 0.5rem; font-size: 1rem; }
.input:focus { outline: none; border-color: #3b82f6; }

.btn { display: inline-flex; align-items: center; gap: 0.5rem; padding: 0.75rem 1.25rem; border-radius: 0.5rem; font-weight: 500; cursor: pointer; border: none; }
.btn-primary { background: #3b82f6; color: white; }
.btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-small { padding: 0.5rem 1rem; font-size: 0.875rem; }
.btn-danger { background: #fee2e2; color: #dc2626; }
.btn-icon { background: none; border: none; cursor: pointer; padding: 0.5rem; color: #64748b; }

.new-key-alert { background: #d1fae5; border: 1px solid #10b981; border-radius: 0.75rem; padding: 1rem; margin-bottom: 1.5rem; }
.alert-header { display: flex; align-items: center; gap: 0.5rem; color: #059669; margin-bottom: 0.5rem; }
.new-key-alert p { color: #047857; margin: 0.5rem 0; }
.key-display { display: flex; align-items: center; gap: 0.5rem; background: white; padding: 0.75rem; border-radius: 0.5rem; margin: 0.75rem 0; }
.key-display code { flex: 1; font-family: monospace; word-break: break-all; }

.data-table { width: 100%; border-collapse: collapse; }
.data-table th, .data-table td { padding: 0.75rem 1rem; text-align: left; border-bottom: 1px solid #e2e8f0; }
.data-table th { font-weight: 600; color: #64748b; font-size: 0.875rem; }
.data-table code { background: #f1f5f9; padding: 0.25rem 0.5rem; border-radius: 0.25rem; font-family: monospace; }

.loading, .empty-state { text-align: center; padding: 2rem; color: #64748b; }
</style>
