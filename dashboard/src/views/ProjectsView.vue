<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api, type Project } from '@/api/client'
import { RouterLink } from 'vue-router'

const projects = ref<Project[]>([])
const loading = ref(true)
const showModal = ref(false)
const newProject = ref({ name: '', repositoryUrl: '', defaultBranch: 'main' })

async function fetchProjects() {
  loading.value = true
  try {
    const response = await api.listProjects(1, 100)
    projects.value = response.data.projects
  } catch (e) {
    console.error('Failed to fetch projects', e)
  } finally {
    loading.value = false
  }
}

async function createProject() {
  try {
    await api.createProject(newProject.value)
    showModal.value = false
    newProject.value = { name: '', repositoryUrl: '', defaultBranch: 'main' }
    await fetchProjects()
  } catch (e) {
    console.error('Failed to create project', e)
  }
}

async function deleteProject(id: string) {
  if (!confirm('Are you sure you want to delete this project?')) return
  try {
    await api.deleteProject(id)
    await fetchProjects()
  } catch (e) {
    console.error('Failed to delete project', e)
  }
}

onMounted(fetchProjects)
</script>

<template>
  <div class="projects-page">
    <header class="page-header">
      <div>
        <h1>Projects</h1>
        <p class="subtitle">Manage your scanned projects</p>
      </div>
      <button class="btn btn-primary" @click="showModal = true">
        <i class="pi pi-plus"></i> New Project
      </button>
    </header>

    <div v-if="loading" class="loading">
      <i class="pi pi-spin pi-spinner"></i> Loading projects...
    </div>

    <div v-else-if="projects.length === 0" class="empty-state">
      <i class="pi pi-folder-open"></i>
      <h3>No projects yet</h3>
      <p>Create your first project to start scanning for license compliance.</p>
      <button class="btn btn-primary" @click="showModal = true">Create Project</button>
    </div>

    <div v-else class="projects-grid">
      <div v-for="project in projects" :key="project.id" class="project-card">
        <div class="project-header">
          <i class="pi pi-folder"></i>
          <h3>{{ project.name }}</h3>
        </div>
        <div class="project-meta">
          <span v-if="project.repositoryUrl" class="meta-item">
            <i class="pi pi-github"></i>
            {{ project.repositoryUrl.split('/').slice(-1)[0] }}
          </span>
          <span class="meta-item">
            <i class="pi pi-code-branch"></i>
            {{ project.defaultBranch }}
          </span>
        </div>
        <div class="project-actions">
          <RouterLink :to="`/projects/${project.id}`" class="btn btn-small">
            View Details
          </RouterLink>
          <button class="btn btn-small btn-danger" @click="deleteProject(project.id)">
            Delete
          </button>
        </div>
      </div>
    </div>

    <!-- Create Project Modal -->
    <div v-if="showModal" class="modal-overlay" @click.self="showModal = false">
      <div class="modal">
        <h2>Create New Project</h2>
        <form @submit.prevent="createProject">
          <div class="form-group">
            <label for="name">Project Name</label>
            <input
              id="name"
              v-model="newProject.name"
              type="text"
              required
              placeholder="My Project"
            />
          </div>
          <div class="form-group">
            <label for="repo">Repository URL (optional)</label>
            <input
              id="repo"
              v-model="newProject.repositoryUrl"
              type="url"
              placeholder="https://github.com/user/repo"
            />
          </div>
          <div class="form-group">
            <label for="branch">Default Branch</label>
            <input
              id="branch"
              v-model="newProject.defaultBranch"
              type="text"
              placeholder="main"
            />
          </div>
          <div class="form-actions">
            <button type="button" class="btn btn-secondary" @click="showModal = false">
              Cancel
            </button>
            <button type="submit" class="btn btn-primary">Create</button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<style scoped>
.projects-page {
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
  margin-top: 0.25rem;
}

.btn {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1.5rem;
  border-radius: 0.5rem;
  font-weight: 500;
  cursor: pointer;
  border: none;
  transition: all 0.2s;
}

.btn-primary {
  background: #3b82f6;
  color: white;
}

.btn-primary:hover {
  background: #2563eb;
}

.btn-secondary {
  background: #e2e8f0;
  color: #475569;
}

.btn-small {
  padding: 0.5rem 1rem;
  font-size: 0.875rem;
}

.btn-danger {
  background: #fee2e2;
  color: #dc2626;
}

.btn-danger:hover {
  background: #fecaca;
}

.projects-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 1.5rem;
}

.project-card {
  background: white;
  border-radius: 1rem;
  padding: 1.5rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.project-header {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.project-header i {
  font-size: 1.5rem;
  color: #3b82f6;
}

.project-header h3 {
  margin: 0;
  font-size: 1.125rem;
  color: #1e293b;
}

.project-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
  margin-bottom: 1rem;
  font-size: 0.875rem;
  color: #64748b;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.project-actions {
  display: flex;
  gap: 0.5rem;
}

.loading, .empty-state {
  text-align: center;
  padding: 4rem 2rem;
  color: #64748b;
}

.empty-state i {
  font-size: 4rem;
  margin-bottom: 1rem;
  opacity: 0.3;
}

.empty-state h3 {
  color: #1e293b;
  margin-bottom: 0.5rem;
}

.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

.modal {
  background: white;
  border-radius: 1rem;
  padding: 2rem;
  width: 100%;
  max-width: 480px;
}

.modal h2 {
  margin: 0 0 1.5rem;
  color: #1e293b;
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

.form-group input {
  width: 100%;
  padding: 0.75rem;
  border: 1px solid #d1d5db;
  border-radius: 0.5rem;
  font-size: 1rem;
}

.form-group input:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.form-actions {
  display: flex;
  gap: 1rem;
  justify-content: flex-end;
  margin-top: 1.5rem;
}
</style>
