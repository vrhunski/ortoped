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
  id?: string
  name: string
  description?: string
  severity: 'ERROR' | 'WARNING' | 'INFO'
  enabled: boolean
  category?: string
  allowlist?: string[]
  denylist?: string[]
  scopes?: string[]
  action: 'ALLOW' | 'DENY' | 'REVIEW' | 'WARN'
  message?: string
}

export interface LicenseCategoryDefinition {
  description?: string
  licenses: string[]
}

export interface AiSuggestionSettings {
  acceptHighConfidence: boolean
  treatMediumAsWarning: boolean
  rejectLowConfidence: boolean
}

export interface FailOnSettings {
  errors: boolean
  warnings: boolean
}

export interface Exemption {
  dependency: string
  reason: string
  approvedBy?: string
  approvedDate?: string
}

export interface PolicySettings {
  aiSuggestions?: AiSuggestionSettings
  failOn?: FailOnSettings
  exemptions?: Exemption[]
  // Legacy/simplified settings used by PolicyEditor
  failOnError?: boolean
  failOnWarning?: boolean
  acceptAiSuggestions?: boolean
  minimumConfidence?: string
}

export interface PolicyConfig {
  version: string
  name: string
  description?: string
  categories?: Record<string, LicenseCategoryDefinition>
  rules: PolicyRule[]
  settings: PolicySettings
  // Legacy/simplified properties used by PolicyEditor
  allowedLicenses?: string[]
  deniedLicenses?: string[]
  exemptions?: string[]
}

export interface PolicyViolation {
  severity: 'ERROR' | 'WARNING' | 'INFO'
  rule: string
  message: string
  dependency: string
  license: string
  suggestion?: string
}

// Enhanced violation types for "Why Not?" explanations
export type ExplanationType =
  'WHY_PROHIBITED' | 'COPYLEFT_RISK' | 'COMPATIBILITY_ISSUE' |
  'OBLIGATION_CONCERN' | 'RISK_LEVEL' | 'PROPAGATION_RISK' | 'USE_CASE_MISMATCH'

export type ResolutionType =
  'REPLACE_DEPENDENCY' | 'ISOLATE_SERVICE' | 'ACCEPT_OBLIGATIONS' |
  'REQUEST_EXCEPTION' | 'REMOVE_DEPENDENCY' | 'CONTACT_AUTHOR' |
  'USE_ALTERNATIVE_VERSION' | 'CHANGE_SCOPE'

export type EffortLevel = 'TRIVIAL' | 'LOW' | 'MEDIUM' | 'HIGH' | 'SIGNIFICANT'

export interface ExplanationContext {
  licenseCategory?: string
  copyleftStrength?: string
  propagationLevel?: number
  riskLevel?: number
  relatedLicenses?: string[]
  triggeredObligations?: string[]
  affectedUseCases?: string[]
}

export interface ViolationExplanation {
  type: ExplanationType
  title: string
  summary: string
  details: string[]
  context?: ExplanationContext
}

export interface AlternativeDependency {
  name: string
  version?: string
  license: string
  reason: string
  popularity?: 'VERY_HIGH' | 'HIGH' | 'MEDIUM' | 'LOW' | 'VERY_LOW'
}

export interface ResolutionOption {
  type: ResolutionType
  title: string
  description: string
  effort: EffortLevel
  tradeoffs?: string[]
  steps?: string[]
  alternatives?: AlternativeDependency[]
  recommended?: boolean
}

export interface EnhancedViolation {
  ruleId: string
  ruleName: string
  severity: string
  dependencyId: string
  dependencyName: string
  dependencyVersion: string
  license: string
  licenseCategory?: string
  scope: string
  message: string
  explanations: ViolationExplanation[]
  resolutions: ResolutionOption[]
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
  enhancedViolations?: EnhancedViolation[]
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
  spdxResolvedLicenses: number
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
  spdxValidated: boolean
  spdxSuggestion: SpdxLicenseInfo | null
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

// Curation Types
export interface CurationSession {
  id: string
  scanId: string
  status: string
  statistics: {
    total: number
    pending: number
    accepted: number
    rejected: number
    modified: number
  }
  approval: {
    approvedBy: string
    approvedAt: string
    comment?: string
  } | null
  createdAt: string
  updatedAt: string
}

export interface PriorityInfo {
  level: string
  score: number
  factors: PriorityFactor[]
}

export interface PriorityFactor {
  type: string
  description: string
  weight: number
}

export interface CurationItem {
  id: string
  dependencyId: string
  dependencyName: string
  dependencyVersion: string
  scope: string | null
  declaredLicenses: string[]
  detectedLicenses: string[]
  originalConcludedLicense: string | null
  aiSuggestion: AiSuggestion | null
  status: string
  curatedLicense: string | null
  curatorComment: string | null
  curatorId: string | null
  curatedAt: string | null
  priority: PriorityInfo | null
  spdxValidated: boolean
  spdxLicense: SpdxLicenseInfo | null
}

export interface CurationDecision {
  action: 'ACCEPT' | 'REJECT' | 'MODIFY'
  curatedLicense?: string
  comment?: string
  curatorId?: string
}

export interface CurationFilter {
  status?: string
  priority?: string
  confidence?: string
  licenseCategory?: string
  search?: string
  sortBy?: string
  sortDirection?: 'asc' | 'desc'
  page?: number
  pageSize?: number
}

export interface CurationTemplate {
  id: string
  name: string
  description: string | null
  conditions: TemplateCondition[]
  actions: TemplateAction[]
  isActive: boolean
  usageCount: number
  createdAt: string
}

export interface TemplateCondition {
  field: string
  operator: string
  value: string
}

export interface TemplateAction {
  type: string
  value: string
}

export interface TemplatePreviewResult {
  matchingItems: number
  affectedItems: CurationItem[]
}

// Report Types
export interface ReportSummary {
  scanId: string
  projectName: string
  scanStatus: string
  scanDate: string
  totalDependencies: number
  resolvedLicenses: number
  unresolvedLicenses: number
  aiResolvedLicenses: number
  hasPolicyEvaluation: boolean
  policyPassed: boolean | null
  hasCuration: boolean
  curationStatus: string | null
  curationApproved: boolean
}

// SPDX License Types
export interface SpdxLicenseInfo {
  licenseId: string
  name: string
  isOsiApproved: boolean
  isFsfLibre: boolean
  isDeprecated: boolean
  seeAlso: string[]
}

export interface SpdxLicenseDetailResponse {
  licenseId: string
  name: string
  isOsiApproved: boolean
  isFsfLibre: boolean
  isDeprecated: boolean
  seeAlso: string[]
  licenseText?: string
  standardHeader?: string
  category: string
}

export interface SpdxLicenseSearchResponse {
  licenses: SpdxLicenseInfo[]
  total: number
}

export interface BulkValidationResponse {
  results: BulkValidationItem[]
  validCount: number
  invalidCount: number
}

export interface BulkValidationItem {
  input: string
  isValid: boolean
  normalizedId?: string
  message?: string
}

export type LicenseValidationResponse = BulkValidationResponse

// Package Manager Types
export interface PackageManagerInfo {
  name: string
  displayName: string
  description: string
  filePatterns: string[]
  category: string
}

export interface PackageManagerListResponse {
  packageManagers: PackageManagerInfo[]
  categories: string[]
}

// ORT Config Export Types
export interface OrtConfigExport {
  configYml: string
  filename: string
}

export interface AnalyzerConfigRequest {
  allowDynamicVersions: boolean
  skipExcluded: boolean
  disabledPackageManagers: string[]
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
    enableSpdx?: boolean
    enableSourceScan?: boolean
    parallelAiCalls?: boolean
    demoMode?: boolean
    disabledPackageManagers?: string[]
    allowDynamicVersions?: boolean
    skipExcluded?: boolean
  }) => apiClient.post<Scan>('/scans', data),

  // Package Managers
  getPackageManagers: () =>
    apiClient.get<PackageManagerListResponse>('/scans/package-managers'),

  // ORT Config Export
  generateOrtConfig: (config: AnalyzerConfigRequest) =>
    apiClient.post<OrtConfigExport>('/scans/ort-config', config),

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
    apiClient.delete(`/auth/api-keys/${id}`),

  // Curation
  startCurationSession: (scanId: string) =>
    apiClient.post<CurationSession>(`/scans/${scanId}/curation/start`, {}),

  getCurationSessionByScan: (scanId: string) =>
    apiClient.get<CurationSession>(`/scans/${scanId}/curation`),

  getCurationSession: (sessionId: string) =>
    apiClient.get<CurationSession>(`/scans/${sessionId}/curation`),

  listCurationItems: (scanId: string, filter?: CurationFilter) =>
    apiClient.get<{ items: CurationItem[]; total: number; page: number; pageSize: number }>(
      `/scans/${scanId}/curation/items`,
      { params: filter }
    ),

  getCurationItem: (scanId: string, dependencyId: string) =>
    apiClient.get<CurationItem>(`/scans/${scanId}/curation/items/${dependencyId}`),

  submitCurationDecision: (scanId: string, dependencyId: string, decision: CurationDecision) =>
    apiClient.put<CurationItem>(`/scans/${scanId}/curation/items/${dependencyId}`, decision),

  bulkCurationDecision: (scanId: string, dependencyIds: string[], decision: CurationDecision) =>
    apiClient.post<{ updated: number; results: CurationItem[] }>(`/scans/${scanId}/curation/bulk`, {
      decisions: dependencyIds.map(id => ({
        dependencyId: id,
        action: decision.action,
        curatedLicense: decision.curatedLicense,
        comment: decision.comment
      }))
    }),

  approveCurationSession: (scanId: string, _approvedBy: string, comment?: string) =>
    apiClient.post<CurationSession>(`/scans/${scanId}/curation/approve`, {
      comment
    }),

  // Curation Templates
  listTemplates: () =>
    apiClient.get<{ templates: CurationTemplate[]; total: number }>('/curation/templates'),

  getTemplate: (templateId: string) =>
    apiClient.get<CurationTemplate>(`/curation/templates/${templateId}`),

  createTemplate: (template: Omit<CurationTemplate, 'id' | 'usageCount' | 'createdAt'>) =>
    apiClient.post<CurationTemplate>('/curation/templates', template),

  updateTemplate: (templateId: string, template: Partial<CurationTemplate>) =>
    apiClient.put<CurationTemplate>(`/curation/templates/${templateId}`, template),

  deleteTemplate: (templateId: string) =>
    apiClient.delete(`/curation/templates/${templateId}`),

  previewTemplate: (sessionId: string, templateId: string) =>
    apiClient.post<TemplatePreviewResult>(`/curation/sessions/${sessionId}/preview-template`, { templateId }),

  applyTemplate: (sessionId: string, templateId: string) =>
    apiClient.post<{ applied: number }>(`/curation/sessions/${sessionId}/apply-template`, { templateId }),

  // Reports
  getReportSummary: (scanId: string) =>
    apiClient.get<ReportSummary>(`/scans/${scanId}/reports/summary`),

  generateReport: (scanId: string, options?: {
    format?: string
    includePolicy?: boolean
    includeCuration?: boolean
    includeAuditTrail?: boolean
    includeDependencyDetails?: boolean
  }) =>
    apiClient.post(`/scans/${scanId}/reports/generate`, options || {}),

  downloadReport: (scanId: string, format: 'json' | 'html' = 'json', includeDetails = false) =>
    apiClient.get(`/scans/${scanId}/reports/download`, {
      params: { format, includeDetails },
      responseType: 'blob'
    }),

  downloadOrtExport: (scanId: string) =>
    apiClient.get<{ scanId: string; filename: string; content: string; format: string; generatedAt: string }>(
      `/scans/${scanId}/reports/ort`
    ),

  // SPDX License APIs
  searchSpdxLicenses: (query: string, options?: { osiOnly?: boolean; limit?: number }) =>
    apiClient.get<SpdxLicenseSearchResponse>('/licenses/spdx/search', {
      params: { q: query, ...options }
    }),

  getSpdxLicense: (id: string) =>
    apiClient.get<SpdxLicenseDetailResponse>(`/licenses/spdx/${id}`),

  getCommonLicenses: () =>
    apiClient.get<{ licenses: SpdxLicenseInfo[] }>('/licenses/spdx/common'),

  validateLicenses: (licenseIds: string[]) =>
    apiClient.post<LicenseValidationResponse>('/licenses/validate', { licenseIds })
}

export default apiClient
