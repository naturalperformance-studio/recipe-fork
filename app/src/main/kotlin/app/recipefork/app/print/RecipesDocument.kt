package app.recipefork.app.print

import android.content.res.Resources
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.card.HeldScale
import app.recipefork.app.card.openingScale
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.RecipeId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.io.IOException

/**
 * A document of several recipes (v0.37, scenarios 4 and 7): a first page with its title and the numbered list of the
 * recipes' titles, then each recipe as [RecipeDocument] lays it out, each beginning a new page. The footers name the
 * document's title.
 */
object RecipesDocument {
    /** [recipes], in their order, under [title]: each one's own title is the line naming it on the first page. */
    fun of(title: String, recipes: List<PrintDocument>): PrintDocument = PrintDocument(
        title,
        buildList {
            add(PrintBlock.Title(title))
            recipes.forEachIndexed { index, recipe -> add(PrintBlock.Numbered(index + 1, recipe.title)) }
            recipes.forEach { recipe ->
                add(PrintBlock.PageBreak)
                addAll(recipe.blocks)
            }
        },
    )

    /**
     * The recipes [members] of the book [recipes] under [title], each read as its card reads it and laid out as its card
     * prints it: at the scale its card would open with — the one [heldScale] says the reader left it at this session, or
     * the reader's usual count of [card], or the authored one — with the dictionaries and the system of units of [card],
     * its parent's title as the book holds it, and the photos [photos] keeps, in the language of [resources]. A recipe
     * that does not read back is left out; null when there are no [members]; it fails when none of them reads back.
     */
    suspend fun read(
        title: String,
        members: List<RecipeId>,
        recipes: RecipeRepository,
        card: CardEnvironment,
        heldScale: (RecipeId) -> HeldScale?,
        resources: Resources,
        photos: PhotoStore,
    ): PrintDocument? {
        if (members.isEmpty()) return null
        val book = recipes.bookName()
        // Read once, as the card reads it on opening; a dictionary that cannot be read names what it would have named
        // as the card does without it.
        val usual = card.usualServings.count.value
        val units = card.unitSystem.system.value
        val entries = orNone { card.ingredients.entries() }.associateBy { it.id }
        val equipment = orNone { card.equipment.equipment() }.associateBy { it.id }
        val read = members.mapNotNull { member ->
            val recipe = orNull { recipes.observe(member).first() } ?: return@mapNotNull null
            val parentTitle = recipe.parent?.let { parent -> orNull { recipes.get(parent)?.title } }
            val scale = openingScale(recipe, heldScale(recipe.id), usual)
            RecipeDocument.of(recipe, entries, equipment, scale, resources, parentTitle, units) { photos.file(book, it) }
        }
        if (read.isEmpty()) throw IOException("none of the recipes reads back")
        return of(title, read)
    }

    private suspend fun <T> orNone(read: suspend () -> List<T>): List<T> = orNull(read).orEmpty()

    private suspend fun <T> orNull(read: suspend () -> T?): T? = try {
        read()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
