package app.recipefork.app.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.Rational
import app.recipefork.core.scaling.Rounding

/**
 * The card's ingredients, in order, at the requested serving count (SCALE-001). Every quantity computed at a count
 * other than the authored one says so in words, and which way it was rounded where rounding moved it (SCALE-004,
 * SCALE-005); once two or more lines are held at their floors, a warning above the list says the quantities no
 * longer hold the authored proportions (SCALE-009). None of it is carried by colour (A11Y-008). With no
 * ingredients the part is left out, heading included (FLOW-019).
 */
@Composable
internal fun IngredientsList(
    ingredients: List<Ingredient>,
    entries: Map<Long, IngredientEntryEntity>,
    language: InterfaceLanguage,
    authoredServings: Int?,
    requestedServings: Rational?,
) {
    val ordered = ingredients.sortedBy { it.position }
    if (ordered.isEmpty()) return
    val list = ingredientList(ordered, entries, language, authoredServings, requestedServings)
    Section(R.string.card_ingredients, CardTags.INGREDIENTS) {
        if (!list.holdsProportions) {
            Text(
                stringResource(R.string.card_proportions_warning),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag(CardTags.PROPORTIONS),
            )
        }
        list.lines.forEachIndexed { i, line -> IngredientRow(line, CardTags.ingredient(ordered[i].position)) }
    }
}

/**
 * One ingredient: its name, its quantity at the line's end, and below them, for a recalculated quantity, the words
 * that say so and which way it was rounded. Read by the screen reader as one line, words included.
 */
@Composable
private fun IngredientRow(line: IngredientLine, tag: String) {
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag(tag)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                line.name ?: stringResource(R.string.card_unnamed_ingredient),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            line.quantity?.let { Text(it, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.End) }
        }
        if (line.derived) {
            Text(
                stringResource(derivedNote(line.rounding)),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The words a recalculated quantity carries: that it was recalculated, and where it was rounded, which way. */
private fun derivedNote(rounding: Rounding): Int = when (rounding) {
    Rounding.NONE -> R.string.card_quantity_recalculated
    Rounding.UP -> R.string.card_quantity_recalculated_exact_lower
    Rounding.DOWN -> R.string.card_quantity_recalculated_exact_higher
}
