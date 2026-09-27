package app.recipefork.server.account

import app.recipefork.server.db.TestDatabase
import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The local deployment's placeholder word lists: marked as placeholders, and giving nicknames of the right form. */
class PlaceholderSeedTest {
    private lateinit var db: HikariDataSource

    @BeforeEach
    fun setUp() {
        db = TestDatabase.fresh()
    }

    @AfterEach
    fun tearDown() = db.close()

    @Test
    fun `the placeholder seed says it is a placeholder, loads twice without change, and yields nicknames of the AUTH-022 form`() {
        assertTrue(File("../deploy/local/seed/nickname-words.sql").readText().contains("PLACEHOLDER"))

        db.loadPlaceholderSeed()
        val adjectives = db.count("nickname_adjective")
        val nouns = db.count("nickname_noun")
        db.loadPlaceholderSeed()
        assertEquals(adjectives to nouns, db.count("nickname_adjective") to db.count("nickname_noun"))
        assertTrue(adjectives > 1 && nouns > 1)

        val generator = NicknameGenerator(AccountStore(db))
        repeat(200) {
            val nickname = assertNotNull(generator.generate()).display
            assertTrue(Regex("^[A-Z][a-z]+ [A-Z][a-z]+ [A-Z][a-z]+$").matches(nickname), nickname)
        }
    }
}
