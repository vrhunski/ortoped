package com.ortoped.api.plugins

import com.ortoped.api.routes.*
import com.ortoped.api.service.*
import com.ortoped.api.repository.ScanRepository
import com.ortoped.api.repository.OrtCacheRepository
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*

fun Application.configureRouting(
    projectService: ProjectService,
    scanService: ScanService,
    policyService: PolicyService,
    authService: AuthService,
    scanRepository: ScanRepository,
    spdxService: SpdxService,
    curationService: CurationService,
    templateService: TemplateService,
    reportService: ReportService,
    licenseGraphService: LicenseGraphService,
    ortCacheRepository: OrtCacheRepository
) {
    routing {
        // API routes
        route("/api/v1") {
            healthRoutes()
            projectRoutes(projectService)
            scanRoutes(scanService)
            policyRoutes(policyService, scanRepository)
            authRoutes(authService)
            licenseRoutes(spdxService)
            curationRoutes(curationService)
            templateRoutes(templateService)
            reportRoutes(reportService)
            licenseGraphRoutes(licenseGraphService)
            cacheRoutes(ortCacheRepository)
        }

        // Serve Vue.js static files (dashboard)
        staticResources("/", "static") {
            default("index.html")
        }

        // Fallback to index.html for SPA routing
        singlePageApplication {
            useResources = true
            filesPath = "static"
            defaultPage = "index.html"
        }
    }
}
