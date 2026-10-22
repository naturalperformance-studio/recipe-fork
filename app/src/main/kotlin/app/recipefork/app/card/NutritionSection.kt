package app.recipefork.app.card

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.recipefork.app.R
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.core.nutrition.DishNutrition
import app.recipefork.core.nutrition.LeftOutReason
import app.recipefork.core.nutrition.Nutrition
import app.recipefork.core.nutrition.NutritionEntries
import app.recipefork.core.nutrition.NutritionEntry
import app.recipefork.core.nutrition.NutritionValues
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.QuantityText
import app.recipefork.core.scaling.Rational
import app.recipefork.core.scaling.RequestedServings
import app.recipefork.core.scaling.ServingCount
import java.math.BigInteger

/**
 * The nutrition of the dish [ordered] makes as [list] shows it, read from the device's dictionary [entries]: at the
 * factor in force, one serving of the count shown where the card shows one, 100 g of the finished dish where the recipe
 * gives its [finishedWeight]. Null where no row can be counted. An entry's measures go to the count as the dictionary
 * hands them over, never to the screen (DOM-011).
 */
internal fun dishNutrition(
    ordered: List<Ingredient>,
    entries: Map<Long, IngredientEntryEntity>,
    list: IngredientList,
    finishedWeight: Int? = null,
): DishNutrition? {
    val read = NutritionEntries { id -> entries[id]?.let { NutritionEntry.of(it.toDto()) } }
    return Nutrition.of(ordered, list.factor, read, list.servingCount?.value?.takeIf { it.signum() > 0 }, finishedWeight)
}

/** What the nutrition section's tiles show: 100 g of the finished dish, one serving, or the whole dish. */
enum class NutritionShown { PER_100_G, SERVING, WHOLE }

/**
 * The card's nutrition section on View (v0.40, T-293; v0.44, T-320), between the ingredients and the steps: its
 * heading, the switch between 100 g of the finished dish, one serving and the whole dish — those of them the recipe
 * gives, or, where it gives the whole dish alone, the words "Whole dish" — four tiles of kcal, protein, fat and
 * carbohydrates, the rows left out of the count by name, and "Details", which opens [NutritionSheet]. The tiles are one
 * phrase for the screen reader. [chosen] is the switch's choice, kept by the card while it is open, null for the one it
 * opens on: one serving, else the whole dish; a choice no longer offered goes back to that. [lines] are the card's
 * lines of [ordered], whose names the section writes as the card does; [editable] tells whether the reader may edit the
 * recipe, and so give it a finished weight.
 */
@Composable
internal fun NutritionSection(
    dish: DishNutrition,
    ordered: List<Ingredient>,
    lines: List<IngredientLine>,
    servings: ServingCount?,
    language: InterfaceLanguage,
    chosen: MutableState<NutritionShown?>,
    editable: Boolean,
) {
    val unnamed = stringResource(R.string.card_unnamed_ingredient)
    val names = remember(ordered, lines, unnamed) {
        ordered.indices.associate { ordered[it].position to (lines.getOrNull(it)?.name ?: unnamed) }
    }
    var details by rememberSaveable { mutableStateOf(false) }
    val perServing = dish.perServing
    val per100g = dish.per100g
    val offered = listOfNotNull(
        NutritionShown.PER_100_G.takeIf { per100g != null },
        NutritionShown.SERVING.takeIf { perServing != null },
        NutritionShown.WHOLE,
    )
    val opening = if (perServing != null) NutritionShown.SERVING else NutritionShown.WHOLE
    val shown = chosen.value?.takeIf { it in offered } ?: opening
    LaunchedEffect(offered) { if (chosen.value != null && chosen.value !in offered) chosen.value = null }
    val values = when (shown) {
        NutritionShown.PER_100_G -> checkNotNull(per100g)
        NutritionShown.SERVING -> checkNotNull(perServing)
        NutritionShown.WHOLE -> dish.whole
    }
    val resources = LocalResources.current
    Section(R.string.card_nutrition, NutritionTags.SECTION, gap = 0.dp) {
        if (offered.size == 1) {
            Text(
                stringResource(R.string.card_nutrition_whole),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = SWITCH_GAP).testTag(NutritionTags.WHOLE_ONLY),
            )
        } else {
            NutritionSwitch(offered, shown) { chosen.value = it }
        }
        NutritionTiles(
            values,
            spoken = remember(values, shown, servings, perServing, language, resources) {
                spokenNutrition(resources, values, shown, servings?.takeIf { perServing != null }, language)
            },
            language = language,
        )
        if (dish.leftOut.isNotEmpty()) {
            Text(
                stringResource(R.string.card_nutrition_left_out, dish.leftOut.joinToString(", ") { names.getValue(it.position) }),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = LEFT_OUT_GAP).testTag(NutritionTags.LEFT_OUT),
            )
        }
        TextButton(
            onClick = { details = true },
            modifier = Modifier.heightIn(min = CardTarget).testTag(NutritionTags.DETAILS),
        ) {
            Text(stringResource(R.string.card_nutrition_details))
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                modifier = Modifier.padding(start = ButtonDefaults.IconSpacing).size(CHEVRON),
            )
        }
    }
    if (details) {
        NutritionSheet(dish, names, servings?.takeIf { perServing != null }, language, editable) { details = false }
    }
}

/**
 * "100 g" | "Serving" | "Whole dish", those of them [offered], an M3 segmented row the content's width of equal segments,
 * [shown] chosen: each segment drawn 40 dp tall — taller at a large font, a label that does not fit broken as its text
 * breaks — with [SWITCH_GAP] above and below it at any size, its touch target the card's [CardTarget] tall, reaching
 * into that room; its role and selection told to the screen reader, the chosen one checked.
 */
@Composable
private fun NutritionSwitch(offered: List<NutritionShown>, shown: NutritionShown, onChoose: (NutritionShown) -> Unit) {
    // A segment answers a touch within the card's target of it: Compose widens a touch to a click smaller than the view
    // configuration's minimum target, here the card's. At 40 dp drawn the gaps above and below are that target's room,
    // so Material lays out no room of its own around it; the gaps are each segment's, so they hold at any text size.
    val configuration = LocalViewConfiguration.current
    val touch = remember(configuration) {
        object : ViewConfiguration by configuration {
            override val minimumTouchTargetSize: DpSize get() = DpSize(CardTarget, CardTarget)
        }
    }
    CompositionLocalProvider(LocalViewConfiguration provides touch, LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        // The segments are as tall as the one whose label takes the most lines, so that each keeps its gaps.
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().height(IntrinsicSize.Min).testTag(NutritionTags.SWITCH)) {
            offered.forEachIndexed { index, choice ->
                SegmentedButton(
                    selected = shown == choice,
                    onClick = { onChoose(choice) },
                    shape = SegmentedButtonDefaults.itemShape(index, offered.size),
                    modifier = Modifier.fillMaxHeight().padding(vertical = SWITCH_GAP).testTag(NutritionTags.segment(choice)),
                    label = { Text(stringResource(labelOf(choice)), style = MaterialTheme.typography.labelLarge) },
                )
            }
        }
    }
}

/** The switch's label for [shown]. */
@StringRes
private fun labelOf(shown: NutritionShown): Int = when (shown) {
    NutritionShown.PER_100_G -> R.string.card_nutrition_100_g
    NutritionShown.SERVING -> R.string.card_nutrition_serving
    NutritionShown.WHOLE -> R.string.card_nutrition_whole
}

/**
 * The four tiles — kcal, protein, fat, carbohydrates — in one row while every value and label fits its tile on one
 * line, in two rows of two otherwise. Display only: one node to the screen reader, read as [spoken].
 */
@Composable
private fun NutritionTiles(values: NutritionValues, spoken: String, language: InterfaceLanguage) {
    val tiles = listOf(
        QuantityText.whole(values.kcal, language) to stringResource(R.string.card_nutrition_kcal_label),
        grams(values.protein, language) to stringResource(R.string.card_nutrition_protein),
        grams(values.fat, language) to stringResource(R.string.card_nutrition_fat),
        grams(values.carbs, language) to stringResource(R.string.card_nutrition_carbs),
    )
    val valueStyle = MaterialTheme.typography.titleLarge.copy(fontSize = TILE_VALUE_SIZE, lineHeight = TILE_VALUE_LINE, fontWeight = FontWeight.Medium)
    val labelStyle = MaterialTheme.typography.bodySmall
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = spoken
                shownText = tiles.joinToString(TILES_SHOWN_APART) { (value, label) -> "$value $label" }
            }
            .testTag(NutritionTags.TILES),
    ) {
        val room = with(density) { ((maxWidth - TILE_GAP * 3) / 4 - TILE_SIDE * 2).roundToPx() }
        val inOneRow = remember(tiles, valueStyle, labelStyle, room, measurer) {
            tiles.all { (value, label) ->
                measurer.measure(value, valueStyle, softWrap = false, maxLines = 1).size.width <= room &&
                    measurer.measure(label, labelStyle, softWrap = false, maxLines = 1).size.width <= room
            }
        }
        val rows = if (inOneRow) listOf(tiles.indices.toList()) else listOf(listOf(0, 1), listOf(2, 3))
        Column(verticalArrangement = Arrangement.spacedBy(TILE_GAP)) {
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(TILE_GAP)) {
                    row.forEach { i ->
                        val (value, label) = tiles[i]
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
                                .padding(horizontal = TILE_SIDE, vertical = TILE_TOP)
                                .clearAndSetSemantics { testTag = NutritionTags.tile(i) },
                        ) {
                            Text(value, style = valueStyle, color = MaterialTheme.colorScheme.onSurface)
                            Text(label, style = labelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** [amount] grams as a tile shows it, grouped by three digits: «37 г», «1 250 г», "37 g". */
@Composable
private fun grams(amount: BigInteger, language: InterfaceLanguage): String =
    stringResource(R.string.card_nutrition_grams, QuantityText.whole(amount, language))

/**
 * "Details": the kcal each counted row gives the whole dish, the rows left out with their reasons, how it is counted,
 * and the finished weight 100 g is counted by — or, without one, on a recipe the reader may edit ([editable]), where to
 * give it — under the section's title, in a sheet over the card. [names] are the rows' names by position; [servings] the
 * count shown, null where the card shows none. A swipe down, the scrim or Back closes it ([onClose]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NutritionSheet(
    dish: DishNutrition,
    names: Map<Int, String>,
    servings: ServingCount?,
    language: InterfaceLanguage,
    editable: Boolean,
    onClose: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val resources = LocalResources.current
    val title = stringResource(R.string.card_nutrition)
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = state,
        modifier = Modifier.testTag(NutritionTags.SHEET),
        // Material's handle, as the card's other sheets have: as wide as it is tall, for a full touch target.
        dragHandle = { BottomSheetDefaults.DragHandle(width = 48.dp) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = SHEET_SIDE, end = SHEET_SIDE, bottom = SHEET_SIDE)
                .semantics { paneTitle = title },
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = SHEET_TITLE_GAP).semantics { heading() }.testTag(NutritionTags.SHEET_TITLE),
            )
            Caption(
                servings?.let { resources.getString(R.string.card_nutrition_by_ingredient_servings, servingsText(resources, it, language, spoken = false)) }
                    ?: stringResource(R.string.card_nutrition_by_ingredient),
                NutritionTags.COUNTED,
            )
            dish.counted.forEach { row ->
                SheetRow(names.getValue(row.position), stringResource(R.string.card_nutrition_row_kcal, QuantityText.whole(row.kcal, language)), muted = false, NutritionTags.counted(row.position))
            }
            if (dish.leftOut.isNotEmpty()) {
                Caption(stringResource(R.string.card_nutrition_not_counted), NutritionTags.NOT_COUNTED, Modifier.padding(top = SHEET_SECTION_GAP))
                dish.leftOut.forEach { row ->
                    SheetRow(names.getValue(row.position), stringResource(reasonOf(row.reason)), muted = true, NutritionTags.notCounted(row.position))
                }
            }
            Text(
                stringResource(R.string.card_nutrition_how),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = HOW_GAP).testTag(NutritionTags.HOW),
            )
            val weight = dish.finishedWeight
            if (weight != null || editable) {
                Text(
                    weight?.let { resources.getString(R.string.card_nutrition_weight, weightText(resources, it, servings, language)) }
                        ?: stringResource(R.string.card_nutrition_no_weight),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = HOW_GAP).testTag(if (weight != null) NutritionTags.WEIGHT else NutritionTags.NO_WEIGHT),
                )
            }
        }
    }
}

/** A caption over a part of the sheet. */
@Composable
private fun Caption(text: String, tag: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(bottom = SHEET_ROW_PADDING).testTag(tag),
    )
}

/**
 * One row of the sheet, read as one item: the [name] at its start, the [value] — kcal, which never wraps, or a reason
 * ([muted]) — at its end, while the value whole and the name's longest word fit beside each other; otherwise the value
 * stands under the name, so that the name never breaks inside a word to make room for it.
 */
@Composable
private fun SheetRow(name: String, value: String, muted: Boolean, tag: String) {
    val color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    val gap = with(LocalDensity.current) { SHEET_ROW_GAP.roundToPx() }
    Layout(
        content = {
            Text(name, style = MaterialTheme.typography.bodyMedium, color = color)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = color, softWrap = muted)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = SHEET_ROW_PADDING)
            .semantics(mergeDescendants = true) {}
            .testTag(tag),
    ) { (named, valued), constraints ->
        val width = constraints.maxWidth
        val valueWidth = valued.maxIntrinsicWidth(Constraints.Infinity)
        val beside = valueWidth + gap + named.minIntrinsicWidth(Constraints.Infinity) <= width
        val loose = Constraints(maxWidth = width)
        if (beside) {
            val v = valued.measure(Constraints(maxWidth = valueWidth))
            val n = named.measure(Constraints(maxWidth = width - valueWidth - gap))
            layout(width, maxOf(n.height, v.height)) {
                n.placeRelative(0, 0)
                v.placeRelative(width - v.width, 0)
            }
        } else {
            val n = named.measure(loose)
            val v = valued.measure(loose)
            layout(width, n.height + v.height) {
                n.placeRelative(0, 0)
                v.placeRelative(0, n.height)
            }
        }
    }
}

/** The reason the sheet gives for [reason]: free text and an entry the dictionary does not hold are one, «нет в словаре». */
@StringRes
internal fun reasonOf(reason: LeftOutReason): Int = when (reason) {
    LeftOutReason.FREE_TEXT, LeftOutReason.NOT_IN_DICTIONARY -> R.string.card_nutrition_not_in_dictionary
    LeftOutReason.TO_TASTE -> R.string.card_nutrition_to_taste
    LeftOutReason.NO_QUANTITY -> R.string.card_nutrition_no_amount
    LeftOutReason.NO_MEASURE -> R.string.card_nutrition_no_measure
    LeftOutReason.NO_DATA -> R.string.card_nutrition_no_data
}

/**
 * What the screen reader says for the tiles showing [values], as [shown]: «На 100 граммов готового блюда: примерно 210
 * килокалорий, …» for 100 g, «На порцию: примерно 680 килокалорий, белки 37 граммов, …» for one serving, else «Всё
 * блюдо, 2 порции: …» with the count shown — «примерно 3 порции» while a fixed amount sets it — or «Всё блюдо: …» with
 * no count. Numbers are said plain, not grouped.
 */
internal fun spokenNutrition(
    resources: Resources,
    values: NutritionValues,
    shown: NutritionShown,
    servings: ServingCount?,
    language: InterfaceLanguage,
): String {
    val what = when {
        shown == NutritionShown.PER_100_G -> resources.getString(R.string.card_nutrition_per_100_g_spoken)
        shown == NutritionShown.SERVING -> resources.getString(R.string.card_nutrition_per_serving)
        servings == null -> resources.getString(R.string.card_nutrition_whole)
        else -> resources.getString(R.string.card_nutrition_whole_servings_spoken, servingsText(resources, servings, language, spoken = true))
    }
    fun grams(amount: BigInteger) = resources.getQuantityString(R.plurals.card_nutrition_grams_spoken, pluralOf(amount), amount.toString())
    return resources.getString(
        R.string.card_nutrition_spoken,
        what,
        resources.getQuantityString(R.plurals.card_nutrition_kcal_spoken, pluralOf(values.kcal), values.kcal.toString()),
        grams(values.protein),
        grams(values.fat),
        grams(values.carbs),
    )
}

/**
 * What the dish weighs at the scale, [grams], as the sheet names it: with the serving [count] shown, «1 120 г на 4
 * порции», «560 г на ≈3 порции», "560 g for 2 servings"; with none, «560 г».
 */
internal fun weightText(resources: Resources, grams: BigInteger, count: ServingCount?, language: InterfaceLanguage): String {
    val weight = QuantityText.whole(grams, language)
    return count?.let {
        resources.getString(R.string.card_nutrition_weight_servings, weight, servingsText(resources, it, language, spoken = false, R.plurals.card_nutrition_servings_for))
    } ?: resources.getString(R.string.card_nutrition_grams, weight)
}

/**
 * The serving [count] as the card writes it, with its word in the form of [plurals]: "2 servings", «≈3 порции», or,
 * [spoken], «примерно 3 порции»; a fraction below one in the singular, "½ serving".
 */
internal fun servingsText(
    resources: Resources,
    count: ServingCount,
    language: InterfaceLanguage,
    spoken: Boolean,
    @PluralsRes plurals: Int = R.plurals.card_nutrition_servings,
): String {
    val number = RequestedServings.text(count.value, language)
    val shown = when {
        !count.approximate -> number
        spoken -> resources.getString(R.string.card_nutrition_servings_approximate_spoken, number)
        else -> resources.getString(R.string.card_nutrition_servings_approximate, number)
    }
    return if (count.value.isWhole) {
        resources.getQuantityString(plurals, pluralOf(count.value.numerator), shown)
    } else {
        val below = count.value < Rational.ONE
        resources.getString(if (below) R.string.card_nutrition_servings_fraction_below_one else R.string.card_nutrition_servings_fraction, shown)
    }
}

/**
 * The number a plural is chosen by for [amount], however large: the amount itself below a hundred, else its last two
 * digits above a hundred, which choose the same word in Russian and in English.
 */
internal fun pluralOf(amount: BigInteger): Int =
    if (amount < HUNDRED) amount.toInt() else amount.mod(HUNDRED).toInt() + 100

private val HUNDRED = BigInteger.valueOf(100)

/** Between the heading and the switch, and between the switch and the tiles, outside the switch's drawing. */
private val SWITCH_GAP = 8.dp

/** Above the rows left out. */
private val LEFT_OUT_GAP = 8.dp

private val TILE_GAP = 8.dp
private val TILE_SIDE = 8.dp
private val TILE_TOP = 10.dp

/** A tile's value: no role of the theme's type scale is 20 sp; the nearest, titleLarge, is 22. */
private val TILE_VALUE_SIZE = 20.sp
private val TILE_VALUE_LINE = 26.sp

private val CHEVRON = 20.dp

private val SHEET_SIDE = 24.dp
private val SHEET_TITLE_GAP = 12.dp
private val SHEET_SECTION_GAP = 16.dp
private val SHEET_ROW_PADDING = 4.dp
private val SHEET_ROW_GAP = 12.dp
private val HOW_GAP = 6.dp

/** What parts the tiles' texts in their [ShownText]. */
internal const val TILES_SHOWN_APART = " | "

object NutritionTags {
    const val SECTION = "card-nutrition"
    const val SWITCH = "card-nutrition-switch"
    const val SWITCH_PER_100_G = "card-nutrition-per-100-g"
    const val SWITCH_PER_SERVING = "card-nutrition-per-serving"
    const val SWITCH_WHOLE = "card-nutrition-whole"
    /** "Whole dish" alone, in the switch's place, for a recipe with no serving count. */
    const val WHOLE_ONLY = "card-nutrition-whole-only"
    const val TILES = "card-nutrition-tiles"
    const val LEFT_OUT = "card-nutrition-left-out"
    const val DETAILS = "card-nutrition-details"
    const val SHEET = "card-nutrition-sheet"
    const val SHEET_TITLE = "card-nutrition-sheet-title"
    /** The caption over the counted rows. */
    const val COUNTED = "card-nutrition-counted"
    /** The caption over the rows left out. */
    const val NOT_COUNTED = "card-nutrition-not-counted"
    const val HOW = "card-nutrition-how"
    /** The finished weight 100 g is counted by. */
    const val WEIGHT = "card-nutrition-weight"
    /** Where to give a finished weight, on a recipe the reader may edit that gives none. */
    const val NO_WEIGHT = "card-nutrition-no-weight"
    /** The switch's segment for [shown]. */
    fun segment(shown: NutritionShown) = when (shown) {
        NutritionShown.PER_100_G -> SWITCH_PER_100_G
        NutritionShown.SERVING -> SWITCH_PER_SERVING
        NutritionShown.WHOLE -> SWITCH_WHOLE
    }
    /** The tile at [index]: kcal, protein, fat, carbohydrates. */
    fun tile(index: Int) = "card-nutrition-tile-$index"
    /** The sheet's row of the counted ingredient at [position]. */
    fun counted(position: Int) = "card-nutrition-counted-$position"
    /** The sheet's row of the ingredient at [position] left out of the count. */
    fun notCounted(position: Int) = "card-nutrition-not-counted-$position"
}
