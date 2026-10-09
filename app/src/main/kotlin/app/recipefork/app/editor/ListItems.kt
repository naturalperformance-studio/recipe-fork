package app.recipefork.app.editor

import app.recipefork.core.recipe.IngredientGroup
import app.recipefork.core.recipe.Recipe

/**
 * One place in the ingredient list as the editor shows it: a row, or the heading of a group (DOM-022), which stands
 * above the rows it owns.
 */
internal sealed interface ListItem {
    /** The row at [position]. */
    data class Row(val position: Int) : ListItem

    /** The heading at [index] of the recipe's headings. */
    data class Heading(val index: Int) : ListItem
}

/** The ingredient list as one column: each heading just above the first row of its group, a heading with none last. */
internal val Recipe.listItems: List<ListItem>
    get() = buildList {
        var heading = 0
        for (position in 0..ingredients.size) {
            while (heading < ingredientGroups.size && ingredientGroups[heading].firstPosition <= position) add(ListItem.Heading(heading++))
            if (position < ingredients.size) add(ListItem.Row(position))
        }
        while (heading < ingredientGroups.size) add(ListItem.Heading(heading++))
    }

/** The positions of the rows the heading at [index] owns: from its own up to the next heading's, or to the end. */
internal fun Recipe.groupRows(index: Int): IntRange {
    val start = ingredientGroups[index].firstPosition.coerceAtMost(ingredients.size)
    val end = (ingredientGroups.getOrNull(index + 1)?.firstPosition ?: ingredients.size).coerceIn(start, ingredients.size)
    return start until end
}

/**
 * The heading at item [from] of [items] with the rows it owns, as one block: how many items it takes. A row is a block
 * of one.
 */
internal fun blockAt(items: List<ListItem>, from: Int): Int =
    if (items[from] is ListItem.Heading) 1 + items.drop(from + 1).takeWhile { it is ListItem.Row }.size else 1

/**
 * Whether the item at [at] can move at all without leaving a group with no rows: neither the only row of a group nor a
 * heading with no rows yet can.
 */
internal fun List<ListItem>.movable(at: Int): Boolean = when (this[at]) {
    is ListItem.Heading -> getOrNull(at + 1) is ListItem.Row
    is ListItem.Row -> getOrNull(at - 1) !is ListItem.Heading || getOrNull(at + 1) is ListItem.Row
}

/**
 * Where a group's block of [span] items picked up at [from] may land, as the first item of the list it is put back into:
 * just above another heading, or at the end after a row — never among the rows above the first heading, which no group
 * owns, nor inside another group, nor after a heading with no rows yet.
 */
internal fun groupLanding(items: List<ListItem>, from: Int, span: Int): (Int) -> Boolean {
    val rest = items.subList(0, from) + items.subList(from + span, items.size)
    return { to -> to == from || rest.getOrNull(to) is ListItem.Heading || to == rest.size && rest.lastOrNull() is ListItem.Row }
}

/**
 * The list rearranged: [recipe] holding its rows and headings as they now stand, [rows] the former position of each row
 * in its new order and [headings] the former index of each heading kept, in order.
 */
internal class Arranged(val recipe: Recipe, val rows: List<Int>, val headings: List<Int>)

/**
 * [this] with its ingredient list standing as [items] — every row and heading of it, each at most once, in a new order;
 * a row left out is gone. Each row takes its place in the new order and every step's reference follows its row; a
 * reference to a row left out is dropped. Each heading starts at the row that follows it. A heading left over no rows —
 * its group emptied by the change — goes, so that no two headings stand at one row and none stands past the last row,
 * except one that had no rows before and is still the last: a group just added at the end.
 */
internal fun Recipe.arranged(items: List<ListItem>): Arranged {
    val rows = items.filterIsInstance<ListItem.Row>().map { it.position }
    val moved = IntArray(ingredients.size) { -1 }.also { rows.forEachIndexed { now, was -> it[was] = now } }
    val kept = mutableListOf<Int>()
    val groups = mutableListOf<IngredientGroup>()
    var above = 0
    items.forEachIndexed { at, item ->
        when (item) {
            is ListItem.Row -> above++
            is ListItem.Heading -> {
                val owns = items.getOrNull(at + 1) is ListItem.Row
                val stillNew = items.subList(at + 1, items.size).isEmpty() && groupRows(item.index).isEmpty()
                if (owns || stillNew) {
                    kept += item.index
                    groups += ingredientGroups[item.index].copy(firstPosition = above)
                }
            }
        }
    }
    val steps = steps.map { step ->
        step.copy(
            ingredientReferences = step.ingredientReferences
                .filter { moved.getOrElse(it.ingredientPosition) { -1 } >= 0 }
                .map { it.copy(ingredientPosition = moved[it.ingredientPosition]) },
        )
    }
    val arranged = copy(
        ingredients = rows.mapIndexed { now, was -> ingredients[was].copy(position = now) },
        steps = steps,
        ingredientGroups = groups,
    )
    return Arranged(arranged, rows, kept)
}

/** [items] with the block of [span] items at [from] taken out and put back so that it starts at [to] of the rest. */
internal fun <T> List<T>.movedBlock(from: Int, span: Int, to: Int): List<T> {
    val block = subList(from, from + span)
    val rest = subList(0, from) + subList(from + span, size)
    return rest.subList(0, to) + block + rest.subList(to, rest.size)
}

/** [this] as the book keeps it: a heading not named yet — still being written — is not saved (DOM-022). */
internal fun Recipe.withNamedGroups(): Recipe =
    if (ingredientGroups.none { it.name.isBlank() }) this else copy(ingredientGroups = ingredientGroups.filter { it.name.isNotBlank() })
