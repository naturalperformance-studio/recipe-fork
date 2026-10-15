package app.recipefork.core.paste

import app.recipefork.core.recipe.QuantityUnit

/** What a unit word of an ingredient line names: a unit of the model, or a clove, which is pieces of what follows it. */
internal sealed interface UnitWord {
    data class Of(val unit: QuantityUnit) : UnitWord

    /** "зубчик", "clove": pieces, the word itself qualifying the entry named after it unless an entry names both. */
    data object Clove : UnitWord
}

/**
 * The unit words of a pasted ingredient line in Russian and English — abbreviated, with or without a dot, and inflected
 * ("г", "гр", "граммов", "ст. л.", "столовые ложки", "tbsp", "cups") — each mapped to the unit of the model it means.
 * A Russian "стакан" is the model's cup.
 */
internal object UnitWords {
    private val words: List<Pair<UnitWord, String>> = listOf(
        QuantityUnit.KILOGRAM to """кг|килограмм(?:а|ов|ы)?|кило|kg|kilos?|kilogram(?:me)?s?""",
        QuantityUnit.GRAM to """г|гр|грамм(?:а|ов|ы)?|g|gr|gram(?:me)?s?""",
        QuantityUnit.MILLILITRE to """мл|миллилитр(?:а|ов|ы)?|ml|millilit(?:re|er)s?""",
        QuantityUnit.TABLESPOON to """ст\.?\s*л|ст\.?\s*ложк[аиу]?|столов\p{L}*\s+лож\p{L}*|ложк[аиу]?|ложек|tbsp|tbs|tablespoons?""",
        QuantityUnit.TEASPOON to """ч\.?\s*л|ч\.?\s*ложк[аиу]?|чайн\p{L}*\s+лож\p{L}*|tsp|teaspoons?""",
        QuantityUnit.LITRE to """л|литр(?:а|ов|ы)?|l|lit(?:re|er)s?""",
        QuantityUnit.FLUID_OUNCE to """fl\.?\s*oz|fluid\s+ounces?""",
        QuantityUnit.OUNCE to """oz|ounces?""",
        QuantityUnit.POUND to """lbs?|pounds?|фунт(?:а|ов)?""",
        QuantityUnit.CUP to """стакан(?:а|ов|ы)?|cups?""",
        QuantityUnit.PINCH to """щепотк[аиу]|щепоток|щепоть|pinch(?:es)?""",
        QuantityUnit.PIECE to """шт|штук[аи]?|штучк[аиу]|pcs?|pieces?""",
    ).map { (unit, pattern) -> UnitWord.Of(unit) to pattern } +
        (UnitWord.Clove to """зубчик(?:а|ов|и)?|cloves?""")

    /** The words as one alternation a line's pattern embeds; a word ends where its letters do, a dot after it is its own. */
    val PATTERN: String = words.joinToString("|", "(?:", """)\.?(?![\p{L}\p{N}])""") { "(?:${it.second})" }

    private val each = words.map { (word, pattern) -> word to Regex("""(?:$pattern)\.?""", RegexOption.IGNORE_CASE) }

    /** The unit word [written] is, as a line's pattern found it; null when it is none. */
    fun of(written: String): UnitWord? = each.firstOrNull { it.second.matches(written.trim()) }?.first

    /** "по вкусу", "to taste": the row is to taste. A run of separators before it is taken once, never backtracked. */
    val TO_TASTE = Regex("""(?:^[\s—–:-]*+|(?<![\s—–:-])[\s—–:-]++)(?:по\s+вкусу|to\s+taste)(?![\p{L}])[\s.]*""", RegexOption.IGNORE_CASE)
}
