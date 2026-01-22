<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
</script>

<template>
  <div class="app-container">
    <nav class="sidebar">
      <div class="logo">
        <h1>OrtoPed</h1>
        <span class="subtitle">License Compliance</span>
      </div>

      <ul class="nav-links">
        <li>
          <RouterLink to="/" class="nav-link">
            <i class="pi pi-home"></i>
            <span>Dashboard</span>
          </RouterLink>
        </li>
        <li>
          <RouterLink to="/projects" class="nav-link">
            <i class="pi pi-folder"></i>
            <span>Projects</span>
          </RouterLink>
        </li>
        <li>
          <RouterLink to="/scans" class="nav-link">
            <i class="pi pi-search"></i>
            <span>Scans</span>
          </RouterLink>
        </li>
        <li>
          <RouterLink to="/policies" class="nav-link">
            <i class="pi pi-shield"></i>
            <span>Policies</span>
          </RouterLink>
        </li>
        <li>
          <RouterLink to="/settings" class="nav-link">
            <i class="pi pi-cog"></i>
            <span>Settings</span>
          </RouterLink>
        </li>
      </ul>
    </nav>

    <main class="main-content">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.app-container {
  display: flex;
  min-height: 100vh;
}

.sidebar {
  width: 250px;
  background: #1e293b;
  color: white;
  padding: 1rem;
  display: flex;
  flex-direction: column;
}

.logo {
  padding: 1rem;
  border-bottom: 1px solid #334155;
  margin-bottom: 1rem;
}

.logo h1 {
  margin: 0;
  font-size: 1.5rem;
  color: #60a5fa;
}

.logo .subtitle {
  font-size: 0.75rem;
  color: #94a3b8;
}

.nav-links {
  list-style: none;
  padding: 0;
  margin: 0;
}

.nav-link {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem 1rem;
  color: #94a3b8;
  text-decoration: none;
  border-radius: 0.5rem;
  transition: all 0.2s;
}

.nav-link:hover {
  background: #334155;
  color: white;
}

.nav-link.router-link-active {
  background: #3b82f6;
  color: white;
}

.main-content {
  flex: 1;
  padding: 2rem;
  background: #f8fafc;
  overflow-y: auto;
}
</style>
