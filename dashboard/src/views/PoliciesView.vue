<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api, type Policy } from '@/api/client'

const policies = ref<Policy[]>([])
const loading = ref(true)

async function fetchPolicies() {
  loading.value = true
  try {
    const response = await api.listPolicies(1, 100)
    policies.value = response.data.policies
  } catch (e) {
    console.error('Failed to fetch policies', e)
  } finally {
    loading.value = false
  }
}

onMounted(fetchPolicies)
</script>

<template>
  <div class="policies-page">
    <header class="page-header">
      <h1>Policies</h1>
      <p class="subtitle">Manage license compliance policies</p>
    </header>

    <div v-if="loading" class="loading">
      <i class="pi pi-spin pi-spinner"></i> Loading policies...
    </div>

    <div v-else class="policies-grid">
      <div v-for="policy in policies" :key="policy.id" class="policy-card">
        <div class="policy-header">
          <i class="pi pi-shield"></i>
          <h3>{{ policy.name }}</h3>
          <span v-if="policy.isDefault" class="default-badge">Default</span>
        </div>
        <div class="policy-meta">
          <span>Created: {{ new Date(policy.createdAt).toLocaleDateString() }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.policies-page { max-width: 1400px; margin: 0 auto; }
.page-header { margin-bottom: 2rem; }
.page-header h1 { margin: 0; font-size: 2rem; color: #1e293b; }
.subtitle { color: #64748b; margin-top: 0.25rem; }

.policies-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 1.5rem; }

.policy-card { background: white; border-radius: 1rem; padding: 1.5rem; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1); }
.policy-header { display: flex; align-items: center; gap: 0.75rem; margin-bottom: 0.75rem; }
.policy-header i { font-size: 1.5rem; color: #8b5cf6; }
.policy-header h3 { margin: 0; flex: 1; font-size: 1.125rem; color: #1e293b; }
.default-badge { padding: 0.25rem 0.5rem; background: #dbeafe; color: #3b82f6; border-radius: 0.25rem; font-size: 0.75rem; font-weight: 600; }
.policy-meta { color: #64748b; font-size: 0.875rem; }

.loading { text-align: center; padding: 4rem; color: #64748b; }
</style>
