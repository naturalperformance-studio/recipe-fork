package app.recipefork.core.file

import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.QuantityInput
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational

/**
 * Whether what a recipe file states passes the rules a sending must pass (decisions/20260928-recipe-file-is-a-zip.md):
 * the limits of DOM-012, the closed list of units of DOM-016, the quantities of DOM-017 and the shape every recipe of
 * the book has — positions in order, references and media that point at what the recipe holds. An importer believes
 * nothing of a file that has any [problems].
 */
object RecipeFileCheck {
    const val MAX_INGREDIENTS = 50
    const val MAX_STEPS = 50
    const val MAX_STEP_TEXT = 2_000
    const val MAX_TITLE = 120
    const val MAX_DESCRIPTION = 2_000
    const val MAX_TIMER_NAME = 60

    /** From 1 minute to 99 h 59 min (FLOW-027). */
    private val TIMER_MINUTES = 1L..(99L * 60 + 59)

    /** An identity as the device mints it: a UUID, or a shorter run of the same letters, digits and dashes. */
    private val IDENTITY = Regex("[A-Za-z0-9_-]{1,64}")

    /** A nickname is three words (spec/50-domain-model); a stated one is held to a length a line can show. */
    private const val MAX_NICKNAME = 100

    /** Every rule [content] breaks, in words for a log; empty when it passes them all. */
    fun problems(content: RecipeFileContent): List<String> = buildList {
        fun check(ok: Boolean, problem: () -> String) {
            if (!ok) add(problem())
        }

        check(IDENTITY.matches(content.id.value)) { "the recipe's identity is not one" }
        // DOM-012: the submission maximum.
        check(content.title.length <= MAX_TITLE) { "the title is over $MAX_TITLE characters" }
        check(content.description.length <= MAX_DESCRIPTION) { "the description is over $MAX_DESCRIPTION characters" }
        check(content.ingredients.size <= MAX_INGREDIENTS) { "more than $MAX_INGREDIENTS ingredients" }
        check(content.steps.size <= MAX_STEPS) { "more than $MAX_STEPS steps" }
        check(content.servings == null || content.servings >= 1) { "a serving count below one" }
        check(content.tags.all { it >= 0 }) { "a tag that is none" }
        content.author?.let { check(it.isNotBlank() && it.length <= MAX_NICKNAME) { "the author's nickname is not one" } }
        content.parent?.let { parent ->
            check(IDENTITY.matches(parent.id.value)) { "the parent's identity is not one" }
            check(parent.title == null || parent.title.length <= MAX_TITLE) { "the parent's title is over $MAX_TITLE characters" }
            check(parent.nickname == null || parent.nickname.isNotBlank() && parent.nickname.length <= MAX_NICKNAME) {
                "the parent's nickname is not one"
            }
        }

        val ingredients = content.ingredients
        check(ingredients.map { it.position } == ingredients.indices.toList()) { "ingredient positions out of order" }
        ingredients.forEach { row ->
            val at = "ingredient ${row.position}"
            // DOM-009: named by an entry or by the author's own words, never both; a qualifier narrows an entry.
            check((row.dictionaryEntry != null) != !row.freeTextName.isNullOrBlank()) { "$at is named by both or neither" }
            check(row.dictionaryEntry == null || row.dictionaryEntry >= 0) { "$at names an entry that is none" }
            check(row.qualifier == null || row.dictionaryEntry != null) { "$at qualifies no entry" }
            check(row.enteredQuantity == null) { "$at holds no value" }
            if (row.unitType == UnitType.TASTE_UNMEASURED) {
                check(row.unit == null || row.unit == QuantityUnit.TO_TASTE) { "$at is to taste in a unit" }
                check(row.quantity == null) { "$at is to taste with a quantity" }
            } else {
                // DOM-016: each unit within the type the table gives it; DOM-017: a value above zero on its grid.
                val unit = row.unit
                check(unit != null && unit.type == row.unitType) { "$at has no unit of its type" }
                val quantity = row.quantity
                check(quantity != null && unit != null && unit.type == row.unitType && QuantityInput.holds(quantity, unit, row.divisible)) {
                    "$at holds no quantity on its grid"
                }
                if (row.unitType == UnitType.PIECE) check(row.divisible != null) { "$at is a piece of no divisibility" }
            }
            if (row.unitType != UnitType.PIECE) {
                check(row.divisible == null && row.pieceWeight == null) { "$at carries what only a piece carries" }
            }
            check(row.pieceWeight == null || row.pieceWeight > Rational.ZERO) { "$at weighs nothing a piece" }
        }

        val steps = content.steps
        check(steps.map { it.position } == steps.indices.toList()) { "step positions out of order" }
        steps.forEach { step ->
            val at = "step ${step.position}"
            check(step.text.length <= MAX_STEP_TEXT) { "$at is over $MAX_STEP_TEXT characters" }
            step.timer?.let { timer ->
                check(timer.duration.inWholeMinutes in TIMER_MINUTES && timer.duration.inWholeSeconds % 60 == 0L) {
                    "$at has a timer out of range"
                }
                check(timer.label == null || timer.label.length <= MAX_TIMER_NAME) { "$at's timer name is over $MAX_TIMER_NAME characters" }
            }
            step.ingredientReferences.forEach { reference ->
                check(reference.ingredientPosition in ingredients.indices) { "$at refers to no ingredient" }
                check(reference.textOffset in 0..step.text.length) { "$at refers outside its text" }
            }
            check(step.ingredientReferences.map { it.textOffset }.toSet().size == step.ingredientReferences.size) {
                "$at holds two references at one place"
            }
        }

        // MEDIA-001: one cover, a photograph, and at most one item per step; each under its own entry of `media/`.
        val media = content.media
        check(media.count { it.attachment == MediaAttachment.Cover } <= 1) { "more than one cover" }
        check(media.none { it.attachment == MediaAttachment.Cover && it.kind != MediaKind.PHOTO }) { "a cover that is no photograph" }
        val attachedSteps = media.mapNotNull { (it.attachment as? MediaAttachment.ToStep)?.stepPosition }
        check(attachedSteps.all { it in steps.indices }) { "media on a step the recipe does not hold" }
        check(attachedSteps.toSet().size == attachedSteps.size) { "two media items on one step" }
        check(media.map { it.mediaId }.toSet().size == media.size) { "two media items of one identity" }
        check(media.map { it.entry }.toSet().size == media.size) { "two media items in one entry" }
        check(media.all { isMediaEntry(it.entry) }) { "a media entry outside `media/`" }
    }

    /** An entry's name an importer reads at all: neither absolute nor climbing out of where it stands. */
    fun isSafeEntryName(name: String): Boolean =
        name.isNotEmpty() && !name.startsWith("/") && !name.startsWith("\\") && '\\' !in name &&
            name.split('/').none { it == ".." } && ".." !in name && ':' !in name && name.none { it.isISOControl() }

    /** A media item's entry: one plain file directly under `media/`. */
    fun isMediaEntry(name: String): Boolean =
        isSafeEntryName(name) && name.startsWith(RecipeFile.MEDIA_DIRECTORY) &&
            name.length > RecipeFile.MEDIA_DIRECTORY.length && '/' !in name.removePrefix(RecipeFile.MEDIA_DIRECTORY)
}
