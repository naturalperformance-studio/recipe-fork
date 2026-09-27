package app.recipefork.server.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import javax.sql.DataSource

/** Where the store is and how to reach it. */
data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String,
    val maxPoolSize: Int = 10,
    /** The schema every connection works in and migrations are applied to; `null` keeps the server's default. */
    val schema: String? = null,
) {
    companion object {
        /** Reads `DATABASE_URL`, `DATABASE_USER` and `DATABASE_PASSWORD`, as `deploy/local/compose.yml` sets them. */
        fun fromEnvironment(env: Map<String, String> = System.getenv()): DatabaseConfig = DatabaseConfig(
            url = env.getValue("DATABASE_URL"),
            user = env.getValue("DATABASE_USER"),
            password = env.getValue("DATABASE_PASSWORD"),
        )
    }
}

object Database {
    /** Opens the connection pool and brings the schema up to the latest migration before returning it. */
    fun connect(config: DatabaseConfig): HikariDataSource {
        val pool = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = config.url
                username = config.user
                password = config.password
                maximumPoolSize = config.maxPoolSize
                config.schema?.let { schema = it }
            },
        )
        try {
            migrate(pool, config.schema)
        } catch (e: Exception) {
            pool.close()
            throw e
        }
        return pool
    }

    /** Applies the SQL migrations under `db/migration` that the store has not yet seen; returns how many ran. */
    fun migrate(dataSource: DataSource, schema: String? = null): Int =
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .apply { if (schema != null) schemas(schema) }
            .load()
            .migrate()
            .migrationsExecuted
}
