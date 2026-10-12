package app.recipefork.core.file

import app.recipefork.core.recipe.RecipeCollection

/**
 * Whether what a collection file's document states could be a collection of the book (v0.36): its identity and each
 * recipe's one minted as the device mints them, a name the book takes ([RecipeCollection.nameOf]), a stated author's
 * nickname that is one, and each recipe listed once, at most [MAX_RECIPES] of them. An importer believes nothing of a
 * file whose document has any [problems]; each recipe of it is checked as its own file is ([RecipeFileCheck]).
 */
object CollectionFileCheck {
    /** The most recipes a collection file is read with: far more than any reader gathers, and a bound on the archive. */
    const val MAX_RECIPES = 5_000

    /** Every rule [content] breaks, in words for a log; empty when it passes them all. */
    fun problems(content: CollectionFileContent): List<String> = buildList {
        fun check(ok: Boolean, problem: () -> String) {
            if (!ok) add(problem())
        }

        check(RecipeFileCheck.IDENTITY.matches(content.id.value)) { "the collection's identity is not one" }
        check(RecipeCollection.nameOf(content.name) != null) { "the name is blank or over ${RecipeCollection.MAX_NAME} characters" }
        content.author?.let {
            check(it.isNotBlank() && it.length <= RecipeFileCheck.MAX_NICKNAME) { "the author's nickname is not one" }
        }
        check(content.recipes.size <= MAX_RECIPES) { "more than $MAX_RECIPES recipes" }
        check(content.recipes.all { RecipeFileCheck.IDENTITY.matches(it.value) }) { "a recipe's identity is not one" }
        check(content.recipes.toSet().size == content.recipes.size) { "a recipe listed twice" }
    }
}
