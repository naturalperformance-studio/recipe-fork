package app.recipefork.server.dictionary

import app.recipefork.server.db.TestDatabase
import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.postgresql.util.PSQLException
import java.sql.PreparedStatement
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Every write here is plain SQL on the store, as the operator maintains the dictionaries
 * (DOM-014): no application code stands between the write and the table.
 */
class DictionaryStoreTest {
    private lateinit var db: HikariDataSource
    private lateinit var store: DictionaryStore

    @BeforeEach
    fun setUp() {
        db = TestDatabase.fresh()
        store = DictionaryStore(db)
    }

    @AfterEach
    fun tearDown() = db.close()

    // DOM-010

    @Test
    fun `DOM-010 - given krylyshki under kuritsa, when kuritsa is read, it has no parent of its own`() {
        val kuritsa = insertEntry("курица", "chicken")
        val krylyshki = insertEntry("крылышки", "chicken wings", parent = kuritsa)
        val myaso = insertEntry("мясо", "meat")

        assertRefusedBy("ingredient_entry_parent_is_top_level") { setParent(kuritsa, myaso) }

        assertNull(store.ingredientEntry(kuritsa)!!.parentId)
        assertEquals(kuritsa, store.ingredientEntry(krylyshki)!!.parentId)
    }

    @Test
    fun `DOM-010 - giving a parent to an entry that already has children is rejected`() {
        val kuritsa = insertEntry("курица", "chicken")
        insertEntry("крылышки", "chicken wings", parent = kuritsa)
        val myaso = insertEntry("мясо", "meat")
        val farsh = insertEntry("фарш", "mince")

        assertRefusedBy("ingredient_entry_parent_is_top_level") { setParent(kuritsa, myaso) }
        assertRefusedBy("ingredient_entry_parent_is_top_level") { setParent(kuritsa, kuritsa) }

        // An entry without children may be given a parent: the refusal is about the children.
        setParent(farsh, myaso)
        assertEquals(myaso, store.ingredientEntry(farsh)!!.parentId)
    }

    @Test
    fun `DOM-010 - attaching a child under an entry that already has a parent is rejected`() {
        val kuritsa = insertEntry("курица", "chicken")
        val krylyshki = insertEntry("крылышки", "chicken wings", parent = kuritsa)
        val farsh = insertEntry("фарш", "mince")

        assertRefusedBy("ingredient_entry_parent_is_top_level") { insertEntry("кончики крылышек", "wing tips", parent = krylyshki) }
        assertRefusedBy("ingredient_entry_parent_is_top_level") { setParent(farsh, krylyshki) }

        assertEquals(listOf("курица", "крылышки", "фарш"), store.ingredientEntries().map { it.labelRu })
        assertNull(store.ingredientEntry(farsh)!!.parentId)
    }

    // L10N-003

    @Test
    fun `L10N-003 - a new ingredient entry labelled in one language only is rejected and reaches no author`() {
        insertEntry("курица", "chicken")

        assertRefusedBy("ingredient_entry_label_in_both_languages") { insertEntry("говядина", null) }
        assertRefusedBy("ingredient_entry_label_in_both_languages") { insertEntry(null, "beef") }
        assertRefusedBy("ingredient_entry_label_in_both_languages") { insertEntry("говядина", "  ") }
        assertRefusedBy("ingredient_entry_label_in_both_languages") { insertEntry("", "beef") }

        assertEquals(listOf("chicken"), store.ingredientEntries().map { it.labelEn })
    }

    @Test
    fun `L10N-003 - a new tag labelled in one language only is rejected and reaches no author`() {
        insertTag("итальянская", "Italian")

        assertRefusedBy("tag_label_in_both_languages") { insertTag("грузинская", null) }
        assertRefusedBy("tag_label_in_both_languages") { insertTag(null, "Georgian") }
        assertRefusedBy("tag_label_in_both_languages") { insertTag(" ", "Georgian") }

        assertEquals(listOf("Italian"), store.tags().map { it.labelEn })
    }

    @Test
    fun `L10N-003 - an existing entry or tag cannot lose one of its labels`() {
        val kuritsa = insertEntry("курица", "chicken")
        val italian = insertTag("итальянская", "Italian")

        assertRefusedBy("ingredient_entry_label_in_both_languages") {
            execute("update ingredient_entry set label_en = null where entry_id = ?", kuritsa)
        }
        assertRefusedBy("tag_label_in_both_languages") {
            execute("update tag set label_ru = '' where tag_id = ?", italian)
        }

        assertEquals("chicken", store.ingredientEntry(kuritsa)!!.labelEn)
        assertEquals("итальянская", store.tags().single().labelRu)
    }

    // DOM-014

    @Test
    fun `DOM-014 - a half-translated entry written directly on the store is refused by the store's own constraint`() {
        val refusedEntry = assertFailsWith<PSQLException> { insertEntry("говядина", null) }
        val refusedTag = assertFailsWith<PSQLException> { insertTag(null, "Georgian") }

        // 23514 is PostgreSQL's check_violation: the refusal comes from the store, not from application code.
        assertEquals("23514", refusedEntry.sqlState)
        assertEquals("ingredient_entry_label_in_both_languages", refusedEntry.serverErrorMessage?.constraint)
        assertEquals("23514", refusedTag.sqlState)
        assertEquals("tag_label_in_both_languages", refusedTag.serverErrorMessage?.constraint)
        assertTrue(store.ingredientEntries().isEmpty())
        assertTrue(store.tags().isEmpty())
    }

    // The operator's writes

    private fun insertEntry(ru: String?, en: String?, parent: Long? = null): Long =
        insertReturningId("insert into ingredient_entry (label_ru, label_en, parent_id) values (?, ?, ?) returning entry_id", ru, en, parent)

    private fun insertTag(ru: String?, en: String?): Long =
        insertReturningId("insert into tag (label_ru, label_en) values (?, ?) returning tag_id", ru, en)

    private fun setParent(entry: Long, parent: Long) =
        execute("update ingredient_entry set parent_id = ? where entry_id = ?", parent, entry)

    private fun insertReturningId(sql: String, vararg params: Any?): Long =
        db.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                bind(stmt, params)
                stmt.executeQuery().use { rs -> rs.next(); rs.getLong(1) }
            }
        }

    private fun execute(sql: String, vararg params: Any?) {
        db.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                bind(stmt, params)
                stmt.executeUpdate()
            }
        }
    }

    // PgJDBC sends a null bound with setObject as an untyped parameter, which the server types from the column.
    private fun bind(stmt: PreparedStatement, params: Array<out Any?>) =
        params.forEachIndexed { i, p -> stmt.setObject(i + 1, p) }

    private fun assertRefusedBy(constraint: String, write: () -> Unit) {
        val refused = assertFailsWith<PSQLException> { write() }
        assertEquals(constraint, refused.serverErrorMessage?.constraint, refused.message)
    }
}
