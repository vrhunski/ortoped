import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/scans'
    },
    {
      path: '/dashboard',
      name: 'dashboard',
      component: () => import('@/views/DashboardView.vue')
    },
    {
      path: '/projects',
      name: 'projects',
      component: () => import('@/views/ProjectsView.vue')
    },
    {
      path: '/projects/:id',
      name: 'project-detail',
      component: () => import('@/views/ProjectDetailView.vue')
    },
    {
      path: '/scans',
      name: 'scans',
      component: () => import('@/views/ScansView.vue')
    },
    {
      path: '/scans/:id',
      name: 'scan-detail',
      component: () => import('@/views/ScanDetailView.vue')
    },
    {
      path: '/policies',
      name: 'policies',
      component: () => import('@/views/PoliciesView.vue')
    },
    {
      path: '/policies/:id',
      name: 'policy-detail',
      component: () => import('@/views/PolicyDetailView.vue')
    },
    {
      path: '/settings',
      name: 'settings',
      component: () => import('@/views/SettingsView.vue')
    },
    {
      path: '/curations',
      name: 'curations',
      component: () => import('@/views/CurationsView.vue')
    },
    {
      path: '/curations/:id',
      name: 'curation-session',
      component: () => import('@/views/CurationSessionView.vue')
    },
    {
      path: '/curation-templates',
      name: 'curation-templates',
      component: () => import('@/views/CurationTemplatesView.vue')
    },
    {
      path: '/compliance',
      name: 'compliance',
      component: () => import('@/views/ComplianceView.vue')
    }
  ]
})

export default router
