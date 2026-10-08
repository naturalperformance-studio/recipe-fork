package app.recipefork.core.file

import app.recipefork.core.recipe.IngredientShares
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.QuantityInput
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.RecipeBounds
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.recipe.VideoLimits
import app.recipefork.core.scaling.Rational
import kotlin.time.Duration

/**
 * Whether what a recipe file states could be a recipe of the book (decisions/20260928-recipe-file-is-a-zip.md): the
 * limits of DOM-012, the closed list of units of DOM-016 each within its type, quantities above zero (DOM-017), and the
 * shape every recipe of the book has — positions in order, references and media that point at what the recipe holds.
 * A row still being written, or holding a value off its grid, is accepted as an own draft holds it, because export
 * takes a recipe in any state (FLOW-063 (1)). An importer believes nothing of a file that has any [problems].
 */
object RecipeFileCheck {
    // DOM-012, as the editor's warnings read it too (FLOW-028).
    const val MAX_INGREDIENTS = RecipeBounds.MAX_INGREDIENTS
    const val MAX_STEPS = RecipeBounds.MAX_STEPS
    const val MAX_STEP_TEXT = RecipeBounds.MAX_STEP_TEXT
    const val MAX_TITLE = RecipeBounds.MAX_TITLE
    const val MAX_DESCRIPTION = RecipeBounds.MAX_DESCRIPTION
    const val MAX_TIMER_NAME = RecipeBounds.MAX_TIMER_NAME
    const val MAX_NOTE = RecipeBounds.MAX_NOTE
    const val MAX_GROUP_NAME = RecipeBounds.MAX_GROUP_NAME

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
            // A row may stand as its author left it — unnamed, with no unit or quantity yet, with what was typed, or
            // with a value off its grid — as an own draft keeps it (FLOW-024 (6), DOM-017 (6)); the card then shows
            // it as written and never scales it. What no row of the book can be is refused.
            // DOM-009: never named by both an entry and the author's own words; a qualifier narrows an entry.
            check(row.dictionaryEntry == null || row.freeTextName.isNullOrBlank()) { "$at is named by both" }
            check(row.dictionaryEntry == null || row.dictionaryEntry >= 0) { "$at names an entry that is none" }
            check(row.qualifier == null || row.dictionaryEntry != null) { "$at qualifies no entry" }
            // A value held, or what was typed, never both; a value held is above zero.
            check(row.quantity == null || row.enteredQuantity == null) { "$at holds a value and what was typed" }
            check(row.quantity == null || row.quantity > Rational.ZERO) { "$at holds a value of zero or below" }
            if (row.unitType == UnitType.TASTE_UNMEASURED) {
                // DOM-002: to taste holds no unit of measure and no quantity.
                check(row.unit == null || row.unit == QuantityUnit.TO_TASTE) { "$at is to taste in a unit" }
                check(row.quantity == null && row.enteredQuantity == null) { "$at is to taste with a quantity" }
            } else {
                // DOM-016: a unit, where there is one, is of the type the table gives it.
                check(row.unit == null || row.unit.type == row.unitType) { "$at has a unit of another type" }
            }
            if (row.unitType != UnitType.PIECE) {
                check(row.divisible == null && row.pieceWeight == null) { "$at carries what only a piece carries" }
            }
            check(row.pieceWeight == null || row.pieceWeight > Rational.ZERO) { "$at weighs nothing a piece" }
            // DOM-023: a note is trimmed, not blank, and short.
            row.note?.let { note ->
                check(note.isNotBlank() && note == note.trim()) { "$at holds a blank or untrimmed note" }
                check(note.length <= MAX_NOTE) { "$at's note is over $MAX_NOTE characters" }
            }
        }

        // DOM-022: each heading named, in order of the row it starts, at most one at a row, each at a row of the list or
        // at its end, where a heading with no rows yet stands.
        val groups = content.ingredientGroups
        groups.forEachIndexed { index, group ->
            val at = "group $index"
            check(group.name.isNotBlank()) { "$at has no name" }
            check(group.name.length <= MAX_GROUP_NAME) { "$at's name is over $MAX_GROUP_NAME characters" }
            check(group.firstPosition in 0..ingredients.size) { "$at starts at no ingredient" }
        }
        check(groups.zipWithNext().none { (a, b) -> a.firstPosition == b.firstPosition }) { "two groups start at one ingredient" }
        check(groups.zipWithNext().all { (a, b) -> a.firstPosition <= b.firstPosition }) { "groups out of order" }

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
                reference.share?.let { share ->
                    // A part is checked like any quantity: above zero, on its unit type's grid (DOM-017), and in a
                    // unit its row reads it in — the row's own, or an `exact` unit of its dimension; a part the row
                    // could never read would silently read as the whole. Parts are not checked against the whole or
                    // against each other.
                    val row = ingredients.getOrNull(reference.ingredientPosition)
                    check(share.amount > Rational.ZERO) { "$at holds a part of zero or below" }
                    check(share.unit.type.carriesQuantity && (row == null || share.unit.type == row.unitType && IngredientShares.fits(share, row.unit))) {
                        "$at holds a part in a unit its ingredient does not read"
                    }
                    check(share.amount <= Rational.ZERO || QuantityInput.holds(share.amount, share.unit, row?.divisible)) {
                        "$at holds a part off its grid"
                    }
                }
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

    /**
     * What is wrong with a clip of the file lasting [duration], or null when nothing is (MEDIA-004, MEDIA-007): longer
     * than [VideoLimits.MAX_DURATION], of no length, or of a duration that could not be read — null here. The document
     * does not state a clip's duration, so an importer reads it from the clip itself once it is written.
     */
    fun clipProblem(duration: Duration?): String? = when {
        duration == null -> "a clip whose duration cannot be read"
        VideoLimits.accepts(duration) -> null
        duration.isPositive() -> "a clip over ${VideoLimits.MAX_DURATION}"
        else -> "a clip of no length"
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
