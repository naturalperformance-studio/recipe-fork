package app.recipefork.core.dictionary

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Ids are checked against the snapshot as committed in git, so neither deleting the generated asset nor regenerating
 * it lets a dropped id through. Each test works in a throwaway repository.
 */
class GitBaselineTest {
    private val repo: File = Files.createTempDirectory("dictionary-baseline").toFile()

    @AfterTest
    fun cleanUp() {
        repo.deleteRecursively()
    }

    private fun git(vararg args: String): String {
        val command = listOf(
            "git", "-c", "user.name=test", "-c", "user.email=test@example.com", "-c", "commit.gpgsign=false",
            "-c", "init.defaultBranch=main", "-c", "core.hooksPath=/dev/null",
        ) + args
        val process = ProcessBuilder(command).directory(repo).redirectErrorStream(true).start()
        val out = process.inputStream.readBytes().toString(Charsets.UTF_8)
        check(process.waitFor() == 0) { "$command failed: $out" }
        return out
    }

    private fun write(path: String, text: String) = File(repo, path).apply { parentFile.mkdirs() }.writeText(text)

    private fun animal(vararg lines: String) =
        write("$SOURCES/animal.tsv", "@range 3000-4999\n@range 1-5\n" + lines.joinToString("") { "$it\n" })

    private fun commitAll(message: String) {
        git("add", "-A")
        git("commit", "-q", "-m", message)
    }

    /** A repository whose committed snapshot holds ids 1, 2 and the tag 10. */
    private fun shipped() {
        git("init", "-q")
        write("$SOURCES/tags.tsv", "@range 10-11\n10\tитальянская\tItalian\n")
        animal("1\tкурица\tchicken", "2\tмясо\tmeat")
        assertIs<BuildResult.Built>(generate(repo, firstBuild = true))
        commitAll("shipped")
    }

    private fun assertRefusedFor(result: BuildResult, what: String) {
        val errors = assertIs<BuildResult.Refused>(result).errors
        assertTrue(errors.any { what in it }, errors.joinToString("\n"))
    }

    @Test
    fun aDroppedIdIsRefusedEvenWithTheAssetDeleted() {
        shipped()
        animal("1\tкурица\tchicken")
        File(repo, ASSET).delete()
        assertRefusedFor(generate(repo), "ingredient id 2 was shipped")
        assertTrue(!File(repo, ASSET).exists())
    }

    @Test
    fun aDroppedIdIsRefusedOnEveryRegeneration() {
        shipped()
        animal("1\tкурица\tchicken")
        val before = File(repo, ASSET).readText()
        assertRefusedFor(generate(repo), "ingredient id 2 was shipped")
        assertRefusedFor(generate(repo), "ingredient id 2 was shipped")
        assertEquals(before, File(repo, ASSET).readText())
        // Marked removed instead, it builds.
        animal("1\tкурица\tchicken", "2\tмясо\tmeat\t\t\t\tremoved")
        assertIs<BuildResult.Built>(generate(repo))
    }

    @Test
    fun withoutAnyCommittedSnapshotTheBuildIsRefusedUnlessItIsTheFirst() {
        write("$SOURCES/tags.tsv", "@range 10-11\n10\tитальянская\tItalian\n")
        animal("1\tкурица\tchicken")
        // Not a repository at all.
        assertIs<BaselineRead.Missing>(gitBaseline(repo))
        assertRefusedFor(generate(repo), "no shipped snapshot")
        // A repository with no commit.
        git("init", "-q")
        assertRefusedFor(generate(repo), "no shipped snapshot")
        assertIs<BuildResult.Built>(generate(repo, firstBuild = true))
        // A commit without the asset.
        git("add", SOURCES)
        git("commit", "-q", "-m", "sources only")
        assertRefusedFor(generate(repo), "is not committed at")
    }

    @Test
    fun onATaskBranchTheBaselineIsWhatDevelopShipped() {
        shipped()
        git("branch", "develop")
        git("checkout", "-q", "-b", "task")
        // The task adds 3000 and commits; the id is not shipped until develop has it.
        animal("1\tкурица\tchicken", "2\tмясо\tmeat", "3000\tяйцо\tegg")
        assertIs<BuildResult.Built>(generate(repo))
        commitAll("task adds an egg")
        val found = assertIs<BaselineRead.Found>(gitBaseline(repo))
        assertTrue("merge base with develop" in found.baseline.name, found.baseline.name)
        assertEquals(listOf(1L, 2L), found.baseline.dictionaries.ingredients.map { it.id })
        // So the task may still drop its own 3000, but never 2, which develop shipped.
        animal("1\tкурица\tchicken", "2\tмясо\tmeat")
        assertIs<BuildResult.Built>(buildFrom(repo))
        animal("1\tкурица\tchicken", "3000\tяйцо\tegg")
        assertRefusedFor(buildFrom(repo), "ingredient id 2 was shipped")
    }

    @Test
    fun withoutDevelopTheBaselineIsHead() {
        shipped()
        animal("1\tкурица\tchicken", "2\tмясо\tmeat", "3000\tяйцо\tegg")
        assertIs<BuildResult.Built>(generate(repo))
        commitAll("an egg")
        val found = assertIs<BaselineRead.Found>(gitBaseline(repo))
        assertTrue("(HEAD)" in found.baseline.name, found.baseline.name)
        animal("1\tкурица\tchicken", "2\tмясо\tmeat")
        assertRefusedFor(buildFrom(repo), "ingredient id 3000 was shipped")
    }

    @Test
    fun aReusedIdIsRefusedAgainstTheCommittedSnapshot() {
        shipped()
        animal("1\tкурица\tchicken", "2\tрыба\tfish")
        assertRefusedFor(generate(repo), "id 2 was shipped as 'мясо' / 'meat'")
    }
}
