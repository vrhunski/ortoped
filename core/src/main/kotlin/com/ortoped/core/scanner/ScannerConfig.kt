package com.ortoped.core.scanner

import java.io.File

/**
 * Configuration for the ORT Scanner integration with ScanCode
 */
data class ScannerConfig(
    /** Enable source code scanning (slower but more accurate) */
    val enabled: Boolean = false,

    /** Scanner backend to use */
    val scannerType: ScannerType = ScannerType.SCANCODE,

    /** Directory for caching scan results */
    val cacheDir: File = File(System.getProperty("user.home"), ".ortoped/scanner-cache"),

    /** Directory for downloaded sources */
    val downloadDir: File = File(System.getProperty("user.home"), ".ortoped/downloads"),

    /** Maximum time to scan a single package (ms) */
    val packageTimeout: Long = 300_000, // 5 minutes

    /** Enable parallel scanning of packages */
    val parallelScanning: Boolean = true,

    /** Maximum concurrent scans */
    val maxConcurrentScans: Int = 4,

    /** Skip scanning for packages above this size (MB) */
    val maxPackageSizeMb: Int = 100,

    /** Only scan packages with unresolved licenses */
    val scanOnlyUnresolved: Boolean = true
)

/**
 * Available scanner backend types
 */
enum class ScannerType {
    /** ScanCode - Primary open source scanner, most comprehensive */
    SCANCODE
}