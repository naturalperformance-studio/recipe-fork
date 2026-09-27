package app.recipefork.server.db

import com.zaxxer.hikari.HikariDataSource
import org.testcontainers.containers.PostgreSQLContainer
import java.util.concurrent.atomic.AtomicInteger

/**
 * The PostgreSQL every server test runs against: one container per test JVM, and a
 * fresh, fully migrated schema for each caller, so tests never see each other's rows.
 */
object TestDatabase {
    private val container: PostgreSQLContainer<*> by lazy {
        PostgreSQLContainer("postgres:16-alpine").apply { start() }
    }
    private val counter = AtomicInteger()

    /** A pool on a new schema brought up to the latest migration. Close it when done. */
    fun fresh(): HikariDataSource = Database.connect(
        DatabaseConfig(
            url = container.jdbcUrl,
            user = container.username,
            password = container.password,
            maxPoolSize = 2,
            schema = "test_${counter.incrementAndGet()}",
        ),
    )
}
