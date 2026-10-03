package app.recipefork.core.search

/**
 * What a filter on the ingredient dictionary entry [entry] matches (CAT-006): that entry and every entry below it, a
 * child, a child's child and so on; [parentOf] gives each entry's parent, null for an entry at the top. A parent is no
 * evidence of any particular child, so a filter on a child matches only it. A line named by free text carries no entry
 * and so matches no filter.
 */
fun ingredientFamily(entry: Long, parentOf: Map<Long, Long?>): Set<Long> {
    val children = parentOf.entries.filter { it.value != null }.groupBy({ it.value!! }, { it.key })
    val family = linkedSetOf(entry)
    val pending = ArrayDeque(listOf(entry))
    while (pending.isNotEmpty()) {
        children[pending.removeFirst()].orEmpty().forEach { if (family.add(it)) pending.addLast(it) }
    }
    return family
}
