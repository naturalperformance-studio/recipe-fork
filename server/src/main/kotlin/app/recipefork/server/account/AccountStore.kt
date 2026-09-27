package app.recipefork.server.account

import org.postgresql.util.PSQLException
import java.sql.Connection
import java.sql.ResultSet
import java.security.SecureRandom
import javax.sql.DataSource

/** The two nickname word lists as the store holds them now (AUTH-023). */
data class NicknameWords(val adjectives: List<String>, val nouns: List<String>)

/** What confirming creation came to. */
sealed interface CreationResult {
    /** Created; [muted] unless a slot was occupied for it (AUTH-004). */
    data class Created(val accountId: Long, val nickname: Nickname, val muted: Boolean) : CreationResult
    data object NicknameNotAdmitted : CreationResult
    data object NicknameTaken : CreationResult
    data object KeyTaken : CreationResult

    /** The account could be created, but the slot could not be occupied, so nothing was stored. */
    data object SlotNotOccupied : CreationResult
}

/**
 * Accounts and the nickname word lists. The word lists are only read: the product owner changes
 * them directly in the store (AUTH-023 (2), DOM-014). An account is written in one place only,
 * [create], which is the confirming call of creation (AUTH-007 (7)).
 */
class AccountStore(private val dataSource: DataSource) {

    fun words(): NicknameWords = dataSource.connection.use { conn ->
        NicknameWords(
            adjectives = conn.query("select word from nickname_adjective order by word") { it.getString(1) },
            nouns = conn.query("select word from nickname_noun order by word") { it.getString(1) },
        )
    }

    fun isTaken(nickname: Nickname): Boolean = dataSource.connection.use { conn ->
        conn.query(
            "select 1 from account where nickname_first = ? and nickname_second = ? and nickname_noun = ?",
            nickname.first, nickname.second, nickname.noun,
        ) { true }.isNotEmpty()
    }

    /**
     * Creates the account in one transaction: the nickname must be two adjectives and a noun from
     * the current lists, and must be held by no account; the keys are stored as digests only. The
     * store's unique keys decide between two creations at the same moment (AUTH-018 (5)).
     *
     * [occupySlot], when given, is called once everything else has been written and before the commit, so a
     * slot is occupied only for an account that is then created (AUTH-001 (3)); if it returns false, nothing
     * is stored. Nothing it involves is written here (AUTH-015).
     */
    fun create(
        nickname: Nickname,
        termsVersion: String,
        keys: KeySet,
        occupySlot: (() -> Boolean)? = null,
    ): CreationResult =
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                var result = conn.createIn(nickname, termsVersion, keys)
                if (result is CreationResult.Created && occupySlot != null) {
                    result = if (occupySlot()) result.copy(muted = false) else CreationResult.SlotNotOccupied
                }
                if (result is CreationResult.Created) conn.commit() else conn.rollback()
                result
            } catch (e: PSQLException) {
                conn.rollback()
                when (e.serverErrorMessage?.constraint) {
                    "account_nickname_unique" -> CreationResult.NicknameTaken
                    "account_key_pkey" -> CreationResult.KeyTaken
                    else -> throw e
                }
            } catch (e: Exception) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = true
            }
        }

    private fun Connection.createIn(nickname: Nickname, termsVersion: String, keys: KeySet): CreationResult {
        val admitted = query(
            """
            select exists (select 1 from nickname_adjective where word = ?)
               and exists (select 1 from nickname_adjective where word = ?)
               and exists (select 1 from nickname_noun where word = ?)
            """.trimIndent(),
            nickname.first, nickname.second, nickname.noun,
        ) { it.getBoolean(1) }.single()
        if (!admitted) return CreationResult.NicknameNotAdmitted

        val accountId = query(
            """
            insert into account (nickname_first, nickname_second, nickname_noun, terms_version, terms_accepted_on)
            values (?, ?, ?, ?, current_date)
            returning account_id
            """.trimIndent(),
            nickname.first, nickname.second, nickname.noun, termsVersion,
        ) { it.getLong(1) }.single()

        prepareStatement("insert into account_key (digest, account_id, kind) values (?, ?, ?)").use { stmt ->
            for (key in keys.all) {
                stmt.setBytes(1, key.digest)
                stmt.setLong(2, accountId)
                stmt.setString(3, key.kind.storedName)
                stmt.executeUpdate()
            }
        }
        return CreationResult.Created(accountId, nickname, muted = true)
    }

    private fun <T> Connection.query(sql: String, vararg params: Any, row: (ResultSet) -> T): List<T> =
        prepareStatement(sql).use { stmt ->
            params.forEachIndexed { i, p -> stmt.setObject(i + 1, p) }
            stmt.executeQuery().use { rs -> buildList { while (rs.next()) add(row(rs)) } }
        }
}

/**
 * Generates nicknames from the current word lists (AUTH-022, AUTH-023). A combination of admitted
 * words is checked against nothing but the accounts that exist, so that a nickname already held is
 * not offered; there is no list of forbidden combinations (AUTH-023 (3)). Generating stores
 * nothing, so a nickname offered or rerolled is not consumed (AUTH-018 (3), DOM-001 (3)).
 */
class NicknameGenerator(
    private val store: AccountStore,
    private val random: SecureRandom = SecureRandom(),
    private val attempts: Int = 256,
) {
    /** A nickname no account holds, or null when none was found (the lists' size is OQ-009). */
    fun generate(): Nickname? {
        val words = store.words()
        if (words.adjectives.isEmpty() || words.nouns.isEmpty()) return null
        repeat(attempts) {
            val candidate = Nickname(
                words.adjectives[random.nextInt(words.adjectives.size)],
                words.adjectives[random.nextInt(words.adjectives.size)],
                words.nouns[random.nextInt(words.nouns.size)],
            )
            if (!store.isTaken(candidate)) return candidate
        }
        return null
    }
}
