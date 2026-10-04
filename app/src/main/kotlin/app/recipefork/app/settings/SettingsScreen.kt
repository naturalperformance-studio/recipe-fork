package app.recipefork.app.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.recipefork.app.Mode
import app.recipefork.app.R
import app.recipefork.app.data.AppLanguage
import app.recipefork.app.data.DragHandleSide
import app.recipefork.app.data.ThemeChoice
import app.recipefork.app.data.ThemeSetting
import app.recipefork.app.data.UsualServings
import app.recipefork.app.library.ClearMediaStatement
import app.recipefork.app.library.ClearingMedia
import app.recipefork.app.library.FIRST_CHOICE
import app.recipefork.app.library.LanguageDialog
import app.recipefork.app.library.NothingToClear
import app.recipefork.app.library.UsualServingsDialog
import app.recipefork.app.library.label
import app.recipefork.app.library.servingsLanguage
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.scaling.RequestedServings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The settings, a destination above the book: one item per setting, each showing its value in force and changing it
 * as the book's menu did — the interface language (L10N-002), the theme (ADR 20261004), the serving count the reader usually cooks for
 * (FLOW-017), the side of the editor's drag column, and the saved media the device need not keep (FLOW-060), cleared
 * only once its statement is confirmed. The clearing runs in [scope], which outlives the screen; its statement
 * differs by [mode] and in [Mode.Serverless] names nothing of the server (FLOW-062 (1)). Back — the bar's or the
 * system's — returns to the book.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    usual: UsualServings,
    handleSide: DragHandleSide,
    theme: ThemeSetting,
    recipes: RecipeRepository,
    mode: Mode,
    scope: CoroutineScope,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize().testTag(SettingsTags.SCREEN),
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.settings_title), Modifier.semantics { heading() }.testTag(SettingsTags.TITLE))
                },
                navigationIcon = {
                    val action = stringResource(R.string.settings_back_action)
                    IconButton(
                        onClick = onBack,
                        // Material's icon button lays out at 40 dp; a target is 48 by 48 dp at least (A11Y-003).
                        modifier = Modifier
                            .size(48.dp)
                            .semantics {
                                onClick(label = action) {
                                    onBack()
                                    true
                                }
                            }
                            .testTag(SettingsTags.BACK),
                    ) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.settings_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).testTag(SettingsTags.LIST)) {
            LanguageItem()
            ThemeItem(theme)
            UsualServingsItem(usual)
            DragHandleItem(handleSide)
            SavedMediaItem(recipes, mode, scope)
        }
    }
}

/** The interface language in force; it opens [LanguageDialog], which shows the whole application again in the one taken. */
@Composable
private fun LanguageItem() {
    val activity = LocalActivity.current ?: return
    var choosing by rememberSaveable { mutableStateOf(false) }
    val chosen = remember(activity) { AppLanguage.chosen(activity) }
    Item(
        headline = stringResource(R.string.language_menu),
        value = stringResource(chosen.label()),
        tag = SettingsTags.LANGUAGE,
        onClick = { choosing = true },
    )
    if (choosing) {
        LanguageDialog(
            chosen = chosen,
            onChoose = {
                choosing = false
                AppLanguage.choose(activity, it)
            },
            onDismiss = { choosing = false },
        )
    }
}

/** The theme in force; it opens [ThemeDialog], and the one taken is shown at once, the Activity left as it is. */
@Composable
private fun ThemeItem(theme: ThemeSetting) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    val chosen by theme.choice.collectAsState()
    Item(
        headline = stringResource(R.string.settings_theme),
        value = stringResource(chosen.label()),
        tag = SettingsTags.THEME,
        onClick = { choosing = true },
    )
    if (choosing) {
        ThemeDialog(
            chosen = chosen,
            onChoose = {
                choosing = false
                theme.set(it)
            },
            onDismiss = { choosing = false },
        )
    }
}

/** The name a theme choice is offered under. */
private fun ThemeChoice.label(): Int = when (this) {
    ThemeChoice.LIGHT -> R.string.settings_theme_light
    ThemeChoice.DARK -> R.string.settings_theme_dark
    ThemeChoice.DEVICE -> R.string.settings_theme_device
}

/** Light, dark or as on the device, the one in force marked; taking one chooses it, dismissing changes nothing. */
@Composable
private fun ThemeDialog(chosen: ThemeChoice, onChoose: (ThemeChoice) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(SettingsTags.THEME_DIALOG),
        title = { Text(stringResource(R.string.settings_theme)) },
        text = {
            Column(Modifier.selectableGroup()) {
                ThemeChoice.entries.forEach { choice ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = choice == chosen, role = Role.RadioButton, onClick = { onChoose(choice) })
                            .testTag(SettingsTags.theme(choice)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = choice == chosen, onClick = null)
                        Text(stringResource(choice.label()), modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp).testTag(SettingsTags.THEME_CANCEL),
            ) { Text(stringResource(R.string.settings_theme_cancel)) }
        },
    )
}

/** The usual serving count, or that none is set; it opens [UsualServingsDialog]. */
@Composable
private fun UsualServingsItem(usual: UsualServings) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    val held by usual.count.collectAsState()
    val language = servingsLanguage()
    Item(
        headline = stringResource(R.string.library_usual_servings),
        value = held?.let { RequestedServings.text(it, language) } ?: stringResource(R.string.library_usual_not_set),
        tag = SettingsTags.USUAL,
        onClick = { choosing = true },
    )
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

/** The editor's drag column on the right of each block, for a left hand: a switch, the whole row toggling it. */
@Composable
private fun DragHandleItem(handleSide: DragHandleSide) {
    val right by handleSide.right.collectAsState()
    ListItem(
        headlineContent = { Text(stringResource(R.string.library_drag_handle_right)) },
        trailingContent = { Switch(checked = right, onCheckedChange = null) },
        modifier = Modifier
            .toggleable(value = right, role = Role.Switch, onValueChange = { handleSide.set(it) })
            .testTag(SettingsTags.HANDLE_RIGHT),
    )
}

/**
 * How much saved media the clearing would delete; it states first what clearing deletes ([ClearMediaStatement]) and
 * clears only once confirmed, or says that nothing is left to clear.
 */
@Composable
private fun SavedMediaItem(recipes: RecipeRepository, mode: Mode, scope: CoroutineScope) {
    var clearing by rememberSaveable { mutableStateOf<ClearingMedia?>(null) }
    // Counted again once a clearing is done.
    var cleared by remember { mutableIntStateOf(0) }
    val count by produceState<Int?>(null, recipes, cleared) { value = recipes.clearableMedia().size }
    Item(
        headline = stringResource(R.string.library_clear_media),
        value = when (val held = count) {
            null -> stringResource(R.string.settings_media_counting)
            0 -> stringResource(R.string.settings_media_none)
            else -> pluralStringResource(R.plurals.settings_media_count, held, held)
        },
        tag = SettingsTags.CLEAR_MEDIA,
        onClick = {
            scope.launch {
                clearing = if (recipes.clearableMedia().isEmpty()) ClearingMedia.NOTHING else ClearingMedia.ASKING
            }
        },
    )
    when (clearing) {
        ClearingMedia.ASKING -> ClearMediaStatement(
            mode,
            onConfirm = {
                clearing = null
                scope.launch {
                    recipes.clearMedia()
                    cleared++
                }
            },
            onDecline = { clearing = null },
        )
        ClearingMedia.NOTHING -> NothingToClear(onDone = { clearing = null })
        null -> Unit
    }
}

/** One setting: its name, and beneath it the value in force; the whole row opens its choice. */
@Composable
private fun Item(headline: String, value: String, tag: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(headline) },
        supportingContent = { Text(value, Modifier.testTag(SettingsTags.value(tag))) },
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick).testTag(tag),
    )
}

object SettingsTags {
    const val SCREEN = "settings"
    const val TITLE = "settings-title"
    const val BACK = "settings-back"
    const val LIST = "settings-list"
    const val LANGUAGE = "settings-language"
    const val THEME = "settings-theme"
    const val THEME_DIALOG = "settings-theme-dialog"
    const val THEME_CANCEL = "settings-theme-cancel"
    const val USUAL = "settings-usual"
    const val HANDLE_RIGHT = "settings-handle-right"
    const val CLEAR_MEDIA = "settings-clear-media"

    /** The value in force an item shows. */
    fun value(item: String) = "$item-value"

    /** One choice of the theme's dialog. */
    fun theme(choice: ThemeChoice) = "settings-theme-${choice.name.lowercase()}"
}
