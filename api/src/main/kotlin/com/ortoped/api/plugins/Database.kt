package com.ortoped.api.plugins

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

private val logger = KotlinLogging.logger {}

fun Application.configureDatabase() {
    val jdbcUrl = System.getenv("DATABASE_URL")
        ?: "jdbc:postgresql://localhost:5432/ortoped"
    val dbUser = System.getenv("DATABASE_USER") ?: "ortoped"
    val dbPassword = System.getenv("DATABASE_PASSWORD") ?: "ortoped"

    logger.info { "Connecting to database: $jdbcUrl" }

    // Configure HikariCP connection pool
    val hikariConfig = HikariConfig().apply {
        this.jdbcUrl = jdbcUrl
        this.username = dbUser
        this.password = dbPassword
        this.driverClassName = "org.postgresql.Driver"
        this.maximumPoolSize = 10
        this.minimumIdle = 2
        this.idleTimeout = 30000
        this.connectionTimeout = 30000
        this.maxLifetime = 1800000
        this.isAutoCommit = false
        this.transactionIsolation = "TRANSACTION_REPEATABLE_READ"
    }

    val dataSource = HikariDataSource(hikariConfig)

    // Run Flyway migrations
    logger.info { "Running database migrations..." }
    val flyway = Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .baselineOnMigrate(true)
        .load()

    val migrationsApplied = flyway.migrate()
    logger.info { "Applied ${migrationsApplied.migrationsExecuted} migrations" }

    // Connect Exposed to the database
    Database.connect(datasource = dataSource)

    logger.info { "Database connection established" }
}
