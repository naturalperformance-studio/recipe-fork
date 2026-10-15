package app.recipefork.core.paste.corpus

import app.recipefork.core.api.DictionariesDto
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.recipe.ReferenceText
import app.recipefork.core.recipe.StepRun
import app.recipefork.core.recipe.StepText
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.UnitLabels
import java.io.File
import java.util.Locale

/**
 * The corpus report: one self-contained HTML page (inline CSS, nothing loaded from elsewhere) that shows each text of
 * the corpus beside the recipe a reader made of it, with the corpus measures on top. Any reader renders: the lead may
 * render the page again with other reader settings.
 */
object CorpusReport {
    /** The page for [texts] as [read] reads them, its entries named from [dictionaries]. */
    fun html(texts: List<CorpusText>, read: (String) -> Recipe?, dictionaries: DictionariesDto, heading: String = "Paste corpus"): String =
        html(CorpusMeasures.measure(texts, read, dictionaries), dictionaries, heading)

    /** [html] written to [file]; its directories are made where missing. */
    fun write(file: File, texts: List<CorpusText>, read: (String) -> Recipe?, dictionaries: DictionariesDto, heading: String = "Paste corpus") {
        file.parentFile?.mkdirs()
        file.writeText(html(texts, read, dictionaries, heading))
    }

    fun html(measures: List<TextMeasure>, dictionaries: DictionariesDto, heading: String): String {
        val ingredients = dictionaries.ingredients.associateBy { it.id }
        val equipment = dictionaries.equipment.associateBy { it.id }
        fun label(ru: String, en: String, language: RecipeLanguage?) = if (language == RecipeLanguage.RU) ru else en
        val totals = CorpusTotals(measures)

        return buildString {
            append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">")
            append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            append("<title>${esc(heading)}</title><style>$CSS</style></head><body><main>")
            append("<h1>${esc(heading)}</h1>")
            append("<p class=\"lede\">Each text as pasted, beside the recipe the parser made of it. ")
            append("<span class=\"free\">Free text</span> names no dictionary entry; ")
            append("<span class=\"as-written\">Not read</span> marks a row whose quantity the parser did not read. ")
            append("Dictionary: ${esc(dictionaries.version.take(12))}.</p>")

            // The corpus measures, whole and by set.
            append("<h2>Measures</h2><div class=\"scroll\"><table class=\"sum\"><thead><tr><th>Set</th><th>Texts</th><th>Recipes</th>")
            append("<th>Rows</th><th>Quantity read</th><th>Bound</th><th>Durations timed</th><th>Refs</th><th>Equip.</th><th>File check</th></tr></thead><tbody>")
            fun totalsRow(name: String, t: CorpusTotals) {
                append("<tr><th>${esc(name)}</th><td>${t.texts}</td><td>${t.recipes}</td><td>${t.rows}</td>")
                append(shareCell(t.quantified, t.rows, t.quantifiedShare, CorpusMeasures.QUANTIFIED_FLOOR))
                append(shareCell(t.bound, t.rows, t.boundShare, CorpusMeasures.BOUND_FLOOR))
                append(shareCell(t.timedDurations, t.writtenDurations, t.timerShare, CorpusMeasures.TIMER_FLOOR))
                append("<td>${t.references}</td><td>${t.equipment}</td>")
                append("<td class=\"${if (t.passingFileCheck == t.recipes) "ok" else "bad"}\">${t.passingFileCheck}/${t.recipes}</td></tr>")
            }
            totalsRow("All", totals)
            CorpusText.SETS.forEach { set -> totalsRow(set, CorpusTotals(measures.filter { it.text.set == set })) }
            append("</tbody></table></div>")
            append("<p class=\"hint\">Floors: quantity read ≥ ${pct(CorpusMeasures.QUANTIFIED_FLOOR)}, bound ≥ ${pct(CorpusMeasures.BOUND_FLOOR)}, ")
            append("durations timed ≥ ${pct(CorpusMeasures.TIMER_FLOOR)}. The shopping list is left out of every figure.</p>")

            // Per text.
            append("<h2>Texts</h2><div class=\"scroll\"><table class=\"sum\"><thead><tr><th>Text</th><th>Rows</th><th>Bound</th><th>Free</th>")
            append("<th>Not read</th><th>Refs</th><th>Timers</th><th>Equip.</th><th>Check</th></tr></thead><tbody>")
            measures.forEachIndexed { i, m ->
                append("<tr><th><a href=\"#t$i\">${esc(m.text.set)}/${esc(m.text.name.substringBeforeLast('.'))}</a></th>")
                if (m.recipe == null) {
                    append("<td colspan=\"8\" class=\"${if (m.text.isShoppingList) "muted" else "bad"}\">no recipe</td></tr>")
                    return@forEachIndexed
                }
                append("<td>${m.rows}</td><td>${m.bound}</td><td class=\"${if (m.free > 0) "warn" else ""}\">${m.free}</td>")
                append("<td class=\"${if (m.asWritten > 0) "bad" else ""}\">${m.asWritten}</td><td>${m.references}</td>")
                append("<td class=\"${if (m.timedDurations < m.writtenDurations) "warn" else ""}\">${m.timers} (${m.timedDurations}/${m.writtenDurations})</td>")
                append("<td>${m.equipment}</td><td class=\"${if (m.fileProblems.isEmpty()) "ok" else "bad"}\">${if (m.fileProblems.isEmpty()) "pass" else "fail"}</td></tr>")
            }
            append("</tbody></table></div><p class=\"hint\">Timers: the timers the steps carry (durations timed / durations the steps name).</p>")

            measures.forEachIndexed { i, m ->
                val recipe = m.recipe
                append("<section class=\"text\" id=\"t$i\"><h2><span class=\"set\">${esc(m.text.set)}</span> ${esc(m.text.name)}</h2><div class=\"pair\">")
                append("<details class=\"source\" open><summary>Source text</summary><pre>${esc(m.text.text)}</pre></details>")
                append("<div class=\"recipe\">")
                if (recipe == null) {
                    append("<p class=\"${if (m.text.isShoppingList) "muted" else "bad"}\">No recipe${if (m.text.isShoppingList) " (expected: a shopping list)" else ""}.</p>")
                } else {
                    val language = recipe.language
                    val ui = if (language == RecipeLanguage.RU) InterfaceLanguage.RUSSIAN else InterfaceLanguage.ENGLISH
                    append("<h3>${esc(recipe.title)}</h3>")
                    append("<p class=\"meta\">Servings: ${recipe.servings?.toString() ?: "<span class=\"warn\">not stated</span>"} · language: ${language?.name ?: "—"}</p>")
                    if (recipe.description.isNotBlank()) append("<p class=\"desc\">${esc(recipe.description)}</p>")

                    append("<h4>Ingredients</h4><div class=\"scroll\"><table class=\"rows\"><thead><tr><th>Quantity</th><th>Ingredient</th><th>Qualifier</th><th>Note</th></tr></thead><tbody>")
                    val readRows = CorpusMeasures.readRows(recipe.ingredients)
                    recipe.ingredients.forEachIndexed { i, row ->
                        recipe.ingredientGroups.filter { it.firstPosition == row.position }.forEach {
                            append("<tr class=\"group\"><td colspan=\"4\">${esc(it.name)}</td></tr>")
                        }
                        val read = readRows[i]
                        val notRead = if (read) "" else " <small class=\"tag\">not read</small>"
                        append("<tr${if (read) "" else " class=\"not-read\""}><td class=\"qty\">${esc(quantity(row, ui))}$notRead</td>")
                        val entry = row.dictionaryEntry?.let(ingredients::get)
                        if (entry != null) {
                            append("<td><span class=\"entry\" title=\"${esc("#${entry.id} ${entry.labelRu} / ${entry.labelEn}")}\">${esc(label(entry.labelRu, entry.labelEn, language))}</span></td>")
                        } else {
                            append("<td><span class=\"free\">${esc(row.freeTextName ?: "—")}</span> <small>free text</small></td>")
                        }
                        append("<td>${esc(row.qualifier ?: "")}</td><td>${esc(row.note ?: "")}</td></tr>")
                    }
                    recipe.ingredientGroups.filter { it.firstPosition >= recipe.ingredients.size }.forEach {
                        append("<tr class=\"group\"><td colspan=\"4\">${esc(it.name)}</td></tr>")
                    }
                    append("</tbody></table></div>")

                    append("<h4>Steps</h4><ol class=\"steps\">")
                    val references = ReferenceText.authored(recipe.ingredients, ui)
                    recipe.steps.forEach { step ->
                        append("<li><p>")
                        StepText.runs(step, references).forEach { run ->
                            when (run) {
                                is StepRun.Typed -> append(esc(run.text))
                                is StepRun.Reference -> {
                                    val row = recipe.ingredients.firstOrNull { it.position == run.ingredientPosition }
                                    val name = row?.let { r -> r.dictionaryEntry?.let(ingredients::get)?.let { label(it.labelRu, it.labelEn, language) } ?: r.freeTextName }
                                    append("<span class=\"ref\" title=\"${esc(name ?: "")}\">${esc(run.text)}</span>")
                                }
                            }
                        }
                        append("</p>")
                        val written = WrittenDurations.count(step.text)
                        if (step.timers.isNotEmpty() || written > 0) {
                            append("<p class=\"timers\">")
                            step.timers.forEach { append("<span class=\"timer\">⏱ ${esc(duration(it.duration.inWholeMinutes, ui))}</span> ") }
                            if (step.timers.size < written) append("<span class=\"warn\">${written - step.timers.size} duration(s) not timed</span>")
                            append("</p>")
                        }
                        step.note?.let { append("<p class=\"note\">${esc(it)}</p>") }
                        append("</li>")
                    }
                    append("</ol>")

                    append("<h4>Equipment</h4>")
                    if (recipe.equipment.isEmpty()) {
                        append("<p class=\"muted\">none</p>")
                    } else {
                        append("<ul class=\"chips\">")
                        recipe.equipment.forEach { item ->
                            val entry = item.dictionaryEntry?.let(equipment::get)
                            if (entry != null) {
                                val q = item.qualifier?.let { " ${esc(it)}" }.orEmpty()
                                append("<li><span class=\"entry\">${esc(label(entry.labelRu, entry.labelEn, language))}</span>$q</li>")
                            } else {
                                append("<li><span class=\"free\">${esc(item.freeTextName ?: "—")}</span></li>")
                            }
                        }
                        append("</ul>")
                    }
                    if (m.fileProblems.isNotEmpty()) {
                        append("<p class=\"bad\">Recipe file check: ${esc(m.fileProblems.joinToString("; "))}</p>")
                    }
                }
                append("</div></div></section>")
            }
            append("</main></body></html>")
        }
    }

    /** A row's quantity as the ingredient list writes it; one not read, as typed, its unit after it. */
    private fun quantity(row: Ingredient, ui: InterfaceLanguage): String {
        val entered = row.enteredQuantity
        if (entered != null) return listOfNotNull(entered, row.unit?.let { UnitLabels.of(it, ui) }).joinToString(" ")
        if (row.unitType == UnitType.TASTE_UNMEASURED) return if (ui == InterfaceLanguage.RUSSIAN) "по вкусу" else "to taste"
        return ReferenceText.authored(row, ui)
    }

    private fun duration(minutes: Long, ui: InterfaceLanguage): String {
        val (h, m) = minutes / 60 to minutes % 60
        val (hw, mw) = if (ui == InterfaceLanguage.RUSSIAN) "ч" to "мин" else "h" to "min"
        return listOfNotNull(if (h > 0) "$h $hw" else null, if (m > 0 || h == 0L) "$m $mw" else null).joinToString(" ")
    }

    private fun shareCell(part: Int, whole: Int, share: Double, floor: Double) =
        "<td class=\"${if (share >= floor) "ok" else "bad"}\">${pct(share)} <small>($part/$whole)</small></td>"

    private fun pct(share: Double) = String.format(Locale.ROOT, "%.1f %%", share * 100)

    private fun esc(text: String) = buildString(text.length) {
        text.forEach { c ->
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> append(c)
            }
        }
    }

    private val CSS = """
        :root{--bg:#fbfaf7;--fg:#1d1c1a;--muted:#6b6862;--line:#e2ded6;--card:#fff;--free-bg:#fff1c2;--free-fg:#6b4e00;
        --bad-bg:#fde0dc;--bad-fg:#8a1c10;--ok-fg:#1d6b35;--ref-bg:#dcebff;--ref-fg:#0d3f7a;--timer-bg:#e3f4e6;--group:#f2efe8}
        @media (prefers-color-scheme:dark){:root{--bg:#161614;--fg:#ecebe7;--muted:#a19e97;--line:#34322e;--card:#1f1e1b;
        --free-bg:#4a3a0c;--free-fg:#ffe08a;--bad-bg:#5a1f19;--bad-fg:#ffc2b8;--ok-fg:#8fd9a5;--ref-bg:#173458;--ref-fg:#bcd8ff;
        --timer-bg:#1d3a24;--group:#2a2925}}
        *{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--fg);font:15px/1.5 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif}
        main{max-width:1280px;margin:0 auto;padding:16px}h1{font-size:1.5rem;margin:.2em 0}h2{font-size:1.2rem;margin:1.4em 0 .4em}
        h3{font-size:1.15rem;margin:0 0 .3em}h4{font-size:.95rem;margin:1em 0 .3em;color:var(--muted);text-transform:uppercase;letter-spacing:.04em}
        .lede,.hint,.muted,small{color:var(--muted)}.hint{font-size:.85rem}
        .scroll{overflow-x:auto}table{border-collapse:collapse;width:100%;font-size:.9rem}
        th,td{border-bottom:1px solid var(--line);padding:4px 6px;text-align:left;vertical-align:top}
        table.sum td{white-space:nowrap}table.sum th{white-space:nowrap}
        .ok{color:var(--ok-fg)}.bad{background:var(--bad-bg);color:var(--bad-fg)}.warn{background:var(--free-bg);color:var(--free-fg)}
        .free{background:var(--free-bg);color:var(--free-fg);padding:0 3px;border-radius:3px}
        .as-written,tr.not-read td{background:var(--bad-bg);color:var(--bad-fg)}.tag{font-weight:600;color:inherit}
        .text{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:12px;margin:18px 0}
        .text h2{margin:0 0 .5em;font-size:1.05rem;word-break:break-word}.set{font-size:.75rem;background:var(--group);padding:1px 6px;border-radius:8px;vertical-align:middle}
        .pair{display:grid;grid-template-columns:1fr;gap:16px}@media (min-width:900px){.pair{grid-template-columns:minmax(0,5fr) minmax(0,7fr)}}
        pre{white-space:pre-wrap;word-break:break-word;font:12.5px/1.45 ui-monospace,Menlo,Consolas,monospace;margin:.4em 0;
        background:var(--bg);padding:8px;border-radius:6px;max-height:80vh;overflow:auto}
        summary{cursor:pointer;color:var(--muted)}.desc{white-space:pre-wrap}.meta{color:var(--muted);margin:0 0 .5em}
        tr.group td{background:var(--group);font-weight:600}td.qty{white-space:nowrap}
        .entry{font-weight:500}.steps{padding-left:1.4em}.steps li{margin-bottom:.6em}.steps p{margin:0}
        .ref{background:var(--ref-bg);color:var(--ref-fg);padding:0 3px;border-radius:3px}
        .timers{margin-top:.2em!important}.timer{background:var(--timer-bg);padding:1px 6px;border-radius:8px;font-size:.85rem;white-space:nowrap}
        .note{color:var(--muted);font-style:italic}.chips{list-style:none;padding:0;display:flex;flex-wrap:wrap;gap:6px}
        .chips li{border:1px solid var(--line);border-radius:12px;padding:1px 8px}a{color:inherit}
    """.trimIndent().replace("\n", " ")
}
