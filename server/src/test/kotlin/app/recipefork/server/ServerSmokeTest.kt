package app.recipefork.server

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.DriverManager
import kotlin.test.assertEquals

class ServerSmokeTest {
    @Test
    fun `the application module starts and answers nothing it has not been given`() = testApplication {
        application { module() }
        assertEquals(HttpStatusCode.NotFound, client.get("/").status)
    }

    @Test
    fun `the suite can reach PostgreSQL in Docker`() {
        PostgreSQLContainer("postgres:16-alpine").use { db ->
            db.start()
            DriverManager.getConnection(db.jdbcUrl, db.username, db.password).use { conn ->
                conn.createStatement().executeQuery("select 1").use { rs ->
                    rs.next()
                    assertEquals(1, rs.getInt(1))
                }
            }
        }
    }
}
