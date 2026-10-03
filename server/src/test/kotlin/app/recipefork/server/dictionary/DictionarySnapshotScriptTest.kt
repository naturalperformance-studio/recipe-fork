package app.recipefork.server.dictionary

import app.recipefork.core.api.DictionariesDto
import app.recipefork.core.api.IngredientEntryDto
import app.recipefork.server.db.TestDatabase
import app.recipefork.server.module
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

/**
 * `scripts/dictionary-snapshot.sh` against a running server: what it writes is what the server served — divisibility,
 * factors and removal included — under the server's own version, which is the digest of exactly that content.
 */
class DictionarySnapshotScriptTest {
    private lateinit var db: HikariDataSource

    @TempDir
    lateinit var dir: File

    @BeforeEach
    fun setUp() {
        db = TestDatabase.fresh()
    }

    @AfterEach
    fun tearDown() = db.close()

    @Test
    fun `the snapshot script passes divisibility, factors and removal through`() {
        assumeTrue(onPath("jq") && onPath("curl") && onPath("bash"), "the script needs bash, curl and jq")
        val kuritsa = sql("insert into ingredient_entry (label_ru, label_en, divisible) values ('курица', 'chicken', true) returning entry_id")
        val krylyshki = sql(
            "insert into ingredient_entry (label_ru, label_en, parent_id, divisible) values ('крылышки', 'chicken wings', $kuritsa, false) returning entry_id",
        )
        val farsh = sql("insert into ingredient_entry (label_ru, label_en) values ('фарш', 'mince') returning entry_id")
        val gus = sql("insert into ingredient_entry (label_ru, label_en, removed) values ('гусь', 'goose', true) returning entry_id")
        sql("insert into ingredient_factor values ($kuritsa, 'g', 1), ($kuritsa, 'pc', 1500), ($krylyshki, 'g', 1), ($krylyshki, 'pc', 80.5), ($farsh, 'g', 1) returning entry_id")
        sql("insert into tag (label_ru, label_en) values ('итальянская', 'Italian') returning tag_id")

        val server = embeddedServer(Netty, port = 0) { module(db) }.start(wait = false)
        try {
            val port = runBlocking { server.engine.resolvedConnectors().first().port }
            val out = File(dir, "dictionaries.json")
            val script = File("../scripts/dictionary-snapshot.sh").canonicalFile
            val process = ProcessBuilder("bash", script.path, "http://127.0.0.1:$port", out.path).redirectErrorStream(true).start()
            val log = process.inputStream.bufferedReader().readText()
            check(process.waitFor(60, TimeUnit.SECONDS)) { "the script did not finish: $log" }
            assertEquals(0, process.exitValue(), log)

            val written = Json.decodeFromString<DictionariesDto>(out.readText())
            assertEquals(
                listOf(
                    IngredientEntryDto(kuritsa, "курица", "chicken", null, true, mapOf("g" to 1.0, "pc" to 1500.0)),
                    IngredientEntryDto(krylyshki, "крылышки", "chicken wings", kuritsa, false, mapOf("g" to 1.0, "pc" to 80.5)),
                    IngredientEntryDto(farsh, "фарш", "mince", factors = mapOf("g" to 1.0)),
                    IngredientEntryDto(gus, "гусь", "goose", removed = true),
                ),
                written.ingredients,
            )
            assertEquals(DictionaryStore(db).snapshot().toDto(), written)
            // A field at its default is left out, as the application encodes it.
            assertEquals(false, out.readText().contains("null"))
        } finally {
            server.stop(0, 0)
        }
    }

    private fun onPath(command: String): Boolean =
        ProcessBuilder("sh", "-c", "command -v $command").start().waitFor() == 0

    private fun sql(statement: String): Long =
        db.connection.use { conn ->
            conn.createStatement().use { it.executeQuery(statement).use { rs -> rs.next(); rs.getLong(1) } }
        }
}
