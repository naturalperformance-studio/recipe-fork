package app.recipefork.server.db

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DatabaseTest {
    @Test
    fun `connecting migrates an empty schema, and migrating again runs nothing`() {
        TestDatabase.fresh().use { db ->
            assertEquals(0, Database.migrate(db, db.schema))
            db.connection.use { conn ->
                conn.createStatement().executeQuery("select count(*) from ingredient_entry").use { rs ->
                    rs.next()
                    assertEquals(0, rs.getInt(1))
                }
            }
        }
    }
}
