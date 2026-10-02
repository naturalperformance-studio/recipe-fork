package app.recipefork.core.file

import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.recipe.Step

/**
 * What a recipe file carries (FLOW-063): the recipe's identity and content, the nickname of its author, and its
 * direct parent as the exporting device knows it. Nothing of the recipe's state, ownership or sending travels: the
 * receiving device makes of it a found recipe of its own (FLOW-064).
 */
data class RecipeFileContent(
    val id: RecipeId,
    val title: String,
    val description: String,
    val tags: Set<Long>,
    val language: RecipeLanguage?,
    val servings: Int?,
    val ingredients: List<Ingredient>,
    val steps: List<Step>,
    /** The author's nickname; null when the recipe was written with no account active (FLOW-063 (3)). */
    val author: String?,
    /** Null when the recipe has no parent (FLOW-063 (5)). */
    val parent: FileParent?,
    /** One entry per media item the archive holds under `media/`; empty while the device holds no media. */
    val media: List<FileMedia> = emptyList(),
) {
    companion object {
        /**
         * The file's content for [recipe]. [parentInBook] is its direct parent when the book holds it; otherwise
         * the parent a file stated for [recipe] is named again, so that a re-exported import keeps its lineage
         * (FLOW-063 (6)). [ownerNickname] is the nickname of the account whose recipe it is, null in the pool.
         */
        fun of(recipe: Recipe, parentInBook: Recipe?, ownerNickname: String?): RecipeFileContent {
            val parent = recipe.parent?.let { id ->
                val held = parentInBook?.takeIf { it.id == id }
                if (held != null) {
                    FileParent(id, held.title, held.statedAuthor)
                } else {
                    FileParent(id, recipe.statedParent?.title, recipe.statedParent?.nickname?.takeIf { it.isNotEmpty() })
                }
            }
            return RecipeFileContent(
                id = recipe.id,
                title = recipe.title,
                description = recipe.description,
                tags = recipe.tags,
                language = recipe.language,
                servings = recipe.servings,
                ingredients = recipe.ingredients,
                steps = recipe.steps,
                author = recipe.statedAuthor ?: ownerNickname,
                parent = parent,
            )
        }
    }
}

/** A recipe's direct parent: its identity always, its title and its author's nickname where known. */
data class FileParent(val id: RecipeId, val title: String?, val nickname: String?)

/** A media item of the archive: [entry] is its name under `media/`. */
data class FileMedia(val mediaId: String, val kind: MediaKind, val attachment: MediaAttachment, val entry: String)
