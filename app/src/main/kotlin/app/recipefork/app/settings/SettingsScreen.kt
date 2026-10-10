package app.recipefork.app.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import app.recipefork.app.dictionary.EquipmentEntryEntity
import app.recipefork.app.kitchen.Kitchen
import app.recipefork.app.kitchen.KitchenEnvironment
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.scaling.RequestedServings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The settings, a destination above the book: one item per setting, each showing its value in force and changing it
 * as the book's menu did — the interface language (L10N-002), the theme (ADR 20261004), the serving count the reader usually cooks for
 * (FLOW-017), My kitchen — how many items of equipment are ticked, opening its own screen — the side of the editor's drag
 * column, said to be the editor's, and the saved media the device need not keep (FLOW-060), cleared only once its
 * statement is confirmed, and disabled, saying why, while there is nothing to clear. The clearing runs in [scope],
 * which outlives the screen, and the screen's snackbar says once it is done; its words differ by [mode] and in
 * [Mode.Serverless] name nothing of the server (FLOW-062 (1)). Back — the bar's or the system's — returns to the book.
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
    kitchen: KitchenEnvironment,
    onBack: () -> Unit,
    onOpenKitchen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbars = remember { SnackbarHostState() }
    Scaffold(
        modifier = modifier.fillMaxSize().testTag(SettingsTags.SCREEN),
        snackbarHost = { SnackbarHost(snackbars) },
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
            KitchenItem(kitchen, onOpenKitchen)
            DragHandleItem(handleSide)
            SavedMediaItem(recipes, mode, scope, snackbars)
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
private fun ThemeDialog(chosen: ThemeChoice, onChoose: (ThemeChoice) -> Unit, onDismiss: () -> Unit) = ChoiceDialog(
    title = stringResource(R.string.settings_theme),
    choices = ThemeChoice.entries,
    chosen = chosen,
    label = { stringResource(it.label()) },
    cancel = stringResource(R.string.settings_theme_cancel),
    tags = ChoiceTags(SettingsTags.THEME_DIALOG, SettingsTags.THEME_CANCEL, SettingsTags::theme),
    onChoose = onChoose,
    onDismiss = onDismiss,
)

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
            notSet = held == null,
            language = language,
            onSet = {
                usual.set(it)
                choosing = false
            },
            onDismiss = { choosing = false },
        )
    }
}

/** How many items of equipment My kitchen has ticked, or that none is; it opens My kitchen. */
@Composable
private fun KitchenItem(kitchen: KitchenEnvironment, onOpen: () -> Unit) {
    // A dictionary that cannot be read counts nothing, as the editor reads it; My kitchen says it could not read it.
    val dictionary by produceState<List<EquipmentEntryEntity>?>(null, kitchen.equipment) {
        value = runCatching { kitchen.equipment.equipment() }.getOrDefault(emptyList())
    }
    val held by kitchen.store.held.collectAsState()
    val count = dictionary?.let { Kitchen(held, it).count }
    Item(
        headline = stringResource(R.string.kitchen_title),
        value = when (count) {
            null -> stringResource(R.string.settings_media_counting)
            0 -> stringResource(R.string.kitchen_none_ticked)
            else -> pluralStringResource(R.plurals.kitchen_ticked, count, count)
        },
        tag = SettingsTags.KITCHEN,
        onClick = onOpen,
    )
}

/**
 * The editor's drag column on the right of each block, for a left hand: a switch, the whole row toggling it; beneath its
 * name, that it is the editor's.
 */
@Composable
private fun DragHandleItem(handleSide: DragHandleSide) {
    val right by handleSide.right.collectAsState()
    ListItem(
        headlineContent = { Text(stringResource(R.string.library_drag_handle_right)) },
        supportingContent = { Text(stringResource(R.string.settings_handle_right_hint)) },
        trailingContent = { Switch(checked = right, onCheckedChange = null) },
        modifier = Modifier
            .toggleable(value = right, role = Role.Switch, onValueChange = { handleSide.set(it) })
            .testTag(SettingsTags.HANDLE_RIGHT),
    )
}

/**
 * How much saved media the clearing would delete — in [Mode.Serverless] named as what alone it can be there, the photos
 * and videos of recipes imported from files; it states first what clearing deletes ([ClearMediaStatement]) and
 * clears only once confirmed, then says in [snackbars] that it is done. With nothing to clear it is disabled, and says
 * so beneath its name.
 */
@Composable
private fun SavedMediaItem(recipes: RecipeRepository, mode: Mode, scope: CoroutineScope, snackbars: SnackbarHostState) {
    var asking by rememberSaveable { mutableStateOf(false) }
    // Counted again once a clearing is done.
    var cleared by remember { mutableIntStateOf(0) }
    val count by produceState<Int?>(null, recipes, cleared) { value = recipes.clearableMedia().size }
    val done = stringResource(
        when (mode) {
            Mode.Serverless -> R.string.library_clear_media_done_serverless
            Mode.Standard -> R.string.library_clear_media_done
        },
    )
    LaunchedEffect(cleared) {
        if (cleared > 0) snackbars.showSnackbar(done)
    }
    Item(
        headline = stringResource(
            when (mode) {
                Mode.Serverless -> R.string.library_clear_media_serverless
                Mode.Standard -> R.string.library_clear_media
            },
        ),
        value = when (val held = count) {
            null -> stringResource(R.string.settings_media_counting)
            0 -> stringResource(R.string.settings_media_none)
            else -> pluralStringResource(R.plurals.settings_media_count, held, held)
        },
        tag = SettingsTags.CLEAR_MEDIA,
        // Until counted, and with nothing to clear, there is nothing to ask.
        enabled = (count ?: 0) > 0,
        onClick = { asking = true },
    )
    if (asking) {
        ClearMediaStatement(
            mode,
            onConfirm = {
                asking = false
                scope.launch {
                    recipes.clearMedia()
                    cleared++
                }
            },
            onDecline = { asking = false },
        )
    }
}

/**
 * One setting: its name, and beneath it the value in force; the whole row opens its choice. A row not [enabled] shows
 * its name dimmed, as a disabled control is, and its value — then the reason — as it is.
 */
@Composable
private fun Item(headline: String, value: String, tag: String, enabled: Boolean = true, onClick: () -> Unit) {
    val dimmed = MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
    ListItem(
        headlineContent = { Text(headline) },
        supportingContent = { Text(value, Modifier.testTag(SettingsTags.value(tag))) },
        colors = if (enabled) ListItemDefaults.colors() else ListItemDefaults.colors(headlineColor = dimmed),
        modifier = Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick).testTag(tag),
    )
}

/** Material's opacity for the content of a disabled control. */
private const val DISABLED_ALPHA = 0.38f

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
    const val KITCHEN = "settings-kitchen"
    const val HANDLE_RIGHT = "settings-handle-right"
    const val CLEAR_MEDIA = "settings-clear-media"

    // The dialogs moved here from the book's menu keep their tags' values.
    const val USUAL_DIALOG = "library-usual-dialog"
    const val USUAL_COUNT = "library-usual-count"
    const val USUAL_FEWER = "library-usual-fewer"
    const val USUAL_MORE = "library-usual-more"
    const val USUAL_SET = "library-usual-set"
    const val USUAL_CANCEL = "library-usual-cancel"
    /** The dialog's two choices: no usual count, and the count its − and + walk. */
    const val USUAL_NOT_SET = "library-usual-not-set"
    const val USUAL_COUNT_CHOICE = "library-usual-count-choice"
    /** The clearing of saved media's statement (FLOW-060). */
    const val CLEAR_MEDIA_STATEMENT = "library-clear-media-statement"
    const val CLEAR_MEDIA_CONFIRM = "library-clear-media-confirm"
    const val CLEAR_MEDIA_DECLINE = "library-clear-media-decline"

    /** The value in force an item shows. */
    fun value(item: String) = "$item-value"

    /** One choice of the theme's dialog. */
    fun theme(choice: ThemeChoice) = "settings-theme-${choice.name.lowercase()}"
}
