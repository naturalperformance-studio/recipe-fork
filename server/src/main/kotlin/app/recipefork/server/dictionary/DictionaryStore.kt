package app.recipefork.server.dictionary

import java.sql.Connection
import java.sql.ResultSet
import javax.sql.DataSource

/** An entry of the ingredient dictionary, as the store holds it. */
data class IngredientEntry(
    val id: Long,
    val labelRu: String,
    val labelEn: String,
    val parentId: Long?,
)

/** A tag of the tag dictionary, as the store holds it. */
data class Tag(
    val id: Long,
    val labelRu: String,
    val labelEn: String,
)

/** Both dictionaries, read together so that neither is newer than the other. */
data class DictionarySnapshot(
    val ingredients: List<IngredientEntry>,
    val tags: List<Tag>,
)

/**
 * Reads both dictionaries. There is deliberately no write here: the dictionaries are
 * maintained only by direct operation on the store (DOM-014), and the store's own
 * constraints refuse a malformed entry (DOM-010, L10N-003).
 */
class DictionaryStore(private val dataSource: DataSource) {

    /** Both dictionaries from one repeatable-read transaction. */
    fun snapshot(): DictionarySnapshot =
        dataSource.connection.use { conn ->
            val autoCommit = conn.autoCommit
            val isolation = conn.transactionIsolation
            conn.transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ
            conn.autoCommit = false
            try {
                DictionarySnapshot(
                    ingredients = conn.query(INGREDIENTS_SQL) { it.toEntry() },
                    tags = conn.query(TAGS_SQL) { it.toTag() },
                ).also { conn.commit() }
            } finally {
                conn.rollback()
                conn.autoCommit = autoCommit
                conn.transactionIsolation = isolation
            }
        }

    fun ingredientEntries(): List<IngredientEntry> =
        query(INGREDIENTS_SQL) { it.toEntry() }

    fun ingredientEntry(id: Long): IngredientEntry? =
        query("select entry_id, label_ru, label_en, parent_id from ingredient_entry where entry_id = ?", id) { it.toEntry() }
            .singleOrNull()

    fun tags(): List<Tag> =
        query(TAGS_SQL) { it.toTag() }

    private fun ResultSet.toEntry() = IngredientEntry(
        id = getLong(1),
        labelRu = getString(2),
        labelEn = getString(3),
        parentId = getLong(4).takeUnless { wasNull() },
    )

    private fun ResultSet.toTag() = Tag(id = getLong(1), labelRu = getString(2), labelEn = getString(3))

    private fun <T> query(sql: String, vararg params: Any, row: (ResultSet) -> T): List<T> =
        dataSource.connection.use { it.query(sql, *params, row = row) }

    private fun <T> Connection.query(sql: String, vararg params: Any, row: (ResultSet) -> T): List<T> =
        prepareStatement(sql).use { stmt ->
            params.forEachIndexed { i, p -> stmt.setObject(i + 1, p) }
            stmt.executeQuery().use { rs -> buildList { while (rs.next()) add(row(rs)) } }
        }

    private companion object {
        const val INGREDIENTS_SQL = "select entry_id, label_ru, label_en, parent_id from ingredient_entry order by entry_id"
        const val TAGS_SQL = "select tag_id, label_ru, label_en from tag order by tag_id"
    }
}
