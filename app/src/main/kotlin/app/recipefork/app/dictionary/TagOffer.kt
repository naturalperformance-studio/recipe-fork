package app.recipefork.app.dictionary

import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.search.WordPrefix
import java.text.Collator
import java.util.Locale

/** The label of this tag in the interface [language], whichever language a search matched it in (L10N-003). */
fun TagEntity.label(language: InterfaceLanguage): String =
    if (language == InterfaceLanguage.RUSSIAN) labelRu else labelEn

/**
 * What a tag search offers for what the author typed: the tags of [dictionary] one of whose labels, in either
 * language, holds for every typed word a word that begins with it ([WordPrefix] — case ignored, `ё` read as `е`),
 * leaving out those of [excluded]. Those whose label begins with the whole typed text come first, then the rest, each
 * in the alphabetical order of the label shown in [language]; at most [limit] are offered. Nothing is offered while
 * nothing but spaces is typed: a dictionary of hundreds of tags is searched, never listed. Typed text itself is never
 * offered (DOM-008).
 */
object TagOffer {
    const val LIMIT = 8

    fun of(
        typed: String,
        dictionary: List<TagEntity>,
        language: InterfaceLanguage,
        excluded: Set<Long> = emptySet(),
        limit: Int = LIMIT,
    ): List<TagEntity> {
        val sought = WordPrefix.fold(typed).trim()
        val typedWords = WordPrefix.words(sought)
        if (typedWords.isEmpty()) return emptyList()
        val matched = dictionary.filter { tag ->
            tag.id !in excluded && tag.labels().any { WordPrefix.holdsEvery(WordPrefix.words(it), typedWords) }
        }
        val collator = Collator.getInstance(if (language == InterfaceLanguage.RUSSIAN) Locale("ru") else Locale.ENGLISH)
        val byLabel = compareBy<TagEntity, String>(collator) { it.label(language) }.thenBy { it.id }
        val (first, rest) = matched.partition { tag -> tag.labels().any { WordPrefix.fold(it).startsWith(sought) } }
        return (first.sortedWith(byLabel) + rest.sortedWith(byLabel)).take(limit)
    }

    private fun TagEntity.labels() = listOf(labelRu, labelEn)
}
