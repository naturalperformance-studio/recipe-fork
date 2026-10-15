package app.recipefork.core.paste

/**
 * Markdown tables of a pasted text. A table with a name column and a column of quantities ("100 г", "2 шт",
 * "щепотка", "—") is an ingredient table: each body row is one line "<name> <quantity>", any other cell dropped.
 * Any other table, a nutrition table (labels, then columns of bare numbers) among them, is skipped.
 */
internal object PasteTables {
    private const val NUMBER = """[~≈]?\s*(?:\d+(?:[.,]\d+)?(?:/\d+)?|[½¼¾⅓⅔⅛⅜⅝⅞])"""
    private val BARE_NUMBER = Regex("""^$NUMBER%?$""")
    private val QUANTITY = Regex("""^$NUMBER(?:\s*[–—-]\s*\d+(?:[.,]\d+)?)?(?:\s*\p{L}{1,6}\.?){0,2}$""")
    private val NO_QUANTITY = setOf("", "—", "–", "-")
    private val QUANTITY_WORDS = setOf("щепотка", "щепотки", "по вкусу", "pinch", "a pinch", "to taste")

    /** The ingredient lines of a table given as its raw rows; empty when it is not an ingredient table. */
    fun ingredientLines(rows: List<String>): List<PasteLine.Item> {
        val separator = rows.indexOfFirst(::isSeparator)
        val body = rows.drop(separator + 1).filterNot(::isSeparator).map(::cells)
        if (body.isEmpty()) return emptyList()
        val width = body.minOf { it.size }
        if (width < 2) return emptyList()
        if ((1 until width).all { c -> body.all { BARE_NUMBER.matches(it[c]) } }) return emptyList()
        val column = (1 until width).firstOrNull { c ->
            body.all { quantity(it[c]) } && body.any { it[c] !in NO_QUANTITY }
        } ?: return emptyList()
        return body.filter { it[0].isNotEmpty() }.map { row ->
            val amount = row[column]
            val text = if (amount in NO_QUANTITY) row[0] else "${row[0]} $amount"
            PasteLine.Item(text, numbered = false, marked = true, fromTable = true)
        }
    }

    /** "|---|:--:|": only bars, dashes, colons and spaces. */
    private fun isSeparator(row: String) = '-' in row && row.all { it == '|' || it == '-' || it == ':' || it.isWhitespace() }

    private fun cells(row: String): List<String> =
        row.trim().removePrefix("|").removeSuffix("|").split('|').map(PasteLines::clean)

    private fun quantity(cell: String) =
        cell in NO_QUANTITY || cell.lowercase() in QUANTITY_WORDS || QUANTITY.matches(cell)
}
