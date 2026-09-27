package app.recipefork.server.dictionary

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

/**
 * Reads both dictionaries. There is deliberately no write here: the dictionaries are
 * maintained only by direct operation on the store (DOM-014), and the store's own
 * constraints refuse a malformed entry (DOM-010, L10N-003).
 */
class DictionaryStore(private val dataSource: DataSource) {

    fun ingredientEntries(): List<IngredientEntry> =
        query("select entry_id, label_ru, label_en, parent_id from ingredient_entry order by entry_id") { it.toEntry() }

    fun ingredientEntry(id: Long): IngredientEntry? =
        query("select entry_id, label_ru, label_en, parent_id from ingredient_entry where entry_id = ?", id) { it.toEntry() }
            .singleOrNull()

    fun tags(): List<Tag> =
        query("select tag_id, label_ru, label_en from tag order by tag_id") {
            Tag(id = it.getLong(1), labelRu = it.getString(2), labelEn = it.getString(3))
        }

    private fun ResultSet.toEntry() = IngredientEntry(
        id = getLong(1),
        labelRu = getString(2),
        labelEn = getString(3),
        parentId = getLong(4).takeUnless { wasNull() },
    )

    private fun <T> query(sql: String, vararg params: Any, row: (ResultSet) -> T): List<T> =
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                params.forEachIndexed { i, p -> stmt.setObject(i + 1, p) }
                stmt.executeQuery().use { rs -> buildList { while (rs.next()) add(row(rs)) } }
            }
        }
}
