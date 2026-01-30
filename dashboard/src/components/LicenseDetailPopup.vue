<script setup lang="ts">
import type { SpdxLicenseDetailResponse } from '@/api/client'
import Badge from 'primevue/badge'

interface Props {
  visible: boolean
  license: SpdxLicenseDetailResponse | null
  loading: boolean
  error: string | null
}

defineProps<Props>()
const emit = defineEmits<{
  close: []
}>()
</script>

<template>
  <div v-show="visible" class="license-popup">
    <div class="popup-header">
      <h4 v-if="license">{{ license.name }}</h4>
      <h4 v-else>License Details</h4>
      <button class="close-btn" @click="emit('close')" aria-label="Close">
        <i class="pi pi-times"></i>
      </button>
    </div>

    <div class="popup-content">
      <div v-if="loading" class="license-loading">
        <i class="pi pi-spin pi-spinner"></i> Loading license details...
      </div>

      <div v-else-if="error" class="license-error">
        <i class="pi pi-exclamation-triangle"></i> {{ error }}
      </div>

      <div v-else-if="license" class="license-details">
        <div class="license-meta">
          <div class="meta-item">
            <span class="meta-label">License ID</span>
            <code class="license-id">{{ license.licenseId }}</code>
          </div>

          <div class="meta-badges">
            <Badge v-if="license.isOsiApproved" value="OSI Approved" severity="success" />
            <Badge v-if="license.isFsfLibre" value="FSF Libre" severity="info" />
            <Badge v-if="license.isDeprecated" value="Deprecated" severity="danger" />
            <Badge :value="license.category" severity="secondary" />
          </div>
        </div>

        <div v-if="license.seeAlso.length > 0" class="see-also-section">
          <strong>References:</strong>
          <ul class="see-also-list">
            <li v-for="url in license.seeAlso" :key="url">
              <a :href="url" target="_blank" rel="noopener noreferrer">
                <i class="pi pi-external-link"></i> {{ url }}
              </a>
            </li>
          </ul>
        </div>

        <div v-if="license.standardHeader" class="header-section">
          <strong>Standard License Header:</strong>
          <pre class="license-header">{{ license.standardHeader }}</pre>
        </div>

        <div v-if="license.licenseText" class="license-text-section">
          <strong>Full License Text:</strong>
          <pre class="license-text">{{ license.licenseText }}</pre>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.license-popup {
  position: fixed;
  z-index: 1000;
  background: white;
  border: 1px solid #cbd5e1;
  border-radius: 0.75rem;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04);
  width: 600px;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  opacity: 0;
  transform: scale(0.95);
  transform-origin: top left;
  transition: opacity 0.15s ease-out, transform 0.15s ease-out;
  pointer-events: auto;
}

.popup-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 1.25rem;
  border-bottom: 1px solid #e2e8f0;
  background: linear-gradient(to bottom, #f8fafc, #ffffff);
}

.popup-header h4 {
  margin: 0;
  color: #1e293b;
  font-size: 1.125rem;
  font-weight: 600;
}

.close-btn {
  background: none;
  border: none;
  cursor: pointer;
  padding: 0.25rem;
  color: #64748b;
  font-size: 1.25rem;
  line-height: 1;
  transition: color 0.2s;
}

.close-btn:hover {
  color: #1e293b;
}

.popup-content {
  overflow-y: auto;
  flex: 1;
}

.license-loading, .license-error {
  padding: 2rem;
  text-align: center;
  color: #64748b;
}

.license-details {
  padding: 1.25rem;
}

.license-meta {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  margin-bottom: 1.5rem;
  padding-bottom: 1.5rem;
  border-bottom: 1px solid #e2e8f0;
}

.meta-item {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.meta-label {
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  color: #64748b;
  letter-spacing: 0.05em;
}

.license-id {
  background: #f1f5f9;
  padding: 0.5rem 0.75rem;
  border-radius: 0.375rem;
  font-family: 'Monaco', 'Menlo', 'Courier New', monospace;
  font-size: 0.875rem;
  color: #3b82f6;
  border: 1px solid #e2e8f0;
}

.meta-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.see-also-section,
.header-section,
.license-text-section {
  margin-bottom: 1.5rem;
}

.see-also-section strong,
.header-section strong,
.license-text-section strong {
  display: block;
  margin-bottom: 0.75rem;
  color: #1e293b;
  font-weight: 600;
}

.see-also-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.see-also-list li {
  margin-bottom: 0.5rem;
}

.see-also-list a {
  color: #3b82f6;
  text-decoration: none;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.875rem;
  transition: color 0.2s;
}

.see-also-list a:hover {
  color: #2563eb;
  text-decoration: underline;
}

.license-header,
.license-text {
  background: #f8fafc;
  padding: 1rem;
  border-radius: 0.5rem;
  border: 1px solid #e2e8f0;
  font-family: 'Monaco', 'Menlo', 'Courier New', monospace;
  font-size: 0.8125rem;
  line-height: 1.6;
  white-space: pre-wrap;
  word-wrap: break-word;
  overflow-x: auto;
  max-height: 300px;
  overflow-y: auto;
}

.license-text {
  max-height: 400px;
}

/* Custom scrollbar */
.popup-content::-webkit-scrollbar,
.license-text::-webkit-scrollbar,
.license-header::-webkit-scrollbar {
  width: 8px;
}

.popup-content::-webkit-scrollbar-track,
.license-text::-webkit-scrollbar-track,
.license-header::-webkit-scrollbar-track {
  background: #f1f5f9;
  border-radius: 4px;
}

.popup-content::-webkit-scrollbar-thumb,
.license-text::-webkit-scrollbar-thumb,
.license-header::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 4px;
}

.popup-content::-webkit-scrollbar-thumb:hover,
.license-text::-webkit-scrollbar-thumb:hover,
.license-header::-webkit-scrollbar-thumb:hover {
  background: #94a3b8;
}
</style>