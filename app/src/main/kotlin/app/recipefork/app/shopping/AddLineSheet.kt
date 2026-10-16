package app.recipefork.app.shopping

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.app.dictionary.EquipmentEntryEntity
import app.recipefork.app.dictionary.EquipmentOffer
import app.recipefork.app.dictionary.EquipmentSource
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.app.dictionary.IngredientOffer
import app.recipefork.app.dictionary.label
import app.recipefork.app.editor.OfferedChoice
import app.recipefork.core.scaling.InterfaceLanguage
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * What the field of an own line offers for what was typed (T-232): the [entries] of the ingredient and equipment
 * dictionaries found as the editor's fields find them — by label or synonym, in either language — each labelled in the
 * interface language, with the synonym it was found by, if any; and whether the typed text is to be offered as written:
 * not when it is exactly one of them ([typedOffered]).
 */
internal class OwnLineOffer(val entries: List<Offered>, val typedOffered: Boolean) {
    /** An entry offered: its [label], the [synonym] that found it, and its [tag]. */
    class Offered(val label: String, val synonym: String?, val tag: String)

    companion object {
        /**
         * The offer for [typed]: the ingredients found, then the items of equipment — a group of equipment is no thing to
         * buy — the exact match of either first, each label once.
         */
        fun of(typed: String, ingredients: List<IngredientEntryEntity>, equipment: List<EquipmentEntryEntity>, language: InterfaceLanguage): OwnLineOffer {
            if (typed.isBlank()) return OwnLineOffer(emptyList(), typedOffered = false)
            val food = IngredientOffer.of(typed, ingredients, language)
            val tools = EquipmentOffer.of(typed, equipment, language)
            val items = tools.entries.filter { it.parentId != null }
            val foodOffered = food.entries.map { Offered(it.label(language), food.synonyms[it.id], ShoppingTags.ingredientOffer(it.id)) }
            val toolsOffered = items.map { Offered(it.label(language), tools.synonyms[it.id], ShoppingTags.equipmentOffer(it.id)) }
            val toolExact = tools.exact?.takeIf { it.parentId != null }
            val offered = if (food.exact == null && toolExact != null) toolsOffered + foodOffered else foodOffered + toolsOffered
            return OwnLineOffer(offered.distinctBy { it.label }, typedOffered = food.exact == null && toolExact == null)
        }
    }
}

/**
 * The sheet "Add item" opens: a field, focused with the keyboard up, and under it what it offers ([OwnLineOffer]) from
 * the [ingredients] and the [equipment] dictionary, then the typed text as written. A pick adds the entry's label, the
 * keyboard's Done or the last offer the text as typed, without the spaces around it ([onAdd]); then the sheet closes
 * ([onClose]). Blank text adds nothing. A dictionary that cannot be read offers nothing, and the text is still added.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddLineSheet(
    ingredients: List<IngredientEntryEntity>,
    equipment: EquipmentSource,
    language: InterfaceLanguage,
    onAdd: (String) -> Unit,
    onClose: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var typed by rememberSaveable { mutableStateOf("") }
    val tools by produceState(emptyList<EquipmentEntryEntity>(), equipment) {
        value = try {
            equipment.equipment()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }
    val offer = remember(typed, ingredients, tools, language) { OwnLineOffer.of(typed, ingredients, tools, language) }
    // Once something is added, the sheet is on its way out and adds nothing more.
    var added by remember { mutableStateOf(false) }
    val add = { text: String ->
        val written = text.trim()
        if (written.isNotEmpty() && !added) {
            added = true
            onAdd(written)
            scope.launch { state.hide() }.invokeOnCompletion { onClose() }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = state,
        modifier = Modifier.testTag(ShoppingTags.ADD_SHEET),
        // Material's handle, as the editor's sheets have (v0.38): as wide as it is tall, so that its own actions (expand,
        // dismiss) have a full touch target. The sheet opens whole; the handle, a swipe down or Back closes it.
        dragHandle = { BottomSheetDefaults.DragHandle(width = 48.dp) },
    ) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(focus) { focus.requestFocus() }
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(bottom = 8.dp)) {
            Text(
                stringResource(R.string.shopping_own_add),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() }.testTag(ShoppingTags.ADD_TITLE),
            )
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                label = { Text(stringResource(R.string.shopping_own_field)) },
                singleLine = true,
                // A search as much as a name: the dictionary's labels are written in lower case.
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add(typed) }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focus).testTag(ShoppingTags.ADD_FIELD),
            )
            offer.entries.forEach { entry ->
                OfferedChoice(
                    text = entry.label,
                    synonym = entry.synonym,
                    synonymTag = ShoppingTags.offerSynonym(entry.tag),
                    action = stringResource(R.string.shopping_own_pick_action, entry.label),
                    onClick = { add(entry.label) },
                    modifier = Modifier.testTag(entry.tag),
                )
            }
            if (offer.typedOffered) {
                val text = typed.trim()
                OfferedChoice(
                    text = stringResource(R.string.shopping_own_typed, text),
                    action = stringResource(R.string.shopping_own_typed_action, text),
                    onClick = { add(text) },
                    modifier = Modifier.testTag(ShoppingTags.ADD_TYPED),
                )
            }
        }
    }
}
