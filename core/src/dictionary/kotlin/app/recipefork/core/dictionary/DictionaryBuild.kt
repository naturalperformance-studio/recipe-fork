package app.recipefork.core.dictionary

import java.io.File
import kotlin.system.exitProcess

/** The tags source within the sources directory; every other `*.tsv` there is an ingredient family. */
const val TAG_FILE = "tags.tsv"

/**
 * Builds from the sources in [directory], checking ids against the snapshot already at [asset]; file names in messages
 * are given relative to [base].
 */
fun buildFrom(directory: File, asset: File, base: File = File(".")): BuildResult {
    fun name(file: File) = file.relativeToOrSelf(base.absoluteFile).path
    val sources = directory.absoluteFile.listFiles { file -> file.isFile && file.name.endsWith(".tsv") }.orEmpty()
        .sortedBy { it.name }
        .map { it to SourceFile(name(it), it.readText(Charsets.UTF_8)) }
    val tags = sources.firstOrNull { it.first.name == TAG_FILE }?.second
        ?: return BuildResult.Refused(listOf("${name(directory.absoluteFile)}: no $TAG_FILE"))
    val previous = asset.takeIf { it.isFile }?.let { DictionaryBuild.parseSnapshot(it.readText(Charsets.UTF_8)) }
    return DictionaryBuild.build(sources.filter { it.first.name != TAG_FILE }.map { it.second }, tags, previous, name(asset.absoluteFile))
}

/** `dictionary-build <sources directory> <asset>`: validates the sources and writes the asset, or names every refusal. */
fun main(args: Array<String>) {
    if (args.size != 2) {
        System.err.println("usage: dictionary-build <sources directory> <asset file>")
        exitProcess(2)
    }
    val (directory, asset) = args.map(::File)
    when (val result = buildFrom(directory, asset)) {
        is BuildResult.Refused -> {
            result.errors.forEach(System.err::println)
            System.err.println("${result.errors.size} problem(s); ${asset.path} left as it was")
            exitProcess(1)
        }
        is BuildResult.Built -> {
            asset.absoluteFile.parentFile.mkdirs()
            val temporary = File(asset.absoluteFile.parentFile, asset.name + ".tmp")
            temporary.writeText(result.text, Charsets.UTF_8)
            if (!temporary.renameTo(asset.absoluteFile)) {
                asset.writeText(result.text, Charsets.UTF_8)
                temporary.delete()
            }
            val live = result.dictionaries.ingredients.count { !it.removed }
            println(
                "${asset.path}: ${result.dictionaries.ingredients.size} ingredients ($live live), " +
                    "${result.dictionaries.tags.size} tags, version ${result.dictionaries.version}",
            )
        }
    }
}
