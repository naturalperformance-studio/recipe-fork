package app.recipefork.app.card

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.scaling.Rational
import app.recipefork.core.scaling.RequestedServings
import java.math.BigInteger
import kotlin.math.roundToInt

/**
 * What a session keeps of each card the reader opened (FLOW-017 (7), FLOW-012): the scale they left it at and, for each
 * of its tabs, the line or step that was at the top of the screen. It lasts exactly as long as the session — the application's saved
 * state, which survives the end of the process while away and is gone once the task is removed (FLOW-058).
 */
interface CardSession {
    fun heldScale(recipe: RecipeId): HeldScale?
    fun holdScale(recipe: RecipeId, scale: HeldScale)
    fun heldAnchor(recipe: RecipeId, tab: CardMode = CardMode.COOKING): CardAnchor?
    fun holdAnchor(recipe: RecipeId, anchor: CardAnchor?, tab: CardMode = CardMode.COOKING)
}

/**
 * A [CardSession] kept as short texts in a store of keys — the application's saved state, or a map for as long as
 * the card is shown. Nothing larger than a count, a fixing and an anchor is ever written.
 */
class TextCardSession(
    private val read: (String) -> String?,
    private val write: (String, String?) -> Unit,
) : CardSession {
    override fun heldScale(recipe: RecipeId): HeldScale? = HeldScale.decoded(read(SCALE_KEY + recipe.value))
    override fun holdScale(recipe: RecipeId, scale: HeldScale) = write(SCALE_KEY + recipe.value, scale.encoded())
    override fun heldAnchor(recipe: RecipeId, tab: CardMode): CardAnchor? = CardAnchor.decoded(read(anchorKey(recipe, tab)))
    override fun holdAnchor(recipe: RecipeId, anchor: CardAnchor?, tab: CardMode) = write(anchorKey(recipe, tab), anchor?.encoded())

    /** Nothing is held for [recipe] any more. */
    fun forget(recipe: RecipeId) {
        write(SCALE_KEY + recipe.value, null)
        CardMode.entries.forEach { write(anchorKey(recipe, it), null) }
    }

    companion object {
        private const val SCALE_KEY = "card.scale."
        private const val ANCHOR_KEY = "card.anchor."
        private const val VIEW_ANCHOR_KEY = "card.anchor.view."

        /** Cooking's place is held under the key the single card held it under, so that a session from before reads it. */
        private fun anchorKey(recipe: RecipeId, tab: CardMode) =
            (if (tab == CardMode.VIEW) VIEW_ANCHOR_KEY else ANCHOR_KEY) + recipe.value

        /** Held in memory alone, for as long as the object lives. */
        fun inMemory(): TextCardSession {
            val held = HashMap<String, String>()
            return TextCardSession(held::get) { key, value -> if (value == null) held.remove(key) else held[key] = value }
        }
    }
}

/**
 * The scale a card was left at: the count [requested], and the [fixing] with the [row] it was taken of — what the
 * row held then, so that a fixing whose row has since changed or gone is dropped rather than applied to another.
 */
data class HeldScale(val requested: Rational?, val fixing: CardFixing? = null, val row: Int? = null) {
    /**
     * The scale this restores on the recipe as it now stands — authored for [authored] servings, with the rows
     * [ordered] — or, for what no longer applies, as it opens with the [usual] count: a count no longer requestable,
     * or of a recipe that no longer has one, is the opening one; a fixing of a row that changed or is gone is dropped.
     */
    fun restored(authored: Int?, ordered: List<Ingredient>, usual: Rational?): CardScale {
        val opening = CardScale.opening(authored, usual)
        val count = requested?.takeIf { opening.requested != null && RequestedServings.holds(it) } ?: opening.requested
        val kept = fixing?.takeIf { fixed -> ordered.firstOrNull { it.position == fixed.position }?.let(::rowOf) == row }
        return CardScale(count, listOfNotNull(kept))
    }

    internal fun encoded(): String = listOfNotNull(
        requested?.toString().orEmpty(),
        fixing?.position?.toString(),
        fixing?.amount?.toString(),
        fixing?.unit?.name,
        fixing?.let { row?.toString() },
    ).joinToString(SEPARATOR)

    companion object {
        /** What [scale] holds, taken of the rows [ordered]. */
        fun of(scale: CardScale, ordered: List<Ingredient>): HeldScale {
            val fixing = scale.fixings.firstOrNull()
            return HeldScale(scale.requested, fixing, fixing?.let { f -> ordered.firstOrNull { it.position == f.position }?.let(::rowOf) })
        }

        /** What was held as [text]; anything that does not read back reads as nothing held. */
        internal fun decoded(text: String?): HeldScale? {
            val parts = text?.split(SEPARATOR) ?: return null
            return runCatching {
                val requested = parts[0].takeIf { it.isNotEmpty() }?.let { rationalOf(it) ?: return null }
                if (parts.size == 1) return HeldScale(requested)
                if (parts.size != 5) return HeldScale(requested)
                val amount = rationalOf(parts[2]) ?: return HeldScale(requested)
                val fixing = CardFixing(parts[1].toInt(), amount, QuantityUnit.valueOf(parts[3]))
                HeldScale(requested, fixing, parts[4].toInt())
            }.getOrNull()
        }

        /**
         * A stable mark of what a row holds, the same from one process to the next. It is taken of
         * [Ingredient.toString], so an update of the application between saving and restoring may read every row as
         * changed and drop the fixing — the count is kept, and nothing worse follows.
         */
        private fun rowOf(ingredient: Ingredient): Int = ingredient.toString().hashCode()
    }
}

/**
 * The line or step that was first at the top of the card — by its [key] ([CardPlaces]) — and how far into it the top
 * of the screen was, in thousandths of its height, so that it is first at the top again whatever the text scale or
 * the orientation does to its size (FLOW-012 (5)).
 */
data class CardAnchor(val key: String, val permille: Int = 0) {
    internal fun encoded(): String = "$key$SEPARATOR$permille"

    companion object {
        internal fun decoded(text: String?): CardAnchor? {
            val parts = text?.split(SEPARATOR)?.takeIf { it.size == 2 } ?: return null
            return CardAnchor(parts[0], parts[1].toIntOrNull() ?: return null)
        }
    }
}

private const val SEPARATOR = ";"

private fun rationalOf(text: String): Rational? {
    val parts = text.split('/')
    if (parts.size > 2) return null
    return try {
        val denominator = if (parts.size == 2) BigInteger(parts[1]) else BigInteger.ONE
        if (denominator.signum() == 0) null else Rational.of(BigInteger(parts[0]), denominator)
    } catch (e: NumberFormatException) {
        null
    }
}

/** Where a card part is in the card's scrolling content: its [top] and its [height], in pixels. */
internal data class Place(val top: Int, val height: Int)

/**
 * Where the parts of one card a reader can find their place by — its cover, title and count, each ingredient line,
 * each step, its description — lie in its scrolling content, measured as they are laid out.
 */
internal class CardPlaces {
    private var content: LayoutCoordinates? = null
    private val parts = HashMap<String, LayoutCoordinates>()
    private val places = mutableStateMapOf<String, Place>()

    fun content(coordinates: LayoutCoordinates) {
        content = coordinates
        parts.keys.forEach(::measure)
    }

    fun part(key: String, coordinates: LayoutCoordinates) {
        parts[key] = coordinates
        measure(key)
    }

    fun forget(key: String) {
        parts.remove(key)
        places.remove(key)
    }

    private fun measure(key: String) {
        val content = content?.takeIf { it.isAttached } ?: return
        val part = parts[key]?.takeIf { it.isAttached } ?: return
        val place = Place(content.localPositionOf(part, Offset.Zero).y.roundToInt(), part.size.height)
        if (places[key] != place) places[key] = place
    }

    /**
     * The part first at the top of the screen with the content scrolled by [scroll]: the one the top cuts through, or
     * the next below it; none at the very top.
     */
    fun anchorAt(scroll: Int): CardAnchor? {
        val sorted = places.entries.sortedBy { it.value.top }
        if (sorted.isEmpty() || scroll <= 0 || scroll < sorted.first().value.top) return null
        val (key, place) = sorted.firstOrNull { it.value.top + it.value.height > scroll } ?: sorted.last()
        val into = (scroll - place.top).coerceAtLeast(0)
        return CardAnchor(key, if (place.height == 0) 0 else (into * 1000L / place.height).toInt())
    }

    /** Where the part [key] lies in the content, once it is laid out. */
    fun placeOf(key: String): Place? = places[key]

    /** The scroll that puts [anchor] first at the top again, once its part is laid out. */
    fun scrollOf(anchor: CardAnchor): Int? = places[anchor.key]?.let { it.top + (it.height * anchor.permille.toLong() / 1000).toInt() }

    companion object {
        const val COVER_KEY = "cover"
        const val TITLE_KEY = "title"
        const val SERVINGS_KEY = "servings"
        const val DESCRIPTION_KEY = "description"
        private const val INGREDIENT_KEY = "i"
        private const val STEP_KEY = "s"
        fun ingredient(position: Int) = INGREDIENT_KEY + position
        fun step(position: Int) = STEP_KEY + position
    }
}

/** The places of the card being drawn; none outside a card. */
internal val LocalCardPlaces = staticCompositionLocalOf<CardPlaces?> { null }

/** Marks the part it modifies as the card's part [key], one a reader's place can be found by. */
@Composable
internal fun cardPlace(key: String): Modifier {
    val places = LocalCardPlaces.current ?: return Modifier
    DisposableEffect(places, key) { onDispose { places.forget(key) } }
    return Modifier.onGloballyPositioned { places.part(key, it) }
}
