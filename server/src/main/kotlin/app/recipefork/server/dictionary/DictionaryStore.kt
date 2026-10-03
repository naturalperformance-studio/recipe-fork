package app.recipefork.server.dictionary

import java.sql.Connection
import java.sql.ResultSet
import javax.sql.DataSource

/**
 * An entry of the ingredient dictionary, as the store holds it: its [divisible] flag (null while the dictionary does
 * not say), its comparison [factors] by unit code (DOM-011, CAT-009), and whether it is [removed] upstream.
 */
data class IngredientEntry(
    val id: Long,
    val labelRu: String,
    val labelEn: String,
    val parentId: Long?,
    val divisible: Boolean? = null,
    val factors: Map<String, Double> = emptyMap(),
    val removed: Boolean = false,
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
                    ingredients = conn.ingredientEntries(),
                    tags = conn.query(TAGS_SQL) { it.toTag() },
                ).also { conn.commit() }
            } finally {
                conn.rollback()
                conn.autoCommit = autoCommit
                conn.transactionIsolation = isolation
            }
        }

    fun ingredientEntries(): List<IngredientEntry> =
        dataSource.connection.use { it.ingredientEntries() }

    fun ingredientEntry(id: Long): IngredientEntry? = dataSource.connection.use { conn ->
        conn.query("$ENTRY_COLUMNS where entry_id = ?", id) { it.toEntry() }.singleOrNull()
            ?.let { it.copy(factors = conn.factors("where entry_id = ?", id)[it.id].orEmpty()) }
    }

    /** Every entry with its factors, in the order of their ids. */
    private fun Connection.ingredientEntries(): List<IngredientEntry> {
        val factors = factors("")
        return query(INGREDIENTS_SQL) { it.toEntry() }.map { it.copy(factors = factors[it.id].orEmpty()) }
    }

    /** The factors of the entries [where] selects, by entry, each in the order of its unit codes. */
    private fun Connection.factors(where: String, vararg params: Any): Map<Long, Map<String, Double>> =
        query("select entry_id, unit, factor from ingredient_factor $where order by entry_id, unit", *params) {
            Triple(it.getLong(1), it.getString(2), it.getDouble(3))
        }.groupBy({ it.first }) { it.second to it.third }.mapValues { (_, pairs) -> pairs.toMap() }

    fun tags(): List<Tag> =
        query(TAGS_SQL) { it.toTag() }

    private fun ResultSet.toEntry() = IngredientEntry(
        id = getLong(1),
        labelRu = getString(2),
        labelEn = getString(3),
        parentId = getLong(4).takeUnless { wasNull() },
        divisible = getBoolean(5).takeUnless { wasNull() },
        removed = getBoolean(6),
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
        const val ENTRY_COLUMNS = "select entry_id, label_ru, label_en, parent_id, divisible, removed from ingredient_entry"
        const val INGREDIENTS_SQL = "$ENTRY_COLUMNS order by entry_id"
        const val TAGS_SQL = "select tag_id, label_ru, label_en from tag order by tag_id"
    }
}
