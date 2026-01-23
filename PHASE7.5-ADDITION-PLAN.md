# Phase 7.5 Addition: Scan Configuration UI, Auto-Refresh & Duration Display

## Confirmed Requirements

| Feature | Decision |
|---------|----------|
| **Scan Config Defaults** | AI: ON, Source Scan: OFF, Parallel AI: ON |
| **UI Layout** | Option A - Always visible inline panel |
| **Persistence** | Global (localStorage, same for all projects) |
| **Demo Mode** | Fixed config: AI=ON, Parallel AI=ON, Source Scan=OFF |
| **Auto-Refresh** | Poll when scans are in progress |
| **Duration Display** | Show for each scan in history table |

---

## Feature 1: Scan Configuration UI

### Backend Changes

**File:** `api/src/main/kotlin/com/ortoped/api/model/ApiModels.kt`

Add `parallelAiCalls` to `TriggerScanRequest`:

```kotlin
@Serializable
data class TriggerScanRequest(
    val projectId: String,
    val enableAi: Boolean = true,           // Default ON
    val enableSourceScan: Boolean = false,  // Default OFF (slower)
    val parallelAiCalls: Boolean = true,    // NEW - Default ON
    val demoMode: Boolean = false,
    val branch: String? = null,
    val tag: String? = null,
    val commit: String? = null
)
```

**File:** `api/src/main/kotlin/com/ortoped/api/service/ScanService.kt`

Wire `parallelAiCalls` from request to orchestrator.

---

## Feature 2: Scan Duration Display

### Backend Changes

**File:** `api/src/main/kotlin/com/ortoped/api/model/ApiModels.kt`

Add `startedAt` to `ScanSummaryResponse`:

```kotlin
@Serializable
data class ScanSummaryResponse(
    val id: String,
    val projectId: String?,
    val status: String,
    val totalDependencies: Int,
    val resolvedLicenses: Int,
    val unresolvedLicenses: Int,
    val aiResolvedLicenses: Int,
    val startedAt: String?,    // NEW
    val completedAt: String?
)
```

**File:** `api/src/main/kotlin/com/ortoped/api/repository/ScanRepository.kt`

Update the `toSummaryResponse()` function to include `startedAt`.

### Frontend Changes

**File:** `dashboard/src/api/client.ts`

Add `startedAt` to `ScanSummary` interface:

```typescript
export interface ScanSummary {
  id: string
  projectId: string | null
  status: string
  totalDependencies: number
  resolvedLicenses: number
  unresolvedLicenses: number
  aiResolvedLicenses: number
  startedAt: string | null    // NEW
  completedAt: string | null
}
```

### Duration Calculation (Frontend)

```typescript
function formatDuration(startedAt: string | null, completedAt: string | null): string {
  if (!startedAt) return '-'

  const start = new Date(startedAt).getTime()
  const end = completedAt ? new Date(completedAt).getTime() : Date.now()
  const durationMs = end - start

  const seconds = Math.floor(durationMs / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)

  if (hours > 0) {
    return `${hours}h ${minutes % 60}m ${seconds % 60}s`
  } else if (minutes > 0) {
    return `${minutes}m ${seconds % 60}s`
  } else {
    return `${seconds}s`
  }
}
```

### Table Column

Add "Duration" column to scan history table:

```html
<th>Duration</th>
...
<td>{{ formatDuration(scan.startedAt, scan.completedAt) }}</td>
```

---

## Feature 3: Auto-Refresh When Scan In Progress

### Implementation Strategy

Use polling with `setInterval` when there are active scans (status: `pending` or `scanning`).

```typescript
import { ref, onMounted, onUnmounted, computed } from 'vue'

// Polling state
let pollInterval: number | null = null
const POLL_INTERVAL_MS = 3000 // 3 seconds

// Check if any scan is in progress
const hasActiveScans = computed(() =>
  scans.value.some(s => s.status === 'pending' || s.status === 'scanning')
)

// Start polling when active scans exist
function startPolling() {
  if (pollInterval) return // Already polling

  pollInterval = window.setInterval(async () => {
    if (!hasActiveScans.value) {
      stopPolling()
      return
    }
    await refreshScans()
  }, POLL_INTERVAL_MS)
}

function stopPolling() {
  if (pollInterval) {
    clearInterval(pollInterval)
    pollInterval = null
  }
}

async function refreshScans() {
  if (!project.value) return
  const scansRes = await api.listScans({ projectId: project.value.id, pageSize: 20 })
  scans.value = scansRes.data.scans
}

// Lifecycle
onMounted(async () => {
  await fetchData()
  if (hasActiveScans.value) {
    startPolling()
  }
})

onUnmounted(() => {
  stopPolling()
})

// After triggering scan, start polling
async function triggerScan(demoMode = false) {
  // ... trigger scan logic ...
  await refreshScans()
  startPolling() // Start polling after scan triggered
}
```

### Visual Indicator

Show a pulsing indicator when scan is in progress:

```html
<span v-if="hasActiveScans" class="scanning-indicator">
  <i class="pi pi-spin pi-spinner"></i> Scanning...
</span>
```

---

## Implementation Tasks Summary

### Task 1: Backend - Add `parallelAiCalls` to TriggerScanRequest
- **File:** `api/src/main/kotlin/com/ortoped/api/model/ApiModels.kt`
- **Change:** Add `parallelAiCalls: Boolean = true` parameter

### Task 2: Backend - Wire `parallelAiCalls` to orchestrator
- **File:** `api/src/main/kotlin/com/ortoped/api/service/ScanService.kt`
- **Change:** Pass `request.parallelAiCalls` to `scanWithAiEnhancement()`

### Task 3: Backend - Add `startedAt` to ScanSummaryResponse
- **File:** `api/src/main/kotlin/com/ortoped/api/model/ApiModels.kt`
- **Change:** Add `startedAt: String?` field

### Task 4: Backend - Include `startedAt` in summary response mapping
- **File:** `api/src/main/kotlin/com/ortoped/api/repository/ScanRepository.kt`
- **Change:** Update `toSummaryResponse()` to include `startedAt`

### Task 5: Frontend - Update API client types
- **File:** `dashboard/src/api/client.ts`
- **Changes:**
  - Add `startedAt` to `ScanSummary` interface
  - Update `triggerScan` signature with all options

### Task 6: Frontend - Add scan config UI with toggles
- **File:** `dashboard/src/views/ProjectDetailView.vue`
- **Changes:**
  - Import `InputSwitch` from PrimeVue
  - Add `scanConfig` reactive state
  - Add localStorage persistence
  - Add configuration panel UI
  - Add CSS styles

### Task 7: Frontend - Add duration display
- **File:** `dashboard/src/views/ProjectDetailView.vue`
- **Changes:**
  - Add `formatDuration()` helper function
  - Add "Duration" column to table
  - Display formatted duration

### Task 8: Frontend - Add auto-refresh polling
- **File:** `dashboard/src/views/ProjectDetailView.vue`
- **Changes:**
  - Add polling logic with `setInterval`
  - Add `hasActiveScans` computed property
  - Add scanning indicator
  - Clean up on unmount

---

## Files Modified Summary

| File | Changes |
|------|---------|
| `api/.../model/ApiModels.kt` | Add `parallelAiCalls` to request, `startedAt` to summary |
| `api/.../service/ScanService.kt` | Wire `parallelAiCalls` to orchestrator |
| `api/.../repository/ScanRepository.kt` | Include `startedAt` in summary mapping |
| `dashboard/src/api/client.ts` | Update types for triggerScan and ScanSummary |
| `dashboard/src/views/ProjectDetailView.vue` | Config UI, duration display, auto-refresh |

---

## UI Mockup

```
┌─────────────────────────────────────────────────────────────────┐
│  Projects > my-project                                          │
│  ═══════════════════════════════════════════════════════════════│
│                                                                 │
│  Scan Configuration                                             │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ Enable AI Analysis                                    [●] │  │
│  │ Use Claude to resolve unknown licenses                    │  │
│  ├───────────────────────────────────────────────────────────┤  │
│  │ Source Code Scan                                      [○] │  │
│  │ Deep scan with ScanCode (slower)                          │  │
│  ├───────────────────────────────────────────────────────────┤  │
│  │ Parallel AI Processing                                [●] │  │
│  │ Process packages concurrently                             │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
│  [Demo Scan]  [Run Scan]                                        │
│                                                                 │
│  Scan History                              ⟳ Scanning...        │
│  ┌──────────┬──────┬────────┬──────────┬──────────┬──────────┐  │
│  │ Status   │ Deps │ Resolved│ Duration │ Completed│          │  │
│  ├──────────┼──────┼────────┼──────────┼──────────┼──────────┤  │
│  │ ◉ scanning│ 45  │ 12     │ 1m 23s   │ -        │ [View]   │  │
│  │ ✓ complete│ 128 │ 125    │ 3m 45s   │ 10:30 AM │ [View]   │  │
│  │ ✓ complete│ 128 │ 120    │ 5m 12s   │ Yesterday│ [View]   │  │
│  └──────────┴──────┴────────┴──────────┴──────────┴──────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Testing Checklist

- [ ] Backend compiles with new `parallelAiCalls` parameter
- [ ] Backend returns `startedAt` in scan summaries
- [ ] API accepts all three scan config options
- [ ] Frontend shows scan configuration panel
- [ ] Toggle states persist across page refreshes
- [ ] Normal scan uses user-configured options
- [ ] Demo scan uses fixed settings (AI ON, Parallel ON, Source OFF)
- [ ] Parallel AI toggle disabled when AI is disabled
- [ ] Duration column shows formatted time (e.g., "2m 15s")
- [ ] Duration updates live for in-progress scans
- [ ] UI auto-refreshes every 3 seconds when scan is active
- [ ] Polling stops when all scans complete
- [ ] Scanning indicator visible during active scans
- [ ] Toast shows correct configuration summary
