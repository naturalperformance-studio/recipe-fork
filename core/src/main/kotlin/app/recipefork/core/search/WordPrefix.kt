package app.recipefork.core.search

import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * The word-prefix match of the ingredient field (FLOW-057), which the recipe book's search uses as well (FLOW-035):
 * a text matches a query when, for every word of the query, one of the text's words begins with it — or is the same
 * word in another grammatical form ([Inflection]: "яйца" finds "яйцо", "огурцы" finds "огурец", "eggs" finds "egg").
 * Letter case is ignored, `ё` is read as `е` and Latin letters lose their diacritics (`é` is `e`, `ñ` is `n`); a word is
 * a run of letters and digits.
 */
object WordPrefix {
    private val separators = Regex("""[^\p{L}\p{N}]+""")

    /** Letter case ignored, `ё` read as `е` (FLOW-057), Latin diacritics dropped: "Crème" is "creme", "jalapeño" "jalapeno". */
    fun fold(text: String): String {
        val lower = text.lowercase(Locale.ROOT).replace('ё', 'е')
        if (lower.none { it.code in LATIN_MARKED }) return lower
        return buildString(lower.length) {
            lower.forEach { char ->
                when {
                    char.code !in LATIN_MARKED -> append(char)
                    char in LIGATURES -> append(LIGATURES.getValue(char))
                    else -> Normalizer.normalize(char.toString(), Normalizer.Form.NFD).forEach {
                        if (Character.getType(it) != Character.NON_SPACING_MARK.toInt()) append(it)
                    }
                }
            }
        }
    }

    /** The words of [text], after [fold]. */
    fun words(text: String): List<String> = fold(text).split(separators).filter { it.isNotEmpty() }

    /** Whether, for every word [sought], one of [words] begins with it or is it in another form; both already [fold]ed and split. */
    fun holdsEvery(words: List<String>, sought: List<String>): Boolean = holdsEvery(Words(words), sought)

    /** Whether, for every word [sought] (already [fold]ed and split), one of [text]'s words begins with it or is it in another form. */
    fun holdsEvery(text: Words, sought: List<String>): Boolean = holdsEvery(text, Sought(sought))

    /** [holdsEvery] for a query matched against many texts, its words' forms worked out once. */
    fun holdsEvery(text: Words, sought: Sought): Boolean = sought.words.indices.all { i ->
        val word = sought.words[i]
        text.words.any { it.startsWith(word) } || sought.forms[i].let { forms ->
            forms.any { it in text.forms } || forms.any { form -> text.forms.any { Inflection.akin(form, it) } }
        }
    }

    /** The words of a query, already [fold]ed and split, with the [Inflection] forms of each. */
    class Sought(val words: List<String>) {
        val forms: List<Set<String>> = words.map { Inflection.forms(it) }
    }

    /** The words of a text and the [Inflection] forms of each, worked out once for many queries. */
    class Words(val words: List<String>) {
        val forms: Set<String> = words.flatMapTo(HashSet()) { Inflection.forms(it) }

        companion object {
            private val known = ConcurrentHashMap<String, Words>()

            /**
             * The words of [text], worked out once for each text: a dictionary label is matched on every keystroke.
             * Held for every text given, without bound — meant for the dictionary's labels, a few thousand.
             */
            fun of(text: String): Words = known.getOrPut(text) { Words(words(text)) }
        }
    }

    /** Latin-1 Supplement and Latin Extended-A and B: the letters that may carry a diacritic. */
    private val LATIN_MARKED = 0x00C0..0x024F

    /** Latin letters that no decomposition takes apart. */
    private val LIGATURES = mapOf('ß' to "ss", 'æ' to "ae", 'œ' to "oe", 'ø' to "o", 'ł' to "l", 'đ' to "d", 'þ' to "th")
}

/**
 * The grammatical forms of a word reduced to one, so that the forms a cook writes find each other: an ending taken off a
 * Russian word ("яйца", "яйцо" → "яйц"; "грецкие", "грецкий" → "грецк") together with its stem without a fleeting vowel
 * ("огурец" → "огурц", the stem of "огурцы"), or the plural ending taken off an English one ("eggs" → "egg", "cherries"
 * → "cherry", "tomatoes" → "tomato", "leaves" → "leaf"). A light reduction for the names of ingredients, not a full stemmer: two words
 * match when one form of each is the same, or is [akin] to it; a word under four letters is its own form, and a stem is
 * never under three.
 */
object Inflection {
    /** The forms of one [folded][WordPrefix.fold] [word]; a word under four letters is its only form. */
    fun forms(word: String): Set<String> {
        if (word.length < MIN_WORD) return setOf(word)
        val stem = if (word.any { it in 'а'..'я' }) russian(word) else english(word)
        val fleeting = fleeting(stem)
        return if (fleeting == null) setOf(stem) else setOf(stem, fleeting)
    }

    /**
     * Whether the form of a typed word is akin to a label's though not the same: a Russian word that begins with the
     * label's, a stem of at least five letters, and goes on by no more than a suffix that makes a diminutive or an
     * adjective of it — "редиска" finds "редис", "морковка" finds "морковь", "картофельное" finds "картофель". Another
     * word built on the stem is not it: "лимонад" is no "лимон", "сахарин" no "сахар"; nor is an English compound:
     * "buttermilk" is no "butter", "pepperoni" no "pepper". The other way round is too open: "pickles" must not find
     * everything "pickled", nor "кури" "куркума".
     */
    fun akin(typed: String, label: String): Boolean =
        label.length >= MIN_AKIN && typed.startsWith(label) && label.any { it in 'а'..'я' } &&
            typed.substring(label.length) in AKIN_SUFFIXES

    /**
     * Whether [typed] and [label], both [folded][WordPrefix.fold] words, are the same word in one form or another: the
     * same, or sharing a [form][forms] — "яйца" is "яйцо", "eggs" "egg", "onions" "onion"; never a word merely akin,
     * nor a beginning of it still being typed: "яйц" and "молок" are not yet "яйцо" and "молоко".
     */
    fun same(typed: String, label: String): Boolean =
        typed == label || !label.startsWith(typed) && forms(typed).let { forms -> forms(label).any { it in forms } }

    private fun russian(word: String): String {
        val ending = RUSSIAN_ENDINGS.firstOrNull { word.endsWith(it) && word.length - it.length >= MIN_STEM }
        return if (ending == null) word else word.dropLast(ending.length)
    }

    private fun english(word: String): String = when {
        word in ENGLISH_OWN -> word
        word.endsWith("ies") && word.length > MIN_WORD -> word.dropLast(3) + "y"
        ENGLISH_VES.any { word.endsWith(it) } -> word.dropLast(3) + "f"
        word.endsWith("oes") || ENGLISH_ES.any { word.endsWith(it) } -> word.dropLast(2)
        word.endsWith("s") && !word.endsWith("ss") && !word.endsWith("us") && !word.endsWith("is") -> word.dropLast(1)
        else -> word
    }

    /** The Russian [stem] without its fleeting vowel ("огурец" → "огурц", "кабачок" → "кабачк", "корен" → "корн"), or null. */
    private fun fleeting(stem: String): String? {
        val tail = FLEETING.firstOrNull { stem.endsWith(it) } ?: return null
        val shorter = stem.dropLast(2) + tail.last()
        return shorter.takeIf { it.length >= MIN_FLEETING && it[it.length - 2] !in VOWELS }
    }

    private const val MIN_WORD = 4
    private const val MIN_STEM = 3
    private const val MIN_FLEETING = 4
    private const val MIN_AKIN = 5

    /** Noun and adjective endings, each before the shorter ones that end it. */
    private val RUSSIAN_ENDINGS = listOf(
        "ями", "ами", "ыми", "ими", "его", "ого", "ему", "ому", "иях", "иям", "ией",
        "ая", "яя", "ое", "ее", "ые", "ие", "ый", "ий", "ой", "ей", "ую", "юю", "ым", "им", "ых", "их",
        "ов", "ев", "ам", "ям", "ах", "ях", "ом", "ем", "ию", "ия", "ье", "ья", "ью",
        "а", "я", "о", "е", "ы", "и", "у", "ю", "ь",
    )
    private val FLEETING = listOf("ец", "ок", "ек", "ен")
    private val VOWELS = "аеиоуыэюя".toSet()
    private val ENGLISH_ES = listOf("ches", "shes", "sses", "xes", "zes")

    /** The "-ves" plurals of a word in "-f": "leaves", "loaves", "halves" — not "olives", "cloves" or "chives". */
    private val ENGLISH_VES = listOf("eaves", "oaves", "lves")

    /** Plurals that are words of their own: "greens" are no "green". */
    private val ENGLISH_OWN = setOf("greens")

    /** What may follow a label's stem in a word [akin] to it: a diminutive ("редиска") or an adjective ("картофельное"). */
    private val AKIN_SUFFIXES = setOf("к", "ок", "ек", "ик", "чик", "очк", "ечк", "н", "нн", "ьн", "ов", "ев", "ен", "енн")
}

/**
 * What a query finds among [items]: those in which every word of it begins a word of one of their names — given by
 * [names] — whichever name each word is found in ([WordPrefix]). The names' words are worked out once, when the index
 * is built, so that each query only compares them; a query of no word finds every item. What is found keeps the
 * order of [items].
 */
class SearchIndex<T>(private val items: List<T>, names: (T) -> List<String>) {
    private val indexed: List<Pair<T, WordPrefix.Words>> = run {
        // The same name — a dictionary entry's label — stands in many recipes; its words are worked out once.
        val wordsOf = HashMap<String, List<String>>()
        items.map { item ->
            item to WordPrefix.Words(names(item).flatMap { wordsOf.getOrPut(it) { WordPrefix.words(it) } }.distinct())
        }
    }

    fun search(query: String): List<T> {
        val sought = WordPrefix.words(query)
        if (sought.isEmpty()) return items
        val matcher = WordPrefix.Sought(sought)
        return indexed.filter { (_, words) -> WordPrefix.holdsEvery(words, matcher) }.map { it.first }
    }
}
