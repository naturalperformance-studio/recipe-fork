package app.recipefork.core.paste

import app.recipefork.core.recipe.QuantityUnit

/**
 * What a unit word of an ingredient line names: a unit of the model, perhaps with the word that [qualifier]s the entry
 * ("мультистакан" is a cup), or a count of whole things, which is pieces of what follows it.
 */
internal sealed interface UnitWord {
    data class Of(val unit: QuantityUnit, val qualifier: String? = null) : UnitWord

    /**
     * A word for one whole thing — "зубчик", "головка", "банка", "пучок", "clove", "bunch", "can" — written in its
     * [base] form: pieces, the word itself qualifying the entry named after it unless an entry names both.
     */
    data class Count(val base: String) : UnitWord
}

/**
 * The unit words of a pasted ingredient line in Russian and English — abbreviated, with or without a dot, and inflected
 * ("г", "гр", "граммов", "ст. л.", "столовые ложки", "tbsp", "cups") — each mapped to the unit of the model it means.
 * A Russian "стакан" is the model's cup, and so is a multicooker's ("мультистакан"), which qualifies the entry. The
 * words for whole things ([UnitWord.Count]) count pieces.
 */
internal object UnitWords {
    private val units: List<Pair<UnitWord, String>> = listOf(
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
        (UnitWord.Of(QuantityUnit.CUP, qualifier = "мультистакан") to """мультистакан(?:а|ов|ы)?""")

    /** Words for whole things, by their base form; an English one may have its size before it ("small bunch"). */
    private val counts: List<Pair<UnitWord, String>> = listOf(
        "зубчик" to """зубчик(?:а|ов|и)?""",
        "головка" to """головк[аиу]|головок""",
        "банка" to """банк[аиу]|банок""",
        "пакетик" to """пакетик(?:а|ов|и)?""",
        "стручок" to """стручок|стручк(?:а|ов|и)""",
        "пучок" to """пучок|пучк(?:а|ов|и)""",
        "веточка" to """веточк[аиу]|веточек""",
        "горсть" to """горст(?:ь|и|ей)""",
        "горошина" to """горошин[аыу]?""",
        "долька" to """дольк[аиу]|долек""",
        "кочан" to """кочан(?:а|ов|ы)?""",
        "пачка" to """пачк[аиу]|пачек""",
        "упаковка" to """упаковк[аиу]|упаковок""",
        "ломтик" to """ломтик(?:а|ов|и)?""",
        "стебель" to """стебель|стебл(?:я|и|ей)""",
        "clove" to """cloves?""",
        "bunch" to """bunch(?:es)?""",
        "handful" to """handfuls?""",
        "can" to """cans?""",
        "tin" to """tins?""",
        "jar" to """jars?""",
        "packet" to """packets?""",
        "sprig" to """sprigs?""",
        "stalk" to """stalks?""",
        "head" to """heads?""",
        "slice" to """slices?""",
        "pack" to """packs?""",
        "package" to """packages?""",
        "stick" to """sticks?""",
    ).map { (base, pattern) -> UnitWord.Count(base) to pattern }

    private val words = units + counts

    private fun alternation(of: List<Pair<UnitWord, String>>) =
        of.joinToString("|", "(?:", """)\.?(?![\p{L}\p{N}])""") { "(?:${it.second})" }

    /**
     * The units and the clove as one alternation a pattern embeds; a word ends where its letters do, a dot after it is
     * its own. The other words for whole things are left out: in a step's text, "в банку" is where something goes.
     */
    val PATTERN: String = alternation(units + counts.filter { (it.first as UnitWord.Count).base in setOf("зубчик", "clove") })

    /** Every word of an ingredient line's quantity: the units and the words for whole things, written as [PATTERN] is. */
    val LINE_PATTERN: String = alternation(words)

    /** The words for whole things alone, written as [PATTERN] is. */
    val COUNT_PATTERN: String = alternation(counts)

    /** A size before an English count word, which is part of what qualifies the entry: "small bunch". */
    const val SIZE: String = """(?:small|medium|large|big)"""

    private val each = words.map { (word, pattern) -> word to Regex("""(?:$pattern)\.?""", RegexOption.IGNORE_CASE) }

    /** The unit word [written] is, as a line's pattern found it; null when it is none. */
    fun of(written: String): UnitWord? = each.firstOrNull { it.second.matches(written.trim()) }?.first

    /** "по вкусу", "to taste": the row is to taste. A run of separators before it is taken once, never backtracked. */
    val TO_TASTE = Regex("""(?:^[\s—–:-]*+|(?<![\s—–:-])[\s—–:-]++)(?:по\s+вкусу|to\s+taste)(?![\p{L}])[\s.]*""", RegexOption.IGNORE_CASE)
}
