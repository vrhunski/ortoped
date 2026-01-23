import axios from 'axios'

const apiClient = axios.create({
  baseURL: '/api/v1',
  headers: {
    'Content-Type': 'application/json'
  }
})

// Request interceptor for API key
apiClient.interceptors.request.use((config) => {
  const apiKey = localStorage.getItem('apiKey')
  if (apiKey) {
    config.headers['X-API-Key'] = apiKey
  }
  return config
})

// Response interceptor for error handling
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('apiKey')
      window.location.href = '/settings'
    }
    return Promise.reject(error)
  }
)

// API Types
export interface Project {
  id: string
  name: string
  repositoryUrl: string | null
  defaultBranch: string
  policyId: string | null
  createdAt: string
}

// Policy Configuration Types
export interface PolicyRule {
  name: string
  description: string
  severity: 'ERROR' | 'WARNING' | 'INFO'
  action: 'DENY' | 'WARN' | 'ALLOW'
  enabled: boolean
  parameters?: Record<string, unknown>
}

export interface LicenseCategory {
  name: string
  licenses: string[]
}

export interface PolicySettings {
  failOnError: boolean
  failOnWarning: boolean
  acceptAiSuggestions: boolean
  minimumConfidence: string
}

export interface PolicyConfig {
  version: string
  name: string
  description: string
  rules: PolicyRule[]
  allowedLicenses: string[]
  deniedLicenses: string[]
  licenseCategories: LicenseCategory[]
  exemptions: string[]
  settings: PolicySettings
}

export interface PolicyViolation {
  severity: 'ERROR' | 'WARNING' | 'INFO'
  rule: string
  message: string
  dependency: string
  license: string
  suggestion?: string
}

export interface PolicyReport {
  policyId: string
  policyName: string
  scanId: string
  passed: boolean
  errorCount: number
  warningCount: number
  infoCount: number
  violations: PolicyViolation[]
  evaluatedAt: string
}

export interface Scan {
  id: string
  projectId: string | null
  status: string
  enableAi: boolean
  startedAt: string | null
  completedAt: string | null
  errorMessage: string | null
  createdAt: string
}

export interface ScanSummary {
  id: string
  projectId: string | null
  status: string
  totalDependencies: number
  resolvedLicenses: number
  unresolvedLicenses: number
  aiResolvedLicenses: number
  startedAt: string | null
  completedAt: string | null
}

export interface Dependency {
  id: string
  name: string
  version: string
  declaredLicenses: string[]
  detectedLicenses: string[]
  concludedLicense: string | null
  scope: string
  isResolved: boolean
  aiSuggestion: AiSuggestion | null
}

export interface AiSuggestion {
  suggestedLicense: string
  confidence: string
  reasoning: string
  spdxId: string | null
  alternatives: string[]
}

export interface Policy {
  id: string
  name: string
  config: string
  isDefault: boolean
  createdAt: string
}

export interface ApiKey {
  id: string
  name: string
  keyPrefix: string
  apiKey: string | null
  createdAt: string
}

// API Functions
export const api = {
  // Health
  health: () => apiClient.get('/health'),

  // Projects
  listProjects: (page = 1, pageSize = 20) =>
    apiClient.get<{ projects: Project[]; total: number }>('/projects', {
      params: { page, pageSize }
    }),

  getProject: (id: string) =>
    apiClient.get<Project>(`/projects/${id}`),

  createProject: (data: { name: string; repositoryUrl?: string; defaultBranch?: string }) =>
    apiClient.post<Project>('/projects', data),

  deleteProject: (id: string) =>
    apiClient.delete(`/projects/${id}`),

  // Scans
  listScans: (params?: { projectId?: string; status?: string; page?: number; pageSize?: number }) =>
    apiClient.get<{ scans: ScanSummary[]; total: number; page: number; pageSize: number }>('/scans', { params }),

  getScan: (id: string) =>
    apiClient.get<Scan>(`/scans/${id}`),

  getScanResult: (id: string) =>
    apiClient.get(`/scans/${id}/result`),

  getDependencies: (id: string, page = 1, pageSize = 20) =>
    apiClient.get<{ dependencies: Dependency[]; total: number; page: number; pageSize: number }>(
      `/scans/${id}/dependencies`,
      { params: { page, pageSize } }
    ),

  triggerScan: (data: {
    projectId: string
    enableAi?: boolean
    enableSourceScan?: boolean
    parallelAiCalls?: boolean
    demoMode?: boolean
  }) => apiClient.post<Scan>('/scans', data),

  generateSbom: (scanId: string, format = 'cyclonedx-json') =>
    apiClient.post(`/scans/${scanId}/sbom`, { format }),

  // Policies
  listPolicies: (page = 1, pageSize = 20) =>
    apiClient.get<{ policies: Policy[]; total: number }>('/policies', {
      params: { page, pageSize }
    }),

  getPolicy: (id: string) =>
    apiClient.get<Policy>(`/policies/${id}`),

  createPolicy: (data: { name: string; config: string; isDefault?: boolean }) =>
    apiClient.post<Policy>('/policies', data),

  updatePolicy: (id: string, data: { name?: string; config?: string; isDefault?: boolean }) =>
    apiClient.put<Policy>(`/policies/${id}`, data),

  deletePolicy: (id: string) =>
    apiClient.delete(`/policies/${id}`),

  evaluatePolicy: (scanId: string, policyId: string) =>
    apiClient.post<PolicyReport>(`/scans/${scanId}/evaluate`, { policyId }),

  // Auth
  listApiKeys: () =>
    apiClient.get<{ apiKeys: ApiKey[]; total: number }>('/auth/api-keys'),

  createApiKey: (name: string) =>
    apiClient.post<ApiKey>('/auth/api-keys', { name }),

  deleteApiKey: (id: string) =>
    apiClient.delete(`/auth/api-keys/${id}`)
}

export default apiClient
