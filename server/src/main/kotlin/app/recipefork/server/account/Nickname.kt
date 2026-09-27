package app.recipefork.server.account

/**
 * A nickname as its three words — two adjectives, then a noun — each held in canonical form,
 * lower-case Latin letters. Two nicknames are equal exactly when their three words are, so they
 * never differ only in case or spacing (AUTH-022 (3)).
 */
data class Nickname(val first: String, val second: String, val noun: String) {
    init {
        require(listOf(first, second, noun).all { WORD.matches(it) }) { "not three canonical words" }
    }

    /** The displayed form: each word capitalised, separated by one space — `Brave Copper Otter` (AUTH-022 (1)). */
    val display: String
        get() = listOf(first, second, noun).joinToString(" ") { w -> w.replaceFirstChar { it.uppercaseChar() } }

    override fun toString(): String = display

    companion object {
        private val WORD = Regex("^[a-z]+$")
        private val LATIN_WORD = Regex("^[A-Za-z]+$")

        /**
         * The three words of [text], compared as words: case and the spaces between them do not
         * matter. Null unless [text] is exactly three words of Latin letters.
         */
        fun parse(text: String): Nickname? {
            val words = text.trim().split(Regex("\\s+"))
            if (words.size != 3 || !words.all { LATIN_WORD.matches(it) }) return null
            val (a, b, n) = words.map { it.lowercase() }
            return Nickname(a, b, n)
        }
    }
}
