import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { api, type ScanSummary, type Project } from '@/api/client'

export const useDashboardStore = defineStore('dashboard', () => {
  const recentScans = ref<ScanSummary[]>([])
  const projects = ref<Project[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  const stats = computed(() => {
    const completedScans = recentScans.value.filter(s => s.status === 'complete')
    const totalDeps = completedScans.reduce((sum, s) => sum + s.totalDependencies, 0)
    const resolvedDeps = completedScans.reduce((sum, s) => sum + s.resolvedLicenses, 0)
    const unresolvedDeps = completedScans.reduce((sum, s) => sum + s.unresolvedLicenses, 0)
    const aiResolvedDeps = completedScans.reduce((sum, s) => sum + s.aiResolvedLicenses, 0)

    return {
      totalProjects: projects.value.length,
      totalScans: recentScans.value.length,
      completedScans: completedScans.length,
      totalDependencies: totalDeps,
      resolvedLicenses: resolvedDeps,
      unresolvedLicenses: unresolvedDeps,
      aiResolvedLicenses: aiResolvedDeps,
      resolutionRate: totalDeps > 0 ? Math.round((resolvedDeps / totalDeps) * 100) : 0
    }
  })

  async function fetchDashboardData() {
    loading.value = true
    error.value = null

    try {
      const [scansResponse, projectsResponse] = await Promise.all([
        api.listScans({ pageSize: 10 }),
        api.listProjects(1, 100)
      ])

      recentScans.value = scansResponse.data.scans
      projects.value = projectsResponse.data.projects
    } catch (e) {
      error.value = 'Failed to load dashboard data'
      console.error(e)
    } finally {
      loading.value = false
    }
  }

  return {
    recentScans,
    projects,
    loading,
    error,
    stats,
    fetchDashboardData
  }
})
