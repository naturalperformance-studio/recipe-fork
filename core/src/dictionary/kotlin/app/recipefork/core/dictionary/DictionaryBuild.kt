package app.recipefork.core.dictionary

import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/** Where the sources live, relative to the repository root. */
const val SOURCES = "dictionary"

/** Where the generated snapshot lives, relative to the repository root. */
const val ASSET = "app/src/main/assets/dictionaries.json"

/** What reading the baseline gave: the shipped snapshot, or why there is none. */
sealed interface BaselineRead {
    class Found(val baseline: Baseline) : BaselineRead
    class Missing(val reason: String) : BaselineRead
}

/**
 * The snapshot as committed in the git repository at [root], never the working-tree file (which a merge or a careless
 * regeneration may have replaced): [path] at the merge base of `HEAD` and `develop` when a `develop` branch exists, so
 * that what develop has shipped is the baseline of every task branch, else at `HEAD`.
 */
fun gitBaseline(root: File, path: String = ASSET): BaselineRead {
    fun git(vararg args: String): String? = try {
        val process = ProcessBuilder(listOf("git") + args).directory(root)
            .redirectError(ProcessBuilder.Redirect.DISCARD).start()
        val out = process.inputStream.readBytes().toString(Charsets.UTF_8)
        if (process.waitFor(60, TimeUnit.SECONDS) && process.exitValue() == 0) out else null
    } catch (e: java.io.IOException) {
        null
    }

    val developExists = git("rev-parse", "--verify", "--quiet", "refs/heads/develop") != null
    val ref = if (developExists) {
        git("merge-base", "HEAD", "develop")?.trim() ?: return BaselineRead.Missing("HEAD and develop have no merge base")
    } else {
        "HEAD"
    }
    val commit = git("rev-parse", "--verify", "--quiet", "$ref^{commit}")?.trim()
        ?: return BaselineRead.Missing("no git commit at $ref in ${root.absolutePath}")
    val short = commit.take(7)
    val where = if (developExists) "$path@$short (merge base with develop)" else "$path@$short (HEAD)"
    val text = git("show", "$commit:$path") ?: return BaselineRead.Missing("$path is not committed at $short")
    return try {
        BaselineRead.Found(Baseline(where, DictionaryBuild.parseSnapshot(text)))
    } catch (e: IllegalArgumentException) {
        BaselineRead.Missing("$where is not a readable snapshot: ${e.message}")
    }
}

/**
 * Builds from the sources under [root]/[SOURCES], checking them against what [baseline] reads. With no baseline the
 * build is refused unless [firstBuild] says no dictionary was ever shipped.
 */
fun buildFrom(root: File, baseline: () -> BaselineRead = { gitBaseline(root) }, firstBuild: Boolean = false): BuildResult {
    val sources = File(root, SOURCES).listFiles { file -> file.isFile && file.name.endsWith(".tsv") }.orEmpty()
        .sortedBy { it.name }
        .map { SourceFile("$SOURCES/${it.name}", it.readText(Charsets.UTF_8)) }
    val shipped = when (val read = baseline()) {
        is BaselineRead.Found -> read.baseline
        is BaselineRead.Missing -> if (firstBuild) {
            null
        } else {
            return BuildResult.Refused(
                listOf(
                    "$ASSET: no shipped snapshot to check the ids against (${read.reason}); pass --first-build " +
                        "only when no dictionary was ever shipped",
                ),
            )
        }
    }
    return DictionaryBuild.build(sources, shipped)
}

/** [buildFrom], then, when it builds, writes [root]/[ASSET]; the asset is left as it was on a refusal. */
fun generate(root: File, baseline: () -> BaselineRead = { gitBaseline(root) }, firstBuild: Boolean = false): BuildResult {
    val result = buildFrom(root, baseline, firstBuild)
    if (result is BuildResult.Built) {
        val asset = File(root, ASSET)
        asset.parentFile.mkdirs()
        val temporary = File(asset.parentFile, asset.name + ".tmp")
        temporary.writeText(result.text, Charsets.UTF_8)
        if (!temporary.renameTo(asset)) {
            asset.writeText(result.text, Charsets.UTF_8)
            temporary.delete()
        }
    }
    return result
}

/** `dictionary-build <repository root> [--first-build]`: validates the sources and writes the asset, or names every refusal. */
fun main(args: Array<String>) {
    val root = args.firstOrNull()?.let(::File)
    if (root == null || args.size > 2 || (args.size == 2 && args[1] != "--first-build")) {
        System.err.println("usage: dictionary-build <repository root> [--first-build]")
        exitProcess(2)
    }
    when (val result = generate(root, firstBuild = "--first-build" in args)) {
        is BuildResult.Refused -> {
            result.errors.forEach(System.err::println)
            System.err.println("${result.errors.size} problem(s); $ASSET left as it was")
            exitProcess(1)
        }
        is BuildResult.Built -> {
            val dictionaries = result.dictionaries
            println(
                "$ASSET: ${dictionaries.ingredients.size} ingredients (${dictionaries.ingredients.count { !it.removed }} live), " +
                    "${dictionaries.tags.size} tags, version ${dictionaries.version}",
            )
        }
    }
}
