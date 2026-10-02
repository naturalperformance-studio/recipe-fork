package app.recipefork.app.card

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.Rounding

/**
 * The card's ingredients, in order, at the scale in force — a requested serving count (SCALE-001) or the amount the
 * reader fixed of one ingredient (SCALE-002). Every quantity computed at a scale other than the authored one says
 * so in words, and which way it was rounded where rounding moved it (SCALE-004, SCALE-005); once two or more lines
 * are held at their floors, a warning above the list says the quantities no longer hold the authored proportions
 * (SCALE-009). A fixed line says it is fixed, and a fixed line the latest fixing outgrew says what it needs against
 * what was fixed (SCALE-006). None of it is carried by colour (A11Y-008). With no ingredients the part is left out,
 * heading included (FLOW-019).
 *
 * Tapping a line that carries a quantity opens its fixing control below it ([FixAmount]); a line "to taste", an
 * unfinished one, or one off its grid offers nothing (SCALE-018 (7)).
 */
@Composable
internal fun IngredientsList(
    ordered: List<Ingredient>,
    list: IngredientList,
    fixings: List<CardFixing>,
    language: InterfaceLanguage,
    onFix: (CardFixing) -> Unit,
    onRelease: (Int) -> Unit,
) {
    if (ordered.isEmpty()) return
    var open by remember(ordered) { mutableStateOf<Int?>(null) }
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
        list.lines.forEachIndexed { i, line ->
            val position = ordered[i].position
            IngredientRow(line, CardTags.ingredient(position)) { open = if (open == position) null else position }
            val offer = line.fixing
            if (offer != null && open == position) {
                key(position) {
                    FixAmount(
                        offer = offer,
                        current = fixings.firstOrNull { it.position == position },
                        language = language,
                        onFix = { amount, unit -> onFix(CardFixing(position, amount, unit)); open = null },
                        onRelease = { onRelease(position); open = null },
                        onClose = { open = null },
                    )
                }
            }
        }
    }
}

/**
 * One ingredient: its name, its quantity at the line's end, and below them, for a recalculated quantity, the words
 * that say so and which way it was rounded, then whether it is fixed. Read by the screen reader as one line, words
 * included; a line that offers fixing is one action that opens its control.
 */
@Composable
private fun IngredientRow(line: IngredientLine, tag: String, onOpen: () -> Unit) {
    val tappable = if (line.fixing != null) {
        Modifier
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = stringResource(R.string.card_fix_action), role = Role.Button, onClick = onOpen)
    } else {
        Modifier
    }
    Column(
        Modifier.fillMaxWidth().then(tappable).semantics(mergeDescendants = true) {}.testTag(tag),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                line.name ?: stringResource(R.string.card_unnamed_ingredient),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            line.quantity?.let { Text(it, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.End) }
        }
        if (line.derived) Note(stringResource(derivedNote(line.rounding)))
        when {
            line.shortfall != null ->
                Note(stringResource(R.string.card_quantity_shortfall, line.shortfall.required, line.shortfall.available))
            line.fixed -> Note(stringResource(R.string.card_quantity_fixed))
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
}

/** The words a recalculated quantity carries: that it was recalculated, and where it was rounded, which way. */
private fun derivedNote(rounding: Rounding): Int = when (rounding) {
    Rounding.NONE -> R.string.card_quantity_recalculated
    Rounding.UP -> R.string.card_quantity_recalculated_exact_lower
    Rounding.DOWN -> R.string.card_quantity_recalculated_exact_higher
}
