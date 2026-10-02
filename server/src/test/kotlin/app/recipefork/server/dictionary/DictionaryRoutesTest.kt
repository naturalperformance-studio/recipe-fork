package app.recipefork.server.dictionary

import app.recipefork.core.api.DictionariesDto
import app.recipefork.core.api.IngredientEntryDto
import app.recipefork.core.api.TagDto
import app.recipefork.server.db.TestDatabase
import app.recipefork.server.module
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The server half of FLOW-001: both dictionaries, and a version that tells a device when they changed. */
class DictionaryRoutesTest {
    private lateinit var db: HikariDataSource

    @BeforeEach
    fun setUp() {
        db = TestDatabase.fresh()
    }

    @AfterEach
    fun tearDown() = db.close()

    @Test
    fun `FLOW-001 - the server serves both dictionaries with their version as the ETag`() = serve {
        val kuritsa = sql("insert into ingredient_entry (label_ru, label_en) values ('курица', 'chicken') returning entry_id")
        val krylyshki = sql("insert into ingredient_entry (label_ru, label_en, parent_id) values ('крылышки', 'chicken wings', $kuritsa) returning entry_id")
        val italian = sql("insert into tag (label_ru, label_en) values ('итальянская', 'Italian') returning tag_id")

        val response = client.get(DictionariesDto.PATH)
        val body = Json.decodeFromString<DictionariesDto>(response.bodyAsText())

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            listOf(IngredientEntryDto(kuritsa, "курица", "chicken"), IngredientEntryDto(krylyshki, "крылышки", "chicken wings", kuritsa)),
            body.ingredients,
        )
        assertEquals(listOf(TagDto(italian, "итальянская", "Italian")), body.tags)
        assertEquals("\"${body.version}\"", response.headers[HttpHeaders.ETag])
    }

    @Test
    fun `FLOW-001 - a device holding the current version is told nothing changed`() = serve {
        sql("insert into tag (label_ru, label_en) values ('итальянская', 'Italian') returning tag_id")
        val etag = client.get(DictionariesDto.PATH).headers[HttpHeaders.ETag]!!

        val again = client.get(DictionariesDto.PATH) { header(HttpHeaders.IfNoneMatch, etag) }

        assertEquals(HttpStatusCode.NotModified, again.status)
        assertTrue(again.bodyAsText().isEmpty())
    }

    @Test
    fun `FLOW-001 - after the product owner changes either dictionary, the held version is answered with the new copy`() = serve {
        val kuritsa = sql("insert into ingredient_entry (label_ru, label_en) values ('курица', 'chicken') returning entry_id")
        val italian = sql("insert into tag (label_ru, label_en) values ('итальянская', 'Italian') returning tag_id")
        val first = client.get(DictionariesDto.PATH).headers[HttpHeaders.ETag]!!

        // The operator relabels a tag directly in the store (DOM-014).
        sql("update tag set label_en = 'Italian cuisine' where tag_id = $italian returning tag_id")
        val afterTag = client.get(DictionariesDto.PATH) { header(HttpHeaders.IfNoneMatch, first) }
        assertEquals(HttpStatusCode.OK, afterTag.status)
        assertEquals("Italian cuisine", Json.decodeFromString<DictionariesDto>(afterTag.bodyAsText()).tags.single().labelEn)
        val second = afterTag.headers[HttpHeaders.ETag]!!
        assertNotEquals(first, second)

        // ...and then removes an ingredient entry.
        sql("delete from ingredient_entry where entry_id = $kuritsa returning entry_id")
        val afterEntry = client.get(DictionariesDto.PATH) { header(HttpHeaders.IfNoneMatch, second) }
        assertEquals(HttpStatusCode.OK, afterEntry.status)
        assertTrue(Json.decodeFromString<DictionariesDto>(afterEntry.bodyAsText()).ingredients.isEmpty())
        assertNotEquals(second, afterEntry.headers[HttpHeaders.ETag])
    }

    private fun serve(test: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { module(db) }
        test()
    }

    private fun sql(statement: String): Long =
        db.connection.use { conn ->
            conn.createStatement().use { it.executeQuery(statement).use { rs -> rs.next(); rs.getLong(1) } }
        }
}
