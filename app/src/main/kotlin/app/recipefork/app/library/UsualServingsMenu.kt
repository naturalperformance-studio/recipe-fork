package app.recipefork.app.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.app.data.UsualServings
import app.recipefork.app.editor.BlockAction
import app.recipefork.app.editor.interfaceLanguage
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.Rational
import app.recipefork.core.scaling.RequestedServings

/** The count the choice starts at while no usual count is set. */
private val FIRST_CHOICE: Rational = Rational.of(2)

/**
 * The book's menu, beside its title: it offers the serving count the reader usually cooks for (FLOW-017), showing the
 * count held or that none is. Choosing it opens [UsualServingsDialog].
 */
@Composable
internal fun BookMenu(usual: UsualServings, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    var choosing by rememberSaveable { mutableStateOf(false) }
    val held by usual.count.collectAsState()
    val language = servingsLanguage()
    Box(modifier) {
        BlockAction(
            R.drawable.ic_row_menu,
            stringResource(R.string.library_menu),
            LibraryTags.MENU,
            onClick = { expanded = !expanded },
            action = stringResource(R.string.library_menu_action),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_usual_servings)) },
                trailingIcon = {
                    Text(
                        held?.let { RequestedServings.text(it, language) } ?: stringResource(R.string.library_usual_not_set),
                        modifier = Modifier.testTag(LibraryTags.USUAL_HELD),
                    )
                },
                onClick = {
                    expanded = false
                    choosing = true
                },
                modifier = Modifier.testTag(LibraryTags.USUAL),
            )
        }
    }
    if (choosing) {
        UsualServingsDialog(
            initial = held ?: FIRST_CHOICE,
            language = language,
            onSet = {
                usual.set(it)
                choosing = false
            },
            onDismiss = { choosing = false },
        )
    }
}

/**
 * The choice of the usual count: − and + walk exactly the counts a reader can request on a card (SCALE-010,
 * SCALE-011), from ¼ up with no ceiling; "Set" keeps the count shown, "Not set" clears the setting, and dismissing
 * the dialog changes nothing.
 */
@Composable
private fun UsualServingsDialog(
    initial: Rational,
    language: InterfaceLanguage,
    onSet: (Rational?) -> Unit,
    onDismiss: () -> Unit,
) {
    var count by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(LibraryTags.USUAL_DIALOG),
        title = { Text(stringResource(R.string.library_usual_servings)) },
        text = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.library_usual_count, RequestedServings.text(count, language)),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }
                        .testTag(LibraryTags.USUAL_COUNT),
                )
                Step(
                    glyph = "−",
                    name = stringResource(R.string.library_usual_fewer),
                    action = stringResource(R.string.library_usual_fewer_action),
                    enabled = count > RequestedServings.MIN,
                    onClick = { count = RequestedServings.decreased(count) },
                    tag = LibraryTags.USUAL_FEWER,
                )
                Step(
                    glyph = "+",
                    name = stringResource(R.string.library_usual_more),
                    action = stringResource(R.string.library_usual_more_action),
                    enabled = true,
                    onClick = { count = RequestedServings.increased(count) },
                    tag = LibraryTags.USUAL_MORE,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSet(count) },
                modifier = Modifier.heightIn(min = 48.dp).testTag(LibraryTags.USUAL_SET),
            ) { Text(stringResource(R.string.library_usual_set)) }
        },
        dismissButton = {
            TextButton(
                onClick = { onSet(null) },
                modifier = Modifier.heightIn(min = 48.dp).testTag(LibraryTags.USUAL_CLEAR),
            ) { Text(stringResource(R.string.library_usual_clear)) }
        },
    )
}

/** A − or + of the choice, 48 by 48 dp (A11Y-003), named by [name] and announced as [action] (A11Y-005). */
@Composable
private fun Step(glyph: String, name: String, action: String, enabled: Boolean, onClick: () -> Unit, tag: String) {
    Box(
        Modifier
            .size(48.dp)
            .clickable(enabled = enabled, onClickLabel = action, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = name }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

@Composable
private fun servingsLanguage(): InterfaceLanguage = when (interfaceLanguage(LocalConfiguration.current)) {
    RecipeLanguage.RU -> InterfaceLanguage.RUSSIAN
    RecipeLanguage.EN -> InterfaceLanguage.ENGLISH
}
